# DBSyncer v3.0

武汉互创联合科技 — 开源数据同步中间件，支持 MySQL、Oracle、SqlServer、PostgreSQL、Elasticsearch(ES)、Kafka、File、SQL 等同步场景。

## 版本更新

| 版本 | 主要变更 |
|------|----------|
| 3.0.0 | JDK 8→21, Spring Boot 2.5→3.5, HikariCP 连接池, Vue 3 前端, 表组并行化, ES 8.x 客户端 |
| 2.0.8 | 初始版本 |

## 技术栈

- **后端**: Java 21, Spring Boot 3.5.3, Maven 3.6+
- **连接池**: HikariCP 6.2
- **前端**: Vue 3 + Vite + TypeScript + Pinia + Element Plus
- **存储**: Apache Lucene 8.8 (磁盘) / MySQL 8.0
- **序列化**: Protobuf 3.21, FastJSON2 2.0

## 环境要求

| 组件 | 版本 |
|------|------|
| JDK | 21+ |
| Maven | 3.6+ |
| Node.js (前端) | 18+ |
| MySQL (可选) | 5.7+ / 8.0+ |

## 快速开始

### 后端构建

```bash
git clone git@gitee.com:ZaneGitHome/hc-dbsync.git
cd hc-dbsync

# 构建
chmod u+x build.sh
./build.sh

# 启动
cd dbsyncer-web
java -jar target/dbsyncer-*.jar
```

### 前端开发

```bash
cd dbsyncer-web-ui
npm install --registry=https://registry.npmmirror.com
npm run dev
```

浏览器访问 `http://127.0.0.1:5173`，默认账号密码 `admin/admin`。

## 应用场景

| 连接器 | 数据源 | 目标源 | 增量方式 |
|--------|--------|--------|----------|
| MySQL | ✔ | ✔ | Binlog CDC |
| Oracle | ✔ | ✔ | LogMiner CDC |
| SqlServer | ✔ | ✔ | Agent CDC |
| PostgreSQL | ✔ | ✔ | Logical Replication |
| SQLite | ✔ | ✔ | — |
| Elasticsearch | ✔ | ✔ | 定时轮询 |
| Kafka | ✔ | ✔ | 原生消费 |
| File | ✔ | ✔ | WatchService |

## 性能

| 系统 | 机器配置 | 数据量 | 耗时 |
|------|----------|--------|------|
| Mac | Apple M3 Pro 12核心 内存18GB | 1亿条 | 31分50秒 |
| Linux | Intel Xeon E5-2696 v3B 8核心 48GB | 1亿条 | 37分52秒 |

增量 TPS 峰值: ~11000/秒

## 项目结构

```
hc-dbsync/
├── dbsyncer-sdk/         # SDK — 连接器接口、数据模型
├── dbsyncer-connector/   # 连接器实现 (8种)
├── dbsyncer-parser/      # 解析引擎 (全量/增量同步管线)
├── dbsyncer-biz/         # 业务逻辑层
├── dbsyncer-manager/     # 任务管理 (FullPuller/IncrementPuller)
├── dbsyncer-storage/     # Lucene 磁盘索引存储
├── dbsyncer-plugin/      # 自定义插件
├── dbsyncer-common/      # 公共工具类
├── dbsyncer-web/         # Spring Boot Web 层 + REST API
├── dbsyncer-web-ui/      # Vue 3 前端
└── docs/                 # 技术文档
```

## 文档

- [架构总览](docs/architecture.md)
- [技术栈参考](docs/tech-stack.md)
- [同步管线深度分析](docs/sync-pipeline.md)
- [性能分析与优化](docs/performance.md)
- [部署配置](docs/deployment.md)
- [插件开发指南](docs/plugin-dev.md)
- [REST API](docs/api/rest-api.md)
- [OpenAPI 集成](docs/api/open-api.md)

## 许可证

Apache License 2.0
