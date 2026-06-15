# 统一对话框封装 + Promise 链模式 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 创建 `$modal` 全局对话框工具，将所有 Vue 页面的用户操作改为 Promise 链模式

**Architecture:** Vue Plugin 注入 `$modal`，封装 Element Plus 的 ElMessageBox/ElMessage；用户操作统一 `.then().catch()` 链式调用，页面加载保留 `async/await`

**Tech Stack:** Vue 3 + Element Plus + TypeScript

---

### Task 1: 创建 `src/utils/modal.js`

**Files:**
- Create: `dbsyncer-web-ui/src/utils/modal.js`

- [ ] **Step 1: 创建 modal.js**

```javascript
import { ElMessage, ElMessageBox } from 'element-plus'

const modal = {
  confirm(message, title = '提示', options = {}) {
    return ElMessageBox.confirm(message, title, {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      ...options,
    })
  },

  msgSuccess(message) {
    ElMessage.success(message)
  },

  msgError(message) {
    ElMessage.error(message)
  },

  msgWarning(message) {
    ElMessage.warning(message)
  },

  msgInfo(message) {
    ElMessage.info(message)
  },
}

export default modal
```

- [ ] **Step 2: 提交**

```bash
git add dbsyncer-web-ui/src/utils/modal.js
git commit -m "feat: 创建 $modal 统一对话框工具（confirm/msgSuccess/msgError/msgWarning/msgInfo）"
```

---

### Task 2: 注册 $modal Vue Plugin

**Files:**
- Modify: `dbsyncer-web-ui/src/main.ts`

- [ ] **Step 1: 在 main.ts 注册 $modal**

找到 `const app = createApp(App)` 之后的位置，添加：

```typescript
import modal from '@/utils/modal'
app.config.globalProperties.$modal = modal
```

- [ ] **Step 2: 构建验证**

```bash
cd dbsyncer-web-ui && npx vite build 2>&1 | tail -3
```

- [ ] **Step 3: 提交**

```bash
git add dbsyncer-web-ui/src/main.ts
git commit -m "feat: main.ts 注册 $modal 全局对话框插件"
```

---

### Task 3: 改造 ConnectorList.vue

**Files:**
- Modify: `dbsyncer-web-ui/src/views/connector/ConnectorList.vue`

- [ ] **Step 1: 添加 proxy 声明，替换 import**

```typescript
// 移除: import { ElMessage, ElMessageBox } from 'element-plus'
// 添加:
import { getCurrentInstance } from 'vue'
const { proxy } = getCurrentInstance()
```

- [ ] **Step 2: 改造 handleTest — .then() 链**

```typescript
// 改前:
// testConnector(row.id).then(() => ElMessage.success('连接测试成功')).catch(() => ElMessage.error('连接测试失败'))
// 改后:
function handleTest(row: any) {
  proxy.$modal.confirm('确定测试该连接?').then(() => {
    return testConnector(row.id)
  }).then(() => {
    proxy.$modal.msgSuccess('连接测试成功')
  }).catch(() => {})
}
```

- [ ] **Step 3: 改造 handleRemove — Promise 链**

```typescript
// 改前:
// ElMessageBox.confirm('确定删除该连接器?', '提示', { type: 'warning' }).then(async () => {
//   await removeConnector(row.id)
//   ElMessage.success('删除成功')
//   loadData()
// }).catch(() => {})

// 改后:
function handleRemove(row: any) {
  proxy.$modal.confirm('确定删除该连接器?').then(() => {
    return removeConnector(row.id)
  }).then(() => {
    loadData()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}
```

- [ ] **Step 4: 构建验证 + 提交**

```bash
cd dbsyncer-web-ui && npx vite build 2>&1 | tail -1
git add dbsyncer-web-ui/src/views/connector/ConnectorList.vue
git commit -m "refactor: ConnectorList 改用 $modal + Promise 链模式"
```

---

### Task 4: 改造 MappingList.vue

