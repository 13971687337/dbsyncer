/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.connector.clickhouse.schema.support;

import org.dbsyncer.sdk.enums.DataTypeEnum;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.schema.support.DecimalType;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * ClickHouse Decimal类型
 *
 * @author zhangxl
 * @version 1.0.0
 * @date 2024-06-14
 */
public final class ClickHouseDecimalType extends DecimalType {

    private static final Set<String> SUPPORTED = new HashSet<>(Arrays.asList(
            "Decimal", "Decimal32", "Decimal64", "Decimal128", "Decimal256"
    ));

    @Override
    public Set<String> getSupportedTypeName() {
        return SUPPORTED;
    }

    @Override
    protected BigDecimal merge(Object val, Field field) {
        if (val instanceof BigDecimal) {
            return (BigDecimal) val;
        }
        if (val instanceof Number) {
            return new BigDecimal(val.toString());
        }
        if (val instanceof String) {
            return new BigDecimal((String) val);
        }
        return throwUnsupportedException(val, field);
    }
}
