/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.parser.flush.impl;

import org.dbsyncer.common.QueueOverflowException;
import org.dbsyncer.common.config.GeneralBufferConfig;
import org.dbsyncer.common.metric.TimeRegistry;
import org.dbsyncer.common.model.Result;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.connector.base.ConnectorFactory;
import org.dbsyncer.parser.ParserComponent;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.TableGroupContext;
import org.dbsyncer.parser.ddl.DDLParser;
import org.dbsyncer.parser.event.RefreshOffsetEvent;
import org.dbsyncer.parser.flush.AbstractBufferActuator;
import org.dbsyncer.parser.flush.WalWriter;
import org.dbsyncer.parser.model.Connector;
import org.dbsyncer.parser.model.Mapping;
import org.dbsyncer.parser.model.Meta;
import org.dbsyncer.parser.model.TableGroup;
import org.dbsyncer.parser.model.TableGroupPicker;
import org.dbsyncer.parser.model.WalEntry;
import org.dbsyncer.parser.model.WriterRequest;
import org.dbsyncer.parser.model.WriterResponse;
import org.dbsyncer.parser.strategy.FlushStrategy;
import org.dbsyncer.parser.util.ConnectorInstanceUtil;
import org.dbsyncer.parser.util.ConnectorServiceContextUtil;
import org.dbsyncer.parser.util.ConvertUtil;
import org.dbsyncer.plugin.PluginFactory;
import org.dbsyncer.plugin.enums.ProcessEnum;
import org.dbsyncer.plugin.impl.IncrementPluginContext;
import org.dbsyncer.sdk.config.DDLConfig;
import org.dbsyncer.sdk.connector.ConnectorInstance;
import org.dbsyncer.sdk.connector.DefaultConnectorServiceContext;
import org.dbsyncer.sdk.enums.ChangedEventTypeEnum;
import org.dbsyncer.sdk.model.ConnectorConfig;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.model.MetaInfo;
import org.dbsyncer.sdk.model.Table;
import org.dbsyncer.sdk.spi.ConnectorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * 通用执行器（单线程消费，多线程批量写，按序执行）
 *
 * @Version 1.0.0
 * @Author zhangxl
 * @Date 2026-06-02 14:25
 */
@Component
public class GeneralBufferActuator extends AbstractBufferActuator<WriterRequest, WriterResponse> {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private GeneralBufferConfig generalBufferConfig;

    @Resource
    private Executor generalExecutor;

    @Resource
    private ConnectorFactory connectorFactory;

    @Resource
    private ParserComponent parserComponent;

    @Resource
    private ProfileComponent profileComponent;

    @Resource
    private PluginFactory pluginFactory;

    @Resource
    private FlushStrategy flushStrategy;

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private DDLParser ddlParser;

    @Resource
    private TableGroupContext tableGroupContext;

    /**
     * WAL目录，可通过系统属性 dbsyncer.wal.dir 配置，默认 ./data/wal
     */
    private static final String WAL_DIR = System.getProperty("dbsyncer.wal.dir", "./data/wal");