**Files:**
- Modify: `dbsyncer-web-ui/src/views/mapping/MappingList.vue`

- [ ] **Step 1: 添加 proxy，移除 ElMessage/ElMessageBox import**

```typescript
import { getCurrentInstance } from 'vue'
const { proxy } = getCurrentInstance()
// 移除: ElMessage, ElMessageBox from element-plus
```

- [ ] **Step 2: 改造 handleStart/handleStop — .then() 链**

```typescript
function handleStart(row: any) {
  proxy.$modal.confirm('确定启动该任务?').then(() => {
    return startMapping(row.id)
  }).then(() => {
    loadData()
    proxy.$modal.msgSuccess('启动成功')
  }).catch(() => {})
}

function handleStop(row: any) {
  proxy.$modal.confirm('确定停止该任务?').then(() => {
    return stopMapping(row.id)
  }).then(() => {
    loadData()
    proxy.$modal.msgSuccess('停止成功')
  }).catch(() => {})
}
```

- [ ] **Step 3: 改造 handleRemove — Promise 链**

```typescript
function handleRemove(row: any) {
  proxy.$modal.confirm('确定删除该任务?').then(() => {
    return removeMapping(row.id)
  }).then(() => {
    loadData()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}
```

- [ ] **Step 4: 构建验证 + 提交**

```bash
cd dbsyncer-web-ui && npx vite build 2>&1 | tail -1
git add dbsyncer-web-ui/src/views/mapping/MappingList.vue
git commit -m "refactor: MappingList 改用 $modal + Promise 链模式"
```

---

### Task 5: 改造 MonitorView.vue

**Files:**
- Modify: `dbsyncer-web-ui/src/views/monitor/MonitorView.vue`

- [ ] **Step 1: 添加 proxy，移除 ElMessage/ElMessageBox import**

```typescript
import { getCurrentInstance } from 'vue'
const { proxy } = getCurrentInstance()
// 移除: ElMessage, ElMessageBox from element-plus
```

- [ ] **Step 2: 改造 handleClearLog/handleClearData/handleDeleteRecord — Promise 链**

```typescript
function handleClearLog() {
  proxy.$modal.confirm('确定清空所有日志？').then(() => {
    return clearLog()
  }).then(() => {
    loadLog()
    proxy.$modal.msgSuccess('已清空')
  }).catch(() => {})
}

function handleClearData() {
  proxy.$modal.confirm('确定清空所有同步数据？').then(() => {
    return syncMonitor()
  }).then(() => {
    loadData()
    proxy.$modal.msgSuccess('已清空')
  }).catch(() => {})
}

function handleDeleteRecord(row: any) {
  proxy.$modal.confirm('确定删除该条记录？').then(() => {
    return clearData(row.id)
  }).then(() => {
    loadData()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}
```

- [ ] **Step 3: 构建验证 + 提交**

```bash
cd dbsyncer-web-ui && npx vite build 2>&1 | tail -1
git add dbsyncer-web-ui/src/views/monitor/MonitorView.vue
git commit -m "refactor: MonitorView 改用 $modal + Promise 链模式"
```

---

### Task 6: 改造 UserList.vue

**Files:**
- Modify: `dbsyncer-web-ui/src/views/user/UserList.vue`

- [ ] **Step 1: 添加 proxy，移除 ElMessage/ElMessageBox import**

```typescript
import { getCurrentInstance } from 'vue'
const { proxy } = getCurrentInstance()
// 移除: ElMessage, ElMessageBox from element-plus
```

- [ ] **Step 2: 改造 handleRemove — Promise 链**

```typescript
function handleRemove(row: any) {
  proxy.$modal.confirm('确定删除该用户?').then(() => {
    return removeUser(row.id)
  }).then(() => {
    loadData()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}
```

- [ ] **Step 3: 构建验证 + 提交**

```bash
cd dbsyncer-web-ui && npx vite build 2>&1 | tail -1
git add dbsyncer-web-ui/src/views/user/UserList.vue
git commit -m "refactor: UserList 改用 $modal + Promise 链模式"
```

