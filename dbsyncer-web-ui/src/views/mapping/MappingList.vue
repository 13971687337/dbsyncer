<template>
  <div>
    <div class="toolbar">
      <h3>同步任务</h3>
      <el-button type="primary" @click="handleAdd">新增任务</el-button>
    </div>
    <el-table :data="items" v-loading="loading" stripe>
      <el-table-column prop="name" label="任务名称" />
      <el-table-column label="数据源" width="180">
        <template #default="{ row }">
          <div class="connector-cell">
            <img :src="`/img/${row.sourceConnector?.config?.connectorType || ''}.png`" class="connector-icon" alt="" @error="onImgError" />
            <span>{{ row.sourceConnector?.name || row.sourceConnectorName }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="目标源" width="180">
        <template #default="{ row }">
          <div class="connector-cell">
            <img :src="`/img/${row.targetConnector?.config?.connectorType || ''}.png`" class="connector-icon" alt="" @error="onImgError" />
            <span>{{ row.targetConnector?.name || row.targetConnectorName }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="model" label="同步方式" width="100" />
      <el-table-column label="操作" width="280">
        <template #default="{ row }">
          <el-button size="small" :type="row.state === 1 ? 'warning' : 'success'" @click="row.state === 1 ? handleStop(row) : handleStart(row)">
            {{ row.state === 1 ? '停止' : '启动' }}
          </el-button>
          <el-button size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleRemove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="pageNum" :total="total" :page-size="10" layout="prev, pager, next" @current-change="loadData" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, getCurrentInstance } from 'vue'
import { useRouter } from 'vue-router'
import { searchMapping, startMapping, stopMapping, removeMapping } from '@/api/mapping'

const router = useRouter()
const { proxy } = getCurrentInstance() as any
const loading = ref(false)
const items = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)

async function loadData() {
  loading.value = true
  try {
    const res: any = await searchMapping({ pageNum: pageNum.value, pageSize: 10 })
    if (res?.data) {
      items.value = res.data.data || []
      total.value = res.data.total || 0
    }
  } finally { loading.value = false }
}

function handleAdd() { router.push('/mappings/add') }
function handleEdit(row: any) { router.push('/mappings/' + row.id) }

function handleStart(row: any) {
  startMapping(row.id).then(() => {
    loadData()
    proxy.$modal.msgSuccess('启动成功')
  }).catch(() => {})
}

function handleStop(row: any) {
  stopMapping(row.id).then(() => {
    loadData()
    proxy.$modal.msgSuccess('停止成功')
  }).catch(() => {})
}

function handleRemove(row: any) {
  proxy.$modal.confirm('确定删除该任务?').then(() => {
    return removeMapping(row.id)
  }).then(() => {
    loadData()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

onMounted(loadData)

function onImgError(e: Event) {
  (e.target as HTMLImageElement).style.display = 'none'
}
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.connector-cell { display: flex; align-items: center; gap: 6px; }
.connector-icon { width: 20px; height: 20px; flex-shrink: 0; }
</style>
