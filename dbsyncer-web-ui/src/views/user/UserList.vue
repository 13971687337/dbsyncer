<template>
  <div>
    <div class="toolbar"><h3>用户管理</h3></div>
    <el-table :data="items" v-loading="loading" stripe>
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="roleCode" label="角色" width="150" />
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleRemove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api'

const loading = ref(false)
const items = ref<any[]>([])

onMounted(async () => {
  loading.value = true
  try {
    const res = await api.post('/user/search', 'pageNum=1&pageSize=50')
    if (res.data?.data) items.value = res.data.data.data || []
  } finally { loading.value = false }
})

function handleEdit(row: any) { /* TODO */ }
function handleRemove(row: any) {
  ElMessageBox.confirm('确定删除?', '提示', { type: 'warning' }).then(async () => {
    await api.post('/user/remove', null, { params: { id: row.id } })
    ElMessage.success('删除成功')
  })
}
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
</style>
