/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.connector.oracle.logminer.parser.impl;

import java.util.List;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.insert.Insert;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.connector.oracle.logminer.parser.AbstractParser;
import org.dbsyncer.sdk.model.Field;

/**
 * @Author zhangxl
 * @Version 1.0.0
 * @Date 2026-06-02 14:25
 */
public class InsertSql extends AbstractParser {

    private Insert insert;

    public InsertSql(Insert insert, List<Field> fields) {
        this.insert = insert;
        setFields(fields);
    }

    @Override
    public List<Object> parseColumns() {
        List<Column> columns = insert.getColumns();
        ExpressionList<Expression> values = (ExpressionList<Expression>) insert.getSelect().getValues().getExpressions();
        for (int i = 0; i < columns.size(); i++) {
            columnMap.put(StringUtil.replace(columns.get(i).getColumnName(), StringUtil.DOUBLE_QUOTATION, StringUtil.EMPTY),
                    values.get(i));
        }
        return columnMapToData();
    }

}