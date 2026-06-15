import request from '@/utils/request'

export function searchTableGroup(query: Record<string, any>) {
  return request({ url: '/tableGroup/search', method: 'post', params: query })
}

export function addTableGroup(data: Record<string, any>) {
  return request({ url: '/tableGroup/add', method: 'post', params: data })
}

export function removeTableGroup(id: string) {
  return request({ url: '/tableGroup/remove', method: 'post', params: { id } })
}
