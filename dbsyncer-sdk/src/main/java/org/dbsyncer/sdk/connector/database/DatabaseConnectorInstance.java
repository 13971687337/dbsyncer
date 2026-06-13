/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.sdk.connector.database;

import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.sdk.SdkException;
import org.dbsyncer.sdk.config.DatabaseConfig;
import org.dbsyncer.sdk.connector.ConnectorInstance;
import org.dbsyncer.sdk.connector.database.ds.HikariDataSourceFactory;
import org.dbsyncer.sdk.connector.database.ds.SimpleConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseConnectorInstance implements ConnectorInstance<DatabaseConfig, Connection> {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private DatabaseConfig config;
    private final javax.sql.DataSource dataSource;
    private final String catalog;
    private final String schema;

    public DatabaseConnectorInstance(DatabaseConfig config) {
        this(config, null, null);
    }

    public DatabaseConnectorInstance(DatabaseConfig config, String catalog, String schema) {
        this.config = config;
        this.catalog = catalog;
        this.schema = schema;
        Properties properties = new Properties();
        properties.putAll(config.getProperties());
        if (StringUtil.isNotBlank(config.getUsername())) {
            properties.put("user", config.getUsername());
        }
        if (StringUtil.isNotBlank(config.getPassword())) {
            properties.put("password", config.getPassword());
        }
        this.dataSource = HikariDataSourceFactory.create(config);
    }

    public <T> T execute(HandleCallback callback) {
        SimpleConnection connection = null;
        try {
            connection = getSimpleConnection();
            return (T) callback.apply(new DatabaseTemplate(connection));
        } catch (EmptyResultDataAccessException e) {
            throw e;
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new SdkException(e.getMessage(), e.getCause());
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    logger.warn("Failed to close connection: {}", e.getMessage());
                }
            }
        }
    }

    @Override
    public String getServiceUrl() {
        return config.getUrl();
    }

    @Override
    public DatabaseConfig getConfig() {
        return config;
    }

    @Override
    public void setConfig(DatabaseConfig config) {
        this.config = config;
    }

    private boolean isOracleDriver() {
        return config.getDriverClassName() != null && config.getDriverClassName().contains("OracleDriver");
    }

    public SimpleConnection getSimpleConnection() throws Exception {
        Connection connection = dataSource.getConnection();
        try {
            if (StringUtil.isNotBlank(catalog)) {
                connection.setCatalog(catalog);
            }
            if (StringUtil.isNotBlank(schema)) {
                connection.setSchema(schema);
            }
        } catch (SQLException e) {
            logger.warn("Failed to set catalog/schema: {}", e.getMessage());
        }
        return new SimpleConnection(connection, isOracleDriver());
    }

    @Override
    public Connection getConnection() throws Exception {
        return getSimpleConnection();
    }

    @Override
    public void close() {
        if (dataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dataSource).close();
            } catch (Exception e) {
                logger.warn("Failed to close data source: {}", e.getMessage());
            }
        }
    }

    public javax.sql.DataSource getDataSource() {
        return dataSource;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

}