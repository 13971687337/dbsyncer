/**
 * DBSyncer Copyright 2020-2025 All Rights Reserved.
 */
package org.dbsyncer.biz.vo;

import java.math.BigDecimal;

/**
 * @Author zhangxl
 * @Version 1.0.0
 * @Date 2026-06-02 14:25
 */
public final class CpuVO extends HistoryStackVo {

    private final int core = Runtime.getRuntime().availableProcessors();

    // 用户使用百分比
    private BigDecimal userPercent;
    // 系统化使用百分比
    private BigDecimal sysPercent;
    // 总使用百分比
    private BigDecimal totalPercent;

    public int getCore() {
        return core;
    }

    public BigDecimal getUserPercent() {
        return userPercent;
    }

    public void setUserPercent(BigDecimal userPercent) {
        this.userPercent = userPercent;
    }

    public BigDecimal getSysPercent() {
        return sysPercent;
    }

    public void setSysPercent(BigDecimal sysPercent) {
        this.sysPercent = sysPercent;
    }

    public BigDecimal getTotalPercent() {
        return totalPercent;
    }

    public void setTotalPercent(BigDecimal totalPercent) {
        this.totalPercent = totalPercent;
    }
}
