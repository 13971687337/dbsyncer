# DBSyncer Web UI 前端重构实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 dbsyncer-web-ui（Vue3 SPA）补全至与 ui_bak 功能等价，统一视觉风格（浅色侧边栏 + #165DFF 主题色 + ECharts 图表）

**Architecture:** 基于现有 Vue3 + Element Plus + Pinia + TypeScript 架构，新增 echarts/vue-echarts 依赖，覆盖 Element Plus CSS 变量对齐旧 UI 主题色，新建 6 个页面 + 补全 2 个页面

**Tech Stack:** Vue 3.5, Element Plus 2.8, ECharts 5, vue-echarts, Pinia, vue-router 4, TypeScript, Vite 6

**验证方式：** 前端项目无自动化测试框架，每完成一个任务通过 `npm run dev` 启动开发服务器，在浏览器中确认页面渲染正确、无控制台报错。

---

### Task 1: 安装 ECharts 依赖

**Files:**
- Modify: `dbsyncer-web-ui/package.json`

- [ ] **Step 1: 安装 echarts 和 vue-echarts**

```bash
cd dbsyncer-web-ui && npm install echarts vue-echarts
```

- [ ] **Step 2: 验证安装**

```bash
cd dbsyncer-web-ui && node -e "require('echarts'); console.log('echarts OK')"
```

---

### Task 2: 全局布局和主题变量覆盖

**Files:**
- Modify: `dbsyncer-web-ui/src/views/layout/MainLayout.vue`
- Modify: `dbsyncer-web-ui/src/main.ts`

**说明：** 将侧边栏从深色改为浅色，Logo 区显示 "DBSyncer"，底部加版权信息，宽度调整为 240px。通过 CSS 覆盖 Element Plus 主题变量对齐 ui_bak。

- [ ] **Step 1: 创建全局主题样式文件**

Create: `dbsyncer-web-ui/src/styles/theme.css`

```css
/* DBSyncer 主题变量覆盖 Element Plus */
:root {
  --el-color-primary: #165DFF;
  --el-color-primary-light-3: rgba(22, 93, 255, 0.3);
  --el-color-primary-light-5: rgba(22, 93, 255, 0.5);
  --el-color-primary-light-7: rgba(22, 93, 255, 0.7);
  --el-color-primary-light-8: rgba(22, 93, 255, 0.8);
  --el-color-primary-light-9: rgba(22, 93, 255, 0.9);
  --el-color-primary-dark-2: #0e42d9;
  --el-color-success: #52c41a;
  --el-color-warning: #fa8c16;
  --el-color-danger: #f5222d;
  --el-color-info: #1890ff;

  /* 侧边栏 */
  --sidebar-bg: #ffffff;
  --sidebar-text: #595959;
  --sidebar-active-bg: rgba(22, 93, 255, 0.1);
  --sidebar-active-text: #165DFF;
  --sidebar-width: 240px;
}

/* 全局 */
html, body, #app {
  height: 100%;
  margin: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Helvetica Neue', Helvetica, Arial, sans-serif;
}
```

- [ ] **Step 2: 在 main.ts 中引入主题样式**

Modify: `dbsyncer-web-ui/src/main.ts` — 在 `import 'element-plus/dist/index.css'` 之后添加：

```typescript
import './styles/theme.css'
```

- [ ] **Step 3: 重写 MainLayout.vue**

Replace: `dbsyncer-web-ui/src/views/layout/MainLayout.vue` 的完整内容：

```vue
<template>
  <el-container class="layout">
    <el-aside :width="sidebarWidth" class="sidebar">
      <div class="sidebar-logo">
        <span class="sidebar-logo-text">DBSyncer</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        background-color="#ffffff"
        text-color="#595959"
        active-text-color="#165DFF"
        class="sidebar-menu"
      >
        <el-menu-item index="/">
          <el-icon><Odometer /></el-icon><span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/connectors">
          <el-icon><Connection /></el-icon><span>连接管理</span>
        </el-menu-item>
        <el-menu-item index="/mappings">
          <el-icon><Link /></el-icon><span>驱动管理</span>
        </el-menu-item>
        <el-menu-item index="/monitor">
          <el-icon><Monitor /></el-icon><span>性能监控</span>
        </el-menu-item>
        <el-menu-item index="/plugins">
          <el-icon><SetUp /></el-icon><span>插件管理</span>
        </el-menu-item>
        <el-menu-item index="/config">
          <el-icon><Files /></el-icon><span>配置管理</span>
        </el-menu-item>
        <el-menu-item index="/users">
          <el-icon><User /></el-icon><span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/system">
          <el-icon><Setting /></el-icon><span>系统配置</span>
        </el-menu-item>
      </el-menu>
      <div class="sidebar-footer">
        <span class="sidebar-footer-text">Copyright &copy; 2026 DBSyncer</span>
      </div>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-right">
          <el-dropdown trigger="click">
            <span class="header-user">
              <el-icon><UserFilled /></el-icon>
              <span>{{ userStore.nickName || userStore.name }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleEditProfile">修改资料</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">注销</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const sidebarWidth = '240px'
const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/connectors')) return '/connectors'
  if (path.startsWith('/mappings')) return '/mappings'
  if (path.startsWith('/users')) return '/users'
  return path
})

function handleEditProfile() {
  router.push('/users/' + encodeURIComponent(userStore.name) + '/edit')
}

async function handleLogout() {
  await userStore.doLogout()
  router.push('/login')
}
</script>

<style scoped>
.layout { height: 100vh; }

.sidebar {
  background-color: #ffffff;
  border-right: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  padding: 16px 20px;
  gap: 8px;
}
.sidebar-logo-text {
  font-size: 20px;
  font-weight: bold;
  color: #262626;
}

.sidebar-menu {
  flex: 1;
  border-right: none !important;
  overflow-y: auto;
}

.sidebar-menu .el-menu-item {
  margin: 2px 8px;
  border-radius: 6px;
  height: 44px;
  line-height: 44px;
}
.sidebar-menu .el-menu-item:hover {
  background-color: rgba(22, 93, 255, 0.05) !important;
}
.sidebar-menu .el-menu-item.is-active {
  background-color: rgba(22, 93, 255, 0.1) !important;
  font-weight: 500;
}

.sidebar-footer {
  padding: 12px 16px;
  border-top: 1px solid #e8e8e8;
}
.sidebar-footer-text {
  font-size: 12px;
  color: #8c8c8c;
}

.header {
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  height: 56px;
  padding: 0 20px;
}

.header-right {
  display: flex;
  align-items: center;
}

.header-user {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #595959;
  font-size: 14px;
}

.main-content {
  background: #fafafa;
  padding: 20px;
  overflow-y: auto;
}
</style>
```

