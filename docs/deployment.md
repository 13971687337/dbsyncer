# 部署配置

DBSyncer 基于 Spring Boot 2.5.14，支持单机部署和 Docker 部署。

## 环境要求

| 组件 | 版本要求 |
|------|----------|
| JDK | 1.8+ |
| Maven | 3.6+ (源码编译) |
| 内存 | 建议 >= 4GB |

## 安装方式

### 方式一：下载安装包

1. 安装 JDK 1.8
2. 下载安装包 `dbsyncer-x.x.x.zip`（从 [Gitee Releases](https://gitee.com/ghi/dbsyncer/releases)）
3. 解压安装包
4. Windows 执行 `bin/startup.bat`，Linux 执行 `bin/startup.sh`
5. 打开浏览器访问 `http://127.0.0.1:18686`
6. 默认账号密码：`admin` / `admin`

### 方式二：Docker 部署

```bash
# 拉取镜像
docker pull registry.cn-hangzhou.aliyuncs.com/xhtb/dbsyncer:latest

# 运行容器
docker run -d \
  --name=dbsyncer \
  --restart=unless-stopped \
  -p 18686:18686 \
  -e TZ="Asia/Shanghai" \
  -m 5g \
  --memory-swap=5g \
  -v /opt/dbsyncer/data:/app/dbsyncer/data \
  -v /opt/dbsyncer/logs:/app/dbsyncer/logs \
  -v /opt/dbsyncer/plugins:/app/dbsyncer/plugins \
  registry.cn-hangzhou.aliyuncs.com/xhtb/dbsyncer:latest

# 常用管理命令
docker logs -f dbsyncer        # 实时日志
docker exec -it dbsyncer /bin/bash  # 进入容器
docker restart dbsyncer         # 重启
```

### 方式三：源码编译

```bash
git clone https://gitee.com/ghi/dbsyncer.git
cd dbsyncer
chmod u+x build.sh
./build.sh
# 编译产物在 dbsyncer-web/target/
```

## 配置参数

### 服务配置

```properties
# 监听IP
server.ip=127.0.0.1
# 监听端口
server.port=18686
# 会话过期时间（秒）
server.servlet.session.timeout=1800
# 上下文路径
server.servlet.context-path=/
```

### SSL 配置

```properties
server.ssl.enabled=false
server.ssl.key-store=conf/dbsyncer.p12
server.ssl.key-store-password=dbsyncer
server.ssl.key-store-type=PKCS12
server.ssl.key-alias=dbsyncer

# HTTP 重定向到 HTTPS
server.http.enabled=false
server.http.port=8080
server.http.redirect=false
```

### 集群配置

```properties
# 工作节点ID（集群中唯一）
dbsyncer.web.worker.id=1
```

### 安全配置

```properties
# 重置 admin 密码（高危操作，用完改回 false）
dbsyncer.web.security.reset-pwd=false
```

### 线程池配置

```properties
# 全局调度线程池
dbsyncer.common.dispatch.thread-core-size=8
dbsyncer.common.dispatch.max-thread-size=16
dbsyncer.common.dispatch.thread-queue-capacity=64

# 通用执行器（增量数据冲洗）
dbsyncer.parser.general.thread-core-size=8
dbsyncer.parser.general.max-thread-size=16
dbsyncer.parser.general.thread-queue-capacity=64
dbsyncer.parser.general.buffer-writer-count=1000
dbsyncer.parser.general.buffer-pull-count=20000
dbsyncer.parser.general.buffer-queue-capacity=50000
dbsyncer.parser.general.buffer-period-millisecond=300

# 表组执行器（按表顺序冲洗）
dbsyncer.parser.table.group.thread-core-size=1
dbsyncer.parser.table.group.max-thread-size=1
dbsyncer.parser.table.group.thread-queue-capacity=16
dbsyncer.parser.table.group.buffer-writer-count=1000
dbsyncer.parser.table.group.buffer-pull-count=1000
dbsyncer.parser.table.group.buffer-queue-capacity=10000
dbsyncer.parser.table.group.buffer-period-millisecond=300

# 存储执行器
dbsyncer.storage.thread-core-size=4
dbsyncer.storage.max-thread-size=8
dbsyncer.storage.thread-queue-capacity=64
dbsyncer.storage.buffer-writer-count=1000
dbsyncer.storage.buffer-pull-count=20000
dbsyncer.storage.buffer-queue-capacity=50000
dbsyncer.storage.buffer-period-millisecond=300
```

### 存储配置

```properties
# 存储类型：disk（默认）/ mysql
dbsyncer.storage.type=disk

# MySQL 存储（生产环境推荐）
dbsyncer.storage.mysql.url=jdbc:mysql://127.0.0.1:3306/dbsyncer?rewriteBatchedStatements=true&useUnicode=true&characterEncoding=UTF8&serverTimezone=Asia/Shanghai&useSSL=false
dbsyncer.storage.mysql.username=root
dbsyncer.storage.mysql.password=123
```

### 邮件通知

```properties
dbsyncer.plugin.notify.mail.enabled=false
dbsyncer.plugin.notify.mail.username=your mail username
dbsyncer.plugin.notify.mail.password=your mail authorization code
```

## 性能调优

### 线程池调优

- **CPU 密集型场景**：增大 `general.thread-core-size` 和 `general.max-thread-size`
- **IO 密集型场景**：增大目标库连接池，减少单线程等待
- **内存限制环境**：减小 `buffer-queue-capacity` 和 `buffer-pull-count`

### 批量大小调优

- `buffer-writer-count`（默认 1000）：每次写入目标库的批大小。网络好、目标库性能好可增大
- `buffer-pull-count`（默认 20000）：每次从缓冲队列拉取的最大条数
- `buffer-period-millisecond`（默认 300）：拉取间隔。降低可减少延迟但增加 CPU 开销

### Buffer 容量调优

- `buffer-queue-capacity`（默认 50000）：缓冲队列容量。太小会导致生产者阻塞，太大会增加内存压力
- 建议：增量 QPS < 1000 → 10000；1000-5000 → 50000；> 5000 → 100000+

## 数据目录

```
{工作目录}/data/
├── config/     # Lucene 索引 → 系统配置
├── log/        # Lucene 索引 → 系统日志
└── data/       # Lucene 索引 → 同步数据断点（按驱动ID分子目录）
```

## 日志

系统使用 Log4j2，默认 INFO 级别。可通过 `application.properties` 调整：

```properties
logging.level.root=info
logging.level.org.dbsyncer=debug   # 开启 DEBUG
```

日志文件位置：
- 安装包部署：`logs/`
- Docker 部署：`/app/dbsyncer/logs/`（已挂载到 `/opt/dbsyncer/logs/`）

## 健康检查

Spring Actuator 端点：`http://127.0.0.1:18686/app/health`

```json
{
  "status": "UP",
  "components": {
    "diskSpace": {"status": "UP", "details": {"total": ..., "free": ...}}
  }
}
```

## 常见问题

### MySQL 无法连接
默认驱动版本 8.0.21。MySQL 5.x 需要手动替换 `mysql-connector-java-5.1.40.jar` 到 `lib/` 目录。

### SQL Server TLS 错误
错误：`The server selected protocol version TLS10 is not accepted by client preferences [TLS12]`
解决：在 SQL Server 端启用 TLS 1.2，或修改 JDBC 连接字符串添加 `encrypt=false`。

### 数据乱码
检查源库和目标库的字符集配置是否一致。MySQL 连接字符串需添加 `useUnicode=true&characterEncoding=UTF8`。
