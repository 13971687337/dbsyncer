<template>
  <div>
    <div class="toolbar">
      <h3>同步任务</h3>
      <el-button type="primary" @click="handleAdd">新增任务</el-button>
    </div>
    <el-table :data="items" v-loading="loading" stripe>
      <el-table-column prop="name" label="任务名称" />
      <el-table-column prop="sourceConnectorName" label="数据源" width="150" />
      <el-table-column prop="targetConnectorName" label="目标源" width="150" />
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
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api'

const router = useRouter()
const loading = ref(false)
const items = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)

async function loadData() {
  loading.value = true
  try {
    const params = new URLSearchParams()
    params.append('pageNum', String(pageNum.value))
    params.append('pageSize', '10')
    const res = await api.post('/mapping/search', params)
    if (res.data?.data) {
      items.value = res.data.data.data || []
      total.value = res.data.data.total || 0
    }
  } finally { loading.value = false }
}

function handleAdd() { router.push('/mappings/add') }
function handleEdit(row: any) { router.push('/mappings/' + row.id) }
function handleStart(row: any) { api.post('/mapping/start', null, { params: { id: row.id } }).then(() => { ElMessage.success('启动成功'); loadData() }) }
function handleStop(row: any) { api.post('/mapping/stop', null, { params: { id: row.id } }).then(() => { ElMessage.success('停止成功'); loadData() }) }
function handleRemove(row: any) {
  ElMessageBox.confirm('确定删除?', '提示', { type: 'warning' }).then(async () => {
    await api.post('/mapping/remove', null, { params: { id: row.id } })
    ElMessage.success('删除成功')
    loadData()
  })
}

onMounted(loadData)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
</style>
