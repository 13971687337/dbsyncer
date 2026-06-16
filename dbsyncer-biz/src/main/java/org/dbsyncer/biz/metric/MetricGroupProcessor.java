/**
 * DBSyncer Copyright 2020-2025 All Rights Reserved.
 */
package org.dbsyncer.biz.metric;

import org.dbsyncer.biz.vo.MetricResponseVo;

import java.util.List;

/**
 * 合并分组指标
 *
 * @Author zhangxl
 * @Version 1.0.0
 * @Date 2026-06-02 14:25
 */
public interface MetricGroupProcessor {
    List<MetricResponseVo> process(List<MetricResponseVo> metrics);
}