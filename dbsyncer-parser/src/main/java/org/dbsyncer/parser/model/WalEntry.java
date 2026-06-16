/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.parser.model;

import java.util.List;

/**
 * WAL（Write-Ahead Log）条目
 * <p>在写入目标端之前，先将binlog事件追加到本地WAL文件。
 * 崩溃恢复时，重放未提交的WAL记录，保证不丢数据、不重复写入。</p>
 *
 * @version 1.0.0
 * @Author zhangxl
 * @Date 2026-06-16
 */
public class WalEntry {

    /**
     * 单调递增序号
     */
    private long sequence;

    /**
     * 源表名
     */
    private String tableName;

    /**
     * 事件类型：INSERT/UPDATE/DELETE
     */
    private String event;

    /**
     * 变更的行数据
     */
    private List<Object> rowData;

    /**
     * Binlog文件名
     */
    private String binlogFile;

    /**
     * Binlog位点
     */
    private long binlogPosition;

    /**
     * 是否已成功写入目标端
     */
    private boolean committed;

    public long getSequence() {
        return sequence;
    }

    public void setSequence(long sequence) {
        this.sequence = sequence;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public List<Object> getRowData() {
        return rowData;
    }

    public void setRowData(List<Object> rowData) {
        this.rowData = rowData;
    }

    public String getBinlogFile() {
        return binlogFile;
    }

    public void setBinlogFile(String binlogFile) {
        this.binlogFile = binlogFile;
    }

    public long getBinlogPosition() {
        return binlogPosition;
    }

    public void setBinlogPosition(long binlogPosition) {
        this.binlogPosition = binlogPosition;
    }

    public boolean isCommitted() {
        return committed;
    }

    public void setCommitted(boolean committed) {
        this.committed = committed;
    }
}
