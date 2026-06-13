import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/',
    component: () => import('@/views/layout/MainLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', name: 'Dashboard', component: () => import('@/views/dashboard/DashboardView.vue') },
      { path: 'connectors', name: 'Connectors', component: () => import('@/views/connector/ConnectorList.vue') },
      { path: 'mappings', name: 'Mappings', component: () => import('@/views/mapping/MappingList.vue') },
      { path: 'mappings/:id', name: 'MappingEdit', component: () => import('@/views/mapping/MappingEdit.vue') },
      { path: 'monitor', name: 'Monitor', component: () => import('@/views/monitor/MonitorView.vue') },
      { path: 'tasks', name: 'Tasks', component: () => import('@/views/task/TaskList.vue') },
      { path: 'users', name: 'Users', component: () => import('@/views/user/UserList.vue') },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, _from, next) => {
  const authStore = useAuthStore()
  if (to.meta.requiresAuth === false) {
    next()
  } else if (authStore.isAuthenticated) {
    next()
  } else {
    next('/login')
  }
})

export default router
