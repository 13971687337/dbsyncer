import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/',
    component: () => import('@/views/layout/MainLayout.vue'),
    children: [
      { path: '', name: 'Dashboard', component: () => import('@/views/dashboard/DashboardView.vue'), meta: { title: '首页' } },
      { path: 'connectors', name: 'Connectors', component: () => import('@/views/connector/ConnectorList.vue'), meta: { title: '连接器' } },
      { path: 'mappings', name: 'Mappings', component: () => import('@/views/mapping/MappingList.vue'), meta: { title: '同步任务' } },
      { path: 'mappings/:id', name: 'MappingEdit', component: () => import('@/views/mapping/MappingEdit.vue'), meta: { title: '编辑任务' } },
      { path: 'monitor', name: 'Monitor', component: () => import('@/views/monitor/MonitorView.vue'), meta: { title: '监控' } },
      { path: 'tasks', name: 'Tasks', component: () => import('@/views/task/TaskList.vue'), meta: { title: '任务' } },
      { path: 'users', name: 'Users', component: () => import('@/views/user/UserList.vue'), meta: { title: '用户' } },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
