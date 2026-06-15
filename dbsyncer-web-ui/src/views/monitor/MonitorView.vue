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
import { ref, onMounted } from 'vue'
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
