# DBSyncer 优化实施计划 — Phase 1（快速收益）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 1-2 周实施 6 项低成本优化：内置监控指标、虚拟线程、事件驱动位点、批处理二级分区、队列等待优化、表级差异化 Buffer 参数。

**Architecture:** 全部改动在现有组件内完成，不新增组件。核心思路：先建监控（有眼睛），再做优化（动手术），每个优化独立验证、独立回滚。

**Tech Stack:** JDK 21, Spring Boot 3.x, Vue 3 + Element Plus + ECharts, Maven

**Spec:** `docs/superpowers/specs/2026-06-16-dbsync-data-architecture.md`

---

## 文件结构

| 文件 | 职责 | 变动 |
|------|------|------|
| `dbsyncer-sdk/.../AbstractListener.java` | Listener 基类 | 5.2.1 事件驱动位点、5.2.3 队列等待 |
| `dbsyncer-parser/.../GeneralBufferActuator.java` | 通用执行器 | 5.2.2 二级分区 |
| `dbsyncer-parser/.../AbstractBufferActuator.java` | 执行器基类 | 5.2.5 虚拟线程 process() |
| `dbsyncer-common/.../GeneralBufferConfig.java` | 通用执行器配置 | 5.2.5 虚拟线程 executor |
| `dbsyncer-common/.../StorageConfig.java` | 持久化配置 | 5.2.5 虚拟线程 executor |
| `dbsyncer-common/.../TableGroupBufferConfig.java` | 表组配置 | 5.2.4 表级参数读取 |
| `dbsyncer-common/.../BatchTaskUtil.java` | 批处理工具 | 5.2.5 虚拟线程 |
| `dbsyncer-manager/.../FullPuller.java` | 全量同步 | 5.2.5 虚拟线程 |
| `dbsyncer-biz/.../MetricReporter.java` | 指标采集 | 5.4.4 核心指标新增 |
| `dbsyncer-biz/.../MonitorService.java` | 监控服务接口 | 5.4.4 新指标 |
| `dbsyncer-biz/.../MonitorServiceImpl.java` | 监控服务实现 | 5.4.4 新指标聚合 |
| `dbsyncer-web/.../MonitorController.java` | 监控 API | 5.4.4 新端点 |
| `dbsyncer-web-ui/.../MonitorView.vue` | 监控页面 | 5.4.4 概览面板 |

---

## Task 1: 内置监控 — 核心指标采集与暴露 (5.4.4 Phase 1)

**Files:**
- Modify: `dbsyncer-biz/src/main/java/org/dbsyncer/biz/enums/MetricEnum.java`
- Modify: `dbsyncer-biz/src/main/java/org/dbsyncer/biz/impl/MetricReporter.java`
- Modify: `dbsyncer-biz/src/main/java/org/dbsyncer/biz/MonitorService.java`
- Modify: `dbsyncer-biz/src/main/java/org/dbsyncer/biz/impl/MonitorServiceImpl.java`
- Modify: `dbsyncer-web/src/main/java/org/dbsyncer/web/controller/monitor/MonitorController.java`

- [ ] **Step 1: 扩展 MetricEnum 增加业务指标**

```java
// dbsyncer-biz/src/main/java/org/dbsyncer/biz/enums/MetricEnum.java
// 在现有枚举值后追加:
BINLOG_LAG_BYTES("binlogLagBytes", "Binlog延迟(字节)"),
EVENTS_PER_SECOND("eventsPerSecond", "每秒事件数"),
WRITE_ROWS_PER_SECOND("writeRowsPerSecond", "每秒写入行数"),
WRITE_ERROR_COUNT("writeErrorCount", "累计写入失败数"),
TABLE_QUEUE_DEPTH("tableQueueDepth", "队列积压"),
TABLE_WRITE_LATENCY("tableWriteLatency", "写入延迟(ms)"),
TABLE_LAST_EVENT_TIME("tableLastEventTime", "最近事件时间"),
HEAP_USED_MB("heapUsedMb", "JVM堆内存(MB)"),
ACTUATOR_COUNT_ACTIVE("actuatorCountActive", "活跃执行器数"),
LISTENER_STATUS("listenerStatus", "监听器状态");
```

- [ ] **Step 2: 引入滑动窗口工具类**

在 `dbsyncer-common/src/main/java/org/dbsyncer/common/util/` 下创建 `SlidingWindow.java`:

