import request from '@/utils/request'

export function getNotifyConfig() {
  return request({ url: '/system/notify/config', method: 'get' })
}

export function saveNotifyConfig(data: Record<string, any>) {
  return request({ url: '/system/notify/config', method: 'post', params: data })
}

export function testNotify() {
  return request({ url: '/system/notify/test', method: 'post' })
}
