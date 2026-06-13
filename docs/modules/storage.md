# dbsyncer-storage — 存储层

位于 `dbsyncer-storage/src/main/java/org/dbsyncer/storage/`，基于 Apache Lucene 的嵌入式磁盘存储，负责同步配置、日志和数据断点的持久化。

## 存储实现

### DiskStorageService `storage/impl/DiskStorageService.java:48`

继承 `AbstractStorageService`，基于 Lucene 索引实现。数据根路径 line 55：`System.getProperty("user.dir") + "/data/"`。

```java
public class DiskStorageService extends AbstractStorageService {
    private Map<String, Shard> shards = new ConcurrentHashMap<>();
    private static final String PATH = System.getProperty("user.dir") + File.separatorChar + "data" + File.separatorChar;
}
```

**数据目录结构：**
```
{user.dir}/data/
├── config/        # StorageEnum.CONFIG → 连接器、映射、系统配置
├── log/           # StorageEnum.LOG    → 同步日志、系统日志
└── data/{metaId}/ # StorageEnum.DATA   → 每个驱动的增量数据 [metaId=驱动ID]
```

### Shard（Lucene 分片）

每个目录对应一个 `Shard` 实例，封装 Lucene 的 `IndexWriter` 和 `IndexReader`：

- 创建索引：`shard.insertBatch(docs)`
- 更新文档：`shard.update(term, doc)`
- 删除文档：`shard.delete(query)` / `shard.deleteBatch(terms)`
- 查询：`shard.query(option, pageNum, pageSize, sort)`

## 存储类型

| 枚举值 | 存储内容 | 说明 |
|--------|----------|------|
| `CONFIG` | 连接器配置、映射配置、系统配置、用户配置 | 全局配置 |
| `DATA` | 同步数据记录、断点信息、增量位点 | 按 metaId 分目录 |
| `LOG` | 同步日志、系统日志 | 全局日志 |
| `TASK_DATA_VERIFICATION_DETAIL` | 任务数据校验详情 | 数据校验记录 |

## 查询体系

### Query

```java
public class Query {
    int pageNum;           // 页码（从1开始）
    int pageSize;          // 每页大小（默认20）
    boolean queryTotal;    // 是否查询总数
    Sort sort;             // 排序（默认按修改时间+创建时间倒序）
    BooleanFilter booleanFilter;  // 布尔过滤条件
    Map<String, FieldResolver> fieldResolverMap;  // 字段解析器
}
```

### Filter 体系

| 类 | 说明 |
|---|---|
| `AbstractFilter` | 过滤条件抽象：name, value, filter, operation, enableHighLightSearch |
| `BooleanFilter` | 布尔组合：filters + clauses + operationEnum |
| `CompareFilter` | 比较过滤器 |
| `FieldResolver` | 字段值解析器 |
| `IntFilter` / `LongFilter` / `StringFilter` | 类型化过滤器 |

### 过滤条件枚举

`FilterEnum`:
- `EQUAL` — 等于（对应 Lucene `TermQuery`）
- `LIKE` — 模糊匹配
- `GT` / `GT_AND_EQUAL` — 大于 / 大于等于
- `LT` / `LT_AND_EQUAL` — 小于 / 小于等于

### 操作符

`OperationEnum`: `AND`（MUST） / `OR`（SHOULD）

## Binlog 存储

`storage/binlog/` 包提供 MySQL Binlog 事件的 Protobuf 序列化：

- `BinlogMessage` — 完整的 Binlog 事件消息
- `BinlogMap` — Binlog 列值映射
- `EventEnum` — 事件类型枚举
- `BinlogMessageProto` — Protobuf 定义

Protobuf 格式用于跨系统传输和持久化 Binlog 事件。

## 文档工具

`DocumentUtil` 负责不同类型数据到 Lucene `Document` 的转换：

| 方法 | 输入 | 输出 |
|------|------|------|
| `convertConfig2Doc(Map)` | 配置 Map | Document（config 索引） |
| `convertLog2Doc(Map)` | 日志 Map | Document（log 索引） |
| `convertData2Doc(Map)` | 数据 Map | Document（data 索引） |

## 存储策略

### Strategy 接口

```java
public interface Strategy {
    String createSharding(StorageEnum type, String metaId);
}
```

| 实现 | 分片策略 |
|------|----------|
| `ConfigStrategy` | `"config"` — 所有配置存一个分片 |
| `LogStrategy` | `"log"` — 所有日志存一个分片 |
| `DataStrategy` | `"data/" + metaId` — 按驱动 ID 分片 |
| `TaskStrategy` | 按任务维度分片 |
| `TaskDataVerificationDetailStrategy` | 数据校验详情分片 |

## 存储状态

`StorageDataStatusEnum` 标记同步数据状态：
- SUCCESS / FAIL / RETRY / IGNORE

## ID 生成

`SnowflakeIdWorker` 基于 Twitter Snowflake 算法的分布式 ID 生成器。
