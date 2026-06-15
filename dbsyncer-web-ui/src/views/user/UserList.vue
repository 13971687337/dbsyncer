<template>
  <div>
    <div class="toolbar">
      <h3>用户管理</h3>
      <el-button type="primary" @click="router.push('/users/add')">添加用户</el-button>
    </div>
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
import { ref, onMounted, getCurrentInstance } from 'vue'
import { useRouter } from 'vue-router'
import { searchUser, removeUser } from '@/api/user'

const { proxy } = getCurrentInstance()
const router = useRouter()

const loading = ref(false)
const items = ref<any[]>([])

onMounted(async () => {
  loading.value = true
  try {
    const res: any = await searchUser({ pageNum: 1, pageSize: 50 })
    if (res?.data) items.value = res.data.data || []
  } finally { loading.value = false }
})

function loadData() {
  loading.value = true
  searchUser({ pageNum: 1, pageSize: 50 }).then((res: any) => {
    if (res?.data) items.value = res.data.data || []
  }).finally(() => { loading.value = false })
}

function handleEdit(row: any) { router.push('/users/' + encodeURIComponent(row.username) + '/edit') }
function handleRemove(row: any) {
  proxy.$modal.confirm('确定删除该用户?').then(() => {
    return removeUser(row.id)
  }).then(() => {
    loadData()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
</style>
