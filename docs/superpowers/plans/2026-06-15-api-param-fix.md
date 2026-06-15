# 前后端参数传递修复计划

## 问题根因

前端重构时将 `Content-Type` 从 `application/x-www-form-urlencoded` 改为 `application/json`，API 调用用 `data: { k: v }` 发送 JSON body。但后端所有端点都使用 `@RequestParam`、`String paramName`（无注解）或 `HttpServletRequest.getParams()` 读取参数，这些只认 URL query string 和 form body，不认 JSON body。

## 修复策略

将所有 `api/*.ts` 中的 `data` 改为 `params`。axios 的 `params` 将参数拼接到 URL query string（如 `POST /task/start?taskId=123`），Spring 所有参数读取方式都支持。

**不改后端，零风险。**

---

### Task 1: 修复 api/login.ts — 登录用 params

Spring Security `UsernamePasswordAuthenticationFilter` 通过 `request.getParameter("username")` / `request.getParameter("password")` 读取，query string 和 form body 都支持。

```typescript
// api/login.ts — login()
export function login(username: string, password: string) {
  return request({
    url: '/login',
    headers: { isToken: false },
    method: 'post',
    params: { username, password },  // data → params
  })
}
```

---

### Task 2: 修复 api/connector.ts — 所有 POST 改用 params

```typescript
// 所有含 data 字段的 POST 请求改为 params

export function searchConnector(query: { pageNum: number; pageSize: number }) {
  return request({ url: '/connector/search', method: 'post', params: query })
}

export function addConnector(data: Record<string, any>) {
  return request({ url: '/connector/add', method: 'post', params: data })
}

export function editConnector(data: Record<string, any>) {
  return request({ url: '/connector/edit', method: 'post', params: data })
}

export function removeConnector(id: string) {
  return request({ url: '/connector/remove', method: 'post', params: { id } })
}

export function testConnector(id: string) {
  return request({ url: '/connector/test', method: 'post', params: { id } })
}
```

---

### Task 3: 修复 api/mapping.ts — 同上

```typescript
export function searchMapping(query: { pageNum: number; pageSize: number }) {
  return request({ url: '/mapping/search', method: 'post', params: query })
}

export function addMapping(data: Record<string, any>) {
  return request({ url: '/mapping/add', method: 'post', params: data })
}

export function editMapping(data: Record<string, any>) {
  return request({ url: '/mapping/edit', method: 'post', params: data })
}

export function removeMapping(id: string) {
  return request({ url: '/mapping/remove', method: 'post', params: { id } })
}

export function startMapping(id: string) {
  return request({ url: '/mapping/start', method: 'post', params: { id } })
}

export function stopMapping(id: string) {
  return request({ url: '/mapping/stop', method: 'post', params: { id } })
}
```

---

### Task 4: 修复 api/monitor.ts — 同上

```typescript
export function queryData(query: Record<string, any>) {
  return request({ url: '/monitor/queryData', method: 'post', params: query })
}

export function queryLog(query: Record<string, any>) {
  return request({ url: '/monitor/queryLog', method: 'post', params: query })
}

export function syncMonitor() {
  return request({ url: '/monitor/sync', method: 'post' })
}

export function clearData(id?: string) {
  return request({ url: '/monitor/clearData', method: 'post', params: { id } })
}

export function clearLog() {
  return request({ url: '/monitor/clearLog', method: 'post' })
}
```

---

### Task 5: 修复 api/task.ts — 同上

```typescript
export function searchTask(query: Record<string, any>) {
  return request({ url: '/task/list', method: 'post', params: query })
}

export function startTask(id: string) {
  return request({ url: '/task/start', method: 'post', params: { taskId: id } })
}

export function stopTask(id: string) {
  return request({ url: '/task/stop', method: 'post', params: { taskId: id } })
}

export function deleteTask(id: string) {
  return request({ url: '/task/delete', method: 'get', params: { taskId: id } })
}
```

---

### Task 6: 修复 api/user.ts + 新建后端 /user/search 端点

#### 6a. 前端 api/user.ts

```typescript
export function searchUser(query: Record<string, any>) {
  return request({ url: '/user/search', method: 'post', params: query })
}

export function removeUser(id: string) {
  return request({ url: '/user/remove', method: 'post', params: { id } })
}
```

#### 6b. 后端 UserController — 新增 /search 端点

在 `UserController.java` 中添加：

```java
@PostMapping("/search")
@ResponseBody
public RestResult search(HttpServletRequest request) {
    try {
        Map<String, String> params = getParams(request);
        return RestResult.restSuccess(userConfigService.search(params));
    } catch (Exception e) {
        logger.error(e.getLocalizedMessage(), e);
        return RestResult.restFail(e.getMessage());
    }
}
```

同时需要在 `UserConfigService` 接口中添加 `search` 方法签名，以及在实现类中实现分页查询逻辑。

---

### Task 7: 修复 api/system.ts

```typescript
export function editSystem(data: Record<string, any>) {
  return request({ url: '/system/edit', method: 'post', params: data })
}
```

---

### Task 8: 修复 api/tableGroup.ts

```typescript
export function searchTableGroup(query: Record<string, any>) {
  return request({ url: '/tableGroup/search', method: 'post', params: query })
}

export function addTableGroup(data: Record<string, any>) {
  return request({ url: '/tableGroup/add', method: 'post', params: data })
}

export function removeTableGroup(id: string) {
  return request({ url: '/tableGroup/remove', method: 'post', params: { id } })
}
```

---

### Task 9: 验证 — vue-tsc + vite build

```bash
cd dbsyncer-web-ui && npx vue-tsc --noEmit && npx vite build
```

---

## 变更汇总

| 文件 | 变更 |
|---|---|
| `api/login.ts` | `data` → `params` |
| `api/connector.ts` | 5 个函数 `data` → `params` |
| `api/mapping.ts` | 6 个函数 `data` → `params` |
| `api/monitor.ts` | 3 个函数 `data` → `params` |
| `api/task.ts` | 3 个函数 `data` → `params` |
| `api/user.ts` | 2 个函数 `data` → `params` |
| `api/system.ts` | 1 个函数 `data` → `params` |
| `api/tableGroup.ts` | 2 个函数 `data` → `params` |
| `UserController.java` | 新增 `POST /user/search` |
| `UserConfigService.java` | 新增 `search(Map)` 方法签名 |

**不改的：**
- `api/openapi.ts` — `/openapi/*` 是外部 API，可能本身就用 JSON body（OpenApiController 可能用 `@RequestBody`）
- `api/config.ts` — upload 用 FormData
- `api/plugin.ts` — upload 用 FormData
- `GET` 请求 — 本来就用 `params`，无需调整
