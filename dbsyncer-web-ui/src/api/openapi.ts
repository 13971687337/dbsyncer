import request from '@/utils/request'

export function openApiLogin(username: string, password: string) {
  return request({ url: '/openapi/auth/login', method: 'post', headers: { isToken: false }, data: { username, password } })
}

export function openApiRefresh(token: string) {
  return request({ url: '/openapi/auth/refresh', method: 'post', data: { token } })
}

export function openApiDataSync(data: Record<string, any>) {
  return request({ url: '/openapi/data/sync', method: 'post', data })
}
