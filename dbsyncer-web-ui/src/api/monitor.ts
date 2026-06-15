import request from '@/utils/request'

export function queryData(query: Record<string, any>) {
  return request({ url: '/monitor/queryData', method: 'post', params: query })
}

export function queryLog(query: Record<string, any>) {
  return request({ url: '/monitor/queryLog', method: 'post', params: query })
}

export function syncMonitor() {
  return request({ url: '/monitor/sync', method: 'post' })
}

export function clearData(id: string) {
  return request({ url: '/monitor/clearData', method: 'post', params: { id } })
}

export function clearLog() {
  return request({ url: '/monitor/clearLog', method: 'post' })
}

export function getMetric() {
  return request({ url: '/monitor/metric', method: 'get' })
}

export function getDashboard() {
  return request({ url: '/monitor/dashboard', method: 'get' })
}
