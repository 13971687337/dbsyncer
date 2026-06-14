/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.connector.clickhouse.schema.support;

import org.dbsyncer.sdk.enums.DataTypeEnum;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.schema.support.StringType;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ClickHouse字符串及特殊类型（JSON序列化为VARCHAR）
 *
 * @author zhangxl
 * @version 1.0.0
 * @date 2024-06-14
 */
public final class ClickHouseStringType extends StringType {

    private enum TypeEnum {
        String, FixedString, UUID, Enum, Enum8, Enum16, Nested, IPv4, IPv6, Array, Tuple, Map
    }

    @Override
    public Set<String> getSupportedTypeName() {
        return Arrays.stream(TypeEnum.values()).map(Enum::name).collect(Collectors.toSet());
    }

    @Override
    protected String merge(Object val, Field field) {
        if (val instanceof String) {
            return (String) val;
        }
        if (val instanceof byte[]) {
            return new String((byte[]) val);
        }
        return String.valueOf(val);
    }
}
