/**
 * DBSyncer Copyright 2019-2024 All Rights Reserved.
 */
package org.dbsyncer.sdk.listener.event;

import org.dbsyncer.sdk.enums.ChangedEventTypeEnum;

import java.util.List;

/**
 * 监听行变更事件
 *
 * @version 1.0.0
 * @Author zhangxl
 * @Date 2026-06-02 14:25
 */
public final class RowChangedEvent extends CommonChangedEvent {
    private final List<Object> changedRow;

    public RowChangedEvent(String sourceTableName, String event, List<Object> data, String nextFileName, Object position) {
        setSourceTableName(sourceTableName);
        setEvent(event);
        setNextFileName(nextFileName);
        setPosition(position);
        this.changedRow = data;
    }

    @Override
    public ChangedEventTypeEnum getType() {
        return ChangedEventTypeEnum.ROW;
    }

    @Override
    public List<Object> getChangedRow() {
        return changedRow;
    }
}