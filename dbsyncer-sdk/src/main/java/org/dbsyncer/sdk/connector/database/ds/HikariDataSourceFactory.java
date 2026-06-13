/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.sdk.connector.database.ds;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.dbsyncer.sdk.config.DatabaseConfig;
import javax.sql.DataSource;

public final class HikariDataSourceFactory {

    private HikariDataSourceFactory() {}

    public static DataSource create(DatabaseConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setDriverClassName(config.getDriverClassName());
        hikariConfig.setMaximumPoolSize(config.getMaxActive());
        hikariConfig.setMinimumIdle(Math.min(config.getMaxActive() / 4, 10));
        hikariConfig.setConnectionTimeout(30000);
        hikariConfig.setIdleTimeout(config.getKeepAlive());
        hikariConfig.setMaxLifetime(1800000);
        hikariConfig.setPoolName("dbsyncer-" + config.getConnectorType());
        return new HikariDataSource(hikariConfig);
    }
}
