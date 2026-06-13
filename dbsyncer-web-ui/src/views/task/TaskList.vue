<template>
  <div>
    <div class="toolbar"><h3>任务管理</h3></div>
    <el-table :data="items" v-loading="loading" stripe>
      <el-table-column prop="taskName" label="任务名称" />
      <el-table-column prop="connectorType" label="连接器类型" width="120" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button size="small" type="success" @click="handleStart(row)">启动</el-button>
          <el-button size="small" type="warning" @click="handleStop(row)">停止</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '@/api'

const loading = ref(false)
const items = ref<any[]>([])

onMounted(async () => {
  loading.value = true
  try {
    const res = await api.post('/task/list', 'pageNum=1&pageSize=50')
    if (res.data?.data) items.value = res.data.data.data || []
  } finally { loading.value = false }
})

function handleStart(row: any) { api.post('/task/start?taskId=' + row.id).then(() => ElMessage.success('启动成功')) }
function handleStop(row: any) { api.post('/task/stop?taskId=' + row.id).then(() => ElMessage.success('停止成功')) }
function handleDelete(row: any) { api.get('/task/delete?taskId=' + row.id).then(() => ElMessage.success('删除成功')) }
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
</style>