```java
package org.dbsyncer.common.util;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * 滑动窗口计数器，O(1) 开销，不创建对象
 */
public class SlidingWindow {
    private final AtomicLongArray buckets;
    private final int size;
    private volatile int cursor;
    private final AtomicLong total = new AtomicLong();

    public SlidingWindow(int size) {
        this.size = size;
        this.buckets = new AtomicLongArray(size);
    }

    public void add(long n) {
        int idx = cursor % size;
        long old = buckets.getAndSet(idx, n);
        total.addAndGet(n - old);
        cursor++;
    }

    public long sum() {
        return total.get();
    }

    public double avg() {
        long s = sum();
        return s == 0 ? 0 : (double) s / size;
    }
}
```

- [ ] **Step 3: MetricReporter 增加新指标采集**

修改 `dbsyncer-biz/src/main/java/org/dbsyncer/biz/impl/MetricReporter.java`，在类中增加:

```java
// 新增采集字段
private final Map<String, SlidingWindow> eventsPerSecWindows = new ConcurrentHashMap<>();   // metaId → window
private final Map<String, SlidingWindow> writeRowsPerSecWindows = new ConcurrentHashMap<>(); // metaId → window
private final Map<String, AtomicLong> writeErrorCounters = new ConcurrentHashMap<>();        // metaId → count
private final Map<String, AtomicLong> lastEventTimes = new ConcurrentHashMap<>();            // tableName → epochMs
private final Map<String, Long> writeLatencies = new ConcurrentHashMap<>();                  // tableName → p50 accum

// 每秒由 ScheduledTaskService 调用一次
public void tick() {
    // 从 Runtime 获取堆内存
    long heapUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

    // 从 BufferActuatorRouter 获取活跃执行器数和队列深度
    AtomicLong queueSize = bufferActuatorRouter.getQueueSize();
    AtomicLong queueCapacity = bufferActuatorRouter.getQueueCapacity();

    // 计算 events_per_second（滑动窗口平均值）
    eventsPerSecWindows.forEach((metaId, w) -> {
        report(MetricEnum.EVENTS_PER_SECOND, metaId, (long) w.avg());
    });
}

// 在现有事件处理中调用（每次收到 ChangedEvent 时）
public void recordEvent(String metaId, String eventType) {
    eventsPerSecWindows.computeIfAbsent(metaId, k -> new SlidingWindow(60)).add(1);
}

// 在 writeBatch 完成后调用
public void recordWrite(String metaId, String tableName, int rows, long latencyMs) {
    writeRowsPerSecWindows.computeIfAbsent(metaId, k -> new SlidingWindow(60)).add(rows);
    lastEventTimes.put(tableName, System.currentTimeMillis());
    writeLatencies.merge(tableName, latencyMs, (old, v) -> (old + v) / 2); // 移动平均近似 p50
}

// 记录写入失败
public void recordError(String metaId) {
    writeErrorCounters.computeIfAbsent(metaId, k -> new AtomicLong()).incrementAndGet();
}

// 从 bufferActuatorRouter 获取每表队列深度
public Map<String, Long> getTableQueueDepths() {
    Map<String, Long> depths = new HashMap<>();
    bufferActuatorRouter.getRouter().forEach((metaId, processors) ->
        processors.forEach((tableName, actuator) ->
            depths.put(tableName, (long) actuator.getQueue().size())));
    return depths;
}
```

- [ ] **Step 4: MonitorService 接口增加新方法**

修改 `dbsyncer-biz/src/main/java/org/dbsyncer/biz/MonitorService.java`，增加:

```java
/**
 * 获取所有 Mapping 的健康概览（绿/黄/红）
 */
Map<String, String> getHealthOverview();

/**
 * 获取表级队列深度
 */
Map<String, Long> getTableQueueDepths();

/**
 * 获取近 1 小时事件吞吐趋势
 */
List<Map<String, Object>> getThroughputTrend(String metaId);
```

- [ ] **Step 5: MonitorServiceImpl 实现新方法**

修改 `dbsyncer-biz/src/main/java/org/dbsyncer/biz/impl/MonitorServiceImpl.java`:

