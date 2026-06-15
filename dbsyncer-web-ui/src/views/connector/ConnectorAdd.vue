<template>
  <div class="page">
    <el-card shadow="never">
      <template #header><span class="card-title">添加连接器</span></template>
      <el-form :model="form" label-width="100px" style="max-width:600px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="64" placeholder="请输入连接名称" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="form.connectorType" placeholder="选择连接器类型" style="width:100%">
            <el-option v-for="t in connectorTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="连接地址" required>
          <el-input v-model="form.url" placeholder="例如: jdbc:mysql://localhost:3306/test" />
        </el-form-item>
        <el-form-item label="用户名" required>
          <el-input v-model="form.username" placeholder="数据库用户名" />
        </el-form-item>
        <el-form-item label="密码" required>
          <el-input v-model="form.password" type="password" placeholder="数据库密码" show-password />
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
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { addConnector, getConnectorTypeAll } from '@/api/connector'

const router = useRouter()
const saving = ref(false)
const connectorTypes = ref<string[]>([])

const form = reactive({
  name: '',
  connectorType: '',
  url: '',
  username: '',
  password: '',
})

onMounted(async () => {
  try {
    const res: any = await getConnectorTypeAll()
    if (res?.data) connectorTypes.value = res.data || []
  } catch { /* ignore */ }
})

async function handleSave() {
  if (!form.name || !form.connectorType || !form.url || !form.username || !form.password) {
    ElMessage.warning('请填写所有必填项')
    return
  }
  saving.value = true
  try {
    await addConnector(form as Record<string, any>)
    ElMessage.success('添加成功')
    router.push('/connectors')
  } catch { /* ignore */ } finally { saving.value = false }
}
</script>

<style scoped>
.card-title { font-size: 15px; font-weight: 600; }
</style>
