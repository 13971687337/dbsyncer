# dbsyncer-parser — 解析引擎

位于 `dbsyncer-parser/src/main/java/org/dbsyncer/parser/`，是整个同步流程的编排引擎。

## 核心接口

### ParserComponent

```java
public interface ParserComponent {
    List<MetaInfo> getMetaInfo(DefaultConnectorServiceContext context);
    Map<String, String> getCommand(Mapping mapping, TableGroup tableGroup);
    void execute(Task task, Mapping mapping, TableGroup tableGroup, Executor executor);
    Result writeBatch(PluginContext pluginContext, Executor executor);
}
```

实现类：`ParserComponentImpl`（`@Component`）

## 全量同步流程

`ParserComponentImpl.execute()` 实现了完整的数据同步管线：

```
1. 加载源和目标连接器配置
2. 合并表组配置 (PickerUtil.mergeTableGroupConfig)
3. 构建 PluginContext（source/target instance, table, fields, command, batchSize）
4. 插件前置处理 (pluginFactory.process BEFORE)
5. ┌─ 循环 ───────────────────────────────────┐
   │ a. 读取一页数据 (connectorFactory.reader)  │
   │ b. 字段映射 (picker.pickTargetData)        │
   │ c. 参数转换 (ConvertUtil.convert)          │
   │ d. 插件转换 (pluginFactory.process CONVERT)│
   │ e. 批量写入 (writeBatch → connectorFactory.writer) │
   │ f. 更新分页索引和游标                      │
   │ g. 刷新进度 (flushStrategy.flushFullData)   │
   │ h. 发布刷新事件 (FullRefreshEvent)          │
   │ i. 插件后置处理 (pluginFactory.process AFTER)│
   └────────────────────────────────────────────┘
6. 判尾：source.size() < pageSize 则结束
```

### 批量写入策略 (`writeBatch`)

- 数据量 ≤ batchSize → 单次同步写入
- 数据量 > batchSize → 拆分为 N 个任务，通过 `CountDownLatch` 并行执行
- 每个任务克隆 `PluginContext`（通过 `clone()`）

## 增量同步流程

增量同步由 `dbsyncer-manager` 的 `IncrementPuller` 驱动，通过 `Listener` 接口接收变更事件：

1. 连接器 `getListener("LOG")` 或 `getListener("TIMING")` 获取监听器
2. 监听器 `start()` 启动，`register(watcher)` 注册事件处理器
3. 数据变更事件 `changeEvent(ChangedEvent)` → `BufferActuator` 缓冲队列
4. BufferActuator 定期（300ms）消费队列 → 批量写入目标库
5. 更新增量位点 → `flushEvent()` 持久化断点

## 命令执行器

`command/CommandExecutor` 负责根据同步配置组装执行参数：

- `PersistenceCommand`：持久化配置命令
- `PreloadCommand`：预加载命令（系统启动时恢复运行中的任务）

## 数据冲洗（BufferActuator）

增量数据的缓冲→批处理→写入机制：

```
增量事件流
  │
  ▼
BufferActuator.queue (缓存队列, capacity=50000)
  │
  ├─ pullCount=20000（每次拉取最大数）
  ├─ periodMillisecond=300（拉取间隔）
  │
  ▼
writeBatch (单次 writerCount=1000)
  │
  ▼
目标库
```

实现类：

| 类 | 说明 |
|---|---|
| `BufferActuatorRouter` | 路由器，根据任务类型分配到不同执行器 |
| `GeneralBufferActuator` | 通用执行器（大部分增量写入，高吞吐配置） |
| `TableGroupBufferActuator` | 表组执行器（按表维度串行，保证顺序） |
| `StorageBufferActuator` | 存储执行器（异步写入 Lucene 索引） |

配置参数（`application.properties`）：

```properties
# GeneralBufferActuator
dbsyncer.parser.general.thread-core-size=8
dbsyncer.parser.general.buffer-writer-count=1000
dbsyncer.parser.general.buffer-pull-count=20000
dbsyncer.parser.general.buffer-queue-capacity=50000
dbsyncer.parser.general.buffer-period-millisecond=300

# TableGroupBufferActuator
dbsyncer.parser.table.group.thread-core-size=1
dbsyncer.parser.table.group.buffer-queue-capacity=10000
dbsyncer.parser.table.group.buffer-period-millisecond=300
```

