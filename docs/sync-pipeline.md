# 同步管线深度分析

全量同步、增量同步的实现机制，以及多任务并发模型。

## 源码路标

| 组件 | 文件                                                                    |
|------|-----------------------------------------------------------------------|
| 全量拉取器 | `dbsyncer-manager/.../manager/impl/FullPuller.java`                   |
| 增量拉取器 | `dbsyncer-manager/.../manager/impl/IncremenzengtPuller.java`          |
| 全量同步引擎 | `dbsyncer-parser/.../parser/impl/ParserComponentImpl.java`            |
| 缓存执行器基类 | `dbsyncer-parser/.../parser/flush/AbstractBufferActuator.java`        |
| 通用执行器 | `dbsyncer-parser/.../parser/flush/impl/GeneralBufferActuator.java`    |
| 表级执行器 | `dbsyncer-parser/.../parser/flush/impl/TableGroupBufferActuator.java` |
| 执行器路由 | `dbsyncer-parser/.../parser/flush/impl/BufferActuatorRouter.java`     |
| 事件消费者 | `dbsyncer-parser/.../parser/consumer/ParserConsumer.java`             |
| 任务生命周期 | `dbsyncer-manager/.../manager/ManagerFactory.java`                    |
| 持久化策略 | `dbsyncer-parser/.../parser/strategy/FlushStrategy.java`              |

---

## 一、全量同步

`FullPuller` (`FullPuller.java:43`) 驱动。每个 mapping 启动一个独立线程。

### 启动流程

```
ManagerFactory.start(mapping)
  └─ FullPuller.start(mapping)                              [line 59]
       └─ new Thread("full-worker-{mappingId}")              [line 82]
            └─ Executors.newFixedThreadPool(mapping.threadNum) [line 64]
                 └─ doTask(task, mapping, tableGroups, executor) [line 68]
```

### 断点恢复

`doTask` (line 102) 先从 `Meta.snapshot` 恢复上次执行位置：

```java
// FullPuller.java:109-114
Meta meta = profileComponent.getMeta(task.getId());
Map<String, String> snapshot = meta.getSnapshot();
task.setPageIndex(NumberUtil.toInt(snapshot.get("pageIndex"), 1));
task.setCursors(PrimaryKeyUtil.getLastCursors(snapshot.get("cursor")));
task.setTableGroupIndex(NumberUtil.toInt(snapshot.get("tableGroupIndex"), 0));
```

### 表组遍历

```java
// FullPuller.java:117-127
int i = task.getTableGroupIndex();
while (i < list.size()) {
    parserComponent.execute(task, mapping, list.get(i), executor);  // 同步一个表组
    if (!task.isRunning()) break;                                     // 可中断
    task.setPageIndex(1);            // 重置分页
    task.setCursors(null);           // 重置游标
    task.setTableGroupIndex(++i);    // 推进到下一个表组
    flush(task);                     // 持久化进度
}
```

### ParserComponentImpl.execute() — 单表同步循环

`ParserComponentImpl.java:113` 实现完整的全量同步管线：

```
1. 加载源和目标连接器配置
2. 合并表组配置（PickerUtil.mergeTableGroupConfig）
3. 构建 FullPluginContext
4. 插件前置处理（BEFORE）
5. ┌─ 循环 ───────────────────────────────────┐
   │ a. connectorFactory.reader() — 分页读源库  │
   │ b. picker.pickTargetData()  — 字段映射      │
   │ c. ConvertUtil.convert()    — 参数转换      │
   │ d. pluginFactory.process(CONVERT) — 插件    │
   │ e. writeBatch()             — 批量写目标库  │
   │ f. 更新 pageIndex 和 cursors                │
   │ g. flushStrategy.flushFullData() — 持久化   │
   │ h. 发布 FullRefreshEvent                    │
   │ i. pluginFactory.process(AFTER) — 后置处理  │
   └────────────────────────────────────────────┘
6. 判尾：source.size() < pageSize → 表组完成
```

