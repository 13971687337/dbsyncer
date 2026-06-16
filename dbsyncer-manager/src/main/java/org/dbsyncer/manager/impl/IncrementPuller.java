/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.manager.impl;


import org.dbsyncer.common.scheduled.ScheduledTaskJob;
import org.dbsyncer.common.scheduled.ScheduledTaskService;
import org.dbsyncer.connector.base.ConnectorFactory;
import org.dbsyncer.connector.mysql.cdc.MySQLListener;
import org.dbsyncer.connector.mysql.cdc.SharedBinlogConsumer;
import org.dbsyncer.manager.AbstractPuller;
import org.dbsyncer.manager.ManagerException;
import org.dbsyncer.parser.LogService;
import org.dbsyncer.parser.LogType;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.TableGroupContext;
import org.dbsyncer.parser.consumer.ParserConsumer;
import org.dbsyncer.parser.event.RefreshOffsetEvent;
import org.dbsyncer.parser.flush.WalRecovery;
import org.dbsyncer.parser.flush.impl.BufferActuatorRouter;
import org.dbsyncer.parser.flush.impl.GeneralBufferActuator;
import org.dbsyncer.parser.model.Connector;
import org.dbsyncer.parser.model.Mapping;
import org.dbsyncer.parser.model.Meta;
import org.dbsyncer.parser.model.Picker;
import org.dbsyncer.parser.model.TableGroup;
import org.dbsyncer.parser.model.WalEntry;
import org.dbsyncer.parser.util.ConnectorInstanceUtil;
import org.dbsyncer.parser.util.PickerUtil;
import org.dbsyncer.plugin.PluginFactory;
import org.dbsyncer.sdk.config.DatabaseConfig;
import org.dbsyncer.sdk.config.ListenerConfig;
import org.dbsyncer.sdk.constant.ConnectorConstant;
import org.dbsyncer.sdk.enums.ListenerTypeEnum;
import org.dbsyncer.sdk.enums.TableTypeEnum;
import org.dbsyncer.sdk.listener.AbstractListener;
import org.dbsyncer.sdk.listener.AbstractQuartzListener;
import org.dbsyncer.sdk.listener.Listener;
import org.dbsyncer.sdk.model.ChangedOffset;
import org.dbsyncer.sdk.model.ConnectorConfig;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.model.Table;
import org.dbsyncer.sdk.model.TableGroupQuartzCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 增量同步
 *
 * @Version 1.0.0
 * @Author zhangxl
 * @Date 2026-06-02 14:25
 */
@Component
public final class IncrementPuller extends AbstractPuller implements ApplicationListener<RefreshOffsetEvent>, ScheduledTaskJob {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private BufferActuatorRouter bufferActuatorRouter;

    @Resource
    private ScheduledTaskService scheduledTaskService;

    @Resource
    private ConnectorFactory connectorFactory;

    @Resource
    private ProfileComponent profileComponent;

    @Resource
    private PluginFactory pluginFactory;

    @Resource
    private LogService logService;


    @Resource
    private TableGroupContext tableGroupContext;

    @Resource
    private GeneralBufferActuator generalBufferActuator;

    /**
     * WAL目录，可通过系统属性 dbsyncer.wal.dir 配置，默认 ./data/wal
     */
    private static final String WAL_DIR = System.getProperty("dbsyncer.wal.dir", "./data/wal");

    private final Map<String, Listener> map = new ConcurrentHashMap<>();

    @PostConstruct
    private void init() {
        scheduledTaskService.start(3000, this);
    }

