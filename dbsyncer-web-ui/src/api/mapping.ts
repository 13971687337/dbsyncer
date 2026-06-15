import request from '@/utils/request'

export function searchMapping(query: { pageNum: number; pageSize: number }) {
  return request({ url: '/mapping/search', method: 'post', params: query })
}

export function addMapping(data: Record<string, any>) {
  return request({ url: '/mapping/add', method: 'post', params: data })
}

export function editMapping(data: Record<string, any>) {
  return request({ url: '/mapping/edit', method: 'post', params: data })
}

export function removeMapping(id: string) {
  return request({ url: '/mapping/remove', method: 'post', params: { id } })
}

export function startMapping(id: string) {
  return request({ url: '/mapping/start', method: 'post', params: { id } })
}

export function stopMapping(id: string) {
  return request({ url: '/mapping/stop', method: 'post', params: { id } })
}
