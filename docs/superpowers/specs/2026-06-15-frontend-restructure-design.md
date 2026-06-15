# 前端项目结构重构设计

## 目标

将 dbsyncer-web-ui 的前端代码组织改造为 RuoYi-Vue/ruoyi-ui 风格，包括：
1. 封装 request.js 统一处理 JWT token 和错误拦截
2. 按业务模块拆分 API 请求函数到 `api/*.ts`
3. 重构路由守卫，token 过期自动跳转登录页
4. 统一目录结构和命名规范

## 认证机制变更

从 Spring Security Cookie/Session → JWT token（Bearer Authorization header）。

| 对比项 | 旧 | 新 |
|---|---|---|
| 认证方式 | Cookie `withCredentials: true` | `Authorization: Bearer <token>` |
| Content-Type | `application/x-www-form-urlencoded` | `application/json;charset=utf-8` |
| Token 存储 | 框架自动 | js-cookie `Admin-Token` |

## 目标目录结构

```
src/
├── main.ts
├── App.vue
├── permission.ts                     # 路由守卫（新增）
├── api/                              # 业务 API 模块（新增）
│   ├── login.ts
│   ├── connector.ts
│   ├── mapping.ts
│   ├── monitor.ts
│   ├── task.ts
│   ├── user.ts
│   ├── plugin.ts
│   ├── config.ts
│   ├── system.ts
│   ├── tableGroup.ts
│   └── openapi.ts
├── utils/                            # 工具（新增）
│   ├── request.ts                    #   axios 实例 + 拦截器
│   ├── auth.ts                       #   token 存取
│   └── errorCode.ts                  #   错误码映射
├── stores/
│   └── user.ts                       #   用户状态（重构，原 auth.ts）
├── router/
│   └── index.ts                      #   路由定义（简化，原 requiresAuth 逻辑移除）
└── views/                            #   页面组件（逐步改造）
    ├── login/LoginView.vue
    ├── layout/MainLayout.vue
    ├── dashboard/DashboardView.vue
    ├── connector/ConnectorList.vue
    ├── mapping/MappingList.vue
    ├── mapping/MappingEdit.vue
    ├── monitor/MonitorView.vue
    ├── task/TaskList.vue
    └── user/UserList.vue
```

### 与旧结构的差异

| 旧文件 | 新文件 | 变化 |
|---|---|---|
| `api/index.ts` | `utils/request.ts` + `api/*.ts` | 拆分为请求基础设施 + 业务 API |
| 无 | `utils/auth.ts` | 新增 token 存取工具 |
| 无 | `utils/errorCode.ts` | 新增错误码映射 |
| `router/index.ts`（含守卫逻辑） | `router/index.ts`（纯路由定义）+ `permission.ts`（守卫） | 守卫抽离 |
| `stores/auth.ts` | `stores/user.ts` | 重构状态结构 |

## utils/request.ts

axios 实例 + 双拦截器。

### 请求拦截器
- 自动从 cookie 读取 token，设置 `Authorization: Bearer <token>`
- 支持 `config.headers.isToken = false` 跳过 token（登录接口等）

### 响应拦截器
- `code === 200` → 返回 `res.data`（解包）
- `code === 401` → 弹确认框"登录已过期"，确认后清除 token 跳转 `/login`
- 其他非 200 → ElMessage.error 提示，reject

### 防重复登录弹窗
- `isRelogin.show` 标志位，同一时间只有一个弹窗

## utils/auth.ts

基于 js-cookie 的 token 存取：
- `getToken()` — 从 cookie 读取 `Admin-Token`
- `setToken(token)` — 写入 cookie
- `removeToken()` — 删除 cookie

## utils/errorCode.ts

后端错误码的中文映射表，格式：
```typescript
export default {
  '401': '登录已过期',
  '500': '服务器错误',
  'default': '系统未知错误'
} as Record<string, string>
```

## permission.ts（路由守卫）

- **白名单**：`/login`
- **有 token 时**：
  - 访问 `/login` → 重定向到 `/`
  - 没有用户信息 → 调 `getInfo()` 拉取完整信息后放行
  - 拉取失败 → 清 token，跳转登录页
- **无 token 时**：
  - 白名单内 → 放行
  - 白名单外 → 跳转 `/login?redirect=原路径`
- **NProgress**：`beforeEach` 开始，`afterEach` 结束

## stores/user.ts

状态：`token`, `name`, `nickName`, `roles`, `permissions`
方法：
- `login(username, password)` → 调 `api/login.ts:login()` → 存 token
- `getInfo()` → 调 `api/login.ts:getInfo()` → 填充用户信息、角色、权限
- `logout()` → 调 `api/login.ts:logout()` → 清空状态 → 删除 token → 跳转 `/login`

## api/ 业务模块模式

每个文件导出一组纯函数，函数调用 `@/utils/request` 返回 Promise。

```typescript
// 示例 api/connector.ts
import request from '@/utils/request'

export function searchConnector(query: { pageNum: number, pageSize: number }) {
  return request({ url: '/connector/search', method: 'post', data: query })
}

export function addConnector(data: any) {
  return request({ url: '/connector/add', method: 'post', data })
}

export function removeConnector(id: string) {
  return request({ url: '/connector/remove', method: 'post', data: { id } })
}
```

页面组件中：
```typescript
import { searchConnector, addConnector } from '@/api/connector'
// 替代原来的:
// import api from '@/api'; api.post('/connector/search', params)
```

## 后端端点清单

| 控制器 | 路径 | api 模块 |
|---|---|---|
| DefaultController | `/`, `/login` | `login.ts` |
| ConnectorController | `/connector/*` | `connector.ts` |
| MappingController | `/mapping/*` | `mapping.ts` |
| MonitorController | `/monitor/*` | `monitor.ts` |
| TaskController | `/task/*` | `task.ts` |
| UserController | `/user/*` | `user.ts` |
| PluginController | `/plugin/*` | `plugin.ts` |
| ConfigController | `/config/*` | `config.ts` |
| SystemController | `/system/*` | `system.ts` |
| IndexController | `/index/*` | 合并到 `login.ts` |
| TableGroupController | `/tableGroup/*` | `tableGroup.ts` |
| OpenApiController | `/openapi/*` | `openapi.ts` |
| LicenseController | `/license/*` | 补充到 `config.ts` |

## 环境变量

`.env.development`:
```
VITE_APP_BASE_API = 'http://127.0.0.1:18686'
```

## 实施策略（渐进式）

1. 建基础设施：`utils/request.ts`, `utils/auth.ts`, `utils/errorCode.ts`, 环境变量
2. 重构 `stores/auth.ts` → `stores/user.ts`
3. 新建 `permission.ts`，简化 `router/index.ts`
4. 建 `api/login.ts`，改造 `LoginView.vue`
5. 逐个模块改造：创建 `api/xxx.ts`，改造对应 `views/xxx.vue`
6. 删除旧 `api/index.ts`
7. 更新 `vite.config.ts` proxy（补充 `/license` 路径）

## 后端新增接口

- `GET /getInfo` — 返回当前用户信息、角色、权限（需新建，前端 getInfo() 调用）
