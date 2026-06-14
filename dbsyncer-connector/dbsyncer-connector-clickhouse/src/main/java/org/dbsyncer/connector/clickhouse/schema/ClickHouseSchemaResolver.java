/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.connector.clickhouse.schema;

import org.dbsyncer.connector.clickhouse.schema.support.*;
import org.dbsyncer.sdk.SdkException;
import org.dbsyncer.sdk.schema.AbstractSchemaResolver;
import org.dbsyncer.sdk.schema.DataType;

import java.util.Map;
import java.util.stream.Stream;

/**
 * ClickHouse数据类型解析器，将ClickHouse原生类型映射为标准DataTypeEnum
 *
 * @author zhangxl
 * @version 1.0.0
 * @date 2024-06-14
 */
public final class ClickHouseSchemaResolver extends AbstractSchemaResolver {

    @Override
    protected void initDataTypeMapping(Map<String, DataType> mapping) {
        Stream.of(
                new ClickHouseIntType(),
                new ClickHouseDoubleType(),
                new ClickHouseStringType(),
                new ClickHouseTimestampType(),
                new ClickHouseDecimalType(),
                new ClickHouseBooleanType()
        ).forEach(t -> t.getSupportedTypeName().forEach(typeName -> {
            if (mapping.containsKey(typeName)) {
                throw new SdkException("Duplicate ClickHouse type name: " + typeName);
            }
            mapping.put(typeName, t);
        }));
    }
}
