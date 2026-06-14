package org.dbsyncer.connector.mysql;

import org.dbsyncer.sdk.config.DatabaseConfig;
import org.dbsyncer.sdk.connector.ConfigValidator;
import org.dbsyncer.sdk.connector.database.AbstractDatabaseConnector;
import org.dbsyncer.sdk.connector.database.Database;
import org.dbsyncer.sdk.connector.database.DatabaseConnectorInstance;
import org.dbsyncer.sdk.enums.ListenerTypeEnum;
import org.dbsyncer.sdk.listener.DatabaseQuartzListener;
import org.dbsyncer.sdk.listener.Listener;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.model.PageSql;
import org.dbsyncer.sdk.plugin.ReaderContext;
import org.dbsyncer.sdk.schema.SchemaResolver;
import org.dbsyncer.sdk.spi.ConnectorService;
import org.dbsyncer.sdk.util.PrimaryKeyUtil;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.connector.mysql.validator.MySQLConfigValidator;
import org.dbsyncer.connector.mysql.schema.MySQLSchemaResolver;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.sdk.config.SqlBuilderConfig;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * StarRocks连接器（复用MySQL协议，端口9030）
 *
 * @author zhangxl
 * @version 1.0.0
 */
public final class StarRocksConnector extends AbstractDatabaseConnector implements ConnectorService<DatabaseConnectorInstance, DatabaseConfig> {

    private final MySQLConfigValidator configValidator = new MySQLConfigValidator();
    private final MySQLSchemaResolver schemaResolver = new MySQLSchemaResolver();
    private static final Set<String> SYSTEM_DBS = Stream.of("information_schema", "_statistics_", "starrocks_audit_db__").collect(Collectors.toSet());

    @Override public String getConnectorType() { return "StarRocks"; }
    @Override public ConfigValidator getConfigValidator() { return configValidator; }
    @Override public SchemaResolver getSchemaResolver() { return schemaResolver; }

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
                return databases.stream().filter(name -> !SYSTEM_DBS.contains(name.toLowerCase())).collect(Collectors.toList());
            }
            return Collections.emptyList();
        });
    }

    @Override public String buildSqlWithQuotation() { return "`"; }
    @Override public String generateUniqueCode() { return ""; }

    @Override
    public String getPageSql(PageSql config) {
        StringBuilder sql = new StringBuilder(config.getQuerySql());
        appendOrderByPrimaryKeys(sql, config);
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
        if (!PrimaryKeyUtil.isSupportedCursor(config.getFields())) return StringUtil.EMPTY;
        StringBuilder sql = new StringBuilder(config.getQuerySql());
        buildCursorConditionAndOrderBy(sql, config);
        sql.append(" LIMIT ? OFFSET ?");
        return sql.toString();
    }

    @Override
    public Object[] getPageCursorArgs(ReaderContext context) {
        int pageSize = context.getPageSize();
        Object[] cursors = context.getCursors();
        if (null == cursors || cursors.length == 0) return new Object[]{pageSize, 0};
        Object[] cursorArgs = buildCursorArgs(cursors);
        if (cursorArgs == null) return new Object[]{pageSize, 0};
        Object[] result = new Object[cursorArgs.length + 2];
        System.arraycopy(cursorArgs, 0, result, 0, cursorArgs.length);
        result[cursorArgs.length] = pageSize;
        result[cursorArgs.length + 1] = 0;
        return result;
    }

    @Override
    public String buildInsertSql(SqlBuilderConfig config) {
        Database database = config.getDatabase();
        List<Field> fields = config.getFields();
        List<String> fs = new ArrayList<>();
        List<String> vs = new ArrayList<>();
        fields.forEach(f -> { fs.add(database.buildWithQuotation(f.getName())); vs.add("?"); });
        StringBuilder table = new StringBuilder();
        table.append(config.getSchema()).append(database.buildWithQuotation(config.getTableName()));
        return String.format("INSERT INTO %s (%s) VALUES (%s)", table, StringUtil.join(fs, ","), StringUtil.join(vs, ","));
    }

    @Override
    public String buildUpsertSql(DatabaseConnectorInstance connectorInstance, SqlBuilderConfig config) {
        return buildInsertSql(config);
    }

    @Override
    public String buildJdbcUrl(DatabaseConfig config, String database) {
        StringBuilder url = new StringBuilder();
        url.append("jdbc:mysql://").append(config.getHost()).append(":").append(config.getPort());
        if (database != null && !database.trim().isEmpty()) url.append("/").append(database);
        return url.toString();
    }

    @Override
    protected String getSchema(String schema, Connection connection) { return null; }
}
