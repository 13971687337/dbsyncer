package org.dbsyncer.biz.enums;

import org.dbsyncer.biz.BizException;
import org.dbsyncer.common.util.StringUtil;

/**
 * 系统指标
 *
 * @author zhangxl
 * @version 1.0.0
 * @date 2021/07/22 19:19
 */
public enum MetricEnum {

    /**
     * 线程活跃数
     */
    THREADS_LIVE("jvm.threads.live", "应用线程", "活跃"),

    /**
     * 线程峰值
     */
    THREADS_PEAK("jvm.threads.peak", "应用线程", "峰值"),

    /**
     * 内存已用
     */
    MEMORY_USED("jvm.memory.used", "内存", "已用"),

    /**
     * 内存空闲
     */
    MEMORY_COMMITTED("jvm.memory.committed", "内存", "空闲"),

    /**
     * 内存总共
     */
    MEMORY_MAX("jvm.memory.max", "内存", "总共"),

    /**
     * GC
     */
    GC_PAUSE("jvm.gc.pause", "GC", ""),

    /**
     * CPU已用
     */
    CPU_USAGE("system.cpu.usage", "CPU", ""),

    /**
     * 系统环境
     */
    SYSTEM_ENV("system.info", "运行环境", ""),

    /**
     * Binlog延迟(字节)
     */
    BINLOG_LAG_BYTES("binlogLagBytes", "同步", "Binlog延迟(字节)"),

    /**
     * 每秒事件数
     */
    EVENTS_PER_SECOND("eventsPerSecond", "同步", "每秒事件数"),

    /**
     * 每秒写入行数
     */
    WRITE_ROWS_PER_SECOND("writeRowsPerSecond", "同步", "每秒写入行数"),

    /**
     * 累计写入失败数
     */
    WRITE_ERROR_COUNT("writeErrorCount", "同步", "累计写入失败数"),

    /**
     * 队列积压
     */
    TABLE_QUEUE_DEPTH("tableQueueDepth", "表", "队列积压"),

    /**
     * 写入延迟(ms)
     */
    TABLE_WRITE_LATENCY("tableWriteLatency", "表", "写入延迟(ms)"),

    /**
     * 最近事件时间
     */
    TABLE_LAST_EVENT_TIME("tableLastEventTime", "表", "最近事件时间"),

    /**
     * JVM堆内存(MB)
     */
    HEAP_USED_MB("heapUsedMb", "JVM", "堆内存(MB)"),

    /**
     * 活跃执行器数
     */
    ACTUATOR_COUNT_ACTIVE("actuatorCountActive", "执行器", "活跃执行器数"),

    /**
     * 监听器状态
     */
    LISTENER_STATUS("listenerStatus", "监听器", "监听器状态");

    private final String code;
    private final String group;
    private final String metricName;

    MetricEnum(String code, String group, String metricName) {
        this.code = code;
        this.group = group;
        this.metricName = metricName;
    }

    public static MetricEnum getMetric(String code) throws BizException {
        for (MetricEnum e : MetricEnum.values()) {
            if (StringUtil.equals(code, e.getCode())) {
                return e;
            }
        }
        return null;
    }

    public String getCode() {
        return code;
    }

    public String getGroup() {
        return group;
    }

    public String getMetricName() {
        return metricName;
    }

}