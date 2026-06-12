# OpenAPI 外部集成接口

DBSyncer 提供 OpenAPI 接口供外部业务系统集成，支持 JWT 认证和数据同步。

## 认证机制

### 获取 Token

```
POST /openapi/auth/login
Content-Type: application/json

{
  "appId": "your_app_id",
  "appSecret": "your_app_secret"
}
```

**成功响应：**
```json
{
  "code": 0,
  "message": "登录成功",
  "data": {
    "token": "eyJ0eXAiOiJKV1Q...",
    "expiresIn": "7200"
  }
}
```

**Token 说明：**
- 算法：HmacSHA256
- 有效期：2小时（7200秒）
- 刷新窗口：1.5小时后可刷新
- 使用方式：`Authorization: Bearer {token}`

### 刷新 Token

```
POST /openapi/auth/refresh
Authorization: Bearer {old_token}
```

**成功响应：** 返回新的 token 和过期时间

**限制：**
- 仅 token 签发后 1.5 小时内可刷新
- 支持密钥轮换（自动使用上一轮密钥验证旧 token）

## 数据同步接口

```
POST /openapi/data/sync
Authorization: Bearer {token}
Content-Type: application/json

{
  "connectorId": "target_connector_id",
  "tableName": "target_table",
  "data": [...]
}
```

## 安全设计

### 请求验证链

1. **Token 验证**（`OpenApiInterceptor`）
   - 解析 `Authorization` 头提取 Bearer Token
   - 验证 Token 签名和过期时间
   - 支持密钥轮换（当前密钥 + 上一个密钥）

2. **时间戳防重放**（`TimestampValidator`）
   - 请求需携带时间戳，与服务器时间偏差不超过阈值

3. **数据加解密**（`AESKeyManager`）
   - 敏感数据使用 AES 加密传输

### AppCredentialManager

管理外部应用的 appId/appSecret 凭证对。管理员在 Web 界面配置应用凭证。

### JwtSecretManager

管理 JWT 签名密钥：
- 支持密钥轮换（保留当前密钥和上一轮密钥）
- 轮换期间两个密钥同时有效，确保 Token 平滑过渡
- 密钥基于系统 RSA 配置生成

## 响应格式

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

| code | 说明 |
|------|------|
| 0 | 成功 |
| 400 | 参数错误 |
| 401 | 认证失败 |
| 500 | 服务端异常 |

## 集成流程

```
外部系统                          DBSyncer
   │                                │
   │  1. POST /openapi/auth/login   │
   │     {appId, appSecret}        │
   │ ──────────────────────────────>│
   │                                │ 2. 验证凭证
   │  3. 返回 JWT Token             │
   │ <──────────────────────────────│
   │                                │
   │  4. POST /openapi/data/sync    │
   │     Authorization: Bearer xxx  │
   │ ──────────────────────────────>│
   │                                │ 5. 验证 Token
   │                                │ 6. 处理同步数据
   │  7. 返回同步结果               │
   │ <──────────────────────────────│
```

## 配置要求

OpenAPI 功能需要在系统配置中：
1. 启用 RSA 配置（`enableRsaConfig = true`）
2. 配置应用凭证（appId/appSecret）

管理员通过 Web 界面 `配置 >> 系统配置` 进行设置。
