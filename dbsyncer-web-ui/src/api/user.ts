import request from '@/utils/request'

export function searchUser(query: Record<string, any>) {
  return request({ url: '/user/search', method: 'post', params: query })
}

export function removeUser(id: string) {
  return request({ url: '/user/remove', method: 'post', params: { id } })
}
