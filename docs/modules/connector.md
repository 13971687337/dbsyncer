# dbsyncer-connector — 连接器实现

位于 `dbsyncer-connector/`，包含 ConnectorService 接口的所有具体实现。通过 Java SPI 机制加载。

## 子模块结构

```
dbsyncer-connector/
├── dbsyncer-connector-base/        # 连接器工厂 + 基类
├── dbsyncer-connector-mysql/       # MySQL 连接器
├── dbsyncer-connector-oracle/      # Oracle 连接器
├── dbsyncer-connector-sqlserver/   # SQL Server 连接器
├── dbsyncer-connector-postgresql/  # PostgreSQL 连接器
├── dbsyncer-connector-sqlite/      # SQLite 连接器
├── dbsyncer-connector-elasticsearch/ # Elasticsearch 连接器
├── dbsyncer-connector-kafka/       # Kafka 连接器
└── dbsyncer-connector-file/        # File 连接器
```

## ConnectorFactory（连接器核心枢纽）`connector-base/.../ConnectorFactory.java:47`

通过 `@PostConstruct` (line 57) 使用 Java `ServiceLoader` 加载所有 `ConnectorService` SPI 实现 (line 58-63)。

```java
@Component
public class ConnectorFactory implements DisposableBean {
    // 连接池 Map<String, ConnectorInstance>
    private final Map<String, ConnectorInstance> pool = new ConcurrentHashMap<>();
    // 连接器服务注册表 Map<String, ConnectorService>
    private final Map<String, ConnectorService> service = new ConcurrentHashMap<>();

    @PostConstruct
    private void init() {
        // 通过 ServiceLoader 扫描所有 ConnectorService SPI 实现
        ServiceLoader<ConnectorService> services = ServiceLoader.load(ConnectorService.class, ...);
    }
}
```

### 关键方法

| 方法 | 行号 | 说明 |
|------|------|------|
| `connect(id, config, catalog, schema)` | `:80` | 建立连接并缓存到池，返回克隆实例 |
| `connect(instanceId)` | `:109` | 从池中获取已有连接 |
| `getListener(connectorType, listenerType)` | `:122` | 获取增量监听器 |
| `reader(context)` | `:198` | 读数据，委托给对应 ConnectorService |
| `writer(context)` | `:207` | 写数据，含 `convertProcessBeforeWriter` 预处理 |
| `writerDDL(instance, config)` | `:236` | 执行 DDL |
| `disconnect(instanceId)` | `:265` | 原子性断开并从池中移除 |

## 各连接器能力

### MySQL (`dbsyncer-connector-mysql`)
- 连接器类型: `mysql`
- JDBC 驱动: `mysql-connector-java 8.0.21`
- 增量方式: Binlog 日志解析（`mysql-binlog-connector-java 0.30.1`）
- 特殊支持: GEOMETRY 空间数据（JTS Core 1.19.0）
- 监听器: `BinaryLogRemoteClientTest` 验证 binlog 远程连接

### Oracle (`dbsyncer-connector-oracle`)
- 连接器类型: `oracle`
- JDBC 驱动: `ojdbc8 21.6.0.0`
- 增量方式: LogMiner / 定时轮询
- 特殊支持: `orai18n 21.6.0.0` 国际化支持

### SQL Server (`dbsyncer-connector-sqlserver`)
- 连接器类型: `sqlserver`
- JDBC 驱动: `mssql-jdbc 8.4.1.jre8`
- 增量方式: CDC (Change Data Capture)
- 特殊注意: TLS 版本兼容性

### PostgreSQL (`dbsyncer-connector-postgresql`)
- 连接器类型: `postgresql`
- JDBC 驱动: `postgresql 42.3.3`
- 增量方式: Logical Replication（PG Replication Slot）
- 特殊支持: PostGIS 空间数据（`postgis-jdbc 2.5.1`）

### Elasticsearch (`dbsyncer-connector-elasticsearch`)
- 连接器类型: `elasticsearch`
- 版本支持: ES 6.0.0 ~ 8.15.3
- 核心类: `ElasticsearchConnector`, `ESConnectorInstance`, `EasyRestHighLevelClient`
- Schema 解析: `ElasticsearchSchemaResolver`
- 增量方式: 定时扫描（`ESQuartzListener`）
- 配置校验: `ESConfigValidator`

### Kafka (`dbsyncer-connector-kafka`)
- 连接器类型: `kafka`
- 客户端: `kafka-clients 0.11.0.0`
- 核心类: `KafkaConnector`, `KafkaConnectorInstance`

### File (`dbsyncer-connector-file`)
- 连接器类型: `file`
- 支持格式: `*.txt`, `*.unl`
- 特性: 文件监控（`FileWatchTest`）

### SQLite (`dbsyncer-connector-sqlite`)
- 连接器类型: `sqlite`
- 内嵌数据库，无需外部服务

## SDK 中的连接器抽象基类

位于 `dbsyncer-sdk`，为连接器实现提供模板方法：

| 类 | 说明 |
|---|---|
| `AbstractConnector` | 连接器顶层抽象，持有 StorageService、SchemaResolver |
| `AbstractDatabaseConnector` | 数据库连接器基类，持有 DatabaseTemplate |
| `AbstractDataBaseConfigValidator` | 数据库连接配置校验基类 |
| `AbstractSqlBuilder` | SQL 语句构建器基类 |

### DatabaseTemplate

```java
public interface DatabaseTemplate {
    SimpleConnection getSimpleConnection();
    <T> T execute(HandleCallback<T> callback);  // 执行数据库操作
}
```

### SQL Builder 族

| 类 | SQL 操作 |
|---|---|
| `SqlBuilderQuery` | SELECT 查询 |
| `SqlBuilderQueryCount` | SELECT COUNT 查询 |
| `SqlBuilderQueryCursor` | 游标方式分页查询 |
| `SqlBuilderInsert` | INSERT 写入 |
| `SqlBuilderUpdate` | UPDATE 更新 |
| `SqlBuilderDelete` | DELETE 删除 |

## 连接器实例管理

### 连接池策略

- 使用 `ConcurrentHashMap` 按 `instanceId` 缓存连接实例
- 每次 `connect()` 返回克隆实例（通过 `clone()`），原实例保持在池中
- 断开连接时原子性地从池中移除：`pool.computeIfPresent(instanceId, ...)`
- Spring 容器销毁时（`DisposableBean.destroy()`）清理所有连接
