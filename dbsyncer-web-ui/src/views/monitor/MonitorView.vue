<template>
  <div>
    <h3>监控面板</h3>
    <el-tabs>
      <el-tab-pane label="同步数据">
        <el-table :data="dataItems" v-loading="loading" stripe>
          <el-table-column prop="id" label="ID" width="200" />
          <el-table-column prop="tableName" label="表名" width="150" />
          <el-table-column prop="event" label="事件" width="100" />
          <el-table-column prop="createTime" label="时间" width="180" />
          <el-table-column prop="error" label="错误信息" />
        </el-table>
        <el-pagination v-model:current-page="pageNum" :total="total" :page-size="20" layout="prev, pager, next" @current-change="loadData" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import api from '@/api'

const loading = ref(false)
const dataItems = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)

async function loadData() {
  loading.value = true
  try {
    const params = new URLSearchParams()
    params.append('pageNum', String(pageNum.value))
    params.append('pageSize', '20')
    const res = await api.post('/monitor/queryData', params)
    if (res.data?.data) {
      dataItems.value = res.data.data.data || []
      total.value = res.data.data.total || 0
    }
  } finally { loading.value = false }
}

onMounted(loadData)
</script>
