<template>
  <el-container class="layout">
    <el-aside width="220px">
      <div class="logo">DBSyncer</div>
      <el-menu :default-active="activeMenu" router background-color="#304156" text-color="#bfcbd9" active-text-color="#409EFF">
        <el-menu-item index="/">
          <el-icon><DataAnalysis /></el-icon><span>首页</span>
        </el-menu-item>
        <el-menu-item index="/connectors">
          <el-icon><Connection /></el-icon><span>连接器</span>
        </el-menu-item>
        <el-menu-item index="/mappings">
          <el-icon><Link /></el-icon><span>同步任务</span>
        </el-menu-item>
        <el-menu-item index="/monitor">
          <el-icon><Monitor /></el-icon><span>监控</span>
        </el-menu-item>
        <el-menu-item index="/tasks">
          <el-icon><List /></el-icon><span>任务</span>
        </el-menu-item>
        <el-menu-item index="/users">
          <el-icon><User /></el-icon><span>用户</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header>
        <span class="user-info">{{ authStore.username }}</span>
        <el-button text @click="handleLogout">退出</el-button>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const activeMenu = computed(() => route.path)

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout { height: 100vh; }
.el-aside { background-color: #304156; overflow-x: hidden; }
.logo { color: #fff; font-size: 20px; font-weight: bold; text-align: center; padding: 16px 0; }
.el-header { background: #fff; border-bottom: 1px solid #e6e6e6; display: flex; align-items: center; justify-content: flex-end; gap: 12px; }
.el-main { background: #f0f2f5; padding: 20px; }
.user-info { font-size: 14px; color: #606266; }
</style>
