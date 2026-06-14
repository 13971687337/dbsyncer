/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.connector.clickhouse.schema.support;

import org.dbsyncer.common.util.NumberUtil;
import org.dbsyncer.sdk.enums.DataTypeEnum;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.schema.support.IntType;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ClickHouse整数类型（有符号/无符号）
 *
 * @author zhangxl
 * @version 1.0.0
 * @date 2024-06-14
 */
public final class ClickHouseIntType extends IntType {

    private enum TypeEnum {
        UInt8, UInt16, UInt32, UInt64,
        Int8, Int16, Int32, Int64
    }

    @Override
    public Set<String> getSupportedTypeName() {
        return Arrays.stream(TypeEnum.values()).map(Enum::name).collect(Collectors.toSet());
    }

    @Override
    protected Integer merge(Object val, Field field) {
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        if (val instanceof String) {
            return NumberUtil.toInt((String) val);
        }
        return throwUnsupportedException(val, field);
    }
}
