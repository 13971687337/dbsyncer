/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.parser.flush.impl;

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

    /**
     * 串行模式Actuator key前缀
     */
    private static final String SERIAL_KEY_PREFIX = "serial-";

    /**
     * 驱动缓存执行路由列表
     */
    private final Map<String, Map<String, TableGroupBufferActuator>> router = new ConcurrentHashMap<>();

    public void execute(String metaId, ChangedEvent event) {
        event.getChangedOffset().setMetaId(metaId);
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
                // 超过执行器上限
                if (processor.size() >= profileComponent.getSystemConfig().getMaxBufferActuatorSize()) {
                    logger.warn("Not allowed more than table processor limited size:{}", profileComponent.getSystemConfig().getMaxBufferActuatorSize());
                    break;
                }
                if (tableGroup.isSerialMode()) {
                    // 串行模式：同mapping下所有表共享一个Actuator
                    String serialKey = SERIAL_KEY_PREFIX + metaId;
                    processor.computeIfAbsent(serialKey, this::createActuator);
                } else {
                    // 每表独立Actuator（默认行为）
                    final String tableName = tableGroup.getSourceTable().getName();
                    processor.computeIfAbsent(tableName, this::createActuator);
                }
            }
            return processor;
        });
    }

    /**
     * 创建缓存执行器
     */
    private TableGroupBufferActuator createActuator(String name) {
        TableGroupBufferActuator newBufferActuator = null;
        try {
            newBufferActuator = (TableGroupBufferActuator) tableGroupBufferActuatorService.clone();
            newBufferActuator.setTableName(name);
            newBufferActuator.buildConfig();
        } catch (CloneNotSupportedException ex) {
            logger.error(ex.getMessage(), ex);
        }
        return newBufferActuator;
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