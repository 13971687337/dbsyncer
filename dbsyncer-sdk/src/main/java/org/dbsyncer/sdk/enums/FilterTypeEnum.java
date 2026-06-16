/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.sdk.enums;

/**
 * @Author zhangxl
 * @Version 1.0.0
 * @Date 2026-06-02 14:25
 */
public enum FilterTypeEnum {
    /**
     * string
     */
    STRING("string"),
    /**
     * int
     */
    INT("int"),
    /**
     * long
     */
    LONG("long");

    private final String type;

    FilterTypeEnum(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}