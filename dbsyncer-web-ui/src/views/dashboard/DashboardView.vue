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
import { ref, computed, onMounted } from 'vue'
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

.connector-card { display: flex; justify-content: space-between; align-items: center; padding: 10px 0; border-bottom: 1px solid #f0f0f0; }
.connector-card:last-child { border-bottom: none; }
.connector-card-left { display: flex; flex-direction: column; gap: 2px; overflow: hidden; }
.connector-name { font-size: 14px; font-weight: 500; }
.connector-url { font-size: 12px; color: #8c8c8c; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.empty-block { text-align: center; padding: 40px 0; color: #bfbfbf; }
</style>
