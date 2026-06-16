/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.connector.clickhouse;

import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.connector.clickhouse.schema.ClickHouseSchemaResolver;
import org.dbsyncer.sdk.config.DatabaseConfig;
import org.dbsyncer.sdk.config.SqlBuilderConfig;
import org.dbsyncer.sdk.connector.ConfigValidator;
import org.dbsyncer.sdk.connector.ConnectorServiceContext;
import org.dbsyncer.sdk.connector.database.AbstractDatabaseConnector;
import org.dbsyncer.sdk.connector.database.Database;
import org.dbsyncer.sdk.connector.database.DatabaseConnectorInstance;
import org.dbsyncer.sdk.connector.database.ds.SimpleConnection;
import org.dbsyncer.sdk.enums.ListenerTypeEnum;
import org.dbsyncer.sdk.enums.TableTypeEnum;
import org.dbsyncer.sdk.listener.DatabaseQuartzListener;
import org.dbsyncer.sdk.listener.Listener;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.model.MetaInfo;
import org.dbsyncer.sdk.model.PageSql;
import org.dbsyncer.sdk.model.Table;
import org.dbsyncer.sdk.plugin.ReaderContext;
import org.dbsyncer.sdk.schema.SchemaResolver;
import org.dbsyncer.sdk.util.PrimaryKeyUtil;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * ClickHouse连接器实现
 *
 * @author zhangxl
 * @version 1.0.0
 * @date 2024-06-14
 */
public final class ClickHouseConnector extends AbstractDatabaseConnector {

    public static final int DEFAULT_PORT = 8123;

    private final ClickHouseConfigValidator configValidator = new ClickHouseConfigValidator();
    private final ClickHouseSchemaResolver schemaResolver = new ClickHouseSchemaResolver();
    private final Set<String> SYSTEM_DATABASES = Stream.of("system", "information_schema", "INFORMATION_SCHEMA").collect(Collectors.toSet());

    @Override
    public String getConnectorType() {
        return "ClickHouse";
    }

    @Override
    public ConfigValidator getConfigValidator() {
        return configValidator;
    }

    @Override
    public Listener getListener(String listenerType) {
        if (ListenerTypeEnum.isTiming(listenerType)) {
            return new DatabaseQuartzListener();
        }
        return null;
    }

    @Override
    public List<String> getDatabases(DatabaseConnectorInstance connectorInstance) {
        return connectorInstance.execute(databaseTemplate -> {
            List<String> databases = databaseTemplate.queryForList("SHOW DATABASES", String.class);
            if (!CollectionUtils.isEmpty(databases)) {
                return databases.stream().filter(name -> !SYSTEM_DATABASES.contains(name)).collect(Collectors.toList());
            }
            return Collections.emptyList();
        });
    }

    @Override
    public List<Table> getTable(DatabaseConnectorInstance connectorInstance, ConnectorServiceContext context) {
        return connectorInstance.execute(databaseTemplate -> {
            SimpleConnection simpleConn = databaseTemplate.getSimpleConnection();
            Connection conn = simpleConn.getConnection();
            String catalog = getCatalog(context.getCatalog(), conn);
            if (StringUtil.isBlank(catalog)) {
                return Collections.emptyList();
            }
            String sql = String.format("SHOW TABLES FROM %s", buildWithQuotation(catalog));
            List<String> tableNames = databaseTemplate.queryForList(sql, String.class);
            if (CollectionUtils.isEmpty(tableNames)) {
                return Collections.emptyList();
            }
            return tableNames.stream().map(name -> {
                Table table = new Table();
                table.setName(name);
                table.setType(TableTypeEnum.TABLE.getCode());
                return table;
            }).collect(Collectors.toList());
        });
    }