- [ ] **Step 4: 验证布局**

```bash
cd dbsyncer-web-ui && npm run dev
```

打开浏览器确认：侧边栏为白色背景，8个菜单项，Logo 显示 "DBSyncer"，底部有版权信息，主内容区为灰色背景。

---

### Task 3: 路由配置补全

**Files:**
- Modify: `dbsyncer-web-ui/src/router/index.ts`

- [ ] **Step 1: 更新路由表**

Replace: `dbsyncer-web-ui/src/router/index.ts` 的完整内容：

```typescript
import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/',
    component: () => import('@/views/layout/MainLayout.vue'),
    children: [
      { path: '', name: 'Dashboard', component: () => import('@/views/dashboard/DashboardView.vue'), meta: { title: '仪表盘' } },
      { path: 'connectors', name: 'Connectors', component: () => import('@/views/connector/ConnectorList.vue'), meta: { title: '连接管理' } },
      { path: 'connectors/add', name: 'ConnectorAdd', component: () => import('@/views/connector/ConnectorAdd.vue'), meta: { title: '添加连接' } },
      { path: 'connectors/:id/edit', name: 'ConnectorEdit', component: () => import('@/views/connector/ConnectorEdit.vue'), meta: { title: '编辑连接' } },
      { path: 'mappings', name: 'Mappings', component: () => import('@/views/mapping/MappingList.vue'), meta: { title: '驱动管理' } },
      { path: 'mappings/:id', name: 'MappingEdit', component: () => import('@/views/mapping/MappingEdit.vue'), meta: { title: '编辑驱动' } },
      { path: 'monitor', name: 'Monitor', component: () => import('@/views/monitor/MonitorView.vue'), meta: { title: '性能监控' } },
      { path: 'plugins', name: 'Plugins', component: () => import('@/views/plugin/PluginList.vue'), meta: { title: '插件管理' } },
      { path: 'config', name: 'Config', component: () => import('@/views/config/ConfigList.vue'), meta: { title: '配置管理' } },
      { path: 'users', name: 'Users', component: () => import('@/views/user/UserList.vue'), meta: { title: '用户管理' } },
      { path: 'users/add', name: 'UserAdd', component: () => import('@/views/user/UserAdd.vue'), meta: { title: '添加用户' } },
      { path: 'users/:username/edit', name: 'UserEdit', component: () => import('@/views/user/UserEdit.vue'), meta: { title: '编辑用户' } },
      { path: 'system', name: 'SystemConfig', component: () => import('@/views/system/SystemConfig.vue'), meta: { title: '系统配置' } },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
```

- [ ] **Step 2: 验证路由不报错**

刷新浏览器，点击侧边栏各菜单项（新建页面会空白或报 404，只要不导致白屏即可）。

---

### Task 4: 仪表盘页面重建（DashboardView）

**Files:**
- Modify: `dbsyncer-web-ui/src/views/dashboard/DashboardView.vue`

**说明：** 用 ECharts 重建仪表盘，包含统计卡片、同步趋势折线图、迷你统计卡片组、任务状态柱状图、连接器列表、驱动列表。

- [ ] **Step 1: 重写 DashboardView.vue**

Replace: `dbsyncer-web-ui/src/views/dashboard/DashboardView.vue` 的完整内容：

