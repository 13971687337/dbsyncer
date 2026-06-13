<template>
  <div>
    <div class="toolbar">
      <h3>连接器管理</h3>
      <el-button type="primary" @click="handleAdd">新增连接器</el-button>
    </div>
    <el-table :data="items" v-loading="loading" stripe>
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="type" label="类型" width="120" />
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button size="small" @click="handleTest(row)">测试</el-button>
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
    const res = await api.post('/connector/search', params)
    if (res.data?.data) {
      items.value = res.data.data.data || []
      total.value = res.data.data.total || 0
    }
  } finally { loading.value = false }
}

function handleAdd() {
  router.push('/connectors/add')
}
function handleEdit(row: any) { /* TODO */ }
function handleTest(row: any) {
  api.post('/connector/test', null, { params: { id: row.id } }).then(() => ElMessage.success('连接测试成功')).catch(() => ElMessage.error('连接测试失败'))
}
function handleRemove(row: any) {
  ElMessageBox.confirm('确定删除该连接器?', '提示', { type: 'warning' }).then(async () => {
    await api.post('/connector/remove', null, { params: { id: row.id } })
    ElMessage.success('删除成功')
    loadData()
  })
}

onMounted(loadData)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
</style>
