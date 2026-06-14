/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.connector.clickhouse.schema.support;

import org.dbsyncer.common.util.DateFormatUtil;
import org.dbsyncer.sdk.enums.DataTypeEnum;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.schema.support.TimestampType;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ClickHouse时间类型
 *
 * @author zhangxl
 * @version 1.0.0
 * @date 2024-06-14
 */
public final class ClickHouseTimestampType extends TimestampType {

    private enum TypeEnum {
        Date, Date32, DateTime, DateTime32, DateTime64
    }

    @Override
    public Set<String> getSupportedTypeName() {
        return Arrays.stream(TypeEnum.values()).map(Enum::name).collect(Collectors.toSet());
    }

    @Override
    protected Timestamp merge(Object val, Field field) {
        if (val instanceof Timestamp) {
            return (Timestamp) val;
        }
        if (val instanceof Date) {
            return new Timestamp(((Date) val).getTime());
        }
        if (val instanceof java.util.Date) {
            return new Timestamp(((java.util.Date) val).getTime());
        }
        if (val instanceof Number) {
            return new Timestamp(((Number) val).longValue());
        }
        if (val instanceof String) {
            Timestamp ts = DateFormatUtil.stringToTimestamp((String) val);
            if (ts != null) {
                return ts;
            }
        }
        return throwUnsupportedException(val, field);
    }
}
