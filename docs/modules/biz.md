# dbsyncer-biz — 业务逻辑层

位于 `dbsyncer-biz/src/main/java/org/dbsyncer/biz/`，是 Web 层和 Parser 层之间的业务编排层。

## 服务接口列表

| 接口 | 实现类 | 职责 |
|------|--------|------|
| `ConnectorService` | `ConnectorServiceImpl` | 连接器 CRUD、连接测试、获取数据库/表/字段 |
| `MappingService` | `MappingServiceImpl` | 同步映射配置 CRUD、启停、复制 |
| `TableGroupService` | `TableGroupServiceImpl` | 表组管理（源表→目标表映射） |
| `DataSyncService` | `DataSyncServiceImpl` | 数据同步触发、重试 |
| `MonitorService` | `MonitorServiceImpl` | 监控数据查询、日志查询 |
| `SystemConfigService` | `SystemConfigServiceImpl` | 系统配置管理（RSA、白名单等） |
| `UserConfigService` | `UserConfigServiceImpl` | 用户管理（登录、授权） |
| `AppConfigService` | `AppConfigServiceImpl` | 外部应用凭证管理 |
| `ConditionService` | `ConditionServiceImpl` | 查询条件构建 |
| `ConvertService` | `ConvertServiceImpl` | 数据转换服务 |
| `PluginService` | `PluginServiceImpl` | 插件管理 |

## 配置校验体系（Checker）

`biz/checker/` 包实现了分层配置校验，采用责任链模式：

```
AbstractChecker
  ├── ConnectorChecker       # 连接器配置校验
  └── MappingConfigChecker
        ├── MappingChecker    # 映射配置校验
        ├── LogConfigChecker  # 日志配置校验
        └── TimingConfigChecker # 定时配置校验
  ├── SystemConfigChecker    # 系统配置校验
  ├── TableGroupChecker      # 表组配置校验
  └── UserConfigChecker      # 用户配置校验
```

每个 Checker 在配置保存前进行预校验，确保连接可用、参数合法。

## 监控指标（Metric）

| 枚举 | 说明 |
|------|------|
| `MetricEnum` | 指标码：CPU_USAGE, MEMORY_USED, THREADS_LIVE, GC_PAUSE 等 |
| `BufferActuatorMetricEnum` | 执行器指标：队列大小、吞吐量 |
| `ThreadPoolMetricEnum` | 线程池指标：活跃线程、队列深度 |
| `StatisticEnum` | 统计维度 |
| `SafeInfoEnum` | 安全信息 |

### MetricReporter

定期采集并上报指标数据。`MonitorController` 通过 `@Scheduled` 每 5 秒采集 CPU/内存/磁盘，每 30 秒清理过期数据。

### MetricGroupProcessor

分组处理指标数据，支持多种格式化器：
- `ValueMetricDetailFormatter` — 原始值
- `DoubleRoundMetricDetailFormatter` — 四舍五入
- `GCMetricDetailFormatter` — GC 指标专用格式化

## VO 对象

| VO | 用途 |
|---|---|
| `RestResult` | 统一 REST 响应 `{code, message, data, status}` |
| `ConnectorVo` | 连接器视图 |
| `MappingVo` | 映射配置视图 |
| `MetaVo` | 驱动元信息视图 |
| `MonitorVo` | 监控数据视图 |
| `CpuVO` / `MemoryVO` / `DiskSpaceVO` | 系统资源视图 |
| `HistoryStackVo` | 历史指标栈（用于趋势图） |
| `TpsVO` | 吞吐量视图 |
| `DataVo` | 同步数据视图 |
| `LogVo` | 日志视图 |

## 安全相关

### AppCredentialManager

管理外部业务系统的 appId/appSecret 凭证，用于 OpenAPI 认证。

### JwtSecretManager

管理 JWT 签名密钥，支持密钥轮换（当前密钥 + 上一个密钥）。

### IpWhitelistManager

IP 白名单管理，限制访问来源。

## 任务统计

| 类 | 说明 |
|---|---|
| `AbstractCountTask` | 统计任务基类 |
| `MappingCountTask` | 映射维度统计 |
| `TableGroupCountTask` | 表组维度统计 |

## BaseServiceImpl

所有 Biz 服务实现的基类，封装通用的存储操作（add/edit/remove 配置），提供模板方法供子类覆写。