    @Override
    public List<MetaInfo> getMetaInfo(DatabaseConnectorInstance connectorInstance, ConnectorServiceContext context) {
        return connectorInstance.execute(databaseTemplate -> {
            SimpleConnection simpleConn = databaseTemplate.getSimpleConnection();
            Connection conn = simpleConn.getConnection();
            String catalog = getCatalog(context.getCatalog(), conn);
            List<MetaInfo> metaInfos = new ArrayList<>();
            for (Table table : context.getTablePatterns()) {
                String tableName = table.getName();
                List<Field> fields = new ArrayList<>();
                // 查询system.columns获取字段信息，按position排序
                String sql = String.format("SELECT name, type FROM system.columns WHERE database = '%s' AND \"table\" = '%s' ORDER BY position",
                        catalog, tableName);
                // 查询主键
                List<String> primaryKeys = findTablePrimaryKeys(conn, catalog, tableName);
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        String columnName = rs.getString("name");
                        String typeName = rs.getString("type");
                        fields.add(new Field(columnName, typeName, java.sql.Types.OTHER,
                                primaryKeys.contains(columnName)));
                    }
                }
                MetaInfo metaInfo = new MetaInfo();
                metaInfo.setTable(tableName);
                metaInfo.setTableType(TableTypeEnum.TABLE.getCode());
                metaInfo.setColumn(fields);
                metaInfos.add(metaInfo);
            }
            return metaInfos;
        });
    }

    /**
     * 查询ClickHouse表主键
     */
    private List<String> findTablePrimaryKeys(Connection conn, String database, String tableName) throws SQLException {
        List<String> primaryKeys = new ArrayList<>();
        String sql = String.format("SELECT name FROM system.columns WHERE database = '%s' AND \"table\" = '%s' AND is_in_primary_key = 1 ORDER BY position",
                database, tableName);
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                primaryKeys.add(rs.getString("name"));
            }
        }
        return primaryKeys;
    }

    @Override
    public String generateUniqueCode() {
        return StringUtil.EMPTY;
    }

    @Override
    public String buildSqlWithQuotation() {
        return "\"";
    }

    @Override
    public String getPageSql(PageSql config) {
        StringBuilder sql = new StringBuilder(config.getQuerySql());
        appendOrderByPrimaryKeys(sql, config);
        // ClickHouse使用 LIMIT ? OFFSET ? (标准SQL)
        sql.append(" LIMIT ? OFFSET ?");
        return sql.toString();
    }

    @Override
    public Object[] getPageArgs(ReaderContext context) {
        int pageSize = context.getPageSize();
        int pageIndex = context.getPageIndex();
        return new Object[]{pageSize, (pageIndex - 1) * pageSize};
    }

    @Override
    public String getPageCursorSql(PageSql config) {
        if (!PrimaryKeyUtil.isSupportedCursor(config.getFields())) {
            return StringUtil.EMPTY;
        }
        StringBuilder sql = new StringBuilder(config.getQuerySql());
        buildCursorConditionAndOrderBy(sql, config);
        sql.append(" LIMIT ? OFFSET ?");
        return sql.toString();
    }

    @Override
    public Object[] getPageCursorArgs(ReaderContext context) {
        int pageSize = context.getPageSize();
        Object[] cursors = context.getCursors();
        if (null == cursors || cursors.length == 0) {
            return new Object[]{pageSize, 0};
        }
        Object[] cursorArgs = buildCursorArgs(cursors);
        if (cursorArgs == null) {
            return new Object[]{pageSize, 0};
        }
        Object[] newCursors = new Object[cursorArgs.length + 2];
        System.arraycopy(cursorArgs, 0, newCursors, 0, cursorArgs.length);
        newCursors[cursorArgs.length] = pageSize;
        newCursors[cursorArgs.length + 1] = 0;
        return newCursors;
    }

    @Override
    public String buildUpsertSql(DatabaseConnectorInstance connectorInstance, SqlBuilderConfig config) {
        // ClickHouse不支持ON DUPLICATE KEY UPDATE
        // 采用 ALTER TABLE ... DELETE + INSERT 方式实现upsert
        Database database = config.getDatabase();
        List<Field> fields = config.getFields();
        StringBuilder sql = new StringBuilder();

        // 1. ALTER TABLE ... DELETE (删除匹配主键的旧数据)
        List<String> pkConditions = new ArrayList<>();
        for (Field f : fields) {
            if (f.isPk()) {
                String quotedName = database.buildWithQuotation(f.getName());
                pkConditions.add(quotedName + " = ?");
            }
        }

        if (!pkConditions.isEmpty()) {
            sql.append("ALTER TABLE ").append(config.getSchema());
            sql.append(database.buildWithQuotation(config.getTableName()));
            sql.append(" DELETE WHERE ");
            sql.append(StringUtil.join(pkConditions, " AND "));
            sql.append(";");
        }

        // 2. INSERT INTO ... VALUES (插入新数据)
        List<String> fs = new ArrayList<>();
        List<String> vs = new ArrayList<>();
        for (Field f : fields) {
            String quotedName = database.buildWithQuotation(f.getName());
            fs.add(quotedName);
            vs.add("?");
        }
        sql.append("INSERT INTO ").append(config.getSchema());
        sql.append(database.buildWithQuotation(config.getTableName()));
        sql.append(" (").append(StringUtil.join(fs, StringUtil.COMMA)).append(") ");
        sql.append("VALUES (").append(StringUtil.join(vs, StringUtil.COMMA)).append(")");

        return sql.toString();
    }

    @Override
    public String buildInsertSql(SqlBuilderConfig config) {
        Database database = config.getDatabase();
        List<Field> fields = config.getFields();
        List<String> fs = new ArrayList<>();
        List<String> vs = new ArrayList<>();
        for (Field f : fields) {
            fs.add(database.buildWithQuotation(f.getName()));
            vs.add("?");
        }
        StringBuilder table = new StringBuilder();
        table.append(config.getSchema());
        table.append(database.buildWithQuotation(config.getTableName()));
        String fieldNames = StringUtil.join(fs, StringUtil.COMMA);
        String values = StringUtil.join(vs, StringUtil.COMMA);
        return String.format("INSERT INTO %s (%s) VALUES (%s)", table, fieldNames, values);
    }

    @Override
    public SchemaResolver getSchemaResolver() {
        return schemaResolver;
    }

    @Override
    protected String getSchema(String schema, Connection connection) throws SQLException {
        return StringUtil.isNotBlank(schema) ? schema : connection.getSchema();
    }

    @Override
    public String buildJdbcUrl(DatabaseConfig config, String database) {
        // jdbc:clickhouse://host:port/db?async_insert=1&wait_for_async_insert=1
        // async_insert=1: 启用ClickHouse服务端异步插入，攒批后合并写入，大幅提升OLAP写入吞吐
        // wait_for_async_insert=1: 等待异步插入完成，保证客户端感知写入结果
        StringBuilder url = new StringBuilder();
        url.append("jdbc:clickhouse://").append(config.getHost()).append(":").append(config.getPort());
        if (StringUtil.isNotBlank(database)) {
            url.append("/").append(database);
        }
        String connector = url.toString().contains("?") ? "&" : "?";
        url.append(connector).append("async_insert=1&wait_for_async_insert=1");
        return url.toString();
    }
}
