# dbsyncer-manager — 任务管理器

位于 `dbsyncer-manager/src/main/java/org/dbsyncer/manager/`，负责同步任务的生命周期管理。

## 核心组件

### ManagerFactory `manager/ManagerFactory.java:22`

任务生命周期管理的统一入口（`@Component` + `ApplicationListener<ClosedEvent>`）。`onApplicationEvent` (line 31) 处理关闭事件，`start` (line 35) 启任务，`close` (line 50) 停任务。

```java
@Component
public class ManagerFactory implements ApplicationListener<ClosedEvent> {
    void start(Mapping mapping);    // 启动同步任务
    void close(Mapping mapping);    // 关闭同步任务
    void changeMetaState(String metaId, MetaEnum metaEnum);  // 更新驱动状态
    void onApplicationEvent(ClosedEvent event);  // 监听关闭事件 → READY
}
```

**启动流程：**
1. 根据 `mapping.getModel()` 拼接 `"Puller"` 后缀获取对应的 Puller（如 `fullPuller`）
2. 将 Meta 状态改为 `RUNNING`
3. 调用 `puller.start(mapping)`
4. 失败时回滚状态为 `READY`

**关闭流程：**
1. 将 Meta 状态改为 `STOPPING`
2. 调用 `puller.close(metaId)`

### Puller 接口

```java
public interface Puller {
    void start(Mapping mapping);   // 启动拉取
    void close(String metaId);     // 停止拉取
}
```

实现类通过 Spring Bean 命名约定自动注册：

| 实现类 | Bean 名称 | 对应 ModelEnum |
|--------|-----------|----------------|
| `FullPuller` | `fullPuller` | FULL（全量） |
| `IncrementPuller` | `incrementPuller` | INCREMENT（增量） |

### FullPuller

全量同步拉取器：
1. 创建 `Task` 对象（含 pageIndex, cursors, running 标志）
2. 为每个 `TableGroup` 调用 `parserComponent.execute(task, mapping, tableGroup, executor)`
3. 任务结束后，发布 `ClosedEvent` 通知 ManagerFactory 将 Meta 状态重置为 READY

### IncrementPuller

增量同步拉取器：
1. 通过 `ConnectorFactory.getListener(connectorType, "LOG")` 获取源库监听器
2. 注册 `Watcher` 到 Listener，当数据变更时触发回调
3. 回调将变更事件写入 `BufferActuator` 的缓存队列
4. BufferActuator 定期消费队列 → 批量写入目标库

### PreloadTemplate

系统启动时的预加载模板：
- 恢复启动前处于 RUNNING 状态的驱动
- 预加载连接器缓存
- 提供 `isPreloadCompleted()` 检查（MonitorController 的健康检查依赖此标记）

## 状态机

```
READY ──start()──→ RUNNING ──close()──→ STOPPING ──ClosedEvent──→ READY
  ↑                                      │
  └──────────── 异常回滚 ────────────────┘
```

- `MetaEnum.READY` (0): 就绪，可启动
- `MetaEnum.RUNNING` (1): 运行中
- `MetaEnum.STOPPING` (2): 停止中

## 部署模式

`deployment/StandaloneProvider` 实现单机部署模式。集群模式通过 `DeploymentService` SPI 扩展。

## 事件系统

| 事件 | 触发时机 | 监听者 |
|------|----------|--------|
| `ClosedEvent` | FullPuller/IncrementPuller 任务结束时 | ManagerFactory → 状态回 READY |
| `FullRefreshEvent` | 全量同步每批次完成后 | 刷新偏移量 |
| `RefreshOffsetEvent` | 增量位点更新时 | StorageService 持久化断点 |
