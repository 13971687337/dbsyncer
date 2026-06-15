<template>
  <div class="page">
    <el-card shadow="never" v-loading="loading">
      <template #header><span class="card-title">编辑用户</span></template>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" style="max-width:500px">
        <el-form-item label="用户名">
          <el-input :model-value="form.username" disabled />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" placeholder="留空则不修改" show-password />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" maxlength="32" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" maxlength="11" />
        </el-form-item>
        <el-form-item label="角色" prop="roleCode">
          <el-select v-model="form.roleCode">
            <el-option label="管理员" value="admin" />
            <el-option label="普通用户" value="user" />
          </el-select>
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
import request from '@/utils/request'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const saving = ref(false)

const form = reactive({
  username: '',
  password: '',
  nickname: '',
  email: '',
  phone: '',
  roleCode: '',
})

const rules = {
  roleCode: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

onMounted(async () => {
  try {
    const res: any = await request({
      url: '/user/getUserInfo.json',
      method: 'get',
      params: { username: route.params.username },
    })
    if (res?.data) {
      const d = res.data
      form.username = d.username || ''
      form.nickname = d.nickname || ''
      form.email = d.email || ''
      form.phone = d.phone || ''
      form.roleCode = d.roleCode || ''
    }
  } catch { /* ignore */ } finally { loading.value = false }
})

async function handleSave() {
  saving.value = true
  try {
    const data: Record<string, any> = { ...form }
    if (!data.password) delete data.password
    await request({ url: '/user/edit', method: 'post', params: data })
    ElMessage.success('修改成功')
    router.push('/users')
  } catch { /* ignore */ } finally { saving.value = false }
}
</script>

<style scoped>
.card-title { font-size: 15px; font-weight: 600; }
</style>