### 批量并行写入 — writeBatch()

`ParserComponentImpl.java:206`：当数据量超过 `batchSize` 时，拆分并并行执行：

```
总数 > batchSize
  → 拆为 taskSize = total/batchSize 个子任务
  → CountDownLatch(taskSize)
  → 每个子任务克隆 PluginContext，executor.execute 并行写
  → latch.await() 等待全部完成
```

---

## 二、增量同步 — CDC 方案总览

DBSyncer 为不同数据库实现了不同的 CDC（Change Data Capture）策略。`ListenerTypeEnum` 区分两种监听模式：

- **LOG（日志解析型 CDC）**：直接读取数据库事务日志，实时捕获变更，延迟低
- **TIMING（定时轮询型）**：通过定时任务检查时间戳/版本号字段，有轮询间隔延迟

| 连接器 | CDC 方案 | 监听类型 | Listenr 实现 | 位点机制 |
|--------|----------|----------|-------------|----------|
| **MySQL** | Binlog 流式解析 | LOG | `MySQLListener` (`connector/mysql/cdc/`) | `(binlogFilename, position)` |
| **PostgreSQL** | Logical Replication + Replication Slot | LOG | `PostgreSQLListener` (`connector/postgresql/cdc/`) | `LogSequenceNumber (LSN)` |
| **SQL Server** | Agent CDC (`sys.sp_cdc_*`) | LOG | `SqlServerListener` (`connector/sqlserver/cdc/`) | `LSN (Log Sequence Number)` |
| **Oracle** | LogMiner (Redo Log 解析) | LOG | `OracleListener` (`connector/oracle/cdc/`) | `SCN (System Change Number)` |
| **Elasticsearch** | 定时轮询（Quartz） | TIMING | `ESQuartzListener` (`connector/elasticsearch/cdc/`) | 基于时间戳/自增 ID |
| **File** | 文件系统 WatchService | TIMING | `FileListener` (`connector/file/cdc/`) | 文件修改事件 |

### MySQL — Binlog CDC

`MySQLListener.java:58` 继承 `AbstractDatabaseListener`，基于 `com.github.shyiko.mysql.binlog` 库（`mysql-binlog-connector-java 0.30.1`）。

**连接建立** (line 108-123)：
```java
client = new BinaryLogRemoteClient(host, port, username, password);
client.setBinlogFilename(snapshot.get("fileName"));    // 从断点恢复
client.setBinlogPosition(Long.parseLong(snapshot.get("position")));
client.registerEventListener(new InnerEventListener());
client.connect();
```

**Binlog 事件处理** (`InnerEventListener.onEvent`, line 203)：单线程顺序消费，处理 WRITE_ROWS / UPDATE_ROWS / DELETE_ROWS / QUERY(DDL) / ROTATE / XID：

```
EventType.WRITE_ROWS  → new RowChangedEvent(..., "INSERT", afterRow, fileName, position)
EventType.UPDATE_ROWS → new RowChangedEvent(..., "UPDATE", afterRow, fileName, position)
EventType.DELETE_ROWS → new RowChangedEvent(..., "DELETE", beforeRow, fileName, position)
EventType.QUERY       → parseDDL(data) → DDLChangedEvent (ALTER TABLE only)
EventType.XID         → refresh(position) — 事务提交时更新位点
EventType.ROTATE      → refresh(newFileName, position) — binlog 切换时更新位点
```

**反压控制** (`trySendEvent`, line 145)：当 `BufferActuator` 队列满时，Binlog 消费线程阻塞等待 1ms 重试，直到队列有空位。`MySQLListener` 还有 `notUniqueCodeEvent` 过滤——跳过 DBSyncer 自身写入的数据（通过 SQL 前缀 `DBS_UNIQUE_CODE` 识别），避免循环同步。

