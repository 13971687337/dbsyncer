<template>
  <el-container class="layout">
    <el-aside :width="sidebarWidth" class="sidebar">
      <div class="sidebar-logo">
        <span class="sidebar-logo-text">DBSyncer</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        background-color="#ffffff"
        text-color="#595959"
        active-text-color="#165DFF"
        class="sidebar-menu"
      >
        <el-menu-item index="/">
          <el-icon><Odometer /></el-icon><span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/connectors">
          <el-icon><Connection /></el-icon><span>连接管理</span>
        </el-menu-item>
        <el-menu-item index="/mappings">
          <el-icon><Link /></el-icon><span>驱动管理</span>
        </el-menu-item>
        <el-menu-item index="/monitor">
          <el-icon><Monitor /></el-icon><span>性能监控</span>
        </el-menu-item>
        <el-menu-item index="/plugins">
          <el-icon><SetUp /></el-icon><span>插件管理</span>
        </el-menu-item>
        <el-menu-item index="/config">
          <el-icon><Files /></el-icon><span>配置管理</span>
        </el-menu-item>
        <el-menu-item index="/users">
          <el-icon><User /></el-icon><span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/system">
          <el-icon><Setting /></el-icon><span>系统配置</span>
        </el-menu-item>
      </el-menu>
      <div class="sidebar-footer">
        <span class="sidebar-footer-text">Copyright &copy; 2026 DBSyncer</span>
      </div>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-right">
          <el-dropdown trigger="click">
            <span class="header-user">
              <el-icon><UserFilled /></el-icon>
              <span>{{ userStore.nickName || userStore.name }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleEditProfile">修改资料</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">注销</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const sidebarWidth = '240px'
const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/connectors')) return '/connectors'
  if (path.startsWith('/mappings')) return '/mappings'
  if (path.startsWith('/users')) return '/users'
  return path
})

function handleEditProfile() {
  router.push('/users/' + encodeURIComponent(userStore.name) + '/edit')
}

async function handleLogout() {
  await userStore.doLogout()
  router.push('/login')
}
</script>

<style scoped>
.layout { height: 100vh; }

.sidebar {
  background-color: #ffffff;
  border-right: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  padding: 16px 20px;
  gap: 8px;
}
.sidebar-logo-text {
  font-size: 20px;
  font-weight: bold;
  color: #262626;
}

.sidebar-menu {
  flex: 1;
  border-right: none !important;
  overflow-y: auto;
}

.sidebar-menu .el-menu-item {
  margin: 2px 8px;
  border-radius: 6px;
  height: 44px;
  line-height: 44px;
}
.sidebar-menu .el-menu-item:hover {
  background-color: rgba(22, 93, 255, 0.05) !important;
}
.sidebar-menu .el-menu-item.is-active {
  background-color: rgba(22, 93, 255, 0.1) !important;
  font-weight: 500;
}

.sidebar-footer {
  padding: 12px 16px;
  border-top: 1px solid #e8e8e8;
}
.sidebar-footer-text {
  font-size: 12px;
  color: #8c8c8c;
}

.header {
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  height: 56px;
  padding: 0 20px;
}

.header-right {
  display: flex;
  align-items: center;
}

.header-user {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #595959;
  font-size: 14px;
}

.main-content {
  background: #fafafa;
  padding: 20px;
  overflow-y: auto;
}
</style>
