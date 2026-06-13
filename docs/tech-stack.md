# 技术栈参考

DBSyncer v2.0.8 完整框架、库及其版本。

## 源码路标

| 文件 | 内容 |
|------|------|
| `pom.xml` | 根 POM：全局属性、依赖管理、仓库配置 |
| `dbsyncer-web/pom.xml` | Web 模块依赖 |
| `dbsyncer-web/src/main/resources/application.properties` | 应用配置参数 |
| `dbsyncer-connector/dbsyncer-connector-*/pom.xml` | 各连接器独立依赖 |

---

## 一、运行时与构建

| 技术 | 版本 | 作用 | 管理方式 |
|------|------|------|----------|
| **Java** | 1.8 | 编译与运行时 | `maven.compiler.source=1.8` |
| **Maven** | 3.x | 项目构建 | POM `modelVersion 4.0.0` |
| **Spring Boot** | 2.5.14 | 应用框架 | `pom.xml` `${spring-boot.version}` |

---

## 二、Spring Boot Starters

| Starter | 版本 | 作用模块 | 管理方式 |
|---------|------|----------|----------|
| `spring-boot-starter-web` | 2.5.14 | `dbsyncer-web` | Spring Boot BOM |
| `spring-boot-starter-security` | 2.5.14 | `dbsyncer-web` | Spring Boot BOM |
| `spring-boot-starter-thymeleaf` | 2.5.14 | `dbsyncer-web` | Spring Boot BOM |
| `spring-boot-starter-actuator` | 2.5.14 | `dbsyncer-web` | Spring Boot BOM |
| `spring-boot-starter-log4j2` | 2.5.14 | `dbsyncer-common`, `connector-base`, `connector-kafka` | Spring Boot BOM |
| `spring-boot-starter-mail` | 2.5.14 | `dbsyncer-plugin` | Spring Boot BOM |
| `spring-boot` | 2.5.14 | `dbsyncer-common` | Spring Boot BOM |
| `spring-boot-autoconfigure` | 2.5.14 | `dbsyncer-common` | Spring Boot BOM |
| `spring-jdbc` | 5.3.x | `dbsyncer-sdk` | Spring Boot BOM |

---

## 三、数据库驱动

| 驱动 | GroupId | 版本 | 作用模块 |
|------|---------|------|----------|
| **MySQL** | `mysql:mysql-connector-java` | 8.0.21 | `connector-mysql` |
| **Oracle** | `com.oracle.database.jdbc:ojdbc8` | 21.6.0.0 | `connector-oracle` |
| **Oracle i18n** | `com.oracle.database.nls:orai18n` | 21.6.0.0 | `connector-oracle` |
| **SQL Server** | `com.microsoft.sqlserver:mssql-jdbc` | 8.4.1.jre8 | `connector-sqlserver` |
| **PostgreSQL** | `org.postgresql:postgresql` | 42.3.3 | `connector-postgresql` |
| **PostGIS** | `net.postgis:postgis-jdbc` | 2.5.1 | `connector-postgresql` |
| **SQLite** | `org.xerial:sqlite-jdbc` | (非显式版本) | `connector-sqlite` |
| **Elasticsearch** | `org.elasticsearch.client:elasticsearch-rest-high-level-client` | 7.12.1 (Spring Boot 管理) | `connector-elasticsearch` |

---

## 四、CDC / 日志变更捕获

| 库 | 版本 | 作用 | 连接器 |
|----|------|------|--------|
| **mysql-binlog-connector-java** | 0.30.1 | MySQL Binlog 流式解析 | MySQL |
| **PostgreSQL Replication API** | 42.3.3 (JDBC 驱动内置) | PG Logical Replication Stream + Slot 管理 | PostgreSQL |

> Oracle LogMiner CDC 基于 `ojdbc8` JDBC 自带的 `DBMS_LOGMNR` 包，无需额外依赖。SQL Server CDC 基于 `mssql-jdbc` 自带的 `sys.sp_cdc_*` 存储过程。

---

## 五、消息与序列化

| 库 | GroupId | 版本 | 作用 | 模块 |
|----|---------|------|------|------|
| **Kafka Clients** | `org.apache.kafka:kafka-clients` | 0.11.0.0 | Kafka 连接器 | `connector-kafka` |
| **Protobuf** | `com.google.protobuf:protobuf-java` | 3.21.1 | Binlog 事件序列化 | `dbsyncer-storage` |
| **FastJSON2** | `com.alibaba.fastjson2:fastjson2` | 2.0.22 | JSON 序列化 | `dbsyncer-common` |

---

## 六、SQL 解析

| 库 | 版本 | 作用 | 模块 |
|----|------|------|------|
| **ANTLR4 Runtime** | 4.7.2 | SQL 语法解析器运行时 | `dbsyncer-parser` |
| **JSqlParser** | 4.9 | SQL 语句解析（DDL/DML） | `dbsyncer-sdk` |