**断点恢复**：位点 `(binlogFilename, position)` 持久化在 `snapshot` Map 中，由 `IncrementPuller.run()` 每 3 秒调用 `Listener.flushEvent()` 写入 `Meta.snapshot`。重启后从上次 binlog 位点继续消费。

### PostgreSQL — Logical Replication CDC

`PostgreSQLListener.java:45` 继承 `AbstractDatabaseListener`，使用 PostgreSQL 9.4+ 的逻辑复制功能。

**前置校验** (line 78-94)：
```java
// 1. 检查 wal_level 必须是 logical
GET_WAL_LEVEL → "SHOW WAL_LEVEL"
// 2. 检查用户权限（LOGIN + REPLICATION/SUPERUSER/ADMIN/RDS_ADMIN）
GET_ROLE → "SELECT rolcanlogin, rolreplication FROM pg_roles WHERE rolname = current_user"
```

**Replication Slot 管理** (line 174-199)：
```java
// 1. 检查 slot 是否存在
"SELECT count(1) FROM pg_replication_slots WHERE slot_name = ? AND plugin = ?"
// 2. 不存在则创建
pgConnection.getReplicationAPI().createReplicationSlot()
    .logical().withSlotName(slotName).withOutputPlugin(plugin).make();
// 3. 获取起始 LSN
startLsn = LogSequenceNumber.valueOf(snapshot.get("position"));
```

**解码插件**：通过 `MessageDecoderEnum` 支持两种插件：
- `pgoutput`：PostgreSQL 内置（PG 10+），`PgOutputMessageDecoder`
- `test_decoding`：PG 自带简单解码器，`TestDecodingMessageDecoder`

**消费循环** (`Worker.run`, line 266-316)：
```java
// 非阻塞读取 WAL 消息
ByteBuffer msg = stream.readPending();  // line 273
RowChangedEvent event = messageDecoder.processMessage(msg);  // line 286
sendChangedEvent(event);  // 发送到 BufferActuator
// 反馈 LSN 确认
stream.setAppliedLSN(lsn);
stream.setFlushedLSN(lsn);
stream.forceUpdateStatus();  // 更新复制槽位点
```

**自动恢复** (`recover()`, line 239-264)：连接断开后自动重连——关闭旧 stream/connection → 阻塞直到重新建立连接 → 从上次确认的 LSN 继续消费。

**Slot 清理** (`dropReplicationSlot`, line 202-237)：默认关闭时删除 replication slot（`dropSlotOnClose=true`），3 次重试处理 `OBJECT_IN_USE` 状态。

### SQL Server — Agent CDC

`SqlServerListener.java:46` 继承 `AbstractDatabaseListener`，使用 SQL Server 2008+ 的 Change Data Capture 功能。要求 SQL Server Agent 运行。

**CDC 启用** (line 216-227)：
```sql
-- 1. 启用数据库 CDC
EXEC sys.sp_cdc_enable_db
-- 2. 为每个表启用 CDC
EXEC sys.sp_cdc_enable_table @source_schema = 'dbo', @source_name = '{table}'
-- 3. 读取变更捕获实例
EXEC sys.sp_cdc_help_change_data_capture
```

**LSN 拉取** (`LsnPuller`)：SQL Server CDC 依赖 `LsnPuller` 定期获取最大 LSN (`sys.fn_cdc_get_max_lsn()`)，通过 `pushStopLsn(Lsn)` 推送到监听器的阻塞队列（`BlockingQueue<Lsn>`, 容量 256）。

**变更查询** (`pull`, line 241-271)：
```sql
-- 从上次 LSN 到当前 LSN 的所有变更
SELECT * FROM cdc.[fn_cdc_get_all_changes_{captureInstance}](startLsn, stopLsn, 'all update old')
ORDER BY [__$start_lsn] ASC, [__$seqval] ASC
```

操作码映射（`TableOperationEnum`）：1=DELETE, 2=INSERT, 3=UPDATE_BEFORE(跳过), 4=UPDATE_AFTER。

