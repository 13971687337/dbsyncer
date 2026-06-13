<template>
  <div class="login-container">
    <el-card class="login-card">
      <h2>武汉互创联合科技</h2>
      <el-form ref="formRef" :model="form" :rules="rules">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" prefix-icon="Lock" show-password @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" style="width:100%" @click="handleLogin">登录</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const form = reactive({ username: 'admin', password: 'admin' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  loading.value = true
  try {
    const ok = await authStore.login(form.username, form.password)
    if (ok) { router.push('/'); ElMessage.success('登录成功') }
    else { ElMessage.error('用户名或密码错误') }
  } catch {
    ElMessage.error('登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container { display: flex; align-items: center; justify-content: center; height: 100vh; background: #f0f2f5; }
.login-card { width: 380px; }
.login-card h2 { text-align: center; margin-bottom: 24px; }
</style>
