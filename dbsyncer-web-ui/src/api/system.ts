import request from '@/utils/request'

export function getSystemInfo() {
  return request({ url: '/system', method: 'get' })
}

export function editSystem(data: Record<string, any>) {
  return request({ url: '/system/edit', method: 'post', params: data })
}

export function generateRSA() {
  return request({ url: '/system/generateRSA', method: 'post' })
}
