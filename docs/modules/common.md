# dbsyncer-common — 公共工具层

位于 `dbsyncer-common/src/main/java/org/dbsyncer/common/`，提供全局共享的工具类、配置、线程调度。

## 工具类

| 类 | 说明 |
|---|---|
| `StringUtil` | 字符串工具（判空、相等比较、截取等） |
| `JsonUtil` | JSON 序列化/反序列化（基于 FastJSON2） |
| `CollectionUtils` | 集合工具（判空） |
| `DateFormatUtil` | 日期格式化（`YYYY_MM_DD_HH_MM_SS` 格式器） |
| `NumberUtil` | 数字类型转换（防 NPE） |
| `SHA1Util` | SHA1 哈希（用于密码存储 `b64_sha1`） |

## 线程调度

### 配置

`common/config/` 提供类型安全的配置模型，支持 `@ConfigurationProperties` 绑定。

### Dispatcher

`common/dispatch/` 全局任务分发器：

```properties
dbsyncer.common.dispatch.thread-core-size=8
dbsyncer.common.dispatch.max-thread-size=16
dbsyncer.common.dispatch.thread-queue-capacity=64
```

用于处理异步任务分发。

## 枚举

| 枚举 | 说明 |
|---|---|
| `common/enums/` | 全局枚举（BufferActuator 类型、操作类型等） |

## 数据模型

| 类 | 说明 |
|---|---|
| `common/model/Result` | 操作结果 `{successData, failData, error}` |
| `common/model/Paging` | 分页结果 `{total, data}` |

## Column（列处理）

`common/column/` 提供列值处理工具。

## Metric（指标）

`common/metric/` 提供指标采集基类。

## Scheduled（定时任务）

`common/scheduled/` 提供定时任务抽象。
