package org.dbsyncer.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 表执行器配置
 *
 * @author zhangxl
 * @version 1.0.0
 * @date 2023/8/28 23:50
 */
@Configuration
@ConfigurationProperties(prefix = "dbsyncer.parser.table.group")
public class TableGroupBufferConfig extends BufferActuatorConfig {

    /**
     * 工作线程数
     */
    private int threadCoreSize = 1;

    /**
     * 最大工作线程数
     */
    private int maxThreadSize = threadCoreSize * 2;

    /**
     * 工作线任务队列
     */
    private int threadQueueCapacity = 1000;

    public int getThreadCoreSize() {
        return threadCoreSize;
    }

    public void setThreadCoreSize(int threadCoreSize) {
        this.threadCoreSize = threadCoreSize;
    }

    public int getMaxThreadSize() {
        return maxThreadSize;
    }

    public void setMaxThreadSize(int maxThreadSize) {
        this.maxThreadSize = maxThreadSize;
    }

    public int getThreadQueueCapacity() {
        return threadQueueCapacity;
    }

    public void setThreadQueueCapacity(int threadQueueCapacity) {
        this.threadQueueCapacity = threadQueueCapacity;
    }

    public void applyHotTableProfile() {
        setBufferPullCount(5000);
        setBufferQueueCapacity(100_000);
        setBufferPeriodMillisecond(100);
        setBufferWriterCount(200);
    }

    public void applyColdTableProfile() {
        setBufferPullCount(100);
        setBufferQueueCapacity(5_000);
        setBufferPeriodMillisecond(1000);
        setBufferWriterCount(50);
    }

    public void applyDefaultProfile() {
        setBufferPullCount(1000);
        setBufferQueueCapacity(30_000);
        setBufferPeriodMillisecond(300);
        setBufferWriterCount(100);
    }

    /**
     * 深拷贝配置对象，使每个执行器拥有独立的配置副本，
     * 避免共享Spring单例Bean导致所有执行器互相干扰。
     */
    public TableGroupBufferConfig copy() {
        TableGroupBufferConfig c = new TableGroupBufferConfig();
        c.setBufferPullCount(this.getBufferPullCount());
        c.setBufferQueueCapacity(this.getBufferQueueCapacity());
        c.setBufferPeriodMillisecond(this.getBufferPeriodMillisecond());
        c.setBufferWriterCount(this.getBufferWriterCount());
        c.setThreadCoreSize(this.getThreadCoreSize());
        c.setMaxThreadSize(this.getMaxThreadSize());
        c.setThreadQueueCapacity(this.getThreadQueueCapacity());
        return c;
    }
}