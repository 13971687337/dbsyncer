/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.storage.enums;

/**
 * 同步数据状态枚举
 *
 * @Author zhangxl
 * @Version 1.0.0
 * @Date 2026-06-02 14:25
 */
public enum StorageDataStatusEnum {
    /**
     * 失败
     */
    FAIL(0, "失败"),
    /**
     * 成功
     */
    SUCCESS(1, "成功");

    private final int value;

    private final String message;

    StorageDataStatusEnum(int value, String message) {
        this.value = value;
        this.message = message;
    }

    public int getValue() {
        return value;
    }

    public String getMessage() {
        return message;
    }

}