# 同步管线深度分析

全量同步、增量同步的实现机制，以及多任务并发模型。

## 源码路标

| 组件 | 文件 |
|------|------|
| 全量拉取器 | `dbsyncer-manager/.../manager/impl/FullPuller.java` |
| 增量拉取器 | `dbsyncer-manager/.../manager/impl/IncrementPuller.java` |
| 全量同步引擎 | `dbsyncer-parser/.../parser/impl/ParserComponentImpl.java` |
| 缓存执行器基类 | `dbsyncer-parser/.../parser/flush/AbstractBufferActuator.java` |
| 通用执行器 | `dbsyncer-parser/.../parser/flush/impl/GeneralBufferActuator.java` |
| 表级执行器 | `dbsyncer-parser/.../parser/flush/impl/TableGroupBufferActuator.java` |
| 执行器路由 | `dbsyncer-parser/.../parser/flush/impl/BufferActuatorRouter.java` |
| 事件消费者 | `dbsyncer-parser/.../parser/consumer/ParserConsumer.java` |
| 任务生命周期 | `dbsyncer-manager/.../manager/ManagerFactory.java` |
| 持久化策略 | `dbsyncer-parser/.../parser/strategy/FlushStrategy.java` |

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

## 二、增量同步

`IncrementPuller` (`IncrementPuller.java:63`)，三条并行时间线。

### 2.1 启动线

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

### 2.2 数据变更线（热路径）

`ParserConsumer` (`consumer/ParserConsumer.java:26`) 是 Listener 和 BufferActuator 之间的桥：

```
Listener (MySQL Binlog / PG Replication / Quartz 定时轮询)
  → Watcher.changeEvent(ChangedEvent event)          [ParserConsumer.java:49]
    → bufferActuatorRouter.execute(metaId, event)     [BufferActuatorRouter.java:54]
      → 查询 router[metaId]
        ├── 有表级路由 → 取 TableGroupBufferActuator → offer(event)
        └── 无表级路由 → 降级到 GeneralBufferActuator → offer(event)
```

### 2.3 定时消费线（批量冲洗）

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

## 三、多任务并发模型

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

## 四、全量+增量的协同

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

## 五、关键设计决策

### 为什么全量和增量同时运行？

全量期间 Listener 不停止——如果不这样做，全量完成后会丢失全量期间产生的增量数据。系统通过在 BufferActuator 队列中缓冲增量事件来解决：全量期间事件进队列，全量完成后消费。

### 为什么有 General 和 TableGroup 两级 BufferActuator？

- **GeneralBufferActuator**：高吞吐（队列 50000），多线程消费（8-16），适合大多数场景
- **TableGroupBufferActuator**：单线程（core=1, max=1），队列小（10000），保证单表顺序消费

表级执行器是按需创建的——只有配置了"按表串行"的表才会分配到独立执行器，其他统一走 GeneralBufferActuator。

### 为什么 DDL 事件需要阻塞等待？

`BufferActuatorRouter.offer():109-126` 中 DDL 事件会阻塞直到队列清空。这是因为 DDL（ALTER TABLE 等）必须在所有 DML（INSERT/UPDATE/DELETE）完成后执行，否则会出现"修改表结构"和"写入数据"的并发冲突。