**消费循环** (`Worker.run`, line 365-394)：
```java
Lsn stopLsn = buffer.take();         // 阻塞获取最新 LSN
while ((poll = buffer.poll()) != null) stopLsn = poll;  // 取最新
pull(stopLsn);                        // 拉取 startLsn→stopLsn 的变更
lastLsn = stopLsn;                    // 推进位点
```

### Oracle — LogMiner CDC

`OracleListener.java:43` 继承 `AbstractDatabaseListener`，使用 Oracle LogMiner 解析 Redo Log。

```java
LogMiner logMiner = new LogMiner(username, password, url, schema, driverClassName);
logMiner.setStartScn(containsPos ? Long.parseLong(snapshot.get("position")) : 0);
logMiner.registerEventListener((event) -> parseEvent(event));
logMiner.start();
```

位点机制：SCN (System Change Number)。核心类：`LogMiner`, `LogMinerHelper`, `RedoEvent`, `TransactionalBuffer`（事务缓冲）。SQL 解析器：`InsertSql`, `UpdateSql`, `DeleteSql`。

### Elasticsearch — 定时轮询（非 CDC）

`ESQuartzListener.java:31` 继承 `AbstractQuartzListener`（定时监听基类，非 `AbstractDatabaseListener`）。ES 不支持事务日志，通过定时检查时间戳/自增 ID 等系统参数实现伪增量。

### File — 文件系统监控（非 CDC）

`FileListener` 继承 `AbstractListener`，使用 Java NIO `WatchService` 监听文件目录的 `ENTRY_MODIFY` 事件，实现文件变更检测。

### CDC 一致性保证

所有 CDC Listener 继承 `AbstractDatabaseListener`，共享以下机制：

1. **幂等过滤**：`isFilterTable(database, table)` 确保只处理已配置的表的变更
2. **循环同步防护**：MySQL 通过 `DBS_UNIQUE_CODE` 过滤自身写入，PG/SQLServer/Oracle 通过 schema 隔离
3. **位点原子更新**：Listener → `snapshot` Map → `IncrementPuller.flushEvent()` (每 3s) → `Meta.snapshot` → Lucene 持久化
4. **异常恢复**：消费线程异常时自动重试或重连，位点不丢失

## 三、增量同步管线（通用流程）

`IncrementPuller` (`IncrementPuller.java:63`)，三条并行时间线。

### 3.1 启动线

```
IncrementPuller.start(mapping)                              [line 96]
  └─ new Thread("increment-worker-{id}")                     [line 125]
       └─ getListener(mapping, connector, targetConnector, ...) [line 117]
            ├─ connectorFactory.getListener(type, "LOG"/"TIMING") [line 163]
            ├─ listener.register(ParserConsumer)               [line 167]
            ├─ 配置 AbstractListener（database, table, filter, snapshot）[line 183-203]
            ├─ listener.init()                                 [line 206]
            └─ listener.start()                                [line 118]
```

### 3.2 数据变更线（热路径）

`ParserConsumer` (`consumer/ParserConsumer.java:26`) 是 Listener 和 BufferActuator 之间的桥：

```
Listener (MySQL Binlog / PG Replication / Quartz 定时轮询)
  → Watcher.changeEvent(ChangedEvent event)          [ParserConsumer.java:49]
    → bufferActuatorRouter.execute(metaId, event)     [BufferActuatorRouter.java:54]
      → 查询 router[metaId]
        ├── 有表级路由 → 取 TableGroupBufferActuator → offer(event)
        └── 无表级路由 → 降级到 GeneralBufferActuator → offer(event)
```

### 3.3 定时消费线（批量冲洗）

`AbstractBufferActuator` 构造时注册定时任务 (`AbstractBufferActuator.java:77`)：