```java
@Override
public Map<String, String> getHealthOverview() {
    Map<String, String> health = new HashMap<>();
    List<Meta> metas = profileComponent.getMetaAll();
    for (Meta meta : metas) {
        long lastEvent = metricReporter.getLastEventTime(meta.getId());
        String status;
        if (lastEvent == 0) {
            status = "gray";     // 无数据
        } else if (System.currentTimeMillis() - lastEvent > 60_000) {
            status = "red";      // 超过 60s 无事件 → 可能卡死
        } else if (System.currentTimeMillis() - lastEvent > 10_000) {
            status = "yellow";   // 10-60s → 可能延迟
        } else {
            status = "green";    // 健康
        }
        health.put(meta.getId(), status);
    }
    return health;
}

@Override
public Map<String, Long> getTableQueueDepths() {
    return metricReporter.getTableQueueDepths();
}

@Override
public List<Map<String, Object>> getThroughputTrend(String metaId) {
    return metricReporter.getThroughputHistory(metaId);
}
```

- [ ] **Step 6: MonitorController 增加 API 端点**

修改 `dbsyncer-web/src/main/java/org/dbsyncer/web/controller/monitor/MonitorController.java`:

```java
@GetMapping("/health/overview")
@ResponseBody
public Map<String, String> getHealthOverview() {
    return monitorService.getHealthOverview();
}

@GetMapping("/metrics/tableQueues")
@ResponseBody
public Map<String, Long> getTableQueueDepths() {
    return monitorService.getTableQueueDepths();
}

@GetMapping("/metrics/throughput/{metaId}")
@ResponseBody
public List<Map<String, Object>> getThroughputTrend(@PathVariable String metaId) {
    return monitorService.getThroughputTrend(metaId);
}
```

- [ ] **Step 7: MonitorView.vue 增加健康概览区域**

修改 `dbsyncer-web-ui/src/views/monitor/MonitorView.vue`，在原模板顶部接入概览:

```vue
<template>
  <div class="monitor-container">
    <!-- 健康概览 -->
    <el-card class="health-overview">
      <template #header>同步健康概览</template>
      <div class="health-grid">
        <div v-for="(status, metaId) in healthMap" :key="metaId"
             :class="['health-dot', status]">
          <span class="dot" :style="{ background: statusColor(status) }" />
          <span>{{ mappingNames[metaId] || metaId }}</span>
        </div>
      </div>
    </el-card>

    <script setup>
    import { ref, onMounted } from 'vue'
    import { getHealthOverview, getTableQueueDepths } from '@/api/monitor'

    const healthMap = ref({})
    const mappingNames = ref({})

    const statusColor = (s) => ({ green: '#67C23A', yellow: '#E6A23C', red: '#F56C6C', gray: '#909399' }[s])

    onMounted(async () => {
      healthMap.value = await getHealthOverview()
      // 定时刷新
      setInterval(async () => { healthMap.value = await getHealthOverview() }, 5000)
    })
    </script>
  </div>
```

- [ ] **Step 8: 创建 API 函数**

在 `dbsyncer-web-ui/src/api/monitor.ts` 中增加:

```typescript
import request from '@/utils/request'

export function getHealthOverview() {
  return request({ url: '/monitor/health/overview', method: 'get' })
}

export function getTableQueueDepths() {
  return request({ url: '/monitor/metrics/tableQueues', method: 'get' })
}

export function getThroughputTrend(metaId: string) {
  return request({ url: `/monitor/metrics/throughput/${metaId}`, method: 'get' })
}
```

- [ ] **Step 9: 编译验证**

```bash
cd dbsyncer-web-ui && npm run build && cd ..
mvn compile -pl dbsyncer-biz,dbsyncer-web -am -q
```

预期: BUILD SUCCESS

- [ ] **Step 10: 启动验证**

```bash
mvn spring-boot:run -pl dbsyncer-web
curl http://localhost:18686/monitor/health/overview
```

预期: 返回 JSON `{"metaId1": "gray", ...}`

- [ ] **Step 11: Commit**

```bash
git add dbsyncer-biz/src/main/java/org/dbsyncer/biz/enums/MetricEnum.java \
        dbsyncer-biz/src/main/java/org/dbsyncer/biz/impl/MetricReporter.java \
        dbsyncer-biz/src/main/java/org/dbsyncer/biz/MonitorService.java \
        dbsyncer-biz/src/main/java/org/dbsyncer/biz/impl/MonitorServiceImpl.java \
        dbsyncer-web/src/main/java/org/dbsyncer/web/controller/monitor/MonitorController.java \
        dbsyncer-common/src/main/java/org/dbsyncer/common/util/SlidingWindow.java \
        dbsyncer-web-ui/src/views/monitor/MonitorView.vue \
        dbsyncer-web-ui/src/api/monitor.ts
git commit -m "feat: 内置监控核心指标 — 健康概览+表级队列深度+事件吞吐

新增 MetricEnum 业务指标、SlidingWindow 滑动窗口、/monitor/health/overview 等 3 个 API、MonitorView 概览面板"
```

