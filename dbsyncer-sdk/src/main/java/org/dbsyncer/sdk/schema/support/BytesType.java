/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.sdk.schema.support;

import org.dbsyncer.sdk.enums.DataTypeEnum;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.schema.AbstractDataType;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * @Author zhangxl
 * @Version 1.0.0
 * @Date 2026-06-02 14:25
 */
public abstract class BytesType extends AbstractDataType<byte[]> {

    @Override
    public DataTypeEnum getType() {
        return DataTypeEnum.BYTES;
    }

    @Override
    protected Object convert(Object val, Field field) {
        if (val instanceof byte[]) {
            return val;
        }
        if (val instanceof String) {
            return ((String) val).getBytes(StandardCharsets.UTF_8);
        }
        if (val instanceof Boolean) {
            Boolean b = (Boolean) val;
            ByteBuffer buffer = ByteBuffer.allocate(2);
            buffer.putShort((short) (b ? 1 : 0));
            return buffer.array();
        }
        return throwUnsupportedException(val, field);
    }
}