---

### Task 7: 批量改造 ElMessage 页面（无 confirm，仅 msg）

改造以下 8 个没有 `ElMessageBox.confirm` 的页面，将 `ElMessage.*` 替换为 `proxy.$modal.msg*`，并按需补充 `getCurrentInstance` + `proxy`：

**涉及文件:**
- `ConnectorAdd.vue`
- `ConnectorEdit.vue`
- `MappingEdit.vue`
- `ConfigList.vue`
- `PluginList.vue`
- `UserAdd.vue`
- `UserEdit.vue`
- `SystemConfig.vue`
- `NotifyView.vue`
- `TaskList.vue`
- `LoginView.vue`

- [ ] **Step 1: ConnectorAdd.vue**

```typescript
// 添加: import { getCurrentInstance } from 'vue'
// 添加: const { proxy } = getCurrentInstance()
// 移除: import { ElMessage } from 'element-plus'
// ElMessage.warning → proxy.$modal.msgWarning
// ElMessage.success → proxy.$modal.msgSuccess
// handleSave 中的 await addConnector → 改为 .then() 链:

async function handleSave() {
  if (!form.name || !form.connectorType || !form.url || !form.username || !form.password) {
    proxy.$modal.msgWarning('请填写所有必填项')
    return
  }
  saving.value = true
  addConnector(form as Record<string, any>).then(() => {
    proxy.$modal.msgSuccess('添加成功')
    router.push('/connectors')
  }).catch(() => {}).finally(() => { saving.value = false })
}
```

- [ ] **Step 2: ConnectorEdit.vue**

```typescript
// 添加: import { getCurrentInstance } from 'vue'
// 添加: const { proxy } = getCurrentInstance()
// 移除: import { ElMessage } from 'element-plus'
// ElMessage.success → proxy.$modal.msgSuccess
// handleSave 改为:

async function handleSave() {
  saving.value = true
  const data: Record<string, any> = { ...form }
  if (!data.password) delete data.password
  editConnector(data).then(() => {
    proxy.$modal.msgSuccess('修改成功')
    router.push('/connectors')
  }).catch(() => {}).finally(() => { saving.value = false })
}
```

- [ ] **Step 3: MappingEdit.vue**

```typescript
// 添加: import { getCurrentInstance } from 'vue'
// 添加: const { proxy } = getCurrentInstance()
// 移除: import { ElMessage } from 'element-plus'
// ElMessage.success/error → proxy.$modal.msgSuccess/msgError
// handleSave 改为:

async function handleSave() {
  saving.value = true
  const url = isNew ? addMapping : editMapping
  url(form as Record<string, any>).then(() => {
    proxy.$modal.msgSuccess('保存成功')
    router.push('/mappings')
  }).catch(() => {}).finally(() => { saving.value = false })
}
```

- [ ] **Step 4: ConfigList.vue**

```typescript
// 添加: import { getCurrentInstance } from 'vue'
// 添加: const { proxy } = getCurrentInstance()
// 移除: import { ElMessage } from 'element-plus'
// ElMessage.success('上传成功') → proxy.$modal.msgSuccess('上传成功')
```

- [ ] **Step 5: PluginList.vue**

```typescript
// 添加: import { getCurrentInstance } from 'vue'
// 添加: const { proxy } = getCurrentInstance()
// 移除: import { ElMessage } from 'element-plus'
// ElMessage.success/error → proxy.$modal.msgSuccess/msgError
```

- [ ] **Step 6: UserAdd.vue**

```typescript
// 添加: import { getCurrentInstance } from 'vue'
// 添加: const { proxy } = getCurrentInstance()
// 移除: import { ElMessage } from 'element-plus'
// handleSave 中 await addUser → .then() 链:

async function handleSave() {
  saving.value = true
  addUser(form as Record<string, any>).then(() => {
    proxy.$modal.msgSuccess('添加成功')
    router.push('/users')
  }).catch(() => {}).finally(() => { saving.value = false })
}
```