---

## Task 2: JDK 21 虚拟线程替代平台线程池 (5.2.5)

**Files:**
- Modify: `dbsyncer-common/src/main/java/org/dbsyncer/common/config/GeneralBufferConfig.java`
- Modify: `dbsyncer-common/src/main/java/org/dbsyncer/common/config/StorageConfig.java`
- Modify: `dbsyncer-manager/src/main/java/org/dbsyncer/manager/impl/FullPuller.java`
- Modify: `dbsyncer-common/src/main/java/org/dbsyncer/common/util/BatchTaskUtil.java`
- Modify: `dbsyncer-parser/src/main/java/org/dbsyncer/parser/flush/AbstractBufferActuator.java`

- [ ] **Step 1: generalExecutor 改为虚拟线程**

```java
// dbsyncer-common/src/main/java/org/dbsyncer/common/config/GeneralBufferConfig.java
// 替换 generalExecutor() 方法:

@Bean(name = "generalExecutor")
public ExecutorService generalExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
}

// 删除字段 threadCoreSize, maxThreadSize, threadQueueCapacity 及对应 getter/setter
// 删除注入 ThreadPoolUtil 的 import
```

- [ ] **Step 2: storageExecutor 改为虚拟线程**

```java
// dbsyncer-common/src/main/java/org/dbsyncer/common/config/StorageConfig.java
// 替换 storageExecutor() 方法:

@Bean(name = "storageExecutor")
public ExecutorService storageExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
}

// 同样删除 threadCoreSize, maxThreadSize, threadQueueCapacity
```

- [ ] **Step 3: FullPuller 改为虚拟线程**

```java
// dbsyncer-manager/src/main/java/org/dbsyncer/manager/impl/FullPuller.java
// 第 66 行，替换:

// 旧: ExecutorService executor = Executors.newFixedThreadPool(mapping.getThreadNum());
// 新:
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

// 同时删除 mapping.getThreadNum() 的引用（如果只有此处使用）
```

- [ ] **Step 4: BatchTaskUtil 改为虚拟线程**

```java
// dbsyncer-common/src/main/java/org/dbsyncer/common/util/BatchTaskUtil.java
// createExecutor 方法改为:

public static ExecutorService createExecutor(int coreSize, int maxSize, int queueSize) {
    return Executors.newVirtualThreadPerTaskExecutor();
}
// 参数保留但不使用（向后兼容），或者直接删掉参数改为无参方法
```

- [ ] **Step 5: AbstractBufferActuator.process() 改为虚拟线程并行写**

```java
// dbsyncer-parser/src/main/java/org/dbsyncer/parser/flush/AbstractBufferActuator.java
// 第 138 行，替换 process() 方法:

protected void process(Map<String, Response> map) {
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
        map.forEach((key, response) -> executor.submit(() -> {
            long now = Instant.now().toEpochMilli();
            try {
                pull(response);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
            logger.info("[{}{}]{}, {}ms", key, response.getSuffixName(), response.getTaskSize(),
                    (Instant.now().toEpochMilli() - now));
        }));
    }
    // try-with-resources 自动等待所有虚拟线程完成后关闭 executor
}

// 增加 import:
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
```

- [ ] **Step 6: 编译验证**

```bash
mvn compile -q
```

预期: BUILD SUCCESS（需要 JDK 21+）

- [ ] **Step 7: Commit**

```bash
git add dbsyncer-common/src/main/java/org/dbsyncer/common/config/GeneralBufferConfig.java \
        dbsyncer-common/src/main/java/org/dbsyncer/common/config/StorageConfig.java \
        dbsyncer-manager/src/main/java/org/dbsyncer/manager/impl/FullPuller.java \
        dbsyncer-common/src/main/java/org/dbsyncer/common/util/BatchTaskUtil.java \
        dbsyncer-parser/src/main/java/org/dbsyncer/parser/flush/AbstractBufferActuator.java
git commit -m "perf: JDK21 虚拟线程替代 5 处平台线程池

generalExecutor/storageExecutor 改为 VirtualThreadPerTaskExecutor
FullPuller 不再限制 threadNum
BatchTaskUtil 线程池改为虚拟线程
AbstractBufferActuator.process() 分区并行写改为虚拟线程"
```

