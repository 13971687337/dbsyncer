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
      { path: '', name: 'Dashboard', component: () => import('@/views/dashboard/DashboardView.vue'), meta: { title: '仪表盘' } },
      { path: 'connectors', name: 'Connectors', component: () => import('@/views/connector/ConnectorList.vue'), meta: { title: '连接管理' } },
      { path: 'connectors/add', name: 'ConnectorAdd', component: () => import('@/views/connector/ConnectorAdd.vue'), meta: { title: '添加连接' } },
      { path: 'connectors/:id/edit', name: 'ConnectorEdit', component: () => import('@/views/connector/ConnectorEdit.vue'), meta: { title: '编辑连接' } },
      { path: 'mappings', name: 'Mappings', component: () => import('@/views/mapping/MappingList.vue'), meta: { title: '数据同步' } },
      { path: 'mappings/add', name: 'MappingAdd', component: () => import('@/views/mapping/MappingEdit.vue'), meta: { title: '新增同步任务' } },
      { path: 'mappings/:id', name: 'MappingEdit', component: () => import('@/views/mapping/MappingEdit.vue'), meta: { title: '编辑同步任务' } },
      { path: 'monitor', name: 'Monitor', component: () => import('@/views/monitor/MonitorView.vue'), meta: { title: '性能监控' } },
      { path: 'plugins', name: 'Plugins', component: () => import('@/views/plugin/PluginList.vue'), meta: { title: '插件管理' } },
      { path: 'config', name: 'Config', component: () => import('@/views/config/ConfigList.vue'), meta: { title: '配置管理' } },
      { path: 'users', name: 'Users', component: () => import('@/views/user/UserList.vue'), meta: { title: '用户管理' } },
      { path: 'users/add', name: 'UserAdd', component: () => import('@/views/user/UserAdd.vue'), meta: { title: '添加用户' } },
      { path: 'users/:username/edit', name: 'UserEdit', component: () => import('@/views/user/UserEdit.vue'), meta: { title: '编辑用户' } },
      { path: 'system', name: 'SystemConfig', component: () => import('@/views/system/SystemConfig.vue'), meta: { title: '系统配置' } },
      { path: 'notifications', name: 'Notifications', component: () => import('@/views/notify/NotifyView.vue'), meta: { title: '告警通知' } },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
