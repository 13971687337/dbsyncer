import request from '@/utils/request'

export function getConfig() {
  return request({ url: '/config', method: 'get' })
}

export function uploadConfig(data: FormData) {
  return request({ url: '/config/upload', method: 'post', data, headers: { 'Content-Type': 'multipart/form-data' } })
}
