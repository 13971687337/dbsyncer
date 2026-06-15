import router from './router'
import { useUserStore } from '@/stores/user'
import { getToken } from '@/utils/auth'
import { useDynamicTitle } from '@/utils/dynamicTitle'
import { ElMessage } from 'element-plus'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { isRelogin } from '@/utils/request'

NProgress.configure({ showSpinner: false })

const whiteList = ['/login']

router.beforeEach((to, _from, next) => {
  NProgress.start()
  useDynamicTitle()

  if (getToken()) {
    if (to.path === '/login') {
      next({ path: '/' })
      NProgress.done()
    } else if (whiteList.includes(to.path)) {
      next()
    } else {
      const userStore = useUserStore()
      if (userStore.roles.length === 0) {
        isRelogin.show = true
        userStore.getInfo().then(() => {
          isRelogin.show = false
          next({ ...to, replace: true })
        }).catch(() => {
          userStore.resetState()
          ElMessage.error('获取用户信息失败，请重新登录')
          next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
          NProgress.done()
        })
      } else {
        next()
      }
    }
  } else {
    if (whiteList.includes(to.path)) {
      next()
    } else {
      next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
      NProgress.done()
    }
  }
})

router.afterEach(() => {
  NProgress.done()
})
