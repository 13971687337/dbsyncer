/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.connector.oracle.schema.support;

import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.schema.support.LongType;

import java.util.Collections;
import java.util.Set;

/**
 * @Author zhangxl
 * @Version 1.0.0
 * @Date 2026-06-02 14:25
 */
public final class OracleLongType extends LongType {

    @Override
    public Set<String> getSupportedTypeName() {
        return Collections.emptySet();
    }

    @Override
    protected Long merge(Object val, Field field) {
        return throwUnsupportedException(val, field);
    }

}