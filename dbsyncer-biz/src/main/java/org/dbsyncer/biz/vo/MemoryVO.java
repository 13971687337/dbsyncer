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
public final class MemoryVO extends HistoryStackVo {
    private BigDecimal jvmUsed;
    private BigDecimal jvmTotal;
    private BigDecimal sysUsed;
    private BigDecimal sysTotal;
    // 总使用百分比
    private BigDecimal totalPercent;

    public BigDecimal getJvmUsed() {
        return jvmUsed;
    }

    public void setJvmUsed(BigDecimal jvmUsed) {
        this.jvmUsed = jvmUsed;
    }

    public BigDecimal getJvmTotal() {
        return jvmTotal;
    }

    public void setJvmTotal(BigDecimal jvmTotal) {
        this.jvmTotal = jvmTotal;
    }

    public BigDecimal getSysUsed() {
        return sysUsed;
    }

    public void setSysUsed(BigDecimal sysUsed) {
        this.sysUsed = sysUsed;
    }

    public BigDecimal getSysTotal() {
        return sysTotal;
    }

    public void setSysTotal(BigDecimal sysTotal) {
        this.sysTotal = sysTotal;
    }

    public BigDecimal getTotalPercent() {
        return totalPercent;
    }

    public void setTotalPercent(BigDecimal totalPercent) {
        this.totalPercent = totalPercent;
    }

}
