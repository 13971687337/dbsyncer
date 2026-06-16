/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.connector.oracle.logminer.parser.impl;

import net.sf.jsqlparser.statement.delete.Delete;
import org.dbsyncer.connector.oracle.logminer.parser.AbstractParser;
import org.dbsyncer.sdk.model.Field;

import java.util.List;

/**
 * @Author zhangxl
 * @Version 1.0.0
 * @Date 2026-06-02 14:25
 */
public class DeleteSql extends AbstractParser {

    private Delete delete;

    public DeleteSql(Delete delete, List<Field> fields) {
        this.delete = delete;
        setFields(fields);
    }

    @Override
    public List<Object> parseColumns() {
        findColumn(delete.getWhere());
        return columnMapToData();
    }

}