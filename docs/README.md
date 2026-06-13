# DBSyncer 技术文档

DBSyncer v2.0.8 — 开源数据同步中间件，支持 MySQL、Oracle、SqlServer、PostgreSQL、Elasticsearch、Kafka、File、SQLite 之间的全量与增量同步。

## 文档索引

| 文档 | 类型 | 说明 |
|------|------|------|
| [architecture.md](architecture.md) | 架构总览 | 系统架构设计、分层模型、技术选型 |
| [sync-pipeline.md](sync-pipeline.md) | 深度分析 | 全量+增量同步管线、CDC方案、多任务并发模型 |
| [performance.md](performance.md) | 性能指南 | 瓶颈分析、优化方案、配置调优速查表 |
| [deployment.md](deployment.md) | 部署配置 | 安装、启动、配置参数、性能调优 |
| [plugin-dev.md](plugin-dev.md) | 插件开发 | 自定义连接器、自定义转换插件开发指南 |
| [modules/sdk.md](modules/sdk.md) | 模块参考 | SDK 层：SPI、连接器接口、存储抽象、Schema |
| [modules/connector.md](modules/connector.md) | 模块参考 | 连接器实现：MySQL、Oracle、PG、ES、Kafka 等 |
| [modules/parser.md](modules/parser.md) | 模块参考 | 解析引擎：全量/增量同步、DDL 解析、数据冲洗 |
| [modules/biz.md](modules/biz.md) | 模块参考 | 业务逻辑：任务管理、监控、配置校验 |
| [modules/manager.md](modules/manager.md) | 模块参考 | 管理器：任务生命周期、拉取器 |
| [modules/storage.md](modules/storage.md) | 模块参考 | 存储层：Lucene 磁盘存储、断点续传 |
| [modules/web.md](modules/web.md) | 模块参考 | Web 层：Spring Boot、安全、Controller |
| [modules/common.md](modules/common.md) | 模块参考 | 公共工具：线程调度、日期工具、配置 |
| [api/rest-api.md](api/rest-api.md) | API 参考 | REST API 接口完整文档 |
| [api/open-api.md](api/open-api.md) | API 参考 | OpenAPI 外部集成接口 |

## 快速导航

- **想了解系统怎么工作？** → [architecture.md](architecture.md)
- **想部署运行？** → [deployment.md](deployment.md)
- **想查 API？** → [api/rest-api.md](api/rest-api.md)
- **想开发插件？** → [plugin-dev.md](plugin-dev.md)
- **想了解某个模块？** → 看 modules/ 下的对应文档

## 技术栈速览

| 层次 | 技术 |
|------|------|
| 语言 | Java 8 |
| 框架 | Spring Boot 2.5.14 |
| 构建 | Maven |
| 安全 | Spring Security + SHA1 密码 |
| 存储 | Apache Lucene 8.8.0（磁盘索引） |
| 日志 | Log4j2 2.17.1 |
| 序列化 | Protobuf 3.21.1 + FastJSON2 2.0.22 |
| SQL 解析 | ANTLR4 4.7.2 + JSqlParser 4.9 |
| 前端 | Chart.js + Spring Thymeleaf（服务端渲染） |
