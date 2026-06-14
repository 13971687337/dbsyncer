/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.connector.clickhouse;

import org.dbsyncer.sdk.config.DatabaseConfig;

/**
 * ClickHouse连接配置
 *
 * @author zhangxl
 * @version 1.0.0
 * @date 2024-06-14
 */
public class ClickHouseConfig extends DatabaseConfig {

    public static final int DEFAULT_PORT = 8123;

    public ClickHouseConfig() {
        setPort(DEFAULT_PORT);
    }
}