```vue
<template>
  <div class="dashboard">
    <!-- 顶部统计卡片 -->
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-card">
            <div class="stat-card-header">
              <span class="stat-card-label">总任务数</span>
              <span class="stat-card-icon stat-card-icon--primary"><el-icon><List /></el-icon></span>
            </div>
            <div class="stat-card-body">
              <span class="stat-card-value">{{ dashboard.totalMeta ?? '--' }}</span>
              <span v-if="totalTrend.percent !== null" :class="totalTrend.isIncrease ? 'stat-trend--up' : 'stat-trend--down'">
                <el-icon><component :is="totalTrend.isIncrease ? 'Top' : 'Bottom'" /></el-icon> {{ totalTrend.diff }} 个
              </span>
            </div>
            <div class="stat-card-footer" v-if="totalTrend.percent !== null">
              较上周{{ totalTrend.isIncrease ? '增长' : '降低' }} {{ totalTrend.percent }}%
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-card">
            <div class="stat-card-header">
              <span class="stat-card-label">运行中任务</span>
              <span class="stat-card-icon stat-card-icon--success"><el-icon><VideoPlay /></el-icon></span>
            </div>
            <div class="stat-card-body">
              <span class="stat-card-value">{{ dashboard.runningMeta ?? '--' }}</span>
              <span class="stat-trend--up"><el-icon><Check /></el-icon> 正常</span>
            </div>
            <div class="stat-card-footer" v-if="dashboard.totalMeta">
              占总任务数 {{ runningPercent }}%
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-card">
            <div class="stat-card-header">
              <span class="stat-card-label">失败任务</span>
              <span class="stat-card-icon stat-card-icon--danger"><el-icon><WarningFilled /></el-icon></span>
            </div>
            <div class="stat-card-body">
              <span class="stat-card-value danger">{{ dashboard.failMeta ?? '--' }}</span>
            </div>
            <div class="stat-card-footer">需要立即处理</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-card">
            <div class="stat-card-header">
              <span class="stat-card-label">同步总数</span>
              <span class="stat-card-icon stat-card-icon--info"><el-icon><Coin /></el-icon></span>
            </div>
            <div class="stat-card-body">
              <span class="stat-card-value">{{ syncTotal }}</span>
              <span v-if="syncTrend.percent !== null" :class="syncTrend.isIncrease ? 'stat-trend--up' : 'stat-trend--down'">
                <el-icon><component :is="syncTrend.isIncrease ? 'Top' : 'Bottom'" /></el-icon> {{ syncTrend.percent }}%
              </span>
            </div>
            <div class="stat-card-footer" v-if="syncTrend.percent !== null">
              较昨日{{ syncTrend.isIncrease ? '增长' : '降低' }}
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 中间图表区域 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <div class="card-header-bar">
              <span class="card-title">同步趋势</span>
              <el-button-group>
                <el-button :type="trendDays === 7 ? 'primary' : ''" size="small" @click="trendDays = 7">一周</el-button>
                <el-button :type="trendDays === 15 ? 'primary' : ''" size="small" @click="trendDays = 15">半个月</el-button>
                <el-button :type="trendDays === 30 ? 'primary' : ''" size="small" @click="trendDays = 30">一个月</el-button>
              </el-button-group>
            </div>
          </template>
          <v-chart :option="trendChartOption" style="height:320px" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header><span class="card-title">任务统计</span></template>
          <div class="mini-stats">
            <div v-for="s in miniStats" :key="s.key" class="mini-stat-card" :class="'mini-stat-card--' + s.bgColor">
              <span class="mini-stat-label">{{ s.label }}</span>
              <span class="mini-stat-value" :class="'text-' + s.bgColor">{{ s.value }}</span>
            </div>
          </div>
          <v-chart :option="statusBarOption" style="height:180px" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 底部连接器 & 驱动列表 -->
    <el-row :gutter="16" class="bottom-row">
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>
            <div class="card-header-bar">
              <span class="card-title">连接器</span>
              <el-button type="primary" size="small" @click="router.push('/connectors/add')">添加连接</el-button>
            </div>
          </template>
          <div v-if="connectors.length === 0" class="empty-block">暂无连接</div>
          <div v-for="c in connectors" :key="c.id" class="connector-card">
            <div class="connector-card-left">
              <span class="connector-name">{{ c.name }}</span>
              <span class="connector-url" :title="c.config?.url">{{ c.config?.url }}</span>
            </div>
            <el-tag :type="c.running ? 'success' : 'danger'" size="small">{{ c.running ? '在线' : '离线' }}</el-tag>
          </div>
        </el-card>
      </el-col>
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <div class="card-header-bar">
              <span class="card-title">驱动列表</span>
              <el-button type="primary" size="small" @click="router.push('/mappings/add')">添加驱动</el-button>
            </div>
          </template>
          <el-table :data="mappings" stripe size="small">
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="model" label="类型" width="80" />
            <el-table-column label="结果" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.meta?.successTotal" type="success" size="small">成功 {{ row.meta.successTotal }}</el-tag>
                <el-tag v-if="row.meta?.failTotal" type="danger" size="small">失败 {{ row.meta.failTotal }}</el-tag>
                <span v-if="!row.meta?.successTotal && !row.meta?.failTotal">--</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.meta?.state === 1 ? 'success' : 'info'" size="small">
                  {{ row.meta?.state === 1 ? '运行中' : '已停止' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="最后同步" width="160">
              <template #default="{ row }">{{ row.meta?.updateTime || '--' }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getDashboard } from '@/api/monitor'
import { searchConnector } from '@/api/connector'
import { searchMapping } from '@/api/mapping'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart, BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

use([LineChart, BarChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const router = useRouter()
const dashboard = ref<Record<string, any>>({})
const connectors = ref<any[]>([])
const mappings = ref<any[]>([])
const trendDays = ref(7)

const totalTrend = computed(() => {
  const r = dashboard.value
  if (r.totalMeta == null || r.lastWeekMeta == null) return { percent: null, isIncrease: false, diff: 0 }
  const isIncrease = r.lastWeekMeta <= r.totalMeta
  const diff = isIncrease ? (r.totalMeta - r.lastWeekMeta) : (r.lastWeekMeta - r.totalMeta)
  const percent = r.lastWeekMeta > 0 ? (diff / r.lastWeekMeta * 100).toFixed(2) : 0
  return { percent, isIncrease, diff }
})

const runningPercent = computed(() => {
  const r = dashboard.value
  if (!r.totalMeta || r.runningMeta == null) return 0
  return (r.runningMeta / r.totalMeta * 100).toFixed(2)
})

const syncTotal = computed(() => {
  const r = dashboard.value
  return (r.success ?? 0) + (r.fail ?? 0)
})

const syncTrend = computed(() => {
  const r = dashboard.value
  const total = (r.success ?? 0) + (r.fail ?? 0)
  if (r.yesterdayData == null) return { percent: null, isIncrease: false }
  const isIncrease = r.yesterdayData <= total
  const diff = isIncrease ? (total - r.yesterdayData) : (r.yesterdayData - total)
  const percent = r.yesterdayData > 0 ? (diff / r.yesterdayData * 100).toFixed(2) : 0
  return { percent, isIncrease }
})

const miniStats = computed(() => {
  const r = dashboard.value
  return [
    { key: 'success', label: '成功', value: r.success ?? 0, bgColor: 'success' },
    { key: 'fail', label: '失败', value: r.fail ?? 0, bgColor: 'danger' },
    { key: 'insert', label: '新增', value: r.insert ?? 0, bgColor: 'primary' },
    { key: 'update', label: '修改', value: r.update ?? 0, bgColor: 'info' },
    { key: 'delete', label: '删除', value: r.delete ?? 0, bgColor: 'warning' },
    { key: 'ddl', label: 'DDL', value: r.ddl ?? 0, bgColor: 'violet' },
  ]
})

const trendChartOption = computed(() => {
  const trend = dashboard.value.trend || { labels: [], success: [], fail: [] }
  const slice = (arr: any[]) => trendDays.value === 7 ? arr.slice(-7) : trendDays.value === 15 ? arr.slice(-15) : arr
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['同步成功', '同步失败'], bottom: 0 },
    grid: { left: 40, right: 20, top: 10, bottom: 40 },
    xAxis: { type: 'category', data: slice(trend.labels || []), boundaryGap: false },
    yAxis: { type: 'value' },
    series: [
      {
        name: '同步成功', type: 'line', data: slice(trend.success || []),
        lineStyle: { color: '#165DFF', width: 2 },
        areaStyle: { color: 'rgba(22, 93, 255, 0.1)' },
        smooth: true, symbol: 'none',
      },
      {
        name: '同步失败', type: 'line', data: slice(trend.fail || []),
        lineStyle: { color: '#fa8c16', width: 2 },
        areaStyle: { color: 'rgba(250, 140, 22, 0.2)' },
        smooth: true, symbol: 'none',
      },
    ],
  }
})

const statusBarOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 60, right: 20, top: 10, bottom: 10 },
  xAxis: { type: 'value' },
  yAxis: { type: 'category', data: ['已失败', '运行中', '总任务'] },
  series: [{
    type: 'bar', data: [
      dashboard.value.failMeta ?? 0,
      dashboard.value.runningMeta ?? 0,
      dashboard.value.totalMeta ?? 0,
    ],
    itemStyle: { borderRadius: 4 },
    colorBy: 'data',
    barWidth: 16,
  }],
}))

onMounted(async () => {
  try {
    const res: any = await getDashboard()
    if (res?.data) dashboard.value = res.data
  } catch { /* ignore */ }
  try {
    const cRes: any = await searchConnector({ pageNum: 1, pageSize: 3 })
    if (cRes?.data) connectors.value = cRes.data.data || []
  } catch { /* ignore */ }
  try {
    const mRes: any = await searchMapping({ pageNum: 1, pageSize: 5 })
    if (mRes?.data) mappings.value = mRes.data.data || []
  } catch { /* ignore */ }
})
</script>

<style scoped>
.dashboard { --c-primary: #165DFF; --c-success: #52c41a; --c-danger: #f5222d; --c-info: #1890ff; --c-warning: #fa8c16; --c-violet: #722ed1; }

.stat-cards { margin-bottom: 16px; }
.stat-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.stat-card-label { font-size: 13px; color: #8c8c8c; }
.stat-card-icon { width: 32px; height: 32px; border-radius: 50%; display: flex; align-items: center; justify-content: center; }
.stat-card-icon--primary { background: rgba(22,93,255,0.1); color: var(--c-primary); }
.stat-card-icon--success { background: rgba(82,196,26,0.1); color: var(--c-success); }
.stat-card-icon--danger { background: rgba(245,34,45,0.1); color: var(--c-danger); }
.stat-card-icon--info { background: rgba(24,144,255,0.1); color: var(--c-info); }
.stat-card-body { display: flex; align-items: baseline; gap: 8px; margin-bottom: 4px; }
.stat-card-value { font-size: 28px; font-weight: bold; color: #262626; }
.stat-card-value.danger { color: var(--c-danger); }
.stat-trend--up { font-size: 12px; color: var(--c-success); display: flex; align-items: center; gap: 2px; }
.stat-trend--down { font-size: 12px; color: var(--c-warning); display: flex; align-items: center; gap: 2px; }
.stat-card-footer { font-size: 11px; color: #8c8c8c; margin-top: 4px; }

.chart-row { margin-bottom: 16px; }
.card-header-bar { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-size: 15px; font-weight: 600; color: #262626; }

.mini-stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-bottom: 12px; }
.mini-stat-card { padding: 8px; border-radius: 6px; text-align: center; }
.mini-stat-card--success { background: rgba(82,196,26,0.08); }
.mini-stat-card--danger { background: rgba(245,34,45,0.08); }
.mini-stat-card--primary { background: rgba(22,93,255,0.08); }
.mini-stat-card--info { background: rgba(24,144,255,0.08); }
.mini-stat-card--warning { background: rgba(250,140,22,0.08); }
.mini-stat-card--violet { background: rgba(114,46,209,0.08); }
.mini-stat-label { font-size: 11px; color: #8c8c8c; display: block; }
.mini-stat-value { font-size: 18px; font-weight: 600; }
.text-success { color: var(--c-success); }
.text-danger { color: var(--c-danger); }
.text-primary { color: var(--c-primary); }
.text-info { color: var(--c-info); }
.text-warning { color: var(--c-warning); }
.text-violet { color: var(--c-violet); }

.bottom-row { }
.connector-card { display: flex; justify-content: space-between; align-items: center; padding: 10px 0; border-bottom: 1px solid #f0f0f0; }
.connector-card:last-child { border-bottom: none; }
.connector-card-left { display: flex; flex-direction: column; gap: 2px; overflow: hidden; }
.connector-name { font-size: 14px; font-weight: 500; }
.connector-url { font-size: 12px; color: #8c8c8c; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.empty-block { text-align: center; padding: 40px 0; color: #bfbfbf; }
</style>
```

