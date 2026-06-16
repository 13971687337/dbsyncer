<template>
  <div class="monitor">
    <!-- 健康概览 -->
    <el-card class="health-overview" style="margin-bottom: 20px">
      <template #header>
        <span>同步健康概览</span>
      </template>
      <div style="display: flex; gap: 16px; flex-wrap: wrap">
        <div v-for="(status, metaId) in healthMap" :key="metaId"
             style="display: flex; align-items: center; gap: 8px; padding: 8px 16px; border: 1px solid #e4e7ed; border-radius: 8px">
          <span :style="{ width: '12px', height: '12px', borderRadius: '50%', display: 'inline-block', background: statusColor(status) }" />
          <span style="font-size: 14px">{{ mappingNames[metaId] || metaId }}</span>
        </div>
        <div v-if="Object.keys(healthMap).length === 0" style="color: #909399; font-size: 14px">
          暂无运行的同步任务
        </div>
      </div>
    </el-card>

    <!-- 表级队列深度 -->
    <el-card class="monitor-section" style="margin-bottom: 20px">
      <template #header>表级队列深度 (Top 20)</template>
      <v-chart :option="queueDepthOption" style="height: 300px" autoresize />
    </el-card>

    <!-- 事件吞吐趋势 -->
    <el-card class="monitor-section" style="margin-bottom: 20px">
      <template #header>事件吞吐趋势</template>
      <v-chart :option="throughputOption" style="height: 300px" autoresize />
    </el-card>

    <!-- 错误日志 -->
    <el-card class="monitor-section">
      <template #header>错误日志</template>
      <div style="max-height: 300px; overflow-y: auto; font-family: monospace; font-size: 12px">
        <div v-for="(log, idx) in errorLogs" :key="idx" style="padding: 4px 0; border-bottom: 1px solid #ebeef5">
          <span style="color: #909399">{{ log.time }}</span>
          <span style="color: #F56C6C; margin-left: 12px">{{ log.message }}</span>
        </div>
        <div v-if="errorLogs.length === 0" style="color: #67C23A; text-align: center; padding: 40px">暂无错误</div>
      </div>
    </el-card>

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
import { ref, onMounted, onUnmounted, getCurrentInstance } from 'vue'
import { queryData, queryLog, clearData, clearLog, getMetric, syncMonitor, getHealthOverview, getTableQueueDepths, getThroughputTrend } from '@/api/monitor'
import { searchMapping } from '@/api/mapping'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart, PieChart, BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

use([LineChart, PieChart, BarChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const { proxy } = getCurrentInstance() as any

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

const healthMap = ref<Record<string, string>>({})
const mappingNames = ref<Record<string, string>>({})

const statusColor = (s: string) => {
  const colors: Record<string, string> = { green: '#67C23A', yellow: '#E6A23C', red: '#F56C6C', gray: '#909399' }
  return colors[s] || '#909399'
}

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

// ECharts 仪表盘数据
const queueDepthOption = ref({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
  xAxis: { type: 'category', data: [], axisLabel: { rotate: 45, fontSize: 10 } },
  yAxis: { type: 'value', name: '积压(条)' },
  series: [{ type: 'bar', data: [], itemStyle: { color: '#409EFF' } }]
})

const throughputOption = ref({
  tooltip: { trigger: 'axis' },
  legend: { data: ['INSERT', 'UPDATE', 'DELETE'] },
  grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
  xAxis: { type: 'category', data: Array.from({length: 60}, (_,i) => 60-i + 's前') },
  yAxis: { type: 'value', name: 'events/s' },
  series: [
    { name: 'INSERT', type: 'line', data: Array(60).fill(0), smooth: true, lineStyle: { color: '#67C23A' } },
    { name: 'UPDATE', type: 'line', data: Array(60).fill(0), smooth: true, lineStyle: { color: '#E6A23C' } },
    { name: 'DELETE', type: 'line', data: Array(60).fill(0), smooth: true, lineStyle: { color: '#F56C6C' } }
  ]
})

const errorLogs = ref<{ time: string; message: string }[]>([])

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

function handleRemoveData(row: any) {
  proxy.$modal.confirm('确定删除该条记录？').then(() => {
    return clearData(row.id)
  }).then(() => {
    loadData()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

onMounted(() => {
  loadData()
  loadLog()
  loadMetric()
  loadMetaList()
  loadHealth()
  loadQueueDepth()
  loadThroughput()
})

// 每10秒自动刷新健康状态和仪表盘数据
const healthTimer = setInterval(() => {
  loadHealth()
  loadQueueDepth()
  loadThroughput()
  loadErrorLogs()
}, 10000)

onUnmounted(() => {
  clearInterval(healthTimer)
})

async function loadHealth() {
  try {
    const res: any = await getHealthOverview()
    healthMap.value = res?.data || {}
    // 用 metaList 填充 mappingNames
    if (metaList.value.length > 0) {
      for (const m of metaList.value) {
        mappingNames.value[m.id] = m.mappingName
      }
    }
  } catch (e) {
    // silent
  }
}

async function loadQueueDepth() {
  try {
    const res: any = await getTableQueueDepths()
    const data: Record<string, number> = res?.data || {}
    const entries: [string, number][] = Object.entries(data)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 20)
    ;(queueDepthOption.value.xAxis as any).data = entries.map(e => e[0])
    ;(queueDepthOption.value.series[0] as any).data = entries.map(e => e[1])
  } catch { /* ignore */ }
}

async function loadThroughput() {
  try {
    // 取第一个驱动作为默认选择
    const id = actuatorMetaId.value || metaList.value[0]?.id
    if (!id) return
    const res: any = await getThroughputTrend(id)
    const data = res?.data || {}
    if (data.inserts) throughputOption.value.series[0].data = data.inserts.slice(-60)
    if (data.updates) throughputOption.value.series[1].data = data.updates.slice(-60)
    if (data.deletes) throughputOption.value.series[2].data = data.deletes.slice(-60)
  } catch { /* ignore */ }
}

function loadErrorLogs() {
  // 从已有日志中筛选错误
  errorLogs.value = (logData.value || [])
    .filter((l: any) => l.message && (l.message.includes('ERROR') || l.message.includes('异常') || l.message.includes('失败')))
    .slice(0, 50)
    .map((l: any) => ({ time: l.createTime || '', message: l.message }))
}
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
