<template>
  <div class="page">
    <el-card shadow="never" v-loading="loading">
      <template #header><span class="card-title">编辑连接器</span></template>
      <el-form :model="form" label-width="100px" style="max-width:600px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="64" />
        </el-form-item>
        <el-form-item label="类型">
          <el-input :model-value="form.connectorType" disabled />
        </el-form-item>
        <el-form-item label="连接地址" required>
          <el-input v-model="form.url" />
        </el-form-item>
        <el-form-item label="用户名" required>
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="密码" required>
          <el-input v-model="form.password" type="password" placeholder="留空则不修改" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
          <el-button @click="router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { editConnector, getConnectorPosition } from '@/api/connector'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const saving = ref(false)

const form = reactive({
  id: '',
  name: '',
  connectorType: '',
  url: '',
  username: '',
  password: '',
})

onMounted(async () => {
  try {
    const res: any = await getConnectorPosition(route.params.id as string)
    if (res?.data) {
      const d = res.data
      form.id = d.id || ''
      form.name = d.name || ''
      form.connectorType = d.config?.connectorType || ''
      form.url = d.config?.url || ''
      form.username = d.config?.username || ''
    }
  } catch { /* ignore */ } finally { loading.value = false }
})

async function handleSave() {
  saving.value = true
  try {
    const data: Record<string, any> = { ...form }
    if (!data.password) delete data.password
    await editConnector(data)
    ElMessage.success('修改成功')
    router.push('/connectors')
  } catch { /* ignore */ } finally { saving.value = false }
}
</script>

<style scoped>
.card-title { font-size: 15px; font-weight: 600; }
</style>
