# dbsyncer-sdk — SDK 层

SDK 层定义了整个系统的核心接口和数据模型，是所有模块的基础。位于 `dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/`。

## SPI 接口

### ConnectorService

连接器基础功能接口，泛型参数 `<I extends ConnectorInstance, C extends ConnectorConfig>`。

| 方法 | 说明 |
|------|------|
| `getConnectorType()` | 连接器类型标识（如 "mysql", "oracle"） |
| `getExtendedTableType()` | 可扩展的表类型（TABLE/VIEW/CUSTOM） |
| `getConfigClass()` | 连接器配置类 |
| `connect(C, ConnectorServiceContext)` | 建立连接，返回 ConnectorInstance |
| `disconnect(I)` | 断开连接 |
| `isAlive(I)` | 连接健康检查 |
| `getDatabases(I)` | 获取数据库列表（默认空） |
| `getSchemas(I, catalog)` | 获取 Schema 列表（默认空） |
| `getTable(I, ConnectorServiceContext)` | 获取表列表 |
| `getMetaInfo(I, ConnectorServiceContext)` | 获取表元信息（列定义） |
| `getCount(I, Map<String,String>)` | 获取表记录总数 |
| `reader(I, ReaderContext)` | 分页读取数据源数据，返回 `Result` |
| `writer(I, PluginContext)` | 批量写入目标源数据，返回 `Result` |
| `writerDDL(I, DDLConfig)` | 执行 DDL 命令（默认抛异常） |
| `getSourceCommand(CommandConfig)` | 获取数据源同步参数 |
| `getTargetCommand(CommandConfig)` | 获取目标源同步参数 |
| `getListener(String)` | 根据类型获取监听器（增量用） |
| `getStorageService()` | 获取存储服务（默认 null） |
| `getSchemaResolver()` | 获取标准数据类型解析器 |
| `getPosition(I)` | 获取当前位点信息（默认空字符串） |

### Listener

增量同步监听器接口：

```java
public interface Listener {
    void init();                        // 初始化
    void start();                       // 启动定时/日志抽取任务
    void close();                       // 关闭任务
    void register(Watcher watcher);     // 注册监听事件
    void changeEventBefore(QuartzListenerContext context);  // 变更前置事件
    void changeEvent(ChangedEvent event);  // 数据变更事件
    void refreshEvent(ChangedOffset offset);  // 更新增量点
    void flushEvent();                  // 刷新增量点
    void forceFlushEvent();             // 强制刷新事件
    void errorEvent(Exception e);       // 异常事件
}
```

实现类层级：
- `AbstractListener` → 提供基础实现和模板方法
- `AbstractDatabaseListener` → 数据库增量监听基类
- `AbstractQuartzListener` → 定时轮询监听基类
- `DatabaseQuartzListener` → 数据库定时检查监听

### StorageService

存储服务接口，负责配置、日志、同步数据的持久化。详见 [storage.md](storage.md)。

```java
public interface StorageService {
    void init(Properties properties);
    Paging query(Query query);
    void delete(Query query);
    void clear(StorageEnum type, String metaId);
    void add/remove/edit(StorageEnum type, ...);
    void addBatch/editBatch/removeBatch(StorageEnum type, String metaId, List);
}
```

## 数据模型

### ConnectorInstance

连接器实例，管理连接生命周期：

```java
public interface ConnectorInstance<K extends ConnectorConfig, V> extends Cloneable {
    String getServiceUrl();    // 服务地址
    K getConfig();             // 连接配置
    void setConfig(K k);       // 设置配置
    V getConnection();         // 获取底层连接对象
    void close();              // 关闭连接
    Object clone();            // 浅拷贝
}
```

子接口：`DatabaseConnectorInstance extends ConnectorInstance<DatabaseConfig, Database>`。

### ChangedEvent

增量变更事件体系：

- `CommonChangedEvent` — 通用事件基类
- `DDLChangedEvent` — DDL 变更事件
- `RowChangedEvent` — 行级数据变更
- `ScanChangedEvent` — 扫描变更事件

### 配置模型