```
每 300ms 触发 run()                                      [line 179]
  → taskLock.tryLock(3s)                                 [line 182]
  → submit()                                             [line 184]
    → 从 queue 拉取最多 bufferPullCount(20000) 条         [line 212]
    → 按 partitionKey 分组到 Map<String, Response>         [line 214]
    → 遇到不同 event 类型（INSERT→DELETE）跳过分区          [line 228]
    → 遇到 DDL 事件跳过分区（保证原子性）                    [line 132]
    → 对每个分区调用 pull(response)                        [line 138]
```

#### GeneralBufferActuator.pull() — 增量写入管线

`GeneralBufferActuator.java:139`：

```
pull(response)
  → 获取 mapping + tableGroupPickers
  → 分支处理：
    ├── DDL
    │     → parseDDl() — 解析 SQL → 应用到目标库 → 更新缓存          [line 151]
    ├── SCAN
    │     → distributeTableGroup() — 全量扫描模式                      [line 158]
    └── ROW
          → distributeTableGroup()                                     [line 161]
            ├── 1. pickTargetData() — 字段映射                          [line 190]
            ├── 2. ConvertUtil.convert() — 参数转换                     [line 200]
            ├── 3. pluginFactory.process(CONVERT) — 插件转换             [line 221]
            ├── 4. parserComponent.writeBatch() — 批量写目标库           [line 224]
            ├── 5. flushStrategy.flushIncrementData() — 持久化结果       [line 229]
            ├── 6. pluginFactory.process(AFTER) — 后置处理               [line 232]
            └── 7. 发布 RefreshOffsetEvent — 更新增量位点                  [line 163]
```

---

## 四、多任务并发模型

系统通过**三层隔离**实现多任务并行执行。

### 第一层：任务级隔离

每个 Mapping 启动独立线程：

```
N 个 Mapping = N 个 full-worker（全量时）+ N 个 increment-worker（增量时）
```

`ManagerFactory.start()` 对每个 mapping 独立调用，无全局锁。`ConcurrentHashMap` 管理所有任务状态。

### 第二层：表组级并行（全量）

`FullPuller.start()` 为每个 mapping 创建固定线程池：

```java
// FullPuller.java:64
ExecutorService executor = Executors.newFixedThreadPool(mapping.getThreadNum());
```

`ParserComponentImpl.writeBatch()` (line 206) 将大数据集按 `batchSize` 拆分：

```java
// ParserComponentImpl.java:222-252
int taskSize = total % batchSize == 0 ? total / batchSize : total / batchSize + 1;
CountDownLatch latch = new CountDownLatch(taskSize);
for (int i = 0; i < taskSize; i++) {
    executor.execute(() -> {
        connectorFactory.writer(tmpContext);  // 并行写目标库
        latch.countDown();
    });
}
latch.await();  // 等待所有批次完成
```

### 第三层：表级隔离（增量）

`BufferActuatorRouter` (`BufferActuatorRouter.java:36`) 的路由策略：

```
router: Map<metaId, Map<tableName, TableGroupBufferActuator>>

同一 metaId 的不同表 → 独立的 TableGroupBufferActuator
  ├── 独立 BlockingQueue（容量 10000）
  ├── 独立 ThreadPoolTaskExecutor（core=1, max=1）
  └── 表之间完全并行，互不阻塞

未绑表的任务 → 统一降级到 GeneralBufferActuator
```

`TableGroupBufferActuator` (`TableGroupBufferActuator.java:25`) 继承 `GeneralBufferActuator`，每个表实例有自己的队列和线程池。`maxBufferActuatorSize` 限制每个驱动最多创建的表级执行器数量：

```java
// BufferActuatorRouter.java:81
if (processor.size() >= systemConfig.getMaxBufferActuatorSize()) {
    break;  // 超过上限，不再创建新的表级执行器
}
```

### 并发安全机制

