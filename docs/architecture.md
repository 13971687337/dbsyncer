# DBSyncer 架构总览

## 项目定位

DBSyncer 是一个开源数据同步中间件，解决"异构数据源之间的数据迁移和实时同步"问题。支持关系型数据库（MySQL、Oracle、SQL Server、PostgreSQL、SQLite）与中间件（Elasticsearch、Kafka、File）之间任意组合的全量和增量同步。


## 源码路标

关键入口文件可通过以下路径快速定位：

| 组件 | 文件 |
|------|------|
| Spring Boot 入口 | `dbsyncer-web/.../web/Application.java:24` |
| 连接器工厂 (SPI) | `dbsyncer-connector/.../connector/base/ConnectorFactory.java:47` |
| 全量同步管线 | `dbsyncer-parser/.../parser/impl/ParserComponentImpl.java:113` |
| 任务生命周期 | `dbsyncer-manager/.../manager/ManagerFactory.java:22` |
| Lucene 存储 | `dbsyncer-storage/.../storage/impl/DiskStorageService.java:48` |
| 安全配置 | `dbsyncer-web/.../web/config/WebAppConfig.java:45` |
| 根 POM | `pom.xml` (v2.0.8) |
| 配置参数 | `dbsyncer-web/src/main/resources/application.properties` |

## 分层架构

```
┌─────────────────────────────────────────────────────────┐
│                   dbsyncer-web                          │
│         Spring Boot + Spring Security + Thymeleaf       │
│         REST API  │  Web UI  │  OpenAPI                 │
├─────────────────────────────────────────────────────────┤
│                   dbsyncer-biz                          │
│    ConnectorService │ MappingService │ MonitorService   │
│    TableGroupService │ ConfigService │ MetricReporter   │
├─────────────────────────────────────────────────────────┤
│                   dbsyncer-manager                      │
│         ManagerFactory  │  FullPuller  │  IncrementPuller│
├─────────────────────────────────────────────────────────┤
│                   dbsyncer-parser                       │
│  ParserComponent │ BufferActuator │ DDLParser           │
│  CommandExecutor │ Convert/Handler │ FlushStrategy       │
├──────────────┬──────────────────────┬───────────────────┤
│ dbsyncer-sdk │ dbsyncer-connector   │ dbsyncer-plugin   │
│ SPI / Model  │ MySQL Oracle PG ES   │ 自定义转换插件    │
│ Storage API  │ Kafka File SQLite    │                   │
├──────────────┴──────────────────────┴───────────────────┤
│              dbsyncer-storage / dbsyncer-common          │
│    Lucene Index  │  线程调度  │  工具类  │  配置        │
└─────────────────────────────────────────────────────────┘
```

## 核心设计

### SPI 插件机制

Connector 通过 Java `ServiceLoader` 加载。每个连接器实现 `ConnectorService` 接口，在 `META-INF/services/org.dbsyncer.sdk.spi.ConnectorService` 中注册。ConnectorFactory 在 `@PostConstruct` 阶段扫描全部实现。

### 同步模型

系统区分两种同步方式（`ModelEnum`）：

- **全量同步（FULL）**：一次性将源表全部数据抽取、转换、写入目标表。由 `FullPuller` 驱动，`ParserComponentImpl.execute()` 执行分页读取→字段映射→插件转换→批量写入的循环。
- **增量同步（INCREMENT）**：持续监听源库变更（Binlog / CDC / 定时轮询）。由 `IncrementPuller` 驱动，通过 `Listener` 接口接收变更事件，经 `BufferActuator` 冲洗到目标库。

### 数据流

```
源库 Connector --reader()--> [源数据 List<Map>]
    │
    ├─ 字段映射 (Picker.pickTargetData)
    ├─ 参数转换 (ConvertUtil.convert)
    ├─ 插件转换 (PluginFactory.process)
    │
    ▼
目标库 Connector --writer()--> [写入结果 Result]
    │
    ▼
StorageService --add(DATA)--> [Lucene 索引 / 断点记录]
```

### 关键接口

- **ConnectorService**: 连接器基础功能——connect/disconnect/reader/writer/getTable/getMetaInfo
- **Listener**: 增量监听——start/close/changeEvent/refreshEvent/flushEvent
- **StorageService**: 存储服务——add/edit/remove/query/clear（基于 Lucene 磁盘索引）
- **ParserComponent**: 解析引擎——execute(全量)/writeBatch(批量写入)/getCommand
- **PluginContext**: 插件上下文——在 reader 和 writer 之间提供数据转换钩子

## 模块依赖图

```
dbsyncer-web
  ├── dbsyncer-biz
  ├── dbsyncer-manager
  ├── dbsyncer-parser
  │     ├── dbsyncer-sdk
  │     ├── dbsyncer-connector (SPI实现)
  │     ├── dbsyncer-plugin
  │     └── dbsyncer-storage
  ├── dbsyncer-common
  └── dbsyncer-storage
```

## 线程模型

系统通过多级线程池处理不同粒度的任务：

1. **调度线程池**（`dbsyncer.common.dispatch`）：全局任务分发，core=8, max=16, queue=64
2. **通用执行器**（`GeneralBufferActuator`）：增量数据冲洗，core=8, max=16, queue=64
3. **表执行器**（`TableGroupBufferActuator`）：按表维度串行冲洗，core=1, max=1, queue=16
4. **存储执行器**（`StorageBufferActuator`）：异步写入 Lucene 索引，core=4, max=8, queue=64
5. **定时任务线程池**（`dbsyncer.web.scheduler`）：监控采集、健康检查，pool-size=8

所有线程池参数通过 `application.properties` 可调。

## 存储策略

默认使用 **Lucene 磁盘索引**（`DiskStorageService`），数据目录结构：
```
data/
├── config/    # 连接器配置、映射配置、用户配置
├── log/       # 系统日志、同步日志
└── data/{metaId}/  # 每个驱动的增量数据和断点
```

存储类型通过 `dbsyncer.storage.type` 配置。生产环境推荐切换为 MySQL 存储（`dbsyncer.storage.mysql.*`）。
