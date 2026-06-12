# REST API 接口文档

DBSyncer Web 界面通过 REST API 与后端交互，同时支持外部系统集成。

## 通用约定

### 基础URL
```
http://{host}:18686
```

### 认证方式
- **Web UI**：Session 认证（Spring Security 表单登录），登录后持有 `JSESSIONID`
- **OpenAPI**：JWT Bearer Token 认证，Header `Authorization: Bearer {token}`

### 响应格式

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "status": 200
}
```

统一封装类：`org.dbsyncer.biz.vo.RestResult`

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | int | 业务状态码，0=成功 |
| `message` | string | 提示信息 |
| `data` | object | 响应数据 |
| `status` | int | HTTP 状态码 |

分页响应 `data` 格式：
```json
{
  "total": 100,
  "data": [...]
}
```

---

## 1. 连接器管理 `/connector`

### 1.1 连接器列表
```
POST /connector/search
Content-Type: application/x-www-form-urlencoded
```

### 1.2 获取连接器详情
```
GET /connector/page/edit?id={connectorId}
```

### 1.3 新增连接器
```
POST /connector/add
Content-Type: application/x-www-form-urlencoded

参数：连接器配置参数（类型、地址、用户名、密码等）
```

### 1.4 编辑连接器
```
POST /connector/edit
Content-Type: application/x-www-form-urlencoded

参数同 add
```

### 1.5 删除连接器
```
POST /connector/remove?id={connectorId}
```

### 1.6 测试连接
```
POST /connector/test?id={connectorId}
```
响应：`"连接测试成功"` 或 `"连接测试失败"`

### 1.7 获取数据库列表
```
GET /connector/getDatabase?id={connectorId}
```
响应：`["db1", "db2", ...]`

### 1.8 获取 Schema 列表
```
GET /connector/getSchema?id={connectorId}&database={dbName}
```
响应：`["schema1", "schema2", ...]`

### 1.9 获取增量位点
```
GET /connector/getPosition?id={connectorId}
```
响应：当前增量位置信息

---

## 2. 同步任务管理 `/mapping`

### 2.1 任务列表
```
POST /mapping/search
```

### 2.2 获取任务配置
```
GET /mapping/page/{page}?id={mappingId}
```

页面类型（`{page}`）：
- `edit` — 基本配置
- `editTable` — 表映射关系
- `editIncrement` — 增量监听配置
- `editFilter` — 过滤条件
- `editConvert` — 数据转换
- `editPlugin` — 插件配置
- `editQuartz` — 定时策略
- `editStrategy` — 同步策略

### 2.3 添加任务
```
POST /mapping/add
```

### 2.4 编辑任务
```
POST /mapping/edit
```

### 2.5 删除任务
```
POST /mapping/remove?id={mappingId}
```

### 2.6 启动任务
```
POST /mapping/start?id={mappingId}
```
响应：包含 `tableGroupId`（表组ID）

### 2.7 停止任务
```
POST /mapping/stop?id={mappingId}
```

### 2.8 刷新表信息
```
POST /mapping/refreshTables?id={mappingId}
```

### 2.9 自定义表操作
```
POST /mapping/getCustomTable
POST /mapping/saveCustomTable
POST /mapping/removeCustomTable
```

---

## 3. 监控 `/monitor`

### 3.1 监控主页
```
GET /monitor
```
参数：`metaId`, `dataStatus`, `pageNum`, `pageSize`

### 3.2 查询同步数据
```
POST /monitor/queryData
```

### 3.3 查询同步日志
```
POST /monitor/queryLog
```

### 3.4 手动触发数据重试
```
POST /monitor/sync
```
参数：`metaId`, `messageId`

### 3.5 获取实时系统指标
```
GET /monitor/metric
```
响应：
```json
{
  "cpu": {"userPercent": 12.5, "sysPercent": 3.2, "totalPercent": 15.7},
  "memory": {"sysTotal": "16.0", "sysUsed": "8.5", "jvmUsed": "2.1", "jvmTotal": "4.0"},
  "disk": {"total": "256.0", "free": "120.0", "used": "136.0"},
  "threadsLive": 42,
  "threadsPeak": 58,
  "gcPause": {...}
}
```

### 3.6 Dashboard 汇总
```
GET /monitor/dashboard
```

### 3.7 查询 Actuator 指标
```
POST /monitor/queryActuator
```

### 3.8 清除数据/日志
```
POST /monitor/clearData?id={metaId}
POST /monitor/clearLog
```

---

## 4. 任务管理 `/task`

### 4.1 任务列表
```
POST /task/list
```

### 4.2 获取任务详情
```
GET /task/getTask?taskId={taskId}
```

### 4.3 新增任务
```
POST /task/add
Content-Type: application/json
Body: {"name": "...", "connectorId": "...", "tableName": "...", ...}
```

### 4.4 修改任务
```
POST /task/modify
Content-Type: application/json
```

### 4.5 删除任务
```
GET /task/delete?taskId={taskId}
```

### 4.6 启动任务
```
POST /task/start?taskId={taskId}
```

### 4.7 停止任务
```
POST /task/stop?taskId={taskId}
```

### 4.8 任务执行结果
```
POST /task/result
```

### 4.9 获取数据库列表（任务用）
```
GET /task/getDatabases?connectorId={connectorId}
```

### 4.10 获取表列表
```
GET /task/getTables?connectorId={connectorId}&database={db}&schema={schema}
```

### 4.11 获取表字段
```
GET /task/getTableFields?connectorId={connectorId}&database={db}&schema={schema}&tableName={name}
```

---

## 5. 用户管理 `/user`

### 5.1 用户列表
```
POST /user/search
```

### 5.2 新增用户
```
POST /user/add
```

### 5.3 编辑用户
```
POST /user/edit
```

### 5.4 删除用户
```
POST /user/remove?id={userId}
```

---

## 6. 系统配置 `/config`

```
POST /config/search    # 获取配置列表
POST /config/edit      # 修改配置
```

---

## 7. 插件管理 `/plugin`

```
POST /plugin/search    # 获取插件列表
POST /plugin/add       # 上传新插件
POST /plugin/remove    # 删除插件
```

---

## HTTP 状态码

| 状态码 | 说明 |
|--------|------|
| 200 | 成功（包括业务异常，通过 data.status 区分） |
| 302 | 重定向到登录页（未认证） |
| 401 | 认证失败 |
| 403 | 权限不足 |

## 错误处理

业务异常统一返回：
```json
{
  "code": -1,
  "message": "错误描述信息",
  "data": null,
  "status": 200
}
```