    @Override
    public void start(Mapping mapping) {
        final String mappingId = mapping.getId();
        final String metaId = mapping.getMetaId();
        Connector connector = profileComponent.getConnector(mapping.getSourceConnectorId());
        Assert.notNull(connector, "连接器不能为空.");
        Connector targetConnector = profileComponent.getConnector(mapping.getTargetConnectorId());
        Assert.notNull(targetConnector, "目标连接器不能为空.");
        List<TableGroup> list = profileComponent.getSortedTableGroupAll(mappingId);
        Assert.notEmpty(list, "表映射关系不能为空，请先添加源表到目标表关系.");
        Meta meta = profileComponent.getMeta(metaId);
        Assert.notNull(meta, "Meta不能为空.");

        // 检测MySQL共享binlog模式：同一源 host:port/database 的多个Mapping复用单条binlog连接
        final SharedBinlogConsumer sharedConsumer = buildSharedBinlogConsumer(connector, mapping);

        Thread worker = new Thread(() -> {
            try {
                // 使用compute代替computeIfAbsent，以便区分「新建」和「已存在」
                final boolean[] isNew = {false};
                Listener listener = map.compute(metaId, (k, existing) -> {
                    if (existing != null) {
                        return existing;
                    }
                    isNew[0] = true;
                    logger.info("开始增量同步：{}, {}", metaId, mapping.getName());
                    long now = Instant.now().toEpochMilli();
                    meta.setBeginTime(now);
                    meta.setEndTime(now);
                    profileComponent.editConfigModel(meta);
                    tableGroupContext.put(mapping, list);
                    Listener l = getListener(mapping, connector, targetConnector, list, meta);

                    // 配置MySQL共享binlog消费者（仅新建Listener时）
                    if (sharedConsumer != null && l instanceof MySQLListener) {
                        MySQLListener mysqlListener = (MySQLListener) l;
                        mysqlListener.setSharedBinlogConsumer(sharedConsumer);

                        // 贡献本Mapping的快照位点，取所有Mapping的最小位点作为启动位点
                        Map<String, String> snapshot = meta.getSnapshot();
                        if (snapshot != null && snapshot.containsKey("fileName")) {
                            long pos = snapshot.containsKey("position")
                                    ? Long.parseLong(snapshot.get("position")) : 0;
                            sharedConsumer.setStartupSnapshot(snapshot.get("fileName"), pos);
                        }
                    }

                    return l;
                });

                if (listener != null) {
                    // WAL恢复：启动前重放未提交的记录
                    recoverWal(metaId);
                    listener.start();
                }

                // 共享消费者统一启动（仅新建Listener时调用；synchronized + started标记保证只连接一次）
                if (isNew[0] && sharedConsumer != null && !sharedConsumer.isStarted()) {
                    sharedConsumer.start();
                }
            } catch (Exception e) {
                close(metaId);
                logService.log(LogType.TableGroupLog.INCREMENT_FAILED, String.format("启动驱动失败：[%s], %s", mapping.getName(), e.getMessage()));
                logger.error("运行异常，结束增量同步：{}", metaId, e);
            }
        });
        worker.setName("increment-worker-" + mapping.getId());
        worker.setDaemon(false);
        worker.start();
    }

    /**
     * 为MySQL源构建共享Binlog消费者。
     * <p>当源连接器类型为MySQL时，创建或获取对应的 SharedBinlogConsumer 实例，
     * 使得指向同一 host:port/database 的多个Mapping共享单条binlog连接。</p>
     *
     * @param connector 源连接器
     * @param mapping   当前Mapping
     * @return SharedBinlogConsumer实例，非MySQL源返回null
     */
    private SharedBinlogConsumer buildSharedBinlogConsumer(Connector connector, Mapping mapping) {
        ConnectorConfig connectorConfig = connector.getConfig();
        if ("MySQL".equals(connectorConfig.getConnectorType()) && connectorConfig instanceof DatabaseConfig) {
            DatabaseConfig dbConfig = (DatabaseConfig) connectorConfig;
            return SharedBinlogConsumer.getOrCreate(
                    dbConfig.getHost(), dbConfig.getPort(),
                    dbConfig.getUsername(), dbConfig.getPassword(),
                    mapping.getSourceDatabase()
            );
        }
        return null;
    }

    @Override
    public void close(String metaId) {
        map.compute(metaId, (k, listener) -> {
            if (listener != null) {
                listener.forceFlushEvent();
                listener.close();
            }
            bufferActuatorRouter.unbind(metaId);
            tableGroupContext.clear(metaId);
            // 清理WAL写入器
            generalBufferActuator.closeWalWriter(metaId);
            publishClosedEvent(metaId);
            logger.info("关闭成功:{}", metaId);
            return null;
        });
    }

    @Override
    public void onApplicationEvent(RefreshOffsetEvent event) {
        ChangedOffset offset = event.getChangedOffset();
        if (offset != null && map.containsKey(offset.getMetaId())) {
            map.get(offset.getMetaId()).refreshEvent(offset);
        }
    }

    @Override
    public void run() {
        // 定时同步增量信息
        map.values().forEach(Listener::flushEvent);
    }