## 数据转换（Convert）

位于 `parser/convert/`，提供 18 种数据转换 Handler：

| Handler | 功能 |
|---------|------|
| `DefaultHandler` | 默认透传 |
| `AppendHandler` | 字符串追加后缀 |
| `PrependHandler` | 字符串添加前缀 |
| `ReplaceHandler` | 字符串替换 |
| `ClearHandler` | 清空字段值 |
| `SubStrFirstHandler` | 从头截取子串 |
| `SubStrLastHandler` | 从尾截取子串 |
| `RemStrFirstHandler` | 从头移除指定字符串 |
| `RemStrLastHandler` | 从尾移除指定字符串 |
| `DateHandler` | 日期格式化转换 |
| `TimestampHandler` | 时间戳格式化 |
| `TimestampToDateHandler` | 时间戳转日期 |
| `TimestampToLongHandler` | 时间戳转毫秒数 |
| `TimestampToChineseStandardTimeHandler` | 时间戳转中国标准时间 |
| `LongToTimestampHandler` | 毫秒数转时间戳 |
| `StringToTimestampHandler` | 字符串转时间戳 |
| `StringToFormatDateHandler` | 字符串按格式转日期 |
| `BytesToStringHandler` | 字节数组转字符串 |
| `NumberToStringHandler` | 数字转字符串 |
| `UUIDHandler` | UUID 生成 |

通过 `ConvertEnum` 注册，在 Web UI 的映射配置中选择。

## 字段映射（Picker）

`Picker` 对象负责源字段到目标字段的映射：

```java
// 构建映射关系
Picker picker = new Picker(group);
// 执行映射转换
List<Map> target = picker.pickTargetData(source);
```

映射规则：
1. 同名自动映射
2. 支持自定义映射关系（`FieldMapping`）
3. 支持自定义表（SQL 查询 / 半结构查询）
4. 过滤条件（`Filter`）支持

## DDL 解析

`DDLParser` 接口及 `DDLParserImpl` 实现，处理表结构变更同步：

| 策略 | 说明 |
|------|------|
| `AddStrategy` | 新增列 → ALTER TABLE ADD COLUMN |
| `DropStrategy` | 删除列 → ALTER TABLE DROP COLUMN |
| `ChangeStrategy` | 重命名列 → ALTER TABLE CHANGE COLUMN |
| `ModifyStrategy` | 修改列定义 → ALTER TABLE MODIFY COLUMN |

## SQL 生成

| 类 | 生成 SQL |
|---|---|
| `InsertSql` | INSERT INTO ... VALUES ... |
| `UpdateSql` | UPDATE ... SET ... WHERE ... |
| `DeleteSql` | DELETE FROM ... WHERE ... |

## 模型对象

| 类 | 说明 |
|---|---|
| `Mapping` | 同步映射配置（连接源+目标+表组+计划） |
| `TableGroup` | 表组（一对源表和目标表的映射关系） |
| `Task` | 同步任务（运行时状态：pageIndex, cursors, endTime） |
| `Connector` | 连接器（含 ConnectorConfig） |
| `Meta` | 驱动元信息（state, updateTime） |
| `ConfigModel` | 配置模型抽象基类 |
| `SystemConfig` | 系统配置（RSA、IP白名单、JWT密钥等） |
| `UserInfo` | 用户信息（username, password, roleCode） |
| `FieldMapping` | 字段映射关系 |
| `Convert` | 转换配置 |
| `Picker` | 字段选择器 |

## 关键枚举

| 枚举 | 说明 |
|---|---|
| `MetaEnum` | 驱动状态：READY / RUNNING / STOPPING |
| `ConvertEnum` | 转换类型注册 |
| `GroupStrategyEnum` | 表组分组合并策略 |
| `CommonTaskStatusEnum` | 任务状态：INIT / RUNNING / SUCCESS / FAIL / STOPPED |
