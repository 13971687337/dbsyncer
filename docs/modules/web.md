# dbsyncer-web — Web 层

位于 `dbsyncer-web/src/main/java/org/dbsyncer/web/`，系统的 Spring Boot 入口和前端交互层。

## 应用入口

### Application.java

```java
@EnableAsync
@EnableScheduling
@SpringBootApplication(scanBasePackages = "org.dbsyncer")
public class Application {
    public static void main(String[] args) throws IOException {
        SpringApplication application = new SpringApplication(Application.class);
        setProperties(application);
        application.run(args);
    }
}
```

启动时动态设置：
- `info.app.version` — 构建版本号
- `info.app.build.time` — 构建时间
- `info.app.start.time` — 启动时间
- `spring.thymeleaf.prefix` → `classpath:/public/`（Thymeleaf 模板路径）
- `management.endpoints.web.base-path` → `/app`（Actuator 端点）

### Version.java

维护 `CURRENT` 版本号常量（用于客户端版本校验）。

## 安全配置

### WebAppConfig

```java
@Configuration
@EnableWebSecurity
@ConfigurationProperties(prefix = "dbsyncer.web.security")
public class WebAppConfig extends WebSecurityConfigurerAdapter
    implements AuthenticationProvider, HttpSessionListener {
}
```

**安全策略：**
- 静态资源（`/css/**`, `/js/**`, `/img/**`, `/plugins/**`）无需认证
- 其他所有请求需要登录
- 登录页面 `/login.html`，表单提交 `/login`
- 同一账号最多 1 个同时会话（`MAXIMUM_SESSIONS = 1`）
- 密码使用 `SHA1Util.b64_sha1()` 哈希存储
- 支持 `reset-pwd` 模式重置 admin 密码

### JWT 认证（OpenAPI）

`security/JwtUtil` 提供 JWT 令牌管理：

| 方法 | 说明 |
|------|------|
| `generateToken(appId, secret)` | 生成 Token（HmacSHA256，2小时过期） |
| `verifyToken(token, secret)` | 验证 Token（签名+过期时间） |
| `refreshToken(token, secret)` | 刷新 Token（1.5小时刷新窗口） |

## 控制器层（Controller）

### 页面路由

| Controller | 路径 | 页面 |
|------------|------|------|
| `IndexController` | `/index` | 首页（同步指标概览） |
| `MappingController` | `/mapping` | 同步任务管理（列表/新增/编辑/启停） |
| `ConnectorController` | `/connector` | 连接器管理（列表/新增/编辑/测试） |
| `MonitorController` | `/monitor` | 监控面板（同步数据/日志） |
| `TableGroupController` | `/tableGroup` | 表组配置（源表→目标表映射） |
| `TaskController` | `/task` | 任务配置管理（列表/新增/编辑/启停） |
| `ConfigController` | `/config` | 系统配置 |
| `UserController` | `/user` | 用户管理 |
| `PluginController` | `/plugin` | 插件管理 |
| `SystemController` | `/system` | 系统设置 |
| `LicenseController` | `/license` | 许可证管理（专业版） |
| `OpenApiController` | `/openapi` | 外部集成 API |

### REST API

每个 Controller 提供如下标准操作：

| 操作 | 请求方法 | 路径 |
|------|----------|------|
| 查询列表 | `POST` | `/xxx/search` |
| 获取详情 | `GET` | `/xxx/get?id=...` |
| 新增 | `POST` | `/xxx/add` |
| 编辑 | `POST` | `/xxx/edit` |
| 删除 | `POST` | `/xxx/remove` |
| 复制 | `POST` | `/xxx/copy` |

### MappingController

核心控制器——管理同步映射配置：

| 端点 | 说明 |
|------|------|
| `GET /mapping/list` | 同步任务列表页面 |
| `GET /mapping/pageAdd` | 添加任务页面 |
| `GET /mapping/page/{page}` | 配置子页面（editTable, editIncrement, editPlugin 等） |
| `POST /mapping/start` | 启动同步任务 |
| `POST /mapping/stop` | 停止同步任务 |
| `POST /mapping/refreshTables` | 刷新表信息 |