- [ ] **Step 2: 验证仪表盘**

浏览器中访问首页，确认：4 个统计卡片、同步趋势图（三个时间按钮切换）、右侧迷你统计 + 柱状图、底部连接器卡片和驱动表格均正常渲染。

---

### Task 5: 插件管理页面（PluginList）

**Files:**
- Create: `dbsyncer-web-ui/src/views/plugin/PluginList.vue`

- [ ] **Step 1: 创建 PluginList.vue**

```vue
<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header-bar">
          <span class="card-title">插件管理</span>
        </div>
      </template>
      <p class="hint-text">上传插件后，选择同步任务，进入高级配置关联插件。</p>
      <el-table :data="plugins" stripe size="small">
        <el-table-column prop="name" label="名称">
          <template #default="{ row }">
            <span>{{ row.name }}</span>
            <el-tag v-if="row.unmodifiable" size="small" type="info" class="ml-1">内置</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="mappingName" label="运行驱动" />
        <el-table-column prop="className" label="类名" show-overflow-tooltip />
        <el-table-column prop="version" label="版本" width="80">
          <template #default="{ row }">
            <el-tag size="small" type="success">{{ row.version }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fileName" label="文件" show-overflow-tooltip />
      </el-table>

      <el-divider />

      <div class="upload-section">
        <el-upload
          :action="uploadUrl"
          :headers="uploadHeaders"
          accept=".jar"
          :limit="5"
          :on-success="handleUploadSuccess"
          :on-error="handleUploadError"
          drag
          multiple
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">点击或拖拽 JAR 文件到此处上传</div>
          <template #tip>
            <div class="el-upload__tip">支持 .jar 文件，最多上传 5 个</div>
          </template>
        </el-upload>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getPlugins } from '@/api/plugin'
import { getToken } from '@/utils/auth'

const plugins = ref<any[]>([])
const uploadUrl = (import.meta as any).env.VITE_APP_BASE_API + '/plugin/upload'
const uploadHeaders = ref<Record<string, string>>({ Authorization: 'Bearer ' + getToken() })

onMounted(async () => {
  try {
    const res: any = await getPlugins()
    if (res?.data) plugins.value = res.data || []
  } catch { /* ignore */ }
})

function handleUploadSuccess() {
  ElMessage.success('插件上传成功')
  // 刷新列表
  getPlugins().then((res: any) => { if (res?.data) plugins.value = res.data || [] })
}
function handleUploadError() {
  ElMessage.error('上传失败')
}
</script>

<style scoped>
.page { }
.card-header-bar { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-size: 15px; font-weight: 600; }
.hint-text { font-size: 12px; color: #8c8c8c; margin-bottom: 12px; }
.upload-section { margin-top: 8px; }
.ml-1 { margin-left: 4px; }
</style>
```

- [ ] **Step 2: 验证插件管理页**

点击侧边栏「插件管理」，确认页面渲染正常（表格 + 上传区域可见）。

---

### Task 6: 配置管理页面（ConfigList）

**Files:**
- Create: `dbsyncer-web-ui/src/views/config/ConfigList.vue`

- [ ] **Step 1: 创建 ConfigList.vue**