---

## Task 3: 事件驱动位点持久化 (5.2.1)

**Files:**
- Modify: `dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/listener/AbstractListener.java`

- [ ] **Step 1: 增加事件计数器和时间戳**

```java
// dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/listener/AbstractListener.java
// 在类字段区增加:

private final AtomicLong eventCounter = new AtomicLong(0);
private volatile long lastFlushTime = System.currentTimeMillis();
private static final long FLUSH_INTERVAL_MS = 1000;  // 1s 最小间隔
private static final long FLUSH_BATCH_SIZE = 1000;    // 1000 个事件触发一次

// 在类顶部增加 import:
import java.util.concurrent.atomic.AtomicLong;
```

- [ ] **Step 2: 改造 flushEvent() 方法**

```java
// 替换现有的 flushEvent() 方法:

@Override
public void flushEvent() {
    long now = System.currentTimeMillis();
    // 条件1: 至少过了 1s
    // 条件2: 20s 内有写入（保持原有逻辑，防止长时间无数据时频繁 flush）
    if (eventCounter.get() >= FLUSH_BATCH_SIZE
            || (now - lastFlushTime >= FLUSH_INTERVAL_MS
                && watcher.getMetaUpdateTime() > Timestamp.valueOf(
                    LocalDateTime.now().minusSeconds(FLUSH_DELAYED_SECONDS)).getTime())) {
        if (!CollectionUtils.isEmpty(snapshot)) {
            logger.info("snapshot：{}", snapshot);
            watcher.flushEvent(snapshot);
            eventCounter.set(0);
            lastFlushTime = now;
        }
    }
}
```

- [ ] **Step 3: 在 changeEvent 中增加计数器**

```java
// 在 changeEvent() 方法末尾（第 83 行之前）增加:

eventCounter.incrementAndGet();

// 在 processEvent() 方法中也增加计数:

private void processEvent(boolean permitEvent, ChangedEvent event) {
    if (permitEvent) {
        watcher.changeEvent(event);
        eventCounter.incrementAndGet();  // 只有实际处理的事件才计数
    }
}
```

- [ ] **Step 4: 增加 ShutdownHook**

在项目启动类或 `IncrementPuller.close()` 中增加:

```java
// dbsyncer-manager/src/main/java/org/dbsyncer/manager/impl/IncrementPuller.java
// close() 方法末尾，在 listener.close() 之后增加:

listener.forceFlushEvent();
```

- [ ] **Step 5: 编译验证**

```bash
mvn compile -pl dbsyncer-sdk,dbsyncer-manager -am -q
```

- [ ] **Step 6: Commit**

```bash
git add dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/listener/AbstractListener.java \
        dbsyncer-manager/src/main/java/org/dbsyncer/manager/impl/IncrementPuller.java
git commit -m "perf: 位点持久化从定时改为事件驱动+时间兜底

每 1000 个事件或每 1s 触发 flush，崩溃丢失窗口从 20s → 1s
ShutdownHook 保证进程关闭时强制持久化"
```

---

## Task 4: 批处理二级分区 (5.2.2)

**Files:**
- Modify: `dbsyncer-parser/src/main/java/org/dbsyncer/parser/flush/impl/GeneralBufferActuator.java`

- [ ] **Step 1: 修改 getPartitionKey 增加事件类型维度**

```java
// dbsyncer-parser/src/main/java/org/dbsyncer/parser/flush/impl/GeneralBufferActuator.java
// 第 108 行，替换 getPartitionKey() 方法:

@Override
protected String getPartitionKey(WriterRequest request) {
    return request.getTableName() + ":" + request.getEvent();
    // 原来是 return request.getTableName();
    // 现在按 "table:eventType" 做二级分区
}
```

- [ ] **Step 2: 删除 skipPartition 的检查**