    /**
     * metaId -> WalWriter 映射，每个驱动一个WAL文件
     */
    private final Map<String, WalWriter> walWriterMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        setConfig(generalBufferConfig);
        buildConfig();
    }

    @Override
    protected String getPartitionKey(WriterRequest request) {
        return request.getTableName() + ":" + request.getEvent();
    }

    @Override
    protected void partition(WriterRequest request, WriterResponse response) {
        if (!CollectionUtils.isEmpty(request.getRow())) {
            response.addData(request.getRow());
        }
        if (request.getChangedOffset() != null) {
            response.setChangedOffset(request.getChangedOffset());
        }
        if (!response.isMerged()) {
            response.setTraceId(request.getTraceId());
            response.setTableName(request.getTableName());
            response.setEvent(request.getEvent());
            response.setTypeEnum(request.getTypeEnum());
            response.setSql(request.getSql());
            response.setMerged(true);
        } else if (profileComponent.getSystemConfig().isEnablePrintTraceInfo() && StringUtil.isNotBlank(request.getTraceId())) {
            logger.info("traceId:{} merge into traceId:{}", request.getTraceId(), response.getTraceId());
        }
    }

    @Override
    protected boolean skipPartition(WriterRequest nextRequest, WriterResponse response) {
        // 跳过表结构修改事件（保证表结构修改原子性），事件类型分离已由二级分区处理
        return ChangedEventTypeEnum.isDDL(response.getTypeEnum());
    }

    @Override
    public void pull(WriterResponse response) {
        Meta meta = profileComponent.getMeta(response.getChangedOffset().getMetaId());
        if (meta == null) {
            return;
        }
        // 打印trace信息
        printTraceInfo(response);
        final Mapping mapping = profileComponent.getMapping(meta.getMappingId());
        List<TableGroupPicker> pickers = tableGroupContext.getTableGroupPickers(meta.getId(), response.getTableName());

        switch (response.getTypeEnum()) {
            case DDL:
                tableGroupContext.update(mapping, pickers.stream().map(picker -> {
                    TableGroup tableGroup = profileComponent.getTableGroup(picker.getTableGroup().getId());
                    parseDDl(response, mapping, tableGroup);
                    return tableGroup;
                }).collect(Collectors.toList()));
                break;
            case SCAN:
                pickers.forEach(picker -> distributeTableGroup(response, mapping, picker, picker.getSourceFields(), false));
                break;
            case ROW:
                pickers.forEach(picker -> distributeTableGroup(response, mapping, picker, picker.getTableGroup().getSourceTable().getColumn(), true));
                // 发布刷新增量点事件
                applicationContext.publishEvent(new RefreshOffsetEvent(applicationContext, response.getChangedOffset()));
                break;
            default:
                break;
        }
    }

    @Override
    protected void offerFailed(BlockingQueue<WriterRequest> queue, WriterRequest request) {
        throw new QueueOverflowException("缓存队列已满");
    }

    @Override
    protected void meter(TimeRegistry timeRegistry, long count) {
        // 统计执行器同步效率TPS
        timeRegistry.meter(TimeRegistry.GENERAL_BUFFER_ACTUATOR_TPS).add(count);
    }

    @Override
    public Executor getExecutor() {
        return generalExecutor;
    }

    private void distributeTableGroup(WriterResponse response, Mapping mapping, TableGroupPicker tableGroupPicker, List<Field> sourceFields, boolean enableFilter) {
        // 1、映射字段
        ConnectorConfig sourceConfig = getConnectorConfig(mapping.getSourceConnectorId());
        ConnectorService sourceConnector = connectorFactory.getConnectorService(sourceConfig.getConnectorType());
        List<Map> sourceDataList = new ArrayList<>();
        List<Map> targetDataList = tableGroupPicker.getPicker()
                .setSourceResolver(sourceConnector.getSchemaResolver())
                .pickTargetData(sourceFields, enableFilter, response.getDataList(), sourceDataList);
        if (CollectionUtils.isEmpty(targetDataList)) {
            return;
        }

        // 2、参数转换
        TableGroup tableGroup = tableGroupPicker.getTableGroup();
        ConvertUtil.convert(tableGroup.getConvert(), targetDataList);

        // 3、插件转换
        final IncrementPluginContext context = new IncrementPluginContext();
        String sourceInstanceId = ConnectorInstanceUtil.buildConnectorInstanceId(mapping.getId(), mapping.getSourceConnectorId(), ConnectorInstanceUtil.SOURCE_SUFFIX);
        String targetInstanceId = ConnectorInstanceUtil.buildConnectorInstanceId(mapping.getId(), mapping.getTargetConnectorId(), ConnectorInstanceUtil.TARGET_SUFFIX);
        context.setSourceConnectorInstance(connectorFactory.connect(sourceInstanceId));
        context.setTargetConnectorInstance(connectorFactory.connect(targetInstanceId));
        context.setSourceTableName(tableGroup.getSourceTable().getName());
        context.setTargetTableName(tableGroup.getTargetTable().getName());
        context.setTraceId(response.getTraceId());
        context.setEvent(response.getEvent());
        context.setTargetFields(tableGroupPicker.getTargetFields());
        context.setCommand(tableGroup.getCommand());
        context.setBatchSize(getBufferWriterCount());
        context.setSourceList(sourceDataList);
        context.setTargetList(targetDataList);
        context.setPlugin(tableGroup.getPlugin());
        context.setPluginExtInfo(tableGroup.getPluginExtInfo());
        context.setForceUpdate(mapping.isForceUpdate());
        context.setEnablePrintTraceInfo(StringUtil.isNotBlank(response.getTraceId()));
        pluginFactory.process(context, ProcessEnum.CONVERT);

        // 4、WAL追加（先写日志）
        WalEntry walEntry = null;
        String metaId = response.getChangedOffset().getMetaId();
        String binlogFile = response.getChangedOffset().getNextFileName();
        long binlogPos = resolveBinlogPosition(response.getChangedOffset().getPosition());
        WalWriter walWriter = getWalWriter(metaId);
        if (walWriter != null) {
            try {
                // 将目标数据转换为Object列表用于WAL记录
                List<Object> walRowData = new ArrayList<>(targetDataList);
                walEntry = walWriter.append(context.getTargetTableName(), response.getEvent(),
                        walRowData, binlogFile, binlogPos);
            } catch (Exception e) {
                logger.error("WAL追加失败: metaId={}, table={}", metaId, context.getTargetTableName(), e);
            }
        }

        // 5、批量执行同步
        Result result = parserComponent.writeBatch(context, getExecutor());

        // 6、WAL提交标记（写入目标端成功后）
        if (walEntry != null && walWriter != null) {
            walWriter.commit(walEntry.getSequence());
        }

        // 7、持久化同步结果
        result.setTableGroupId(tableGroup.getId());
        result.setTargetTableGroupName(context.getTargetTableName());
        flushStrategy.flushIncrementData(mapping.getMetaId(), result, response.getEvent());

        // 8、执行后置处理
        pluginFactory.process(context, ProcessEnum.AFTER);
    }

    /**
     * 解析DDL
     */
    private void parseDDl(WriterResponse response, Mapping mapping, TableGroup tableGroup) {
        try {
            ConnectorConfig sConnConfig = getConnectorConfig(mapping.getSourceConnectorId());
            ConnectorConfig tConnConfig = getConnectorConfig(mapping.getTargetConnectorId());
            String sConnType = sConnConfig.getConnectorType();
            String tConnType = tConnConfig.getConnectorType();
            ConnectorService connectorService = connectorFactory.getConnectorService(tConnType);
            DDLConfig targetDDLConfig = ddlParser.parse(connectorService, tableGroup, response.getSql());
            // 1.生成目标表执行SQL(暂支持同源)
            if (mapping.getListener().isEnableDDL() && StringUtil.equals(sConnType, tConnType)) {
                String instanceId = ConnectorInstanceUtil.buildConnectorInstanceId(mapping.getId(), mapping.getTargetConnectorId(), ConnectorInstanceUtil.TARGET_SUFFIX);
                ConnectorInstance tConnectorInstance = connectorFactory.connect(instanceId);
                Result result = connectorFactory.writerDDL(tConnectorInstance, targetDDLConfig);
                // 2.持久化增量事件数据
                result.setTableGroupId(tableGroup.getId());
                result.setTargetTableGroupName(tableGroup.getTargetTable().getName());
                flushStrategy.flushIncrementData(mapping.getMetaId(), result, response.getEvent());
            }

            // 3.更新表属性字段
            updateTableColumn(mapping, ConnectorInstanceUtil.SOURCE_SUFFIX, tableGroup.getSourceTable());
            updateTableColumn(mapping, ConnectorInstanceUtil.TARGET_SUFFIX, tableGroup.getTargetTable());

            // 4.更新表字段映射关系
            ddlParser.refreshFiledMappings(tableGroup, targetDDLConfig);

            // 5.更新执行命令
            tableGroup.setCommand(parserComponent.getCommand(mapping, tableGroup));

            // 6.持久化存储 & 更新缓存配置
            profileComponent.editTableGroup(tableGroup);

            // 7.发布更新事件
            applicationContext.publishEvent(new RefreshOffsetEvent(applicationContext, response.getChangedOffset()));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    private void updateTableColumn(Mapping mapping, String suffix, Table table) {
        boolean isSource = StringUtil.equals(ConnectorInstanceUtil.SOURCE_SUFFIX, suffix);
        DefaultConnectorServiceContext context = ConnectorServiceContextUtil.buildConnectorServiceContext(mapping, isSource);
        context.addTablePattern(table);

        List<MetaInfo> metaInfos = parserComponent.getMetaInfo(context);
        MetaInfo metaInfo = CollectionUtils.isEmpty(metaInfos) ? null : metaInfos.get(0);
        Assert.notNull(metaInfo, "无法获取连接器表信息:" + table.getName());
        table.setColumn(metaInfo.getColumn());
    }

    /**
     * 获取连接器配置
     */
    private ConnectorConfig getConnectorConfig(String connectorId) {
        Assert.hasText(connectorId, "Connector id can not be empty.");
        Connector conn = profileComponent.getConnector(connectorId);
        Assert.notNull(conn, "Connector can not be null.");
        return conn.getConfig();
    }

    /**
     * 获取或创建metaId对应的WalWriter
     */
    private WalWriter getWalWriter(String metaId) {
        return walWriterMap.computeIfAbsent(metaId, id -> {
            try {
                return new WalWriter(WAL_DIR, id);
            } catch (IOException e) {
                logger.error("创建WalWriter失败: metaId={}, dir={}", id, WAL_DIR, e);
                return null;
            }
        });
    }

    /**
     * 关闭并移除metaId对应的WalWriter（驱动停止时调用）
     */
    public void closeWalWriter(String metaId) {
        WalWriter writer = walWriterMap.remove(metaId);
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                logger.error("关闭WalWriter失败: metaId={}", metaId, e);
            }
        }
    }

    /**
     * 解析binlog位点为long值
     */
    private long resolveBinlogPosition(Object position) {
        if (position instanceof Number) {
            return ((Number) position).longValue();
        }
        if (position instanceof String) {
            try {
                return Long.parseLong((String) position);
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        return 0L;
    }

    private void printTraceInfo(WriterResponse response) {
        if (profileComponent.getSystemConfig().isEnablePrintTraceInfo() && StringUtil.isNotBlank(response.getTraceId())) {
            logger.info("traceId:{}, tableName:{}, event:{}, offset:{}, row:{}", response.getTraceId(), response.getTableName(), response.getEvent(), JsonUtil.objToJson(response.getChangedOffset()), response.getDataList());
        }
    }

}