### MonitorController

监控控制器——实时系统指标和同步状态：

| 端点 | 说明 |
|------|------|
| `GET /monitor` | 监控主页（数据列表） |
| `GET /monitor/page/retry` | 数据重试页面 |
| `POST /monitor/queryData` | 查询同步数据 |
| `POST /monitor/queryLog` | 查询同步日志 |
| `POST /monitor/sync` | 触发数据重试/重新同步 |
| `GET /monitor/metric` | 实时系统指标（CPU/内存/磁盘） |
| `GET /monitor/dashboard` | Dashboard 汇总指标 |

定时任务（`@Scheduled`）：
- 每 5 秒：采集 CPU/内存/磁盘（`recordHistoryStackMetric`）
- 每 10 秒：刷新连接器健康状态（`refreshConnectorHealth`）
- 每 30 秒：清理过期数据和日志（`deleteExpiredDataAndLog`）

系统监控使用 `oshi.SystemInfo` 采集硬件指标，Spring Actuator `MetricsEndpoint` 采集 JVM 指标。

### OpenApiController

外部系统集成接口：

| 端点 | 方法 | 说明 |
|------|------|------|
| `/openapi/auth/login` | POST | 使用 appId/appSecret 获取 JWT Token |
| `/openapi/auth/refresh` | POST | 刷新 JWT Token |
| `/openapi/data/sync` | POST | 外部系统推送同步数据 |

认证流程：
1. 外部系统用 appId + appSecret 调用 login 获取 Token
2. 后续请求在 `Authorization: Bearer {token}` 头中携带 Token
3. `OpenApiInterceptor` 拦截器验证 Token 并解密请求体

### BaseController

所有 Controller 的基类，提供 `getParams(request)` 方法从 `HttpServletRequest` 提取参数 Map。

## 拦截器

| 类 | 说明 |
|---|---|
| `OpenApiInterceptor` | OpenAPI 请求拦截：Token 验证、AES 解密、时间戳防重放 |

## 模板引擎

前端使用 **Thymeleaf**（Spring Boot 自动配置），模板路径为 `classpath:/public/`。

### 页面结构

```
public/
├── index.html         # 主框架（sidebar + header + 内容区）
├── login.html         # 登录页
├── 404.html           # 404页面
├── sidebar.html       # 侧边栏导航
├── header.html        # 顶部导航
├── footer.html        # 页脚
├── index/list.html    # 首页 Dashboard
├── connector/         # 连接器管理页面
├── mapping/           # 映射配置页面（14个子页面）
├── monitor/           # 监控页面
├── config/            # 系统配置
├── user/              # 用户管理
├── plugin/            # 插件管理
├── system/            # 系统设置
├── license/           # 许可证
└── task/              # 任务管理
```

### 前端技术

| 技术 | 用途 |
|------|------|
| Chart.js | 监控图表（CPU/内存/同步趋势/TPS） |
| jQuery | DOM 操作和 AJAX |
| Bootstrap 风格 | UI 布局 |

静态资源目录：`static/css/`, `static/js/`, `static/img/`, `static/plugins/`

## 配置

`application.properties` 完整参数见 [部署文档](../deployment.md)。

关键配置项：
```properties
server.port=18686                           # 服务端口
dbsyncer.web.worker.id=1                    # 集群节点编号
dbsyncer.web.scheduler.pool-size=8          # 定时任务线程数
dbsyncer.web.security.reset-pwd=false       # 重置管理员密码
dbsyncer.storage.type=disk                  # 存储类型：disk/mysql
```

## 异常处理

所有 Controller 方法用 try-catch 包裹，返回 `RestResult.restFail(errorMessage)`，HTTP 状态码仍为 200。前端通过 `data.status` 判断成功/失败。
