import request from '@/utils/request'

export function searchUser(query: Record<string, any>) {
  return request({ url: '/user/search', method: 'post', params: query })
}

export function addUser(data: Record<string, any>) {
  return request({ url: '/user/add', method: 'post', params: data })
}

export function editUser(data: Record<string, any>) {
  return request({ url: '/user/edit', method: 'post', params: data })
}

export function getUserInfo(username: string) {
  return request({ url: '/user/getUserInfo.json', method: 'get', params: { username } })
}

export function removeUser(id: string) {
  return request({ url: '/user/remove', method: 'post', params: { id } })
}