```vue
<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header-bar">
          <span class="card-title">配置管理</span>
        </div>
      </template>
      <el-row :gutter="24">
        <el-col :span="12">
          <h4 class="section-title">配置列表</h4>
          <p class="hint-text">
            导出所有配置，请点击
            <el-button type="primary" link size="small" @click="handleDownload">下载</el-button>
          </p>
          <el-table :data="configs" stripe size="small">
            <el-table-column prop="type" label="类型" width="100">
              <template #default="{ row }">
                <el-tag size="small" type="primary">{{ row.type }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="updateTime" label="修改时间" width="170" />
          </el-table>
        </el-col>
        <el-col :span="12">
          <div class="upload-section">
            <el-upload
              :action="uploadUrl"
              :headers="uploadHeaders"
              accept=".json"
              :limit="5"
              :on-success="handleUploadSuccess"
              drag
              multiple
            >
              <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
              <div class="el-upload__text">点击或拖拽 JSON 文件到此处上传</div>
              <template #tip>
                <div class="el-upload__tip">支持 .json 文件，最多上传 5 个</div>
              </template>
            </el-upload>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getConfig } from '@/api/config'
import { getToken } from '@/utils/auth'

const configs = ref<any[]>([])
const uploadUrl = (import.meta as any).env.VITE_APP_BASE_API + '/config/upload'
const uploadHeaders = ref<Record<string, string>>({ Authorization: 'Bearer ' + getToken() })

onMounted(async () => {
  try {
    const res: any = await getConfig()
    if (res?.data) configs.value = res.data || []
  } catch { /* ignore */ }
})

function handleDownload() {
  window.open((import.meta as any).env.VITE_APP_BASE_API + '/config/download')
}

function handleUploadSuccess() {
  ElMessage.success('上传成功')
  getConfig().then((res: any) => { if (res?.data) configs.value = res.data || [] })
}
</script>

<style scoped>
.card-header-bar { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-size: 15px; font-weight: 600; }
.section-title { font-size: 14px; font-weight: 600; margin-bottom: 4px; }
.hint-text { font-size: 12px; color: #8c8c8c; margin-bottom: 12px; }
.upload-section { padding-top: 28px; }
</style>
```

- [ ] **Step 2: 验证配置管理页**

点击侧边栏「配置管理」，确认左右两栏布局正常渲染。

---

### Task 7: 系统配置页面（SystemConfig）

**Files:**
- Create: `dbsyncer-web-ui/src/views/system/SystemConfig.vue`

- [ ] **Step 1: 创建 SystemConfig.vue**

```vue
<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header-bar">
          <span class="card-title">系统配置</span>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        </div>
      </template>
      <el-form :model="form" label-width="200px" label-position="left">
        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="记录同步成功数据">
              <el-switch v-model="form.enableStorageWriteSuccess" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="记录全量数据">
              <el-switch v-model="form.enableStorageWriteFull" />
              <el-tooltip content="不推荐在生产环境下开启，可在源库数据量较少时使用" placement="top">
                <el-icon class="help-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="记录同步失败数据">
              <el-switch v-model="form.enableStorageWriteFail" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="记录失败日志长度" required>
              <el-input-number v-model="form.maxStorageErrorLength" :min="1024" :max="8192" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="表执行器上限数" required>
              <el-input-number v-model="form.maxBufferActuatorSize" :min="1" :max="200" />
              <el-tooltip content="每新增一张驱动表的映射关系，就会单独新开一个表执行器处理，超过限制后使用通用执行器" placement="top">
                <el-icon class="help-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="同步数据过期时间(天)" required>
              <el-input-number v-model="form.expireDataDays" :min="1" :max="180" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="系统日志过期时间(天)" required>
              <el-input-number v-model="form.expireLogDays" :min="1" :max="180" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="打印trace信息">
              <el-switch v-model="form.enablePrintTraceInfo" />
              <el-tooltip content="仅用于排查问题，生产环境建议关闭" placement="top">
                <el-icon class="help-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="水印">
              <el-switch v-model="form.enableWatermark" />
              <el-tooltip content="刷新页面生效" placement="top">
                <el-icon class="help-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </el-form-item>
          </el-col>
          <el-col :span="12" v-if="form.enableWatermark">
            <el-form-item label="水印内容">
              <el-input v-model="form.watermark" maxlength="64" placeholder="请输入水印(最多64个字)" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="开放API">
              <el-switch v-model="form.enableRsaConfig" />
              <el-tooltip content="外部业务系统可凭此配置生成token，直接调用DBSyncer的管理接口" placement="top">
                <el-icon class="help-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </el-form-item>
          </el-col>
        </el-row>

        <template v-if="form.enableRsaConfig">
          <el-divider />
          <el-row :gutter="24">
            <el-col :span="8">
              <el-form-item label="密钥长度">
                <el-input-number v-model="form.rsaKeyLength" :min="1024" :max="8192" :step="1024" />
                <el-button style="margin-left:8px" @click="handleGenerateRSA" :loading="generating">生成RSA</el-button>
              </el-form-item>
            </el-col>
            <el-col :span="16" />
            <el-col :span="12">
              <el-form-item label="RSA公钥">
                <el-input v-model="form.rsaPublicKey" type="textarea" :rows="4" readonly />
                <el-button size="small" style="margin-top:4px" @click="copyText(form.rsaPublicKey, 'RSA公钥')">复制</el-button>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="RSA私钥">
                <el-input v-model="form.rsaPrivateKey" type="textarea" :rows="6" readonly />
                <el-button size="small" style="margin-top:4px" @click="copyText(form.rsaPrivateKey, 'RSA私钥')">复制</el-button>
              </el-form-item>
            </el-col>
          </el-row>
        </template>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSystemInfo, editSystem, generateRSA } from '@/api/system'

const saving = ref(false)
const generating = ref(false)

const form = reactive<Record<string, any>>({
  enableStorageWriteSuccess: false,
  enableStorageWriteFull: false,
  enableStorageWriteFail: false,
  maxStorageErrorLength: 1024,
  maxBufferActuatorSize: 50,
  expireDataDays: 30,
  expireLogDays: 30,
  enablePrintTraceInfo: false,
  enableWatermark: false,
  watermark: '',
  enableRsaConfig: false,
  rsaKeyLength: 2048,
  rsaPublicKey: '',
  rsaPrivateKey: '',
})

onMounted(async () => {
  try {
    const res: any = await getSystemInfo()
    if (res?.data) {
      const d = res.data
      Object.keys(form).forEach(k => {
        if (d[k] !== undefined) (form as any)[k] = d[k]
      })
      if (d.rsaConfig) {
        form.rsaKeyLength = d.rsaConfig.keyLength || 2048
        form.rsaPublicKey = d.rsaConfig.publicKey || ''
        form.rsaPrivateKey = d.rsaConfig.privateKey || ''
      }
    }
  } catch { /* ignore */ }
})

async function handleSave() {
  saving.value = true
  try {
    await editSystem(form as Record<string, any>)
    ElMessage.success('修改成功')
  } catch { /* ignore */ } finally { saving.value = false }
}

async function handleGenerateRSA() {
  generating.value = true
  try {
    const res: any = await generateRSA()
    if (res?.data) {
      form.rsaPublicKey = res.data.publicKey || ''
      form.rsaPrivateKey = res.data.privateKey || ''
      ElMessage.success('生成成功')
    }
  } catch { /* ignore */ } finally { generating.value = false }
}

function copyText(text: string, label: string) {
  if (!text) { ElMessage.warning(label + '为空'); return }
  navigator.clipboard.writeText(text).then(() => ElMessage.success('复制' + label + '成功'))
}
</script>

<style scoped>
.card-header-bar { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-size: 15px; font-weight: 600; }
.help-icon { margin-left: 4px; color: #8c8c8c; cursor: help; }
</style>
```

