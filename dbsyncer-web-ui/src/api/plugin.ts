import request from '@/utils/request'

export function getPlugins() {
  return request({ url: '/plugin', method: 'get' })
}

export function uploadPlugin(data: FormData) {
  return request({ url: '/plugin/upload', method: 'post', data, headers: { 'Content-Type': 'multipart/form-data' } })
}
