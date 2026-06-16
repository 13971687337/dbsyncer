/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.sdk.listener;

import org.dbsyncer.common.scheduled.ScheduledTaskService;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.sdk.config.ListenerConfig;
import org.dbsyncer.sdk.connector.ConnectorInstance;
import org.dbsyncer.sdk.constant.ConnectorConstant;
import org.dbsyncer.sdk.model.ChangedOffset;
import org.dbsyncer.sdk.model.ConnectorConfig;
import org.dbsyncer.sdk.model.Table;
import org.dbsyncer.sdk.spi.ConnectorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * @version 1.0.0
 * @Author zhangxl
 * @Date 2026-06-02 14:25
 */
public abstract class AbstractListener<C extends ConnectorInstance> implements Listener {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final int FLUSH_DELAYED_SECONDS = 20;
    private final AtomicLong eventCounter = new AtomicLong(0);
    private volatile long lastFlushTime = System.currentTimeMillis();
    private static final long FLUSH_INTERVAL_MS = 1000;
    private static final long FLUSH_BATCH_SIZE = 1000;
    protected String database;
    protected String schema;
    protected ConnectorInstance connectorInstance;
    protected ConnectorInstance targetConnectorInstance;
    protected ConnectorService connectorService;
    protected ScheduledTaskService scheduledTaskService;
    protected ConnectorConfig connectorConfig;
    protected ListenerConfig listenerConfig;
    protected Set<String> filterTable;
    protected List<Table> sourceTable;
    protected List<Table> customTable;
    protected Map<String, String> snapshot;
    protected String metaId;
    private Watcher watcher;

    @Override
    public void register(Watcher watcher) {
        this.watcher = watcher;
    }

    @Override
    public void changeEventBefore(QuartzListenerContext context) {
        watcher.changeEventBefore(context);
    }

    @Override
    public void changeEvent(ChangedEvent event) {
        if (null != event) {
            switch (event.getEvent()) {
                case ConnectorConstant.OPERTION_UPDATE:
                    // 是否支持监听修改事件
                    processEvent(listenerConfig.isEnableUpdate(), event);
                    break;
                case ConnectorConstant.OPERTION_INSERT:
                    // 是否支持监听新增事件
                    processEvent(listenerConfig.isEnableInsert(), event);
                    break;
                case ConnectorConstant.OPERTION_DELETE:
                    // 是否支持监听删除事件
                    processEvent(listenerConfig.isEnableDelete(), event);
                    break;
                case ConnectorConstant.OPERTION_ALTER:
                    // 表结构变更事件
                    watcher.changeEvent(event);
                    break;
                default:
                    break;
            }
            eventCounter.incrementAndGet();
        }
    }

    @Override
    public void refreshEvent(ChangedOffset offset) {
        // nothing to do
    }

    @Override
    public void flushEvent() {
        long now = System.currentTimeMillis();
        if (eventCounter.get() >= FLUSH_BATCH_SIZE
                || (now - lastFlushTime >= FLUSH_INTERVAL_MS
                    && watcher.getMetaUpdateTime() > Timestamp.valueOf(
                        LocalDateTime.now().minusSeconds(FLUSH_DELAYED_SECONDS)).getTime())) {
            if (!CollectionUtils.isEmpty(snapshot)) {
                logger.info("snapshot：{}", snapshot);
                watcher.flushEvent(snapshot);
                eventCounter.set(0);
                lastFlushTime = now;
            }
        }
    }

    @Override
    public void forceFlushEvent() {
        if (!CollectionUtils.isEmpty(snapshot)) {
            logger.info("snapshot：{}", snapshot);
            watcher.flushEvent(snapshot);
        }
    }

    @Override
    public void errorEvent(Exception e) {
        watcher.errorEvent(e);
    }

    private int spinCounter = 0;
    private static final int MAX_SPIN_COUNT = 100;

    /**
     * 队列满时退避等待，避免 busy-spin。使用 LockSupport.parkNanos(100μs) 替代 Thread.sleep(1ms)，
     * 在高频队列满的场景下可减少上下文切换开销并将精度控制在微秒级。
     */
    protected void backpressureWait() {
        LockSupport.parkNanos(100_000);
        if (++spinCounter > MAX_SPIN_COUNT) {
            Thread.yield();
            spinCounter = 0;
        }
    }

    protected void sleepInMills(long timeout) {
        try {
            TimeUnit.MILLISECONDS.sleep(timeout);
        } catch (InterruptedException e) {
            logger.info(e.getMessage());
        }
    }

    /**
     * 如果允许监听该事件，则触发事件通知
     *
     * @param permitEvent
     * @param event
     */
    private void processEvent(boolean permitEvent, ChangedEvent event) {
        if (permitEvent) {
            watcher.changeEvent(event);
            eventCounter.incrementAndGet();
        }
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public void setConnectorInstance(ConnectorInstance connectorInstance) {
        this.connectorInstance = connectorInstance;
    }

    public C getConnectorInstance() {
        return (C) connectorInstance;
    }

    public void setTargetConnectorInstance(ConnectorInstance targetConnectorInstance) {
        this.targetConnectorInstance = targetConnectorInstance;
    }

    public void setConnectorService(ConnectorService connectorService) {
        this.connectorService = connectorService;
    }

    public void setScheduledTaskService(ScheduledTaskService scheduledTaskService) {
        this.scheduledTaskService = scheduledTaskService;
    }

    public void setConnectorConfig(ConnectorConfig connectorConfig) {
        this.connectorConfig = connectorConfig;
    }

    public void setListenerConfig(ListenerConfig listenerConfig) {
        this.listenerConfig = listenerConfig;
    }

    public void setFilterTable(Set<String> filterTable) {
        this.filterTable = filterTable;
    }

    public void setSourceTable(List<Table> sourceTable) {
        this.sourceTable = sourceTable;
    }

    public void setCustomTable(List<Table> customTable) {
        this.customTable = customTable;
    }

    public void setSnapshot(Map<String, String> snapshot) {
        this.snapshot = snapshot;
    }

    public void setMetaId(String metaId) {
        this.metaId = metaId;
    }

}