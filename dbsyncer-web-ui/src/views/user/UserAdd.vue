<template>
  <div class="page">
    <el-card shadow="never">
      <template #header><span class="card-title">添加用户</span></template>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" style="max-width:500px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" maxlength="32" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" maxlength="32" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="支持多个邮箱，逗号分隔" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" maxlength="11" />
        </el-form-item>
        <el-form-item label="角色" prop="roleCode">
          <el-select v-model="form.roleCode" placeholder="选择角色">
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
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { addUser } from '@/api/user'

const router = useRouter()
const saving = ref(false)

const form = reactive({
  username: '',
  password: '',
  nickname: '',
  email: '',
  phone: '',
  roleCode: 'user',
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

async function handleSave() {
  saving.value = true
  try {
    await addUser(form as Record<string, any>)
    ElMessage.success('添加成功')
    router.push('/users')
  } catch { /* ignore */ } finally { saving.value = false }
}
</script>

<style scoped>
.card-title { font-size: 15px; font-weight: 600; }
</style>
