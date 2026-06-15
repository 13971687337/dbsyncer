import request from '@/utils/request'

export function searchTask(query: Record<string, any>) {
  return request({ url: '/task/list', method: 'post', params: query })
}

export function startTask(id: string) {
  return request({ url: '/task/start', method: 'post', params: { taskId: id } })
}

export function stopTask(id: string) {
  return request({ url: '/task/stop', method: 'post', params: { taskId: id } })
}

export function deleteTask(id: string) {
  return request({ url: '/task/delete', method: 'get', params: { taskId: id } })
}