- [ ] **Step 7: UserEdit.vue**

```typescript
// 添加: import { getCurrentInstance } from 'vue'
// 添加: const { proxy } = getCurrentInstance()
// 移除: import { ElMessage } from 'element-plus'
// handleSave 改为:

async function handleSave() {
  saving.value = true
  const data: Record<string, any> = { ...form }
  if (!data.password) delete data.password
  editUser(data).then(() => {
    proxy.$modal.msgSuccess('修改成功')
    router.push('/users')
  }).catch(() => {}).finally(() => { saving.value = false })
}
```

- [ ] **Step 8: SystemConfig.vue**

```typescript
// 添加: import { getCurrentInstance } from 'vue'
// 添加: const { proxy } = getCurrentInstance()
// 移除: import { ElMessage } from 'element-plus'
// ElMessage.success/warning → proxy.$modal.msgSuccess/msgWarning
```

- [ ] **Step 9: NotifyView.vue**

```typescript
// 添加: import { getCurrentInstance } from 'vue'
// 添加: const { proxy } = getCurrentInstance()
// 移除: import { ElMessage } from 'element-plus'
// ElMessage.success/warning → proxy.$modal.msgSuccess/msgWarning
```

- [ ] **Step 10: TaskList.vue**

```typescript
// 添加: import { getCurrentInstance } from 'vue'
// 添加: const { proxy } = getCurrentInstance()
// 移除: import { ElMessage } from 'element-plus'
// ElMessage.success → proxy.$modal.msgSuccess
```

- [ ] **Step 11: LoginView.vue**

```typescript
// 添加: import { getCurrentInstance } from 'vue'
// 添加: const { proxy } = getCurrentInstance()
// 移除: import { ElMessage } from 'element-plus'
// handleLogin 改为 .then() 链:

function handleLogin() {
  userStore.login(form.username, form.password).then(() => {
    proxy.$modal.msgSuccess('登录成功')
    router.push('/')
  }).catch(() => {
    proxy.$modal.msgError('用户名或密码错误')
  })
}
```

- [ ] **Step 12: 构建验证 + 提交**

```bash
cd dbsyncer-web-ui && npx vite build 2>&1 | tail -1
git add dbsyncer-web-ui/src/views/
git commit -m "refactor: 全部 Vue 页面改用 $modal + Promise 链模式"
```

---

### Task 8: 构建验证 + 浏览器验收

- [ ] **Step 1: 整体构建**

```bash
cd dbsyncer-web-ui && npx vite build 2>&1 | tail -5
```

- [ ] **Step 2: 浏览器验证每个页面的对话框和提示**

```bash
# 登录，逐一访问每个页面，验证：
# - 删除确认对话框正常弹出
# - 确定后 API 调用正常
# - msgSuccess 提示正常显示
# - 取消对话框无副作用
# - 启动/停止确认正常
```

- [ ] **Step 3: 更新 CLAUDE.md 规则**

在 CLAUDE.md 第5节补充 Promise 链模式规则：

```markdown
### Promise 链模式

用户操作（删除、保存、启停等）必须使用 `.then()` 链式调用：

```typescript
proxy.$modal.confirm('确定删除?').then(() => {
  return removeUser(id)  // API 返回 Promise，传给下一个 then
}).then(() => {
  loadData()
  proxy.$modal.msgSuccess('删除成功')
}).catch(() => {})  // 必须空 catch 收尾
```

规则：
- 所有用户操作的 Promise 链末尾必须有 `.catch(() => {})`
- API 调用使用 `return apiFunction()` 将 Promise 传给下一个 `.then()`
- `onMounted` 数据加载保留 `async/await`
- 所有页面使用 `const { proxy } = getCurrentInstance()` 获取全局方法
```

- [ ] **Step 4: 最终提交**

```bash
git add CLAUDE.md
git commit -m "docs: CLAUDE.md 补充 Promise 链模式规则"
```