- [ ] **Step 2: 验证系统配置页**

点击侧边栏「系统配置」，确认开关、数字输入、RSA区域正常工作。切换「水印」开关显示/隐藏水印输入，切换「开放API」显示/隐藏 RSA 配置区。

---

### Task 8: 连接器 Add/Edit 页面

**Files:**
- Create: `dbsyncer-web-ui/src/views/connector/ConnectorAdd.vue`
- Create: `dbsyncer-web-ui/src/views/connector/ConnectorEdit.vue`

- [ ] **Step 1: 创建 ConnectorAdd.vue**

```vue
<template>
  <div class="page">
    <el-card shadow="never">
      <template #header><span class="card-title">添加连接器</span></template>
      <el-form :model="form" label-width="100px" style="max-width:600px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="64" placeholder="请输入连接名称" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="form.connectorType" placeholder="选择连接器类型" style="width:100%">
            <el-option v-for="t in connectorTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="连接地址" required>
          <el-input v-model="form.url" placeholder="例如: jdbc:mysql://localhost:3306/test" />
        </el-form-item>
        <el-form-item label="用户名" required>
          <el-input v-model="form.username" placeholder="数据库用户名" />
        </el-form-item>
        <el-form-item label="密码" required>
          <el-input v-model="form.password" type="password" placeholder="数据库密码" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
          <el-button @click="router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { addConnector } from '@/api/connector'
import request from '@/utils/request'

const router = useRouter()
const saving = ref(false)
const connectorTypes = ref<string[]>([])

const form = reactive({
  name: '',
  connectorType: '',
  url: '',
  username: '',
  password: '',
})

onMounted(async () => {
  try {
    const res: any = await request({ url: '/connector/getConnectorTypeAll', method: 'get' })
    if (res?.data) connectorTypes.value = res.data || []
  } catch { /* ignore */ }
})

async function handleSave() {
  if (!form.name || !form.connectorType || !form.url || !form.username || !form.password) {
    ElMessage.warning('请填写所有必填项')
    return
  }
  saving.value = true
  try {
    await addConnector(form as Record<string, any>)
    ElMessage.success('添加成功')
    router.push('/connectors')
  } catch { /* ignore */ } finally { saving.value = false }
}
</script>

<style scoped>
.card-title { font-size: 15px; font-weight: 600; }
</style>
```

- [ ] **Step 2: 创建 ConnectorEdit.vue**

```vue
<template>
  <div class="page">
    <el-card shadow="never" v-loading="loading">
      <template #header><span class="card-title">编辑连接器</span></template>
      <el-form :model="form" label-width="100px" style="max-width:600px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="64" />
        </el-form-item>
        <el-form-item label="类型">
          <el-input :model-value="form.connectorType" disabled />
        </el-form-item>
        <el-form-item label="连接地址" required>
          <el-input v-model="form.url" />
        </el-form-item>
        <el-form-item label="用户名" required>
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="密码" required>
          <el-input v-model="form.password" type="password" placeholder="留空则不修改" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
          <el-button @click="router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { editConnector } from '@/api/connector'
import request from '@/utils/request'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const saving = ref(false)

const form = reactive({
  id: '',
  name: '',
  connectorType: '',
  url: '',
  username: '',
  password: '',
})

onMounted(async () => {
  try {
    const res: any = await request({ url: '/connector/getPosition', method: 'get', params: { id: route.params.id } })
    if (res?.data) {
      const d = res.data
      form.id = d.id || ''
      form.name = d.name || ''
      form.connectorType = d.config?.connectorType || ''
      form.url = d.config?.url || ''
      form.username = d.config?.username || ''
    }
  } catch { /* ignore */ } finally { loading.value = false }
})

async function handleSave() {
  saving.value = true
  try {
    const data: Record<string, any> = { ...form }
    if (!data.password) delete data.password
    await editConnector(data)
    ElMessage.success('修改成功')
    router.push('/connectors')
  } catch { /* ignore */ } finally { saving.value = false }
}
</script>

<style scoped>
.card-title { font-size: 15px; font-weight: 600; }
</style>
```

- [ ] **Step 3: 更新 ConnectorList.vue 的操作跳转**

Modify: `dbsyncer-web-ui/src/views/connector/ConnectorList.vue`

找到 `function handleAdd()` 和 `function handleEdit()`，替换为：

```typescript
function handleAdd() { router.push('/connectors/add') }
function handleEdit(row: any) { router.push('/connectors/' + row.id + '/edit') }
```

- [ ] **Step 4: 验证连接器 Add/Edit**

在连接管理页点击「新增连接器」，填写表单保存 → 跳转回列表。点击编辑 → 加载已有数据 → 修改保存。

---

### Task 9: 用户 Add/Edit 页面

**Files:**
- Create: `dbsyncer-web-ui/src/views/user/UserAdd.vue`
- Create: `dbsyncer-web-ui/src/views/user/UserEdit.vue`
- Modify: `dbsyncer-web-ui/src/views/user/UserList.vue`

- [ ] **Step 1: 创建 UserAdd.vue**

```vue
<template>
  <div class="page">
    <el-card shadow="never">
      <template #header><span class="card-title">添加用户</span></template>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" style="max-width:500px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" maxlength="32" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" maxlength="32" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="支持多个邮箱，逗号分隔" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" maxlength="11" />
        </el-form-item>
        <el-form-item label="角色" prop="roleCode">
          <el-select v-model="form.roleCode" placeholder="选择角色">
            <el-option label="管理员" value="admin" />
            <el-option label="普通用户" value="user" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
          <el-button @click="router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const router = useRouter()
const saving = ref(false)

const form = reactive({
  username: '',
  password: '',
  nickname: '',
  email: '',
  phone: '',
  roleCode: 'user',
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

async function handleSave() {
  saving.value = true
  try {
    await request({ url: '/user/add', method: 'post', params: form as Record<string, any> })
    ElMessage.success('添加成功')
    router.push('/users')
  } catch { /* ignore */ } finally { saving.value = false }
}
</script>

<style scoped>
.card-title { font-size: 15px; font-weight: 600; }
</style>
```

