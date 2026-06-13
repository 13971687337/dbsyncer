、# 性能分析与优化指南

数百张表全量+增量同步场景下的瓶颈分析与优化方案。

## 源码路标

| 组件 | 文件 |
|------|------|
| 连接池 | `dbsyncer-sdk/.../connector/database/ds/SimpleDataSource.java` |
| 连接池配置 | `dbsyncer-sdk/.../config/DatabaseConfig.java:44` (maxActive=64) |
| 全量拉取器 | `dbsyncer-manager/.../manager/impl/FullPuller.java` |
| 增量拉取器 | `dbsyncer-manager/.../manager/impl/IncrementPuller.java` |
| 缓存执行器基类 | `dbsyncer-parser/.../parser/flush/AbstractBufferActuator.java` |
| 通用执行器 | `dbsyncer-parser/.../parser/flush/impl/GeneralBufferActuator.java` |
| 执行器路由 | `dbsyncer-parser/.../parser/flush/impl/BufferActuatorRouter.java` |
| 系统配置 | `dbsyncer-parser/.../parser/model/SystemConfig.java` |
| 映射配置 | `dbsyncer-parser/.../parser/model/Mapping.java` |
| 存储实现 | `dbsyncer-storage/.../storage/impl/DiskStorageService.java` |
| 应用配置 | `dbsyncer-web/src/main/resources/application.properties` |

---

## 一、关键配置默认值

| 配置项 | 默认值 | 作用域 | 位置 |
|--------|--------|--------|------|
| `maxActive` | 64 (1-512) | 每个连接器实例 | `DatabaseConfig.java:44` |
| `threadNum` | 10 | 每个 Mapping 全量线程数 | `Mapping.java:74` |
| `readNum` | 10000 | 全量单次读行数 | `Mapping.java:68` |
| `batchNum` | 1000 | 批量写目标库行数 | `Mapping.java:71` |
| `maxBufferActuatorSize` | 50 | 单驱动表级执行器上限 | `SystemConfig.java:68` |
| `general.thread-core-size` | 8 | 通用执行器消费线程 | `application.properties` |
| `general.max-thread-size` | 16 | 通用执行器最大线程 | `application.properties` |
| `general.buffer-queue-capacity` | 50000 | 增量事件缓存队列 | `application.properties` |
| `general.buffer-pull-count` | 20000 | 每次消费最大拉取数 | `application.properties` |
| `general.buffer-period-millisecond` | 300 | 消费间隔 | `application.properties` |
| `table.group.thread-core-size` | 1 | 表级执行器线程数 | `application.properties` |
| `table.group.buffer-queue-capacity` | 10000 | 表级执行器队列 | `application.properties` |
| `MAX_PULL_TIME` | 20 | 连接池获取重试次数 | `SimpleDataSource.java:26` |

---

## 二、瓶颈分析

### 瓶颈 1：自研连接池性能不足

`SimpleDataSource` (`ds/SimpleDataSource.java:23`) 是基于 `LinkedBlockingQueue` 的自研连接池：

- **无连接预热** — `activeNum` 从 0 开始，冷启动逐个建连
- **无 PreparedStatement 缓存** — 每批 INSERT 重新 prepare SQL
- **借出不验证** — 只在归还时检查 `keepAlive` 过期，不在借出时验证存活
- **池满直接抛异常** (line 61-62) — `activeNum >= maxActive` 抛 `SdkException`，不阻塞等待

**数百表影响：**

```
每个 Mapping 持有 source + target 两个连接器实例
每个实例一个 SimpleDataSource (maxActive=64)
100 Mapping × 2 × 64 = 最多 12800 个数据库连接
→ 对源库形成连接风暴，大量连接处于空闲
→ 同表映射无法共享连接
```

### 瓶颈 2：全量表组串行处理

`FullPuller.doTask()` (line 117-127) 使用 while 循环逐个表组同步：

```java
int i = task.getTableGroupIndex();
while (i < list.size()) {
    parserComponent.execute(task, mapping, list.get(i), executor);  // 阻塞
    if (!task.isRunning()) break;
    task.setTableGroupIndex(++i);
    flush(task);
}
```

- 表组内部可并行（`threadNum=10`，`writeBatch` 拆分）
- 表组之间严格串行
- 100 张表，每表平均 5 分钟 = 500 分钟（8 小时+）
- Mapping 之间虽可并行，但每个 Mapping 通常对应一个源库实例，拆成多个 Mapping 增加管理复杂度

### 瓶颈 3：GeneralBufferActuator 单线程消费

`AbstractBufferActuator.run()` (line 179-193) 使用 `taskLock.tryLock(3s)` 保证同一时刻只有一个线程消费：

```java
boolean locked = taskLock.tryLock(3, TimeUnit.SECONDS);
if (locked) {
    submit();  // 单线程拉取 → 分区 → 批量写入
}
```

```
消费模型：单线程拉取，多线程写入
消费间隔：300ms
每次拉取：20000 条
理论上限：~66000 条/秒（假设处理耗时为零）

实际链路：
  pull() → distributeTableGroup() → writeBatch() → connectorFactory.writer()
  这 3 步都有同步 DB IO，实际在 2000-8000 条/秒（取决于目标库性能）
```

