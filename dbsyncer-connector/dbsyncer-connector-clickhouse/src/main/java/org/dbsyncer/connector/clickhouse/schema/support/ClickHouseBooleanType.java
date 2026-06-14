/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.connector.clickhouse.schema.support;

import org.dbsyncer.sdk.enums.DataTypeEnum;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.schema.support.BooleanType;

import java.util.Collections;
import java.util.Set;

/**
 * ClickHouse Bool类型
 *
 * @author zhangxl
 * @version 1.0.0
 * @date 2024-06-14
 */
public final class ClickHouseBooleanType extends BooleanType {

    @Override
    public Set<String> getSupportedTypeName() {
        return Collections.singleton("Bool");
    }

    @Override
    protected Boolean merge(Object val, Field field) {
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        if (val instanceof Number) {
            return ((Number) val).intValue() == 1;
        }
        return throwUnsupportedException(val, field);
    }
}
