<template>
  <div>
    <h3>Dashboard</h3>
    <el-row :gutter="16">
      <el-col :span="8">
        <el-card><h4>CPU Usage</h4><div class="metric">{{ cpu }}%</div></el-card>
      </el-col>
      <el-col :span="8">
        <el-card><h4>Memory</h4><div class="metric">{{ memory }}</div></el-card>
      </el-col>
      <el-col :span="8">
        <el-card><h4>Threads</h4><div class="metric">{{ threads }}</div></el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import api from '@/api'

const cpu = ref('--')
const memory = ref('--')
const threads = ref('--')

onMounted(async () => {
  try {
    const res = await api.get('/monitor/metric')
    if (res.data?.data) {
      const d = res.data.data
      cpu.value = (d.cpu?.totalPercent || 0) + '%'
      memory.value = (d.memory?.jvmUsed || '--') + ' / ' + (d.memory?.jvmTotal || '--')
      threads.value = (d.threadsLive || 0) + ' / ' + (d.threadsPeak || 0)
    }
  } catch { /* dashboard load failed */ }
})
</script>

<style scoped>
.metric { font-size: 28px; font-weight: bold; color: #409EFF; margin-top: 8px; }
</style>
