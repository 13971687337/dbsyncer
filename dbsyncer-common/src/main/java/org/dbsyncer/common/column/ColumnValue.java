/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.common.column;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;

/**
 * @Author zhangxl
 * @Version 1.0.0
 * @Date 2026-06-02 14:25
 */
public interface ColumnValue {

    boolean isNull();

    String asString();

    byte[] asByteArray();

    Byte asByte();

    Short asShort();

    Integer asInteger();

    Long asLong();

    Float asFloat();

    Double asDouble();

    Boolean asBoolean();

    BigDecimal asBigDecimal();

    Date asDate();

    Timestamp asTimestamp();

    Time asTime();

}