| 机制 | 位置 | 作用 |
|------|------|------|
| `ConcurrentHashMap` | FullPuller.map, IncrementPuller.map, router | 无锁读，分段锁写 |
| `ReentrantLock.tryLock(3s)` | `AbstractBufferActuator.run():182` | 防止同队列并发消费 |
| `CountDownLatch` | `ParserComponentImpl.writeBatch():225` | 等待所有分片写入完成 |
| `skipPartition` | `GeneralBufferActuator:132` | INSERT→DELETE 异事件跳过分区，保证顺序 |
| DDL 阻塞等待 | `BufferActuatorRouter.offer():109-126` | DDL 事件等待队列清空后才插入，保证原子性 |

### 线程池配置

```properties
# 全量同步 — 每个 mapping 自定义线程数（Web UI 配置）

# 通用执行器（增量数据冲洗）
dbsyncer.parser.general.thread-core-size=8
dbsyncer.parser.general.max-thread-size=16
dbsyncer.parser.general.buffer-queue-capacity=50000  # 缓存队列容量
dbsyncer.parser.general.buffer-pull-count=20000      # 每次消费最大条数
dbsyncer.parser.general.buffer-period-millisecond=300 # 消费间隔

# 表级执行器（按表顺序冲洗）
dbsyncer.parser.table.group.thread-core-size=1
dbsyncer.parser.table.group.max-thread-size=1
dbsyncer.parser.table.group.buffer-queue-capacity=10000
dbsyncer.parser.table.group.buffer-period-millisecond=300
```

---

## 五、全量+增量的协同

```
启动时刻:
  ManagerFactory.start(mapping)
    ├──→ IncrementPuller.start(mapping)    ← Listener 立即开始监听
    └──→ FullPuller.start(mapping)         ← 开始全量同步

全量期间:
  FullPuller: 分页读源库 → 写入目标库
  IncrementPuller: Listener 接收增量变更 → BufferActuator 缓冲
                    └── 写入队列但不消费（全量期间增量事件进队列堆积）

全量完成:
  FullPuller: publishClosedEvent → ManagerFactory 设 Meta 为 READY
  IncrementPuller: 队列中的增量数据被消费
                    └── 300ms 定时消费 → 批量写入目标库

稳态运行:
  FullPuller: 已退出
  IncrementPuller: 持续监听 → 300ms 消费 → 实时同步
```

**断点续传：** 全量期间 `pageIndex`、`cursor`、`tableGroupIndex` 每批次写入 `Meta.snapshot` (`FullPuller.flush()` line 135)。重启后从上次位置继续，不会重复同步。

**增量位点持久化：** `IncrementPuller` 注册 `ScheduledTaskJob` (`line 92`)，每 3 秒调用 `Listener.flushEvent()`（`run()` line 153），将最新位点写入 `Meta.snapshot`。同时监听 `RefreshOffsetEvent` (`line 145`)，位点变更时实时刷新。

---

## 六、关键设计决策

### 为什么全量和增量同时运行？

全量期间 Listener 不停止——如果不这样做，全量完成后会丢失全量期间产生的增量数据。系统通过在 BufferActuator 队列中缓冲增量事件来解决：全量期间事件进队列，全量完成后消费。

### 为什么有 General 和 TableGroup 两级 BufferActuator？

- **GeneralBufferActuator**：高吞吐（队列 50000），多线程消费（8-16），适合大多数场景
- **TableGroupBufferActuator**：单线程（core=1, max=1），队列小（10000），保证单表顺序消费

表级执行器是按需创建的——只有配置了"按表串行"的表才会分配到独立执行器，其他统一走 GeneralBufferActuator。

### 为什么 DDL 事件需要阻塞等待？

`BufferActuatorRouter.offer():109-126` 中 DDL 事件会阻塞直到队列清空。这是因为 DDL（ALTER TABLE 等）必须在所有 DML（INSERT/UPDATE/DELETE）完成后执行，否则会出现"修改表结构"和"写入数据"的并发冲突。