---

## 七、搜索引擎

| 库 | GroupId | 版本 | 作用 | 模块 |
|----|---------|------|------|------|
| **Lucene Analyzers SmartCN** | `org.apache.lucene:lucene-analyzers-smartcn` | 8.8.0 | 中文分词 + 磁盘索引存储 | `dbsyncer-storage` |

> Lucene 8.8.0 的 `IndexWriter` 作为底层存储引擎，通过 `DiskStorageService` 的 `Shard` 封装使用。同时 `lucene-analyzers-smartcn` 提供中文分词能力。

---

## 八、空间数据

| 库 | 版本 | 作用 | 连接器 |
|----|------|------|--------|
| **JTS Core** | 1.19.0 | 几何空间数据类型（GEOMETRY/GEOGRAPHY） | MySQL, PostgreSQL, SQL Server |

---

## 九、系统监控

| 库 | GroupId | 版本 | 作用 | 模块 |
|----|---------|------|------|------|
| **OSHI Core** | `com.github.oshi:oshi-core` | 6.0.0 (Spring Boot 2.5.x 管理) | CPU/内存/磁盘硬件监控 | `dbsyncer-web` |
| **Spring Actuator** | 2.5.14 | JVM 指标采集 (memory/gc/threads) | `dbsyncer-web` |

---

## 十、工具库

| 库 | GroupId | 版本 | 作用 |
|----|---------|------|------|
| **Commons IO** | `commons-io:commons-io` | 2.7 | IO 操作工具 |
| **Commons Codec** | `commons-codec:commons-codec` | 1.15 | 编解码工具 |
| **Commons FileUpload** | `commons-fileupload:commons-fileupload` | 1.4 | 文件上传 |
| **Commons Lang3** | `org.apache.commons:commons-lang3` | 3.12.0 (Spring Boot 管理) | 通用工具类 |
| **javax.annotation-api** | `javax.annotation:javax.annotation-api` | 1.3.2 (Spring Boot 管理) | `@Resource`/`@PostConstruct` 注解 |

---

## 十一、日志

| 库 | 版本 | 作用 | 管理方式 |
|----|------|------|----------|
| **Log4j API** | 2.17.1 | 日志门面 | 显式 `${log4j2.version}` |
| **Log4j Core** | 2.17.1 | 日志实现 | 显式 |
| **Log4j SLF4J Impl** | 2.17.1 | SLF4J 桥接 | 显式 |
| **Log4j JUL** | 2.17.1 | Java Util Logging 桥接 | 显式 |
| **Log4j-to-SLF4J** | 2.17.1 | Log4j → SLF4J 路由 | 显式 |

> 通过 `spring-boot-starter-log4j2` 引入并排除默认的 Logback。

---

## 十二、测试

| 库 | 版本 | 作用 |
|----|------|------|
| **JUnit** | 4.13.1 | 单元测试框架 |

---

## 十三、前端

| 技术 | 说明 |
|------|------|
| **Thymeleaf** | 服务端模板引擎（Spring Boot 自动配置，模板路径 `classpath:/public/`） |
| **Chart.js** | 监控图表展示（`static/plugins/js/chartjs/`） |
| **jQuery** | DOM 操作与 AJAX 请求（`static/plugins/js/`） |
| **Bootstrap 风格** | UI 布局样式（`static/css/`, `static/plugins/css/`） |

---

## 十四、Maven 仓库

| 仓库 | URL | 用途 |
|------|-----|------|
| DataNucleus | `http://www.datanucleus.org/downloads/maven2/` | DataNucleus 相关依赖 |
| 阿里云 Maven | `https://maven.aliyun.com/repository/google` | 国内加速 |
| Atlassian | `https://packages.atlassian.com/mvn/maven-external/` | Atlassian 产品依赖 |

---

## 十五、版本兼容性矩阵

### 数据库支持范围

| 数据库 | 最低版本 | 已验证版本 | 增量方式 |
|--------|----------|------------|----------|
| MySQL | 5.7.19 | 5.7 / 8.0 | Binlog CDC |
| Oracle | 10g | 10g-19c | LogMiner CDC |
| SQL Server | 2008 | 2008+ | Agent CDC |
| PostgreSQL | 9.5.25 | 9.5+ (9.4+ 支持逻辑复制) | Logical Replication |
| SQLite | 2.x | — | 无增量 |
| Elasticsearch | 6.0.0 | 6.0.0-8.15.3 | 定时轮询 |
| Kafka | 2.10-0.9.0.0 | — | 原生消费 |

### Java 兼容性

| 组件 | 要求 |
|------|------|
| 编译 JDK | 1.8 |
| 运行 JRE | 1.8+ |
| `mssql-jdbc` | `8.4.1.jre8`（JRE 8 专用版本） |
| `ojdbc8` | Java 8 命名（也兼容 Java 11） |