| 类 | 用途 |
|---|---|
| `ConnectorConfig` | 连接器配置（URL、用户名、密码等） |
| `CommandConfig` | 同步执行命令配置 |
| `DatabaseConfig` | 数据库连接配置（继承 ConnectorConfig） |
| `DDLConfig` | DDL 执行配置 |
| `ListenerConfig` | 监听器配置 |
| `SqlBuilderConfig` | SQL 构建配置 |

### 核心数据对象

| 类 | 用途 |
|---|---|
| `Table` | 数据库表（名称、类型、列集合） |
| `Field` | 字段定义（名称、类型、主键标识、是否可为空） |
| `MetaInfo` | 表元信息（表名 + 列列表） |
| `PageSql` | 分页 SQL 记录 |
| `Plugin` | 插件定义 |
| `Filter` | 过滤条件 |
| `CommonTask` | 通用任务定义 |
| `ChangedOffset` | 增量位点偏移 |
| `Point` | 数据点位（BinaryLogPosition） |

## 枚举体系

| 枚举 | 说明 |
|------|------|
| `ModelEnum` | 同步方式：FULL / INCREMENT |
| `ChangedEventTypeEnum` | 变更类型：INSERT / UPDATE / DELETE |
| `DDLOperationEnum` | DDL 操作：CREATE / ALTER / DROP / TRUNCATE |
| `ListenerTypeEnum` | 监听类型：LOG（日志解析）/ TIMING（定时） |
| `DataTypeEnum` | 标准数据类型（VARCHAR, INTEGER, BIGINT, DECIMAL, DATE, TIME, TIMESTAMP, BOOLEAN, BLOB, CLOB, BIT, BINARY, GEOMETRY, JSON, NVARCHAR, NCHAR, OTHER） |
| `FilterEnum` | 过滤条件类型：EQUAL / LIKE / GT / LT / GT_AND_EQUAL / LT_AND_EQUAL |
| `TableTypeEnum` | 表类型：TABLE / VIEW / CUSTOM |
| `StorageEnum` | 存储类型：CONFIG / DATA / LOG |
| `SqlBuilderEnum` | SQL 构建器类型 |
| `QuartzFilterEnum` | 定时任务过滤条件 |
| `SortEnum` | 排序方向：ASC / DESC |

## Schema 类型系统

位于 `org.dbsyncer.sdk.schema`，提供标准数据类型定义和解析：

| 类型 | 说明 |
|------|------|
| `StringType` | 字符串 |
| `IntType` | 整数 |
| `LongType` | 长整型 |
| `DoubleType` | 双精度浮点 |
| `FloatType` | 单精度浮点 |
| `DecimalType` | 定点数 |
| `BooleanType` | 布尔 |
| `DateType` | 日期 |
| `TimeType` | 时间 |
| `TimestampType` | 时间戳 |
| `ByteType` | 字节 |
| `BytesType` | 字节数组 |
| `ShortType` | 短整型 |

每个 `SchemaResolver` 实现负责将连接器特定的数据类型映射到标准 `DataTypeEnum`。

## 插件上下文

### PluginContext

```java
public interface PluginContext extends BaseContext {
    ModelEnum getModelEnum();        // 同步方式
    boolean isTerminated();          // 是否终止写入
    void setTerminated(boolean);     // 终止写入
    ConnectorInstance getTargetConnectorInstance();
    String getSourceTableName();
    String getTargetTableName();
    String getEvent();               // INSERT/UPDATE/DELETE
    List<Field> getTargetFields();
    int getBatchSize();
    boolean isForceUpdate();
    List<Map> getSourceList();       // 数据源数据
    List<Map> getTargetList();       // 转换后待写入数据
    Plugin getPlugin();
    String getPluginExtInfo();       // 插件参数
    String getTraceId();
}
```

### ReaderContext

继承 `PluginContext`，额外提供数据读取相关上下文（分页、游标等）。

## SPI 工厂接口

| 接口 | 用途 |
|------|------|
| `ServiceFactory` | 获取 Bean 的工厂 |
| `ConnectorService` | 连接器服务（SPI) |
| `DeploymentService` | 部署服务（集群模式） |
| `LicenseService` | 许可证服务（专业版） |
| `PluginService` | 插件服务 |
| `TaskService` | 任务调度服务 |
| `TableGroupBufferActuatorService` | 表组分批执行器服务 |
