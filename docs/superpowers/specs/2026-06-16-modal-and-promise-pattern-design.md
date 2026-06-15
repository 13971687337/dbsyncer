# 统一对话框封装 + Promise 链模式

**日期**: 2026-06-16
**状态**: 已确认

## 目标

1. 创建 `src/utils/modal.js`，统一封装 Element Plus 的对话框和消息提示
2. 所有 Vue 页面的用户操作改为 Promise 链模式（`.then().catch()`）
3. 页面加载（`onMounted`）保留 `async/await`

## API 设计

### `$modal` 全局对象（Vue Plugin）

| 方法 | 说明 | 返回值 |
|------|------|--------|
| `$modal.confirm(message, title?, options?)` | 确认对话框，默认 warning 类型 | Promise |
| `$modal.msgSuccess(message)` | 成功 Toast | void |
| `$modal.msgError(message)` | 错误 Toast | void |
| `$modal.msgWarning(message)` | 警告 Toast | void |
| `$modal.msgInfo(message)` | 信息 Toast | void |

### 注册方式

`main.ts` 中通过 `app.config.globalProperties.$modal = modal` 注册。

## 调用模式

### 规则

| 场景 | 模式 |
|------|------|
| 页面加载 (`onMounted`) | `async/await` — 保留 |
| 用户操作（删/改/启停/清空） | `proxy.$modal.confirm().then().then().catch(() => {})` |
| 无确认的操作提示 | `proxy.$modal.msgSuccess/msgError/msgWarning/msgInfo(...)` |

### 组件内调用

```javascript
import { getCurrentInstance } from 'vue'
const { proxy } = getCurrentInstance()
```

### 示例

```javascript
// 确认删除
function handleDelete(row) {
  proxy.$modal.confirm('确定删除该记录?').then(() => {
    return removeUser(row.id)
  }).then(() => {
    loadData()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

// 确认清空
function handleClear() {
  proxy.$modal.confirm('确定清空所有数据?').then(() => {
    return clearData()
  }).then(() => {
    loadData()
    proxy.$modal.msgSuccess('已清空')
  }).catch(() => {})
}
```

### `.catch(() => {})` 规则

所有用户操作的 Promise 链末尾必须有 `.catch(() => {})`：
- 用户取消对话框 → reject 被 catch 吞掉，不做任何处理
- API 失败 → `request.ts` 拦截器已显示错误消息，catch 无需重复提示

## 改动范围

### 新建文件
- `src/utils/modal.js`

### 修改文件
- `src/main.ts` — 注册 `$modal` 插件
- 所有 Vue 页面（12 个）— `ElMessageBox.confirm` → `proxy.$modal.confirm`，`ElMessage.*` → `proxy.$modal.msg*`

### 不改动
- `onMounted` 中的 `async/await` 保留
- `request.ts` 拦截器保持不变
- API 文件（`src/api/*.ts`）保持不变
