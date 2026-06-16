/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.parser.flush.impl;

import org.dbsyncer.common.config.TableGroupBufferConfig;
import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.common.util.UUIDUtil;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.flush.AbstractBufferActuator;
import org.dbsyncer.parser.model.TableGroup;
import org.dbsyncer.parser.model.WriterRequest;
import org.dbsyncer.sdk.enums.ChangedEventTypeEnum;
import org.dbsyncer.sdk.listener.ChangedEvent;
import org.dbsyncer.sdk.spi.TableGroupBufferActuatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 缓存执行器路由
 *
 * @Version 1.0.0
 * @Author zhangxl
 * @Date 2026-06-02 14:25
 */
@Component
public final class BufferActuatorRouter implements DisposableBean {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private ProfileComponent profileComponent;

    @Resource
    private TableGroupBufferActuatorService tableGroupBufferActuatorService;

    @Resource
    private GeneralBufferActuator generalBufferActuator;

    @Resource
    private TableGroupBufferConfig tableGroupBufferConfig;

    /**
     * 串行模式Actuator key前缀
     */
    private static final String SERIAL_KEY_PREFIX = "serial-";

    /**
     * 热表阈值：5分钟内收到事件且超过100条即为热表
     */
    private static final long HOT_THRESHOLD_MS = 5 * 60 * 1000;

    /**
     * 温表阈值：1小时内收到事件即为温表，超过则为冷表
     */
    private static final long WARM_THRESHOLD_MS = 60 * 60 * 1000;

    /**
     * 热表事件数门槛：5分钟内事件数低于此值仍算温表
     */
    private static final long HOT_EVENT_THRESHOLD = 100;

    /**
     * 表热度分级
     */
    public enum TableTier { HOT, WARM, COLD }

    /**
     * 驱动缓存执行路由列表
     */
    private final Map<String, Map<String, TableGroupBufferActuator>> router = new ConcurrentHashMap<>();

    /**
     * 记录每张表的最后一次事件时间 metaId -> (tableName -> epochMs)
     */
    private final Map<String, Map<String, Long>> tableLastEventTime = new ConcurrentHashMap<>();

    /**
     * 记录每张表的事件计数 metaId -> (tableName -> count)
     */
    private final Map<String, Map<String, Long>> tableEventCounts = new ConcurrentHashMap<>();

    public void execute(String metaId, ChangedEvent event) {
        event.getChangedOffset().setMetaId(metaId);
        // 记录表活动统计
        recordTableActivity(metaId, event.getSourceTableName());
        // 打印trace信息
        printTraceInfo(event);
        router.compute(metaId, (k, processor) -> {
            if (processor == null) {
                offer(generalBufferActuator, event);
                return null;
            }

            // 串行模式：优先使用共享Actuator
            String serialKey = SERIAL_KEY_PREFIX + metaId;
            TableGroupBufferActuator serialActuator = processor.get(serialKey);
            if (serialActuator != null) {
                offer(serialActuator, event);
                return processor;
            }

            processor.compute(event.getSourceTableName(), (x, actuator) -> {
                if (actuator == null) {
                    offer(generalBufferActuator, event);
                    return null;
                }
                offer(actuator, event);
                return actuator;
            });
            return processor;
        });
    }

