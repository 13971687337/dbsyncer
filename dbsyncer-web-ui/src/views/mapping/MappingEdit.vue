<template>
  <div>
    <h3>{{ isNew ? '新增同步任务' : '编辑同步任务' }}</h3>
    <el-form :model="form" label-width="120px">
      <el-form-item label="任务名称"><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="数据源">
        <el-select v-model="form.sourceConnectorId" placeholder="选择数据源">
          <el-option v-for="c in connectors" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="目标源">
        <el-select v-model="form.targetConnectorId" placeholder="选择目标源">
          <el-option v-for="c in connectors" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="同步方式">
        <el-radio-group v-model="form.model">
          <el-radio value="full">全量</el-radio>
          <el-radio value="increment">增量</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item><el-button type="primary" @click="handleSave">保存</el-button></el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '@/api'

const route = useRoute()
const router = useRouter()
const isNew = !route.params.id
const connectors = ref<any[]>([])
const form = reactive({ name: '', sourceConnectorId: '', targetConnectorId: '', model: 'full' })

onMounted(async () => {
  try {
    const res = await api.post('/connector/search', new URLSearchParams({ pageNum: '1', pageSize: '100' }))
    if (res.data?.data?.data) connectors.value = res.data.data.data
  } catch {}
})

async function handleSave() {
  const params = new URLSearchParams()
  Object.entries(form).forEach(([k, v]) => params.append(k, v as string))
  try {
    const url = isNew ? '/mapping/add' : '/mapping/edit'
    await api.post(url, params)
    ElMessage.success('保存成功')
    router.push('/mappings')
  } catch { ElMessage.error('保存失败') }
}
</script>
