import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getToken, removeToken } from '@/utils/auth'
import errorCode from '@/utils/errorCode'

export const isRelogin = { show: false }

const service = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API as string,
  timeout: 10000,
  headers: { 'Content-Type': 'application/json;charset=utf-8' },
})

service.interceptors.request.use(config => {
  const isToken = (config.headers as any)?.isToken === false
  const token = getToken()
  if (token && !isToken) {
    config.headers['Authorization'] = 'Bearer ' + token
  }
  return config
}, error => Promise.reject(error))

service.interceptors.response.use(res => {
  const body = res.data
  const status = body.status || 200
  const msg = errorCode[status] || body.message || errorCode['default']

  if (res.request.responseType === 'blob' || res.request.responseType === 'arraybuffer') {
    return body
  }

  if (status === 401) {
    if (!isRelogin.show) {
      isRelogin.show = true
      ElMessageBox.confirm('登录状态已过期，请重新登录', '系统提示', {
        confirmButtonText: '重新登录',
        cancelButtonText: '取消',
        type: 'warning',
      }).then(() => {
        isRelogin.show = false
        removeToken()
        location.href = '/login'
      }).catch(() => {
        isRelogin.show = false
      })
    }
    return Promise.reject(new Error('无效的会话，或者会话已过期，请重新登录。'))
  } else if (status === 500) {
    ElMessage({ message: msg, type: 'error' })
    return Promise.reject(new Error(msg))
  } else if (!body.success) {
    ElMessage({ message: msg, type: 'warning' })
    return Promise.reject(msg as string)
  }
  return body
}, error => {
  let message = error.message
  if (message === 'Network Error') {
    message = '后端接口连接异常'
  } else if (message.includes('timeout')) {
    message = '系统接口请求超时'
  }
  ElMessage({ message, type: 'error', duration: 5 * 1000 })
  return Promise.reject(error)
})

export default service