```java
// 第 132 行，替换 skipPartition() 方法:

@Override
protected boolean skipPartition(WriterRequest nextRequest, WriterResponse response) {
    // 原来通过 skip 来避免同批次事件类型混合
    // 现在二级分区已解决，只需保留 DDL 跳过逻辑
    return ChangedEventTypeEnum.isDDL(response.getTypeEnum());
    // 原来: !StringUtil.equals(nextRequest.getEvent(), response.getEvent())
    //       || ChangedEventTypeEnum.isDDL(response.getTypeEnum());
}
```

- [ ] **Step 3: 编译验证**

```bash
mvn compile -pl dbsyncer-parser -am -q
```

- [ ] **Step 4: Commit**

```bash
git add dbsyncer-parser/src/main/java/org/dbsyncer/parser/flush/impl/GeneralBufferActuator.java
git commit -m "perf: 批处理按 tableName+event 二级分区，消除同表不同事件碎片化

getPartitionKey 增加事件类型维度，skipPartition 简化
同一批次 INSERT/UPDATE/DELETE 各自独立成批满载写入"
```

---

## Task 5: 队列满等待优化 (5.2.3)

**Files:**
- Modify: `dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/listener/AbstractListener.java`
- Modify: `dbsyncer-connector/dbsyncer-connector-mysql/src/main/java/org/dbsyncer/connector/mysql/cdc/MySQLListener.java` (以及所有有 trySendEvent 的 Listener)

- [ ] **Step 1: AbstractListener 增加 Condition 工具方法**

```java
// dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/listener/AbstractListener.java
// 在类字段区增加:

private final Lock backpressureLock = new ReentrantLock();
private final Condition backpressureCondition = backpressureLock.newCondition();

// 增加 import:
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
```

- [ ] **Step 2: 增加 signalBackpressure 方法**

```java
// 在 AbstractListener 中增加方法:

public void signalBackpressure() {
    backpressureLock.lock();
    try {
        backpressureCondition.signalAll();
    } finally {
        backpressureLock.unlock();
    }
}

protected void awaitBackpressure(long micros) {
    backpressureLock.lock();
    try {
        backpressureCondition.await(micros, TimeUnit.MICROSECONDS);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    } finally {
        backpressureLock.unlock();
    }
}
```

- [ ] **Step 3: MySQLListener.trySendEvent 改为 Condition**

```java
// dbsyncer-connector/dbsyncer-connector-mysql/src/main/java/org/dbsyncer/connector/mysql/cdc/MySQLListener.java
// 第 145 行，替换 trySendEvent() 方法:

private void trySendEvent(ChangedEvent event) {
    try {
        while (client.isConnected()) {
            try {
                sendChangedEvent(event);
                break;
            } catch (QueueOverflowException e) {
                awaitBackpressure(100);  // 100μs 等待
            }
        }
    } catch (Exception e) {
        logger.error(e.getMessage(), e);
    }
}
```

- [ ] **Step 4: 在消费端 drain 后 signal**

在 `BufferActuatorRouter` 或 `AbstractBufferActuator.submit()` 的 `process(map)` 调用后增加 signal 通知:

```java
// dbsyncer-parser/src/main/java/org/dbsyncer/parser/flush/AbstractBufferActuator.java
// submit() 方法末尾，process(map) 之后:

// 通知等待中的生产者
// 通过 Listener 的 signalBackpressure（需要注入或事件通知）
```

注意：这一步需要让 BufferActuator 能通知到 Listener。简化方案——在 `GeneralBufferActuator.process()` 完成后通过 Spring Event 发布 `BackpressureRelievedEvent`，`IncrementPuller` 监听后调用对应 Listener 的 `signalBackpressure()`。

（如果觉得这个改动太大，可以先用 `LockSupport.parkNanos(100_000)` 替代 `sleep(1)` 作为过渡方案。）

- [ ] **Step 5: 编译验证**

```bash
mvn compile -q
```

- [ ] **Step 6: Commit**

```bash
git add dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/listener/AbstractListener.java \
        dbsyncer-connector/dbsyncer-connector-mysql/src/main/java/org/dbsyncer/connector/mysql/cdc/MySQLListener.java
git commit -m "perf: 队列满等待 sleep(1ms) → Condition.await(100μs)

新增 backpressureLock+Condition 机制，背压响应延迟从 1ms → 100μs"
```

---

## Task 6: Buffer 参数表级差异化 (5.2.4)

