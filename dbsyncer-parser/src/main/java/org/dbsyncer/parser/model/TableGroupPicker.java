/**
 * DBSyncer Copyright 2020-2025 All Rights Reserved.
 */
package org.dbsyncer.parser.model;

import org.dbsyncer.sdk.model.Field;

import java.util.List;
import java.util.Map;

/**
 * @Author zhangxl
 * @Version 1.0.0
 * @Date 2026-06-02 14:25
 */
public class TableGroupPicker {

    private final TableGroup tableGroup;

    private final Picker picker;

    private final List<Field> sourceFields;
    private final List<Field> targetFields;

    public TableGroupPicker(TableGroup tableGroup) {
        this.tableGroup = tableGroup;
        this.picker = new Picker(tableGroup);
        this.sourceFields = picker.getSourceFields();
        this.targetFields = picker.getTargetFields();
    }

    public TableGroup getTableGroup() {
        return tableGroup;
    }

    public Picker getPicker() {
        return picker;
    }

    public List<Field> getSourceFields() {
        return sourceFields;
    }

    public List<Field> getTargetFields() {
        return targetFields;
    }
}