<template>
  <div>
    <div class="toolbar">
      <h3>连接器管理</h3>
      <el-button type="primary" @click="handleAdd">新增连接器</el-button>
    </div>
    <el-table :data="items" v-loading="loading" stripe>
      <el-table-column prop="name" label="名称" />
      <el-table-column label="类型" width="140">
        <template #default="{ row }">
          <div class="type-cell">
            <img :src="`/img/${row.config?.connectorType || ''}.png`" class="type-icon" alt="" @error="onImgError" />
            <span>{{ row.config?.connectorType || row.type }}</span>
          </div>
        </template>
      </el-table-column>
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
import { ref, onMounted, getCurrentInstance } from 'vue'
import { useRouter } from 'vue-router'
import { searchConnector, testConnector, removeConnector } from '@/api/connector'

const { proxy } = getCurrentInstance()
const router = useRouter()
const loading = ref(false)
const items = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)

async function loadData() {
  loading.value = true
  try {
    const res: any = await searchConnector({ pageNum: pageNum.value, pageSize: 10 })
    if (res?.data) {
      items.value = res.data.data || []
      total.value = res.data.total || 0
    }
  } finally { loading.value = false }
}

function handleAdd() { router.push('/connectors/add') }
function handleEdit(row: any) { router.push('/connectors/' + row.id + '/edit') }

function handleTest(row: any) {
  testConnector(row.id).then(() => {
    proxy.$modal.msgSuccess('连接测试成功')
  }).catch(() => {})
}

function handleRemove(row: any) {
  proxy.$modal.confirm('确定删除该连接器?').then(() => {
    return removeConnector(row.id)
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
.type-cell { display: flex; align-items: center; gap: 6px; }
.type-icon { width: 22px; height: 22px; flex-shrink: 0; }
</style>