- [ ] **Step 2: 创建 UserEdit.vue**

```vue
<template>
  <div class="page">
    <el-card shadow="never" v-loading="loading">
      <template #header><span class="card-title">编辑用户</span></template>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" style="max-width:500px">
        <el-form-item label="用户名">
          <el-input :model-value="form.username" disabled />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" placeholder="留空则不修改" show-password />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" maxlength="32" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" maxlength="11" />
        </el-form-item>
        <el-form-item label="角色" prop="roleCode">
          <el-select v-model="form.roleCode">
            <el-option label="管理员" value="admin" />
            <el-option label="普通用户" value="user" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
          <el-button @click="router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const saving = ref(false)

const form = reactive({
  username: '',
  password: '',
  nickname: '',
  email: '',
  phone: '',
  roleCode: '',
})

const rules = {
  roleCode: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

onMounted(async () => {
  try {
    const res: any = await request({
      url: '/user/getUserInfo.json',
      method: 'get',
      params: { username: route.params.username },
    })
    if (res?.data) {
      const d = res.data
      form.username = d.username || ''
      form.nickname = d.nickname || ''
      form.email = d.email || ''
      form.phone = d.phone || ''
      form.roleCode = d.roleCode || ''
    }
  } catch { /* ignore */ } finally { loading.value = false }
})

async function handleSave() {
  saving.value = true
  try {
    const data: Record<string, any> = { ...form }
    if (!data.password) delete data.password
    await request({ url: '/user/edit', method: 'post', params: data })
    ElMessage.success('修改成功')
    router.push('/users')
  } catch { /* ignore */ } finally { saving.value = false }
}
</script>

<style scoped>
.card-title { font-size: 15px; font-weight: 600; }
</style>
```

- [ ] **Step 3: 更新 UserList.vue**

Modify: `dbsyncer-web-ui/src/views/user/UserList.vue`

在 toolbar 中添加「添加用户」按钮，并更新编辑跳转：

将 `<div class="toolbar"><h3>用户管理</h3></div>` 替换为：

```html
<div class="toolbar">
  <h3>用户管理</h3>
  <el-button type="primary" @click="router.push('/users/add')">添加用户</el-button>
</div>
```

在 `<script setup>` 中添加：

```typescript
import { useRouter } from 'vue-router'
const router = useRouter()
```

更新 `handleEdit`:

```typescript
function handleEdit(row: any) { router.push('/users/' + encodeURIComponent(row.username) + '/edit') }
```

- [ ] **Step 4: 验证用户 Add/Edit**

用户管理页 → 添加用户 → 填写表单保存 → 编辑用户 → 修改保存。

---

### Task 10: 性能监控页面补全（MonitorView）

**Files:**
- Modify: `dbsyncer-web-ui/src/views/monitor/MonitorView.vue`

**说明：** 目前只有一个简单的数据表格，补全为完整的监控面板：ECharts 图表 + 系统状态 + 执行器任务列表 + 日志列表 + 数据管理

- [ ] **Step 1: 重写 MonitorView.vue**