    public void bind(String metaId, List<TableGroup> tableGroups) {
        router.computeIfAbsent(metaId, k -> {
            Map<String, TableGroupBufferActuator> processor = new ConcurrentHashMap<>();
            for (TableGroup tableGroup : tableGroups) {
                // LRU淘汰 + 上限检查（支持冷表淘汰后腾出空间）
                if (processor.size() >= profileComponent.getSystemConfig().getMaxBufferActuatorSize()) {
                    evictIfNeeded(metaId, processor);
                    if (processor.size() >= profileComponent.getSystemConfig().getMaxBufferActuatorSize()) {
                        logger.warn("已达执行器上限:{}，无法为表{}创建专用Actuator",
                                profileComponent.getSystemConfig().getMaxBufferActuatorSize(),
                                tableGroup.getSourceTable().getName());
                        continue;
                    }
                }
                if (tableGroup.isSerialMode()) {
                    // 串行模式：同mapping下所有表共享一个Actuator，固定使用WARM配置
                    String serialKey = SERIAL_KEY_PREFIX + metaId;
                    processor.computeIfAbsent(serialKey, name -> createActuator(name, TableTier.WARM));
                } else {
                    // 每表独立Actuator，根据热度分配配置
                    final String tableName = tableGroup.getSourceTable().getName();
                    processor.computeIfAbsent(tableName, name -> {
                        TableTier tier = getTableTier(metaId, tableName);
                        return createActuator(name, tier);
                    });
                }
            }
            return processor;
        });
    }

    /**
     * 创建缓存执行器（使用默认WARM配置）
     */
    private TableGroupBufferActuator createActuator(String name) {
        return createActuator(name, TableTier.WARM);
    }

    /**
     * 创建缓存执行器，按表热度分级注入独立配置副本。
     *
     * @param name 表名或串行key
     * @param tier 热度等级 HOT/WARM/COLD
     */
    private TableGroupBufferActuator createActuator(String name, TableTier tier) {
        TableGroupBufferActuator newBufferActuator = null;
        try {
            newBufferActuator = (TableGroupBufferActuator) tableGroupBufferActuatorService.clone();
            newBufferActuator.setTableName(name);
            // 为每个执行器创建独立的配置副本，防止修改共享Bean影响其他执行器
            TableGroupBufferConfig configCopy = tableGroupBufferConfig.copy();
            switch (tier) {
                case HOT:
                    configCopy.applyHotTableProfile();
                    break;
                case WARM:
                    configCopy.applyDefaultProfile();
                    break;
                case COLD:
                    configCopy.applyColdTableProfile();
                    break;
            }
            newBufferActuator.setTableGroupBufferConfig(configCopy);
            newBufferActuator.buildConfig();
        } catch (CloneNotSupportedException ex) {
            logger.error(ex.getMessage(), ex);
        }
        return newBufferActuator;
    }

    /**
     * 记录表级的活动统计信息，用于热度计算
     */
    public void recordTableActivity(String metaId, String tableName) {
        long now = System.currentTimeMillis();
        tableLastEventTime.computeIfAbsent(metaId, k -> new ConcurrentHashMap<>())
                .put(tableName, now);
        tableEventCounts.computeIfAbsent(metaId, k -> new ConcurrentHashMap<>())
                .merge(tableName, 1L, Long::sum);
    }

    /**
     * 根据最近活动时间和事件数判断表的热度等级
     */
    public TableTier getTableTier(String metaId, String tableName) {
        long now = System.currentTimeMillis();
        Long lastTime = tableLastEventTime.getOrDefault(metaId, Collections.emptyMap()).get(tableName);
        Long count = tableEventCounts.getOrDefault(metaId, Collections.emptyMap()).getOrDefault(tableName, 0L);

        if (lastTime == null || now - lastTime > WARM_THRESHOLD_MS) {
            return TableTier.COLD;
        }
        if (now - lastTime < HOT_THRESHOLD_MS && count >= HOT_EVENT_THRESHOLD) {
            return TableTier.HOT;
        }
        return TableTier.WARM;
    }

    /**
     * LRU淘汰：当执行器数量超过上限时，移除最久无活动的表级执行器。
     */
    private void evictIfNeeded(String metaId, Map<String, TableGroupBufferActuator> processor) {
        int maxSize = profileComponent.getSystemConfig().getMaxBufferActuatorSize();
        while (processor.size() >= maxSize) {
            String evictTable = processor.keySet().stream()
                    .filter(k -> !k.startsWith(SERIAL_KEY_PREFIX))
                    .min(Comparator.comparingLong(k ->
                            tableLastEventTime.getOrDefault(metaId, Collections.emptyMap())
                                    .getOrDefault(k, 0L)))
                    .orElse(null);

            if (evictTable == null) {
                break;
            }

            TableGroupBufferActuator removed = processor.remove(evictTable);
            if (removed != null) {
                logger.info("LRU淘汰: metaId={}, table={}, 移至共享池", metaId, evictTable);
                removed.stop();
            }
        }
    }

