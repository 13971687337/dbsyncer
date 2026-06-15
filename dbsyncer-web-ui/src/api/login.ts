import request from '@/utils/request'

export function login(username: string, password: string) {
  return request({
    url: '/login',
    headers: { isToken: false },
    method: 'post',
    data: { username, password },
  })
}

export function getInfo() {
  return request({
    url: '/index/getInfo',
    method: 'get',
  })
}

export function logout() {
  return request({
    url: '/logout',
    method: 'post',
  })
}