    private Listener getListener(Mapping mapping, Connector connector, Connector targetConnector, List<TableGroup> list, Meta meta) {
        ConnectorConfig connectorConfig = connector.getConfig();
        ListenerConfig listenerConfig = mapping.getListener();
        String listenerType = listenerConfig.getListenerType();

        Listener listener = connectorFactory.getListener(connectorConfig.getConnectorType(), listenerType);
        if (null == listener) {
            throw new ManagerException(String.format("Unsupported listener type \"%s\".", connectorConfig.getConnectorType()));
        }
        listener.register(new ParserConsumer(bufferActuatorRouter, profileComponent, pluginFactory, logService, (metaId, event) -> {}, meta.getId(), list));

        // 默认定时抽取
        if (ListenerTypeEnum.isTiming(listenerType) && listener instanceof AbstractQuartzListener) {
            AbstractQuartzListener quartzListener = (AbstractQuartzListener) listener;
            List<TableGroupQuartzCommand> quartzCommands = list.stream().map(t -> {
                final TableGroup group = PickerUtil.mergeTableGroupConfig(mapping, t);
                final Picker picker = new Picker(group);
                List<Field> fields = picker.getSourceFields();
                Assert.notEmpty(fields, "表字段映射关系不能为空：" + group.getSourceTable().getName() + " > " + group.getTargetTable().getName());
                return new TableGroupQuartzCommand(t.getSourceTable(), fields, t.getTargetTable(), t.getCommand(), group.getPlugin(), group.getPluginExtInfo());
            }).collect(Collectors.toList());
            quartzListener.setMappingName(mapping.getName());
            quartzListener.setCommands(quartzCommands);
        }

        if (listener instanceof AbstractListener) {
            AbstractListener abstractListener = (AbstractListener) listener;
            Set<String> filterTable = new HashSet<>();
            List<Table> sourceTable = new ArrayList<>();
            List<Table> customTable = new ArrayList<>();
            list.forEach(t -> addSourceTable(sourceTable, customTable, filterTable, t.getSourceTable()));
            abstractListener.setDatabase(mapping.getSourceDatabase());
            abstractListener.setSchema(mapping.getSourceSchema());
            abstractListener.setConnectorService(connectorFactory.getConnectorService(connectorConfig.getConnectorType()));
            String sourceInstanceId = ConnectorInstanceUtil.buildConnectorInstanceId(mapping.getId(), connector.getId(), ConnectorInstanceUtil.SOURCE_SUFFIX);
            String targetInstanceId = ConnectorInstanceUtil.buildConnectorInstanceId(mapping.getId(), targetConnector.getId(), ConnectorInstanceUtil.TARGET_SUFFIX);
            abstractListener.setConnectorInstance(connectorFactory.connect(sourceInstanceId));
            abstractListener.setTargetConnectorInstance(connectorFactory.connect(targetInstanceId));
            abstractListener.setScheduledTaskService(scheduledTaskService);
            abstractListener.setConnectorConfig(connectorConfig);
            abstractListener.setListenerConfig(listenerConfig);
            abstractListener.setFilterTable(filterTable);
            abstractListener.setSourceTable(sourceTable);
            abstractListener.setCustomTable(customTable);
            abstractListener.setSnapshot(meta.getSnapshot());
            abstractListener.setMetaId(meta.getId());
        }

        listener.init();
        return listener;
    }

    /**
     * WAL原子提交恢复：扫描WAL文件，重放未提交的记录。
     * <p>恢复完成后WAL文件被自动截断，源端从最后提交的binlog位点继续消费。</p>
     * <p>重放的记录由binlog消费者重新处理，依赖目标端UPSERT保证幂等。</p>
     */
    private void recoverWal(String metaId) {
        try {
            WalRecovery.RecoveryResult result = WalRecovery.recover(WAL_DIR, metaId);
            List<WalEntry> uncommitted = result.getUncommitted();
            if (uncommitted.isEmpty()) {
                return;
            }
            logger.info("WAL恢复开始: metaId={}, 重放={}, 跳过={}, 损坏={}",
                    metaId, result.getReplayed(), result.getSkipped(), result.getCorrupted());
            for (int i = 0; i < uncommitted.size(); i++) {
                WalEntry entry = uncommitted.get(i);
                logger.info("WAL恢复进度 [{}/{}]: table={}, event={}, binlog={}:{}",
                        i + 1, uncommitted.size(), entry.getTableName(),
                        entry.getEvent(), entry.getBinlogFile(), entry.getBinlogPosition());
            }
            logger.info("WAL恢复完成: metaId={}, 共{}条未提交记录（将在下次binlog消费时重新处理，WAL已截断）", metaId, result.getReplayed());
        } catch (Exception e) {
            logger.error("WAL恢复异常: metaId={}", metaId, e);
        }
    }

    private void addSourceTable(List<Table> sourceTable, List<Table> customTable, Set<String> filterTable, Table table) {
        switch (TableTypeEnum.getTableType(table.getType())) {
            case TABLE:
            case VIEW:
            case MATERIALIZED_VIEW:
                if (!filterTable.contains(table.getName())) {
                    sourceTable.add(table);
                    filterTable.add(table.getName());
                }
                break;
            case SQL:
            case SEMI:
                if (!filterTable.contains(table.getName())) {
                    customTable.add(table);
                    filterTable.add(table.getName());
                    Object mainTable = table.getExtInfo().get(ConnectorConstant.CUSTOM_TABLE_MAIN);
                    if (mainTable instanceof String) {
                        filterTable.add(String.valueOf(mainTable));
                    }
                }
                break;
            default:
                break;
        }
    }

}