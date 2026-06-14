/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.connector.clickhouse;

import org.dbsyncer.sdk.config.DatabaseConfig;
import org.dbsyncer.sdk.connector.AbstractDataBaseConfigValidator;
import org.dbsyncer.sdk.connector.database.AbstractDatabaseConnector;
import org.springframework.util.Assert;

import java.util.Map;

/**
 * ClickHouse配置校验器
 *
 * @author zhangxl
 * @version 1.0.0
 * @date 2024-06-14
 */
public class ClickHouseConfigValidator extends AbstractDataBaseConfigValidator {

    @Override
    public void modify(AbstractDatabaseConnector connectorService, DatabaseConfig connectorConfig, Map<String, String> params) {
        super.modify(connectorService, connectorConfig, params);

        int port = connectorConfig.getPort();
        Assert.isTrue(port >= 1 && port <= 65535, "Port is invalid (1-65535).");
    }
}
