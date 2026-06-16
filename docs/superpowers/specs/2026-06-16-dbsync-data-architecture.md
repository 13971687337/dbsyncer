# DBSyncer 数据同步架构分析与优化路线

> 编写日期：2026-06-16 | 基于代码版本：master | 状态：已审核

---

## 阅读指引

- **理解现状** → 读 Part 1（第 1-4 章）：架构、CDC 原理、全量同步、业界对比
- **了解优化项** → 读 Part 2（第 5 章）：三阶段优化，每项含问题→方案→改动→收益
- **准备实施** → 读 5.5 执行路线 + 附录 A/B（异常处理矩阵 + 测试矩阵）
- **外部参考** → 附录 B 的测试矩阵可以直接作为验收标准

---

## 目录

### Part 1 — 现状分析

1. [整体架构概览](#1-整体架构概览)
2. [增量 CDC 同步](#2-增量-cdc-同步)
3. [全量同步](#3-全量同步)
4. [与 FlinkCDC / SeaTunnel 对比](#4-与-flinkcdc--seatunnel-对比)

### Part 2 — 优化方案

5. [优化路线](#5-优化路线)
   - 5.1 [优化目标](#51-优化目标)
   - 5.2 [第一阶段：快速收益（1-2 周）](#52-第一阶段快速收益1-2-周)
   - 5.3 [第二阶段：稳定性增强（2-4 周）](#53-第二阶段稳定性增强2-4-周)
   - 5.4 [第三阶段：架构级提升（1-3 月）](#54-第三阶段架构级提升1-3-月)
   - 5.5 [执行路线与优先级矩阵](#55-执行路线与优先级矩阵)
   - 5.6 [不做的事情](#56-不做的事情)
   - 5.7 [附录 A：异常处理矩阵](#57-附录-a异常处理矩阵)
   - 5.8 [附录 B：关键测试矩阵](#58-附录-b关键测试矩阵)

---

# Part 1 — 现状分析

## 1. 整体架构概览

### 1.1 核心分层

```
┌────────────────────────────────────────────────────────────┐
│                      Web UI 管理层                          │
│          (Mapping/Connector/TableGroup 配置)                │
└────────────────────────────────────────────────────────────┘
                              │
            ┌─────────────────┴─────────────────┐
            ▼                                   ▼
   ┌─────────────────┐                 ┌─────────────────┐
   │   FullPuller     │                 │ IncrementPuller │
   │   (全量同步)      │                 │   (增量同步)     │
   └────────┬────────┘                 └────────┬────────┘
            │                                   │
            ▼                                   ▼
   ┌─────────────────┐                 ┌─────────────────┐
   │  ParserComponent │                 │    Listener      │
   │  (分页读取源端)    │                 │ (CDC/定时/Kafka) │
   └────────┬────────┘                 └────────┬────────┘
            │                                   │
            └─────────────┬─────────────────────┘
                          ▼
               ┌─────────────────────┐
               │ BufferActuatorRouter │  ← 按源表名路由
               │   (事件分发路由)      │
               └──────────┬──────────┘
                          │
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
   ┌────────────┐ ┌────────────┐ ┌────────────────┐
   │TableGroup  │ │TableGroup  │ │GeneralBuffer   │
   │BufferAct.1 │ │BufferAct.N │ │Actuator (兜底)  │
   │(user表专用) │ │(order表专用)│ │(多线程批量写)    │
   └─────┬──────┘ └─────┬──────┘ └───────┬────────┘
         │              │               │
         └──────────────┼───────────────┘
                        ▼
               ┌─────────────────┐
               │   目标端 Connector │
               │ (MySQL/PG/Oracle/ │
               │  ES/Kafka/File)   │
               └─────────────────┘
```

### 1.2 同步模式（ModelEnum）

| 模式 | 标识 | 说明 |
|------|------|------|
| 全量 | `FULL` | 分页读取源表全量数据，逐行/批量写入目标 |
| 增量 | `INCREMENT` | 仅同步变化数据，支持日志 CDC 和定时轮询两种监听器 |

同一个 Mapping 可以在全量和增量之间切换。

### 1.3 监听器类型（ListenerTypeEnum）

| 类型 | 标识 | 说明 |
|------|------|------|
| 日志 | `LOG` | 基于数据库日志（binlog/WAL/redo）实时捕获变更 |
| 定时 | `TIMING` | 基于 Cron 表达式定时查询源表，通过时间戳/自增ID识别增量 |

---

## 2. 增量 CDC 同步

### 2.1 监听器接口设计

```java
public interface Listener {
    void start();                              // 启动日志/定时抽取
    void close();                              // 关闭
    void changeEvent(ChangedEvent event);       // 数据变更事件
    void refreshEvent(ChangedOffset offset);    // 更新增量位点
    void flushEvent();                          // 持久化位点（定时调用）
    void forceFlushEvent();                     // 强制持久化位点
    void errorEvent(Exception e);               // 异常通知
}
```

所有监听器继承自 `AbstractListener`，位点数据存储在内存 `Map<String, String> snapshot` 中，通过 `flushEvent()` 定时（20s 窗口）或 `forceFlushEvent()` 立即持久化到 Meta 配置。

### 2.2 日志级 CDC（ListenerTypeEnum.LOG）

#### 2.2.1 MySQL Binlog 实现

**连接方式：伪装成 MySQL Slave**

```
1. TCP Socket 连接 MySQL:3306
2. MySQL 认证握手（username/password）
3. SHOW MASTER STATUS → 获取当前 binlog 位点
4. 发送 COM_BINLOG_DUMP 命令 → MySQL 开始持续推送 binlog 事件流
```

使用的底层库：`com.github.shyiko:mysql-binlog-connector-java`，项目进行了自定义封装（`BinaryLogRemoteClient`）。

**事件消费流程（严格单线程）：**

```
binlog stream
  │
  ▼
listenForEventPackets()  [单 Worker 线程循环读取 Socket InputStream]
  │
  ▼
EventDeserializer.nextEvent()  [反序列化]
  │
  ▼
InnerEventListener.onEvent()   [事件路由]
  │
  ├── ROTATE        → 更新 binlog 文件名
  ├── TABLE_MAP     → 缓存表结构映射 (tableId → tableName)
  ├── WRITE_ROWS    → RowChangedEvent(INSERT, afterRows, binlogPos)
  ├── UPDATE_ROWS   → RowChangedEvent(UPDATE, afterRows, binlogPos)
  ├── DELETE_ROWS   → RowChangedEvent(DELETE, beforeRows, binlogPos)
  ├── XID           → 更新 binlog position（事务边界）
  ├── ROWS_QUERY    → 检查 DBS_UNIQUE_CODE 标记（防循环同步）
  └── QUERY         → 解析 DDL（ALTER TABLE），通知目标端同步修改
```

**位点追踪：**

每个 binlog 事件的 Header 携带 `nextPosition`（事件结束后的字节偏移量）。断点续传时：

```java
client.setBinlogFilename(snapshot.get("fileName"));     // binlog 文件名
client.setBinlogPosition(Long.parseLong(snapshot.get("position"))); // 字节偏移
```

**防循环同步机制：**

利用 MySQL `binlog_rows_query_log_events` 参数，写入端在 SQL 中携带 `DBS_UNIQUE_CODE` 注释标记，消费端检查后跳过自己产生的变更。

**错误处理策略：**

| 错误场景 | 处理方式 |
|----------|----------|
| 错误码 1236（binlog 位点过期） | 提示用户重新保存驱动 |
| 队列满（QueueOverflowException） | busy-wait 1ms 后重试 |
| binlog 文件被清理 | 自动回退到最新 binlog |
| 通用异常 | 通过 LifecycleListener 通知上层 |

#### 2.2.2 Oracle LogMiner 实现

- 通过 `DBMS_LOGMNR` 包读取 redo log
- SCN（System Change Number）作为位点
- 定期查询 `V$LOGMNR_CONTENTS` 获取变更数据

#### 2.2.3 SQL Server CDC 实现

- 利用 SQL Server 内置 CDC 功能
- 启动时自动启用数据库和表的 CDC：`sys.sp_cdc_enable_db` / `sys.sp_cdc_enable_table`
- LSN（Log Sequence Number）作为位点
- 通过 `cdc.fn_cdc_get_all_changes_<capture_instance>` 拉取变更
- 使用 `LinkedBlockingQueue` 缓冲 LSN 范围，批量拉取

#### 2.2.4 PostgreSQL WAL 实现

- 基于 PostgreSQL 逻辑复制协议
- 使用 `PGReplicationStream` 读取 WAL
- 要求：`wal_level = logical` + 复制权限
- 支持多种解码插件：pgoutput、decoderbufs、wal2json
- 启动时自动创建 Replication Slot

### 2.3 定时轮询 CDC（ListenerTypeEnum.TIMING）

用于不支持原生 CDC 的数据源（旧版数据库、Elasticsearch、File 等）。

```
Cron 表达式触发
  │
  ▼
AbstractQuartzListener.run()
  │
  ▼
for each TableGroupCommand:
  ├── checkLastPoint()    → 获取上次同步的增量点（时间戳/自增ID/游标）
  ├── connector.reader()  → 分页查询源表（WHERE 增量条件 ORDER BY PK LIMIT 5000）
  ├── 遍历结果行 → 根据事件字段值判定 INSERT/UPDATE/DELETE
  ├── trySendEvent(new ScanChangedEvent(...))
  └── 更新游标 → 持久化 snapshot
```

增量识别方式：
- **时间戳列**：`WHERE update_time > ? ORDER BY update_time`
- **自增 ID**：`WHERE id > ? ORDER BY id`
- **游标**：`WHERE (col1, col2) > (?, ?) ORDER BY col1, col2`
- **自定义事件字段**：通过配置的字段值区分 INSERT/UPDATE/DELETE（例如 `op_type='I'/'U'/'D'`）

### 2.4 其他监听器

| 监听器 | 场景 | 实现方式 |
|--------|------|----------|
| KafkaListener | 消费 Kafka 增量数据 | 多线程 poll + offset 管理，手动 commit |
| FileListener | 文件增量追加 | WatchService 监听目录 + RandomAccessFile 增量读取 |
| ESQuartzListener | Elasticsearch 索引变更 | 定时 scroll/search_after 扫描 |

### 2.5 写入链路

```
Listener.onEvent()
  │
  ▼
BufferActuatorRouter.execute(metaId, event)
  │  按 event.sourceTableName 路由
  ├── table_user  → TableGroupBufferActuator["user"]  → 批量写入目标
  ├── table_order → TableGroupBufferActuator["order"] → 批量写入目标
  └── 未绑定      → GeneralBufferActuator              → 兜底批量写入
```

**批处理流程：**

```
LinkedBlockingQueue<WriterRequest> (容量 30000)
  │
  ▼ 定时调度（300ms 间隔）
submit():
  ├── poll 最多 1000 条
  ├── 按 tableName 分区
  ├── 跳过不同类型事件（INSERT/UPDATE/DELETE 各自成批）
  └── process(map)
        └── for each partition:
              ├── Picker.pickTargetData()     → 字段映射
              ├── ConvertUtil.convert()       → 类型转换
              ├── pluginFactory.process()     → 自定义插件处理
              ├── writeBatch()                → 目标端批量写入（线程池）
              └── flushIncrementData()        → 更新同步统计
```

`GeneralBufferActuator`：单线程从队列消费（保证同表事件顺序），多线程批量写入目标（`generalExecutor` 线程池），`skipPartition()` 保证同批次内事件类型一致，DDL 事件阻塞等待队列清空后执行。

### 2.6 数据准确性保障

| 机制 | 解决的问题 |
|------|-----------|
| 单线程串行消费 binlog | 事务提交顺序 = 目标写入顺序 |
| nextPosition 逐事件字节追踪 | 崩溃后精确恢复到"最后一个已处理事件之后" |
| DBS_UNIQUE_CODE 标记过滤 | 防止双向同步死循环 |
| 位点定时持久化（20s 窗口） | 位点不丢失（最多丢 20s 进度） |
| binlog 文件不存在自动回退 | 防止 binlog 过期导致启动失败 |
| 队列满 busy-wait | 背压保护，不丢弃数据 |

---

## 3. 全量同步

### 3.1 架构

全量同步由 `FullPuller` 组件负责，支持串行和并行两种模式。

### 3.2 串行模式

```
FullPuller.start(mapping)
  │
  ▼
doTaskSequential():
  for each TableGroup (按配置顺序):
    ├── parserComponent.execute()  → 分页读取源表
    │     ├── SELECT * FROM source_table LIMIT offset, pageSize
    │     ├── Picker.pickTargetData()  → 字段映射
    │     ├── ConvertUtil.convert()    → 类型转换
    │     └── connector.writer()       → 逐批写入目标
    ├── 更新 pageIndex
    ├── flush(task) → 持久化进度
    └── 下一个 TableGroup
```

### 3.3 并行模式

当 `mapping.threadNum > 1` 时自动启用，将表列表均分到 N 个线程，每个线程独立分页读 + 批量写。

**断点续传机制：**

```java
// 持久化的进度信息
snapshot.put("PAGE_INDEX", pageIndex);              // 当前表的分页位置
snapshot.put("CURSOR", cursors);                    // 游标位置（主键值）
snapshot.put("TABLE_GROUP_INDEX", tableGroupIndex);  // 当前处理到第几张表
```

重启后从上次位置继续——跳过已完成的表、跳过已完成的分页。

### 3.4 全量 vs 增量对比

| 维度 | 全量同步 | 增量同步 |
|------|---------|---------|
| 数据来源 | `connector.reader()` 分页读 | Listener 事件流 |
| 写入方式 | 直接调用 `connector.writer()` | 经过 BufferActuator 队列缓冲 |
| 并发模型 | 表级多线程并行 | 单 binlog 线程 → 表级 Actuator 并行写 |
| 进度跟踪 | pageIndex + cursors + tableGroupIndex | binlog position / LSN / 时间戳 |
| 失败处理 | 整体失败，支持断点续传 | 单条失败进入 Storage 重试队列 |

---

## 4. 与 FlinkCDC / SeaTunnel 对比

### 4.1 定位差异

| | **DBSyncer** | **FlinkCDC** | **SeaTunnel** |
|---|---|---|---|
| 定位 | 嵌入式 CDC 同步工具 | 流处理 CDC Source | 通用 ETL 框架 |
| 核心依赖 | Spring Boot 单 JVM | Flink 集群 | Spark/Flink/Zeta 引擎 |
| 部署方式 | 单节点，开箱即用 | 分布式集群 | 多引擎可选 |
| 数据出口 | 目标库直写 | Flink DataStream/SQL | 100+ Connector |
| 适用规模 | 中小规模（TB 级以下） | 大规模（PB 级） | 中大规模 |

### 4.2 CDC 能力对比

| 能力 | **DBSyncer** | **FlinkCDC** | **SeaTunnel** |
|------|-------------|-------------|--------------|
| MySQL binlog | ✓ 自研封装 | ✓ 原生支持 | ✓ 支持 |
| Oracle LogMiner | ✓ 自研 | ✓ 支持 | ✓ 支持 |
| SQL Server CDC | ✓ 自研 | ✓ 支持 | ✓ 支持 |
| PostgreSQL WAL | ✓ 自研 | ✓ 支持 | ✓ 支持 |
| 定时轮询 | ✓ | ✗ 不需要 | ✓ |
| DDL 同步 | ✓ 检测+通知 | 部分支持 | 部分支持 |
| 位点管理 | 内嵌 H2/文件自管理 | Flink Checkpoint | 引擎自带 |
| Exactly-Once | at-least-once | ✓ 支持 | 取决于引擎 |

### 4.3 优劣分析

**DBSyncer 的优势：**

1. **零外部依赖**：不需要 Kafka、Flink、Spark。单 JAR 包启动，运维成本极低
2. **位点自管理**：不依赖 ZooKeeper/Kafka offset，断电重启后自动恢复
3. **全量+增量一体化**：同一个 Mapping 配置，无缝切换
4. **失败重试+数据修复**：同步失败的数据进入 Storage，支持修改数据后手动重试
5. **Web UI 管理**：非开发人员可直接操作
6. **自定义 DQL（1→多表）**：一张源表的变更可以触发多张目标表的写入

**DBSyncer 的劣势：**

1. **单点瓶颈**：binlog 消费单线程，吞吐受限于单核 CPU
2. **无分布式 HA**：单实例运行，没有自动故障转移
3. **无复杂流计算**：不支持 JOIN、聚合、窗口等流处理操作
4. **扩展性有限**：无法像 Flink 那样通过增加节点水平扩展
5. **跨表顺序丢失**：表级并行写入导致全局 binlog 顺序被打破

### 4.4 适用场景建议

| 场景 | 推荐工具 | 原因 |
|------|---------|------|
| ERP/CRM → 数仓实时同步 | **DBSyncer** | 部署简单，够用 |
| MySQL → ClickHouse/Doris 整库同步 | **DBSyncer** | 单表 CDC 能力完善 |
| 多源 JOIN 后写入宽表 | FlinkCDC | 需要流计算能力 |
| 百 TB 级数据迁移 | SeaTunnel | 分布式并行 + 断点续传 |
| 需要 Exactly-Once 语义 | FlinkCDC | Checkpoint 机制 |
| 需要可视化配置管理 | **DBSyncer** | Web UI 完善 |

---

# Part 2 — 优化方案

## 5. 优化路线

### 5.1 优化目标

针对**整库同步场景**（数百到上千张表），特别是源端 MySQL → 目标端 OLAP 数据库（ClickHouse/Doris/StarRocks）的同步链路：

- **极致性能**：提升吞吐量，降低延迟
- **稳定性**：长时间运行不 OOM、不丢数据、不停止消费
- **异常恢复**：任何组件故障后能自动恢复，最小化数据丢失

### 5.2 第一阶段：快速收益（1-2 周）

低投入、低风险、快速见效的优化。所有改动在已有组件内完成。

---

#### 5.2.1 位点持久化从定时 → 事件驱动

- **问题**：位点每 20s 持久化一次，崩溃时最多丢失 20s 的消费进度
- **方案**：每 N 个事件（如 1000）或每 1s 立即 flush，进程关闭时 ShutdownHook 强制 flush
- **改动**：`AbstractListener.flushEvent()` 增加计数器+时间戳判断
- **收益**：丢失窗口从 20s → 1s 以内

---

#### 5.2.2 批处理二级分区

- **问题**：`skipPartition()` 在遇到不同事件类型时跳过分区，INSERT/UPDATE/DELETE 交替时批处理效率大幅下降
- **方案**：按 `tableName + event` 做二级分区——同表不同事件各自成批、并行写入
- **改动**：`GeneralBufferActuator.submit()` 和 `partition()`
- **收益**：同批次每种事件类型满载批量写入，解决交替时的碎片化

---

#### 5.2.3 队列满等待优化

- **问题**：多处 `TimeUnit.MILLISECONDS.sleep(1)` 做自旋等待，延迟粒度太粗
- **方案**：使用 `Condition.await(100, MICROSECONDS)` + `signal()` 替代 sleep(1ms)
- **改动**：`AbstractListener.trySendEvent()` 模式统一改为 Condition
- **收益**：背压响应延迟从 1ms → 100μs 量级

---

#### 5.2.4 Buffer 参数表级差异化

- **问题**：所有表共享同一套 Buffer 参数，热点表和冷表无差异
- **方案**：`TableGroupBufferActuator` 支持独立配置，表级参数建议值：
  - 热点表（> 1000 rows/s）：`pullCount=5000`, `queueCapacity=100000`, `periodMs=100`
  - 普通表（10-1000 rows/s）：默认配置
  - 冷表（< 10 rows/s）：`pullCount=100`, `queueCapacity=5000`, `periodMs=1000`
- **改动**：`TableGroupBufferConfig` 增加配置项，UI 增加表级参数设置
- **收益**：热点表吞吐提升 3-5x，冷表不浪费内存

---

#### 5.2.5 JDK 21 虚拟线程替代平台线程池

- **问题**：5 处线程池全部使用平台线程，每个 1MB 栈开销，I/O 等待期间白白占用。慢速 OLAP 写入时 CPU×2=16 个线程瞬间耗尽
- **原理**：JDK 21 虚拟线程在 I/O 阻塞时自动让出 carrier 线程，一个 carrier 承载成千上万个虚拟线程，每个约几百字节开销
- **方案**（5 处改动，约 30 行代码）：

| # | 位置 | 改动 |
|---|------|------|
| 1 | `GeneralBufferConfig.java:35` | `generalExecutor` Bean → `Executors.newVirtualThreadPerTaskExecutor()` |
| 2 | `StorageConfig.java:39` | `storageExecutor` Bean → `Executors.newVirtualThreadPerTaskExecutor()` |
| 3 | `FullPuller.java:66` | `newFixedThreadPool(threadNum)` → `newVirtualThreadPerTaskExecutor()` |
| 4 | `BatchTaskUtil.java:69` | `createExecutor()` → `newVirtualThreadPerTaskExecutor()` |
| 5 | `AbstractBufferActuator.java:138` | `process()` 内 `forEach` 串行 → `try (var executor) { map.forEach(kv -> executor.submit(() -> pull(v))); }` |

- **兼容性验证**：

| 检查项 | 结论 |
|--------|------|
| `synchronized` pin carrier | 代码中使用 `ReentrantLock`，无 pinning 风险 |
| JDBC 驱动 | MySQL 8.0.33+、ClickHouse 0.6+、Doris/StarRocks 均支持 |
| Spring Boot | 已使用 `jakarta.annotation.*`（Boot 3.x），天然兼容 |
| HikariCP 连接池 | `maximumPoolSize` 建议从 20 → 50-100 |

- **不改动**：binlog 消费线程（顺序正确性保证）、`AbstractBufferActuator.run()`（已是轻量锁）、`ScheduledTaskService`（轻量任务）
- **收益**：全量同步并行度不再受 `threadNum` 限制；1000 张表分区写入从串行变全并行；不再需要调优 coreSize/maxSize/queueCapacity

---

### 5.3 第二阶段：稳定性增强（2-4 周）

解决整库同步和 OLAP 目标端的核心瓶颈。

---

#### 5.3.1 OLAP 写入优化

- **问题**：目标端是 ClickHouse/Doris/StarRocks 时，逐行 INSERT 性能极差，这些数据库需要大批次写入
- **方案**：
  - ClickHouse：攒到 10000-50000 行 → 一次 INSERT，或 JDBC `async_insert=1` + 更大 buffer
  - Doris：Stream Load 攒批 → group commit
  - StarRocks：Stream Load 攒批 → transaction stream load
- **改动**：各 Connector 的 `writer()` 方法增加攒批逻辑
- **收益**：OLAP 写入吞吐提升 10-30x

---

#### 5.3.2 多 Mapping 共享 binlog 连接

- **问题**：同一 MySQL 源上 N 个 Mapping = N 个 binlog 复制连接，资源浪费
- **方案**：新增 `SharedBinlogConsumer` 组件——单连接消费 + RingBuffer 多线程分发到各 Mapping
- **改动**：新增组件，`BufferActuatorRouter` 支持跨 Mapping 路由
- **收益**：连接数从 N → 1，MySQL 压力下降，消费端资源节省

**架构细节**：
- 单线程只负责 Socket 读取 + Event 反序列化，路由和写入由 RingBuffer Worker 线程处理
- 位点更新在所有 Mapping 都完成该事件写入后统一推进（min watermark）
- 若共享消费者断开，`IncrementPuller` 检测后触发全局重连，从最后一致位点恢复

---

#### 5.3.3 全局顺序的可选模式

- **问题**：表级并行写入破坏了跨表全局顺序，外键关联表可能短暂违反参照完整性
- **方案**：增加 `serialMode` 配置，开启后指定表走同一 Actuator，保证写入顺序
- **改动**：`BufferActuatorRouter` 增加串行模式判断
- **收益**：满足外键关联场景，不引入额外复杂度

---

#### 5.3.4 DDL 事件栅栏机制

- **问题**：DDL 事件通过 10ms 轮询等待队列清空，高吞吐下可能阻塞数分钟
- **方案**：在队列中插入"栅栏"标记，消费线程处理完栅栏前事件 → 执行 DDL → 继续消费
- **改动**：`WriterRequest` 增加 `isBarrier` 标记，`submit()` 增加栅栏处理逻辑
- **收益**：DDL 不再阻塞在线写入流量，延迟从分钟级降至秒级

---

### 5.4 第三阶段：架构级提升（1-3 月）

实现数据可靠性质的飞跃和规模化调度。

---

#### 5.4.1 本地 WAL（Write-Ahead Log）机制

- **问题**：数据写入和位点持久化不是原子的——写入成功+位点未持久化=重复消费，位点持久化+写入失败=数据丢失
- **方案**：binlog 事件先追加写本地 WAL 文件（格式：`[seq][tableName][event][rowData][binlogPos]`），异步写目标端成功后标记 committed，定时截断。崩溃恢复时重放 uncommitted 记录
- **改动**：新增 `WalWriter` 和 `WalRecovery` 组件，`flushEvent()` 改为 WAL 位点更新
- **收益**：不丢数据、不重复消费；WAL 通常几百 MB，几秒完成重放

---

#### 5.4.2 整库同步的智能调度

- **问题**：1000 张表的 Actuator 全部常驻，内存压力和线程竞争严重
- **方案**：
  - 热点表（5 分钟内有数据）→ 独立 Actuator 常驻
  - 温表（1 小时内有数据）→ 轻量 Actuator，500ms 周期
  - 冷表（1 小时无数据）→ 共享 Actuator 池按需分配，LRU 淘汰
- **改动**：`BufferActuatorRouter` 增加统计和生命周期管理
- **收益**：同时活跃 Actuator 从 1000 降至数十个，内存下降 90%+

---

#### 5.4.3 目标端写入与位点提交的原子化

- **问题**：即使有 WAL，仍存在目标端写入和 WAL 标记 committed 之间的小窗口
- **方案**：借鉴 Flink Two-Phase Commit——Phase 1 批量写入目标→ Phase 2 标记 WAL committed + 更新位点。失败时重放（目标端 UPSERT 保证幂等）
- **改动**：`AbstractBufferActuator.process()` 增加两阶段提交流程
- **前提**：所有目标端写入使用 UPSERT 语义（`INSERT ... ON DUPLICATE KEY UPDATE` 或 `MERGE`）

---

#### 5.4.4 内置监控面板

- **问题**：只有基础线程池指标，缺乏业务级可观测性
- **设计原则**：不引入外部监控系统，所有指标内置在 Web UI 中展示，走 `/api/metrics` JSON 接口
- **方案**：扩展 `MetricReporter`，增加三级指标：

| 维度 | 指标 | 说明 |
|------|------|------|
| Mapping | `binlog_lag_bytes` | binlog 消费延迟（字节差） |
| Mapping | `events_per_second` | 每秒事件数（I/U/D 分别统计） |
| Mapping | `write_rows_per_second` | 每秒写入行数 |
| Mapping | `write_error_count` | 累计写入失败数 |
| 表级 | `table_queue_depth` | 当前队列积压 |
| 表级 | `table_write_latency_ms` | 写入延迟（p50/p99） |
| 表级 | `table_last_event_time` | 最近一次事件时间（判断卡死） |
| 系统 | `heap_used_mb` | JVM 堆内存 |
| 系统 | `actuator_count_active` | 活跃 Actuator 数量 |
| 系统 | `listener_status` | Listener 状态（connected/disconnected/error） |

- **Web UI 呈现**：Mapping 健康概览（绿/黄/红）、队列深度柱状图（ECharts）、吞吐折线图、错误日志实时流
- **实现**：`ConcurrentHashMap` + 滑动窗口，O(1) 开销，不写磁盘、不连外部系统
- **改动**：`MetricReporter` 扩展，`MonitorService` 聚合查询，前端监控页面改版
- **收益**：整库同步时一眼定位延迟最大/卡死的表和 Mapping

---

### 5.5 执行路线与优先级矩阵

**优先级矩阵**（横轴=投入，纵轴=收益）：

```
                      高收益
                        │
        ★ 5.2.1 位点    │  ★ 5.2.4 Buffer参数
        ★ 5.2.2 二级分区 │  ★ 5.2.5 虚拟线程
        ★ 5.2.3 队列等待 │  ★ 5.3.1 OLAP写入
                        │  ★ 5.3.2 共享binlog
                        │  ★ 5.4.1 本地WAL
   ─────────────────────┼──────────────────────
          低投入        │        高投入
                        │
        ★ 5.3.4 DDL栅栏 │  ★ 5.4.2 智能调度
        ★ 5.4.4 内置监控│  ★ 5.4.3 原子提交
        ★ 5.3.3 顺序模式│
                        │
                      低收益
```

**推荐执行路线**（C→B→A 渐进，每阶段独立验证）：

```
Week 1    │ 5.4.4 核心指标 + 5.2.5  内置监控先行 + 虚拟线程替换平台线程池
          │                         有监控才能验证优化效果
Week 1-2  │ 5.2.1 + 5.2.2 + 5.2.3  位点/批处理/队列
          │                         快速提升稳定性和吞吐
Week 3-4  │ 5.2.4 + 5.4.4 仪表盘    表级差异化配置 + ECharts 仪表盘美化
          │                         指标数据驱动后续决策
Week 5-6  │ 5.3.1 + 5.3.4           OLAP写入 + DDL栅栏
          │                         解决整库同步核心痛点
Week 7-8  │ 5.3.2 + 5.3.3           共享binlog + 顺序模式
          │                         资源优化 + 外键场景
Week 9-12 │ 5.4.1 + 5.4.2           本地WAL + 智能调度
          │                         可靠性和规模的质变
Week 13+  │ 5.4.3                   原子提交（需要前序阶段验证）
```

### 5.6 不做的事情

以下优化在现阶段不建议做：

1. **引入 Kafka 做中间缓冲**：违背"零外部依赖"的设计原则，运维复杂度翻倍
2. **分布式多实例消费同一 binlog**：需要外部协调器（ZK/etcd），且 binlog 本身是单点顺序的，分片消费的正确性极其复杂
3. **通用流计算能力（JOIN/聚合/窗口）**：这会把项目变成另一个 Flink，失去轻量优势
4. **GTID 替代 binlog position**：虽然 GTID 更精确，但引入复杂度不匹配当前用户场景

### 5.7 附录 A：异常处理矩阵

| 组件 | 失败场景 | 异常类型 | 处理策略 | 用户可见 |
|------|---------|---------|---------|---------|
| `SharedBinlogConsumer` | Socket 断连 | `IOException` | `IncrementPuller` 检测 → 全局重连 → 从 min watermark 恢复 | "共享 binlog 连接断开，正在重连..." |
| `SharedBinlogConsumer` | RingBuffer 满 | `InsufficientCapacityException` | 阻塞等待 drain，超时 30s 触发 errorEvent | 监控面板积压告警 |
| WAL 写入 | 磁盘满 | `IOException(No space left)` | 暂停 binlog 消费（停止 ack），触发 errorEvent | "WAL 磁盘空间不足，同步已暂停" |
| WAL 恢复 | WAL 文件损坏 | `CorruptedWALException` | 跳过损坏段，从最后完整 checkpoint 重放 | "WAL 恢复：[N] 重放，[M] 跳过" |
| DDL 栅栏 | 目标端 ALTER 失败 | `SQLException` | 栅栏转 error 状态，后续数据正常消费不阻塞 | "表 [name] DDL 同步失败：[reason]" |
| 二级分区 | 分区 key 组合数暴涨 | 内存压力（非异常） | batch 结束后 `clear()`，response 复用 | 无（自愈） |
| 位点持久化 | H2/文件写入失败 | `IOException` | 重试 3 次，间隔 1s，仍失败触发 errorEvent | "位点持久化失败，请检查磁盘" |

### 5.8 附录 B：关键测试矩阵

| 优化项 | 单元测试 | 集成测试 | 验证方式 |
|--------|---------|---------|---------|
| 5.2.1 事件驱动位点 | 计数器达阈值触发 flush | SIGTERM → 重启 → 位点恢复 | 模拟崩溃恢复 |
| 5.2.2 二级分区 | I+U+D 交替 → 各自成批 | 100 条混合事件 → 3 个 Response，无丢事件 | `submit()` 输出验证 |
| 5.2.3 队列等待 | `Condition.await()` 被 `signal()` 唤醒 | 队列满 → 生产者阻塞 → 消费者 drain | `CountDownLatch` 同步 |
| 5.2.4 表级参数 | 热点表独立配置生效 | 配置 `pullCount=5000` → 验证每批 5000 条 | Actuator 参数生效 |
| 5.2.5 虚拟线程 | 500 并发写入全部完成 | 1000 分区并行写 → 写入完整、顺序正确 | `process()` 后 COUNT 校验 |
| 5.3.1 OLAP 写入 | ClickHouse 攒批 50000 行写入 | 目标端重启 → 幂等（INSERT 重复不报错） | `ON DUPLICATE KEY` |
| 5.3.2 共享 binlog | RingBuffer 多线程分发 | kill 消费者 → 全局重连 + min watermark 恢复 | 断连恢复闭环 |
| 5.3.3 顺序模式 | `serialMode=true` 走同一 Actuator | 两表按 binlog 顺序写入 | 时间戳校验 |
| 5.3.4 DDL 栅栏 | 栅栏插入后正确执行 | DDL 失败 → 后续数据不阻塞 | 栅栏状态机 |
| 5.4.1 WAL | kill -9 → 重启 → 重放无丢失 | 1000 条中 3 条 corrupt → 跳过 3，重放 997 | 损坏 WAL 恢复 |

---

> 文档状态：已审核 | 下一步：按 5.5 执行路线拆分为实施计划

## GSTACK REVIEW REPORT

| Review | Trigger | Why | Runs | Status | Findings |
|--------|---------|-----|------|--------|----------|
| CEO Review | `/plan-ceo-review` | Scope & strategy | 1 | CLEAN | 4 issues found, 4 fixed |

**VERDICT:** CEO REVIEW CLEARED — ready for /writing-plans

**Decisions made:**
1. Shared binlog 架构补充：单线程 socket 读取 + RingBuffer 多线程分发 + min watermark 位点推进
2. 新增异常处理矩阵：覆盖 WAL磁盘满、binlog断连重连、DDL失败、二级分区内存等 7 个核心场景
3. 新增关键测试矩阵：覆盖 10 个优化项的最小单元测试和集成测试场景
4. 监控前置：5.4.4 核心指标（MetricReporter + JSON API + 概览页）移至第一阶段第一周
5. 虚拟线程：JDK 21 平台线程池 5 处替换为虚拟线程，列为 5.2.5

NO UNRESOLVED DECISIONS