    /**
     * 周期性重平衡：重新评估各表热度等级，调整执行器配置或触发LRU淘汰。
     *
     * 注意：完整实现需要 @Scheduled + @EnableScheduling。
     * 当前提供手动调用入口，可由定时任务或外部调度器触发。
     */
    public void rebalanceTiers() {
        router.forEach((metaId, processor) -> {
            processor.forEach((tableName, actuator) -> {
                // 跳过串行模式执行器
                if (tableName.startsWith(SERIAL_KEY_PREFIX)) {
                    return;
                }
                TableTier newTier = getTableTier(metaId, tableName);
                TableGroupBufferConfig config = (TableGroupBufferConfig) actuator.getConfig();
                // 逐级调整：冷→温→热，避免跳跃式升级
                TableTier currentTier = inferCurrentTier(config);
                if (newTier != currentTier) {
                    logger.info("表热度变化: metaId={}, table={}, {} -> {}",
                            metaId, tableName, currentTier, newTier);
                    // 热升级：停止旧执行器，重建新配置
                    TableGroupBufferActuator newActuator = createActuator(tableName, newTier);
                    actuator.stop();
                    processor.put(tableName, newActuator);
                }
            });
            evictIfNeeded(metaId, processor);
        });
        // 重置事件计数器（每个重平衡周期后清零，避免计数器无限增长）
        tableEventCounts.values().forEach(Map::clear);
    }

    /**
     * 从执行器当前配置推断对应的热度等级（启发式方法）
     */
    private TableTier inferCurrentTier(TableGroupBufferConfig config) {
        if (config.getBufferPullCount() >= 5000) {
            return TableTier.HOT;
        }
        if (config.getBufferPullCount() <= 100) {
            return TableTier.COLD;
        }
        return TableTier.WARM;
    }

    public void unbind(String metaId) {
        router.computeIfPresent(metaId, (k, processor) -> {
            processor.values().forEach(TableGroupBufferActuator::stop);
            return null;
        });
    }

    private void offer(AbstractBufferActuator actuator, ChangedEvent event) {
        WriterRequest request = new WriterRequest(event);
        if (ChangedEventTypeEnum.isDDL(event.getType())) {
            // DDL事件：插入栅栏标记，消费端遇到时先清空累积数据再执行DDL
            request.setBarrier(true);
        }
        actuator.offer(request);
    }

    @Override
    public void destroy() {
        router.values().forEach(map -> map.values().forEach(TableGroupBufferActuator::stop));
        router.clear();
    }

    public AtomicLong getQueueSize() {
        AtomicLong total = new AtomicLong();
        router.values().forEach(map -> map.values().forEach(actuator -> total.addAndGet(actuator.getQueue().size())));
        return total;
    }

    public AtomicLong getQueueCapacity() {
        AtomicLong total = new AtomicLong();
        router.values().forEach(map -> map.values().forEach(actuator -> total.addAndGet(actuator.getQueueCapacity())));
        return total;
    }

    public Map<String, Map<String, TableGroupBufferActuator>> getRouter() {
        return Collections.unmodifiableMap(router);
    }

    private void printTraceInfo(ChangedEvent event) {
        if (profileComponent.getSystemConfig().isEnablePrintTraceInfo()) {
            event.setTraceId(UUIDUtil.getUUID().toLowerCase());
            logger.info("traceId:{}, tableName:{}, event:{}, offset:{}, row:{}", event.getTraceId(), event.getSourceTableName(), event.getEvent(), JsonUtil.objToJson(event.getChangedOffset()), event.getChangedRow());
        }
    }

}