几百张表的增量变更全部涌入同一队列（容量 50000），高负载时：

```
BufferActuator 队列满 → offerFailed() 抛 QueueOverflowException
  → MySQLListener.trySendEvent() 阻塞重试 1ms
    → Binlog 消费线程停顿 → Binlog 位点推进变慢
      → 重启后需要从上次位点追数据，延迟进一步加大
```

### 瓶颈 4：表级执行器上限硬限制

`BufferActuatorRouter.bind()` (line 77-99) 创建 `TableGroupBufferActuator` 时：

```java
if (processor.size() >= systemConfig.getMaxBufferActuatorSize()) {  // 默认 50
    break;  // 不再创建，后续表全部降级
}
```

- 前 50 张表 → 独立 `TableGroupBufferActuator`（单线程，串行化单表操作）
- 第 51+ 张表 → 降级到 `GeneralBufferActuator`（与其他所有表共用）
- 超限的表之间缺乏隔离，一张大表的批量写入可能阻塞其他表

### 瓶颈 5：Lucene 索引写入争用

`DiskStorageService` 的 `Shard` 封装 Lucene `IndexWriter`，单写入者设计：

- `flushFullData()` — 每批次全量写入调用
- `flushIncrementData()` — 每批次增量写入调用
- `IncrementPuller.run()` — 每 3 秒遍历所有 Listener 更新位点

三者争用一个 `IndexWriter`，高并发下大量线程在 Lucene 内部排队。

建议：生产环境切换 `dbsyncer.storage.type=mysql`。

### 瓶颈 6：IncrementPuller 全局定时 flush

`IncrementPuller.run()` (line 152-156) 每 3 秒执行：

```java
public void run() {
    map.values().forEach(Listener::flushEvent);  // N 个驱动全部 flush
}
```

几百个驱动 × 每 3 秒 = 高频位点写入。`flushEvent` 最终走 `profileComponent.editConfigModel(meta)` → Lucene `IndexWriter.updateDocument()`。

### 瓶颈 7：Binlog 事件全链路对象传递

数据从 CDC Listener 到目标库的传递链条：

```
Binlog Event (byte[])
  → RowChangedEvent(List<Object>)        // MySQLListener:223
    → BufferActuatorRouter.offer()        // 进队列
      → GeneralBufferActuator.submit()    // 出队列
        → distributeTableGroup()
          → picker.pickTargetData()       // 列类型解析 + 字段映射
          → ConvertUtil.convert()         // 参数转换
          → writeBatch()                  // 批量写
            → connectorFactory.writer()   // 生成 SQL + JDBC 执行
```

`List<Object>` 从 Listener 一路传到 writer，中间经过 6-7 次对象传递。大字段（BLOB/CLOB/GEOMETRY）在高吞吐下 GC 压力显著。列类型解析 (`picker.pickTargetData`) 应该尽可能在 Listener 端早做。

---

## 三、优化方案

### P0 — 替换 SimpleDataSource 为 HikariCP

**问题：** 自研池缺少预热、缓存、存活检测。高负载下连接数爆炸。

**方案：** 引入 HikariCP 替换 `SimpleDataSource`。

```
改动点：DatabaseConnectorInstance.java:43
  new SimpleDataSource(...) → new HikariDataSource(hikariConfig)

预计收益：
  - 连接复用率提升 50%+（minIdle 预热 + connectionTimeout 阻塞等待）
  - PreparedStatement 缓存减少 SQL 解析开销
  - 同源库可共享连接池
```

**实现复杂度：** 低。HikariCP 可通过 Spring Boot Starter 自动配置，`DatabaseConfig` 加 `hikari` 配置段即可。

### P0 — 全量表组并行化

**问题：** 100 张表串行同步耗时 8 小时+。

**方案：** `FullPuller.doTask()` 拆分为按并行度分组执行。

```
改造前：
  while (i < list.size()) {
      execute(tableGroup[i]);  // 串行
  }

改造后：
  List<TableGroup> groups = partitionByDependency(list);
  groups.parallelStream() 或 线程池分片
      .forEach(g -> execute(task, mapping, g, executor));
```

**注意：** 表组间可能存在外键依赖，需支持配置依赖顺序。默认可并行，有依赖的串行执行。

**实现复杂度：** 中。需要引入表组依赖配置 + 并行度控制。

### P1 — 提高 maxBufferActuatorSize 或改为动态

**问题：** 默认 50，超过后全部降级到 GeneralBufferActuator。

**方案：** 短期在 `application.properties` 增大此值。长期改为按需动态创建。

```properties
# 短期：增大上限
dbsyncer.parser.max-buffer-actuator-size=200
```

**实现复杂度：** 低（配置即可）。

### P1 — GeneralBufferActuator 多分区并行消费

**问题：** 单线程 `submit()` 从队列拉取，消费瓶颈在 3-8 千条/秒。

**方案：** 引入 partition-level 并行——不同表的分区可以并行 pull。