**Files:**
- Modify: `dbsyncer-common/src/main/java/org/dbsyncer/common/config/TableGroupBufferConfig.java`
- Modify: `dbsyncer-parser/src/main/java/org/dbsyncer/parser/flush/AbstractBufferActuator.java` (setter)

- [ ] **Step 1: TableGroupBufferConfig 增加动态配置方法**

```java
// dbsyncer-common/src/main/java/org/dbsyncer/common/config/TableGroupBufferConfig.java
// 在现有类中增加动态配置更新方法:

public void applyHotTableProfile() {
    setBufferPullCount(5000);
    setBufferQueueCapacity(100_000);
    setBufferPeriodMillisecond(100);
    setBufferWriterCount(200);
}

public void applyColdTableProfile() {
    setBufferPullCount(100);
    setBufferQueueCapacity(5_000);
    setBufferPeriodMillisecond(1000);
    setBufferWriterCount(50);
}

public void applyDefaultProfile() {
    setBufferPullCount(1000);
    setBufferQueueCapacity(30_000);
    setBufferPeriodMillisecond(300);
    setBufferWriterCount(100);
}
```

- [ ] **Step 2: AbstractBufferActuator 增加配置热更新方法**

```java
// dbsyncer-parser/src/main/java/org/dbsyncer/parser/flush/AbstractBufferActuator.java
// 增加方法:

public void reconfig(BufferActuatorConfig newConfig) {
    this.config = newConfig;
    // 队列容量更新——需要新建队列（或使用 LinkedBlockingQueue 的动态容量不支持，改为替换）
    // 简化方案：只更新 pullCount 和 periodMs，队列容量在下次 buildConfig 时生效
}
```

- [ ] **Step 3: 编译验证**

```bash
mvn compile -q
```

- [ ] **Step 4: Commit**

```bash
git add dbsyncer-common/src/main/java/org/dbsyncer/common/config/TableGroupBufferConfig.java \
        dbsyncer-parser/src/main/java/org/dbsyncer/parser/flush/AbstractBufferActuator.java
git commit -m "feat: TableGroupBufferActuator 支持表级差异化配置

新增 applyHotTableProfile/applyColdTableProfile/applyDefaultProfile
AbstractBufferActuator 增加 reconfig 热更新"
```

---

## Phase 1 完成检查清单

- [ ] `curl /monitor/health/overview` 返回健康状态
- [ ] 虚拟线程替代后全量同步能正常完成（创建 100 张表全量同步测试）
- [ ] 进程 kill -9 重启后，位点恢复到最后 1s 以内
- [ ] 同表 INSERT+UPDATE+DELETE 交替场景，批处理各自独立成批
- [ ] 队列满时生产者等待 < 200μs（而非 1ms）
- [ ] 所有现有测试通过 `mvn test`

---

# Phase 2 & 3（后续阶段 — 概要）

## Phase 2: 稳定性增强 (Week 5-8)

| 任务 | 关键文件 | 说明 |
|------|---------|------|
| 5.3.1 OLAP 写入 | `ClickHouseConnector.java`, `DorisConnector.java`, `StarRocksConnector.java` | writer() 增加攒批到 10000-50000 行 |
| 5.3.4 DDL 栅栏 | `WriterRequest.java`, `GeneralBufferActuator.java` | isBarrier 标记 + submit() 栅栏逻辑 |
| 5.3.2 共享 binlog | 新增 `SharedBinlogConsumer.java`, 修改 `BufferActuatorRouter.java` | RingBuffer 多线程分发 |
| 5.3.3 顺序模式 | `BufferActuatorRouter.java`, Mapping 配置 | serialMode 选项 |

## Phase 3: 架构级提升 (Week 9-13+)

| 任务 | 关键文件 | 说明 |
|------|---------|------|
| 5.4.1 本地 WAL | 新增 `WalWriter.java`, `WalRecovery.java` | 先写日志再写目标端 |
| 5.4.2 智能调度 | `BufferActuatorRouter.java` | 冷热分离 + LRU 淘汰 |
| 5.4.3 原子提交 | `AbstractBufferActuator.java` | Two-Phase Commit |
| 5.4.4 仪表盘美化 | `MonitorView.vue` | ECharts 折线图/柱状图 |

---

> Phase 2 & 3 的详细计划将在 Phase 1 验证完成后单独拆分。
> 整体 Spec: `docs/superpowers/specs/2026-06-16-dbsync-data-architecture.md`