```vue
<template>
  <div class="monitor">
    <!-- 图表区 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :span="16">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-card shadow="never">
              <template #header><span class="card-title">执行器TPS</span></template>
              <v-chart :option="tpsOption" style="height:200px" autoresize />
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="never">
              <template #header><span class="card-title">堆积数据</span></template>
              <v-chart :option="queueOption" style="height:200px" autoresize />
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="never">
              <template #header><span class="card-title">持久化</span></template>
              <v-chart :option="storageOption" style="height:200px" autoresize />
            </el-card>
          </el-col>
        </el-row>
        <el-row :gutter="16" style="margin-top:16px">
          <el-col :span="12">
            <el-card shadow="never">
              <template #header><span class="card-title">CPU(%)</span></template>
              <v-chart :option="cpuOption" style="height:200px" autoresize />
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="never">
              <template #header><span class="card-title">内存(MB)</span></template>
              <v-chart :option="memoryOption" style="height:200px" autoresize />
            </el-card>
          </el-col>
        </el-row>
      </el-col>

      <!-- 右侧系统状态 -->
      <el-col :span="8">
        <el-card shadow="never" class="mb-16">
          <template #header><span class="card-title">系统状态</span></template>
          <div class="progress-bar-item">
            <span>CPU</span>
            <el-progress :percentage="cpuPercent" :color="cpuPercent > 80 ? '#f5222d' : '#165DFF'" />
          </div>
          <div class="progress-bar-item">
            <span>内存</span>
            <el-progress :percentage="memoryPercent" :color="memoryPercent > 80 ? '#f5222d' : '#165DFF'" />
          </div>
          <div class="progress-bar-item">
            <span>磁盘</span>
            <el-progress :percentage="diskPercent" :color="diskPercent > 80 ? '#f5222d' : '#165DFF'" />
          </div>
          <el-divider />
          <table class="metrics-table">
            <tr v-for="m in metrics" :key="m.label">
              <td class="metrics-label">{{ m.label }}</td>
              <td class="metrics-value">{{ m.value }}</td>
            </tr>
          </table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 执行器 & 日志 -->
    <el-row :gutter="16" class="table-row">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-header-bar">
              <span class="card-title">执行器任务</span>
              <el-select v-model="actuatorMetaId" placeholder="选择驱动" clearable size="small" style="width:180px">
                <el-option v-for="m in metaList" :key="m.id" :label="m.mappingName" :value="m.id" />
              </el-select>
            </div>
          </template>
          <el-table :data="actuatorData" stripe size="small">
            <el-table-column type="index" width="50" />
            <el-table-column prop="sourceTableName" label="数据源表" />
            <el-table-column label="结果" width="120">
              <template #default="{ row }">
                <el-tag v-if="row.successTotal" type="success" size="small">成功 {{ row.successTotal }}</el-tag>
                <el-tag v-if="row.failTotal" type="danger" size="small">失败 {{ row.failTotal }}</el-tag>
                <span v-if="!row.successTotal && !row.failTotal">--</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-header-bar">
              <span class="card-title">日志</span>
              <el-button size="small" @click="handleClearLog">清空日志</el-button>
            </div>
          </template>
          <el-table :data="logData" stripe size="small" max-height="280">
            <el-table-column type="index" width="50" />
            <el-table-column prop="message" label="日志内容" show-overflow-tooltip />
            <el-table-column prop="createTime" label="时间" width="160" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 同步数据管理 -->
    <el-row :gutter="16" style="margin-top:16px">
      <el-col :span="24">
        <el-card shadow="never">
          <template #header>
            <div class="card-header-bar">
              <span class="card-title">同步数据</span>
              <el-button size="small" @click="handleClearData">清空数据</el-button>
            </div>
          </template>
          <el-table :data="dataItems" stripe size="small" v-loading="dataLoading">
            <el-table-column type="index" width="50" />
            <el-table-column prop="targetTableName" label="目标表" width="150" />
            <el-table-column prop="event" label="事件" width="100" />
            <el-table-column label="结果" width="80">
              <template #default="{ row }">
                <el-tag :type="row.success ? 'success' : 'danger'" size="small">
                  {{ row.success ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="error" label="异常" show-overflow-tooltip />
            <el-table-column prop="createTime" label="时间" width="170" />
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button type="danger" link size="small" @click="handleRemoveData(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="dataPageNum"
            :total="dataTotal"
            :page-size="20"
            layout="prev, pager, next"
            size="small"
            style="margin-top:12px; justify-content:flex-end"
            @current-change="loadData"
          />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { queryData, queryLog, clearData, clearLog, getMetric, syncMonitor } from '@/api/monitor'
import { searchMapping } from '@/api/mapping'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

use([LineChart, PieChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const dataItems = ref<any[]>([])
const dataTotal = ref(0)
const dataPageNum = ref(1)
const dataLoading = ref(false)
const logData = ref<any[]>([])
const actuatorData = ref<any[]>([])
const metaList = ref<any[]>([])
const actuatorMetaId = ref('')

const cpuPercent = ref(0)
const memoryPercent = ref(0)
const diskPercent = ref(0)
const metrics = ref<{ label: string; value: string }[]>([])

const emptyLineOption = (): any => ({
  xAxis: { type: 'category', data: [] },
  yAxis: { type: 'value' },
  series: [{ type: 'line', data: [] }],
})
const tpsOption = ref(emptyLineOption())
const queueOption = ref(emptyLineOption())
const storageOption = ref(emptyLineOption())
const cpuOption = ref(emptyLineOption())
const memoryOption = ref(emptyLineOption())

async function loadData() {
  dataLoading.value = true
  try {
    const res: any = await queryData({ pageNum: dataPageNum.value, pageSize: 20 })
    if (res?.data) {
      dataItems.value = res.data.data || []
      dataTotal.value = res.data.total || 0
    }
  } catch { /* ignore */ } finally { dataLoading.value = false }
}

async function loadLog() {
  try {
    const res: any = await queryLog({ pageNum: 1, pageSize: 50 })
    if (res?.data) logData.value = res.data.data || []
  } catch { /* ignore */ }
}

async function loadMetric() {
  try {
    const res: any = await getMetric()
    if (res?.data) {
      const d = res.data
      cpuPercent.value = Math.round(d.cpu?.totalPercent || 0)
      memoryPercent.value = Math.round((d.memory?.jvmUsed || 0) / (d.memory?.jvmTotal || 1) * 100)
      diskPercent.value = Math.round(d.disk?.totalPercent || 0)
      metrics.value = [
        { label: 'JVM 已用内存', value: (d.memory?.jvmUsed || '--') + ' / ' + (d.memory?.jvmTotal || '--') + ' MB' },
        { label: '活动线程', value: String(d.threadsLive ?? '--') },
        { label: '线程峰值', value: String(d.threadsPeak ?? '--') },
        { label: '磁盘总量', value: (d.disk?.total || '--') + ' GB' },
      ]
    }
  } catch { /* ignore */ }
}

async function loadMetaList() {
  try {
    const res: any = await searchMapping({ pageNum: 1, pageSize: 100 })
    if (res?.data) metaList.value = res.data.data || []
  } catch { /* ignore */ }
}

function handleClearLog() {
  ElMessageBox.confirm('确定清空所有日志？', '提示', { type: 'warning' }).then(async () => {
    await clearLog()
    ElMessage.success('已清空')
    loadLog()
  }).catch(() => {})
}

function handleClearData() {
  ElMessageBox.confirm('确定清空所有同步数据？', '提示', { type: 'warning' }).then(async () => {
    await syncMonitor()
    ElMessage.success('已清空')
    loadData()
  }).catch(() => {})
}

function handleRemoveData(row: any) {
  ElMessageBox.confirm('确定删除该条记录？', '提示', { type: 'warning' }).then(async () => {
    await clearData(row.id)
    ElMessage.success('删除成功')
    loadData()
  }).catch(() => {})
}

onMounted(() => {
  loadData()
  loadLog()
  loadMetric()
  loadMetaList()
})
</script>

<style scoped>
.monitor { }
.card-title { font-size: 14px; font-weight: 600; }
.card-header-bar { display: flex; justify-content: space-between; align-items: center; }
.chart-row { margin-bottom: 16px; }
.table-row { margin-bottom: 16px; }
.mb-16 { margin-bottom: 16px; }
.progress-bar-item { margin-bottom: 12px; }
.progress-bar-item span { display: block; font-size: 13px; color: #595959; margin-bottom: 4px; }
.metrics-table { width: 100%; font-size: 13px; }
.metrics-table tr { border-bottom: 1px solid #f0f0f0; }
.metrics-table td { padding: 6px 0; }
.metrics-label { color: #8c8c8c; }
.metrics-value { text-align: right; color: #262626; font-weight: 500; }
</style>
```

- [ ] **Step 2: 验证监控页**

点击侧边栏「性能监控」，确认 5 个图表区域、右侧系统状态进度条、执行器/日志/数据表格均正常渲染。

---

### Task 11: 已有页面微调

**Files:**
- Modify: `dbsyncer-web-ui/src/views/login/LoginView.vue`

- [ ] **Step 1: 登录页标题改为 DBSyncer**

Modify: `dbsyncer-web-ui/src/views/login/LoginView.vue`

将 `<h2>武汉互创联合科技</h2>` 改为：

```html
<h2>DBSyncer</h2>
```

- [ ] **Step 2: 验证**

确认登录页标题显示为 "DBSyncer"。

---

### Task 12: 验证并 commit

- [ ] **Step 1: 全局验证**

```bash
cd dbsyncer-web-ui && npm run dev
```

检查所有 8 个菜单项可点击、页面不白屏、控制台无报错。

- [ ] **Step 2: commit 所有改动**

```bash
cd /Users/work2021/DataCenterRespo/hc-dbsync
git add dbsyncer-web-ui/
git commit -m "feat: 补全 dbsyncer-web-ui 前端页面，统一 ui_bak 风格

- 全局布局改为浅色侧边栏 + #165DFF 主题色
- 新建插件管理/配置管理/系统配置页面
- 新建连接器 Add/Edit、用户 Add/Edit 页面
- 用 ECharts 重建仪表盘和性能监控页面
- 路由补全至 13 条，8 个菜单项
- 新增 echarts/vue-echarts 依赖"
```
