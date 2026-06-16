/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.common.metric;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author zhangxl
 * @Version 1.0.0
 * @Date 2026-06-02 14:25
 */
@Component
public final class TimeRegistry {

    public static final String GENERAL_BUFFER_ACTUATOR_TPS = "general.buffer.actuator.tps";

    private Map<String, TimeMetric> metricMap = new ConcurrentHashMap<>();

    public TimeMetric meter(String name) {
        return metricMap.computeIfAbsent(name, k -> new TimeMetric());
    }
}