```
改造 submit():
  Map<String, Response> map = pollAndPartition(queue, pullCount);
  // 改为并行处理不同分区
  ExecutorService parallelExecutor = ...;
  map.forEach((key, response) -> parallelExecutor.submit(() -> pull(response)));
```

**注意：** 同表数据必须顺序处理（保证 INSERT→UPDATE→DELETE 顺序）。不同表之间可以并行。

**实现复杂度：** 中。需要确保 `skipPartition` 逻辑在多线程下安全。

### P1 — 关闭不必要的存储写入

**问题：** 同步成功数据写入 Lucene 索引，大数据量下 Lucene 成为瓶颈。

**方案：** 仅保留失败数据记录，关闭成功/全量数据写入。

```properties
# application.properties 或 Web UI 系统配置
dbsyncer.storage.enable-storage-write-success=false   # 关闭成功记录
dbsyncer.storage.enable-storage-write-full=false      # 关闭全量记录
dbsyncer.storage.enable-storage-write-fail=true       # 保留失败记录
```

**实现复杂度：** 低（配置即可）。减少 Lucene 写入量 80%+。

### P2 — 存储切换为 MySQL

**问题：** Lucene 磁盘索引在数百并发写入者场景下性能不足。

**方案：** `dbsyncer.storage.type=mysql`，配置 MySQL 连接参数。

```properties
dbsyncer.storage.type=mysql
dbsyncer.storage.mysql.url=jdbc:mysql://127.0.0.1:3306/dbsyncer?rewriteBatchedStatements=true
dbsyncer.storage.mysql.username=root
dbsyncer.storage.mysql.password=***
```

**实现复杂度：** 低（配置即可）。MySQL 存储支持行级并发写入，比 Lucene 单写入者更适合高并发场景。

### P2 — 位点持久化改为异步批量

**问题：** `IncrementPuller.run()` 每 3 秒同步遍历 N 个 Listener，逐个写 Lucene。

**方案：** 位点更新推入专用缓冲队列，攒批异步写入。

```
改造：
  Listener.flushEvent() → 写入位点缓冲队列（内存）
  独立的位点消费线程 → 每 10s 或积攒 100 条 → 批量写 Lucene

注意：进程崩溃会丢失最近 10s 的位点更新，重启后可能重复同步少量数据。
DBSyncer 的幂等设计（INSERT 写覆盖/忽略）保证了短期重复数据的安全性。
```

**实现复杂度：** 中。需要在 Listener 和 StorageService 之间加一层异步缓冲。

### P2 — CDC 列类型早解析

**问题：** Binlog 事件中的 byte[] / Object 在管线中传递 6-7 跳后才解析为具体类型。

**方案：** 在 Listener 端（`MySQLListener`, `PostgreSQLListener`, `SqlServerListener`）的 `sendChangedEvent()` 之前完成列类型解析，将 `List<Object>` 转换为 `List<Map<String, Object>>`（带字段名和类型）。

**实现复杂度：** 高。需要将 `SchemaResolver` 下推到 Listener 层，改变 `ChangedEvent` 的数据结构。但收益显著——减少中间跳转的对象创建，提前释放 Binlog 原始数据。

---

## 四、配置调优速查表

### 小规模（< 10 表）

```properties
# 保持默认即可
dbsyncer.parser.general.buffer-queue-capacity=50000
dbsyncer.parser.general.buffer-pull-count=20000
dbsyncer.parser.general.buffer-period-millisecond=300
```

### 中规模（10-50 表）

```properties
dbsyncer.parser.general.buffer-queue-capacity=100000
dbsyncer.parser.general.buffer-pull-count=50000
dbsyncer.parser.general.thread-core-size=16
dbsyncer.parser.general.max-thread-size=32
dbsyncer.parser.max-buffer-actuator-size=100
dbsyncer.storage.type=mysql
```

### 大规模（50-200 表）

```properties
dbsyncer.parser.general.buffer-queue-capacity=200000
dbsyncer.parser.general.buffer-pull-count=100000
dbsyncer.parser.general.buffer-period-millisecond=100
dbsyncer.parser.general.thread-core-size=32
dbsyncer.parser.general.max-thread-size=64
dbsyncer.parser.max-buffer-actuator-size=500
dbsyncer.storage.type=mysql
dbsyncer.storage.enable-storage-write-success=false
dbsyncer.storage.enable-storage-write-full=false
```

---

## 五、监控指标

| 指标 | 说明 | 健康阈值 |
|------|------|----------|
| `GeneralBufferActuator.queue.size()` | 增量事件堆积量 | < 队列容量 50% |
| `BufferActuatorRouter.getQueueSize()` | 总路由队列大小 | < 总容量 50% |
| `ThreadPoolExecutor.activeCount` | 活跃写线程数 | < maxThreadSize 80% |
| `SimpleDataSource.activeNum` | 活跃连接数 | < maxActive 80% |
| `Meta.snapshot` 位点延迟 | Binlog 位点与当前时间差 | < 10s |
| Lucene IndexWriter RAM usage | 索引内存占用 | < 256MB |

通过 Spring Actuator (`/app/metrics`) 和 MonitorController (`/monitor/metric`) 可获取部分指标。
