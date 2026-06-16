/**
 * DBSyncer Copyright 2020-2025 All Rights Reserved.
 */
package org.dbsyncer.parser.util;

/**
 * @Author zhangxl
 * @Version 1.0.0
 * @Date 2026-06-02 14:25
 */
public abstract class ConnectorInstanceUtil {

    /**
     * 数据源连接实例后缀
     */
    public static final String SOURCE_SUFFIX = "S";

    /**
     * 目标源连接实例后缀
     */
    public static final String TARGET_SUFFIX = "T";

    public static String buildConnectorInstanceId(String mappingId, String connectorId, String suffix) {
        return String.format("%s@%s@%s", mappingId, connectorId, suffix);
    }
}