import request from '@/utils/request'

export function searchConnector(query: { pageNum: number; pageSize: number }) {
  return request({ url: '/connector/search', method: 'post', params: query })
}

export function getConnectorTypeAll() {
  return request({ url: '/connector/getConnectorTypeAll', method: 'get' })
}

export function getConnectorPosition(id: string) {
  return request({ url: '/connector/getPosition', method: 'get', params: { id } })
}

export function addConnector(data: Record<string, any>) {
  return request({ url: '/connector/add', method: 'post', params: data })
}

export function editConnector(data: Record<string, any>) {
  return request({ url: '/connector/edit', method: 'post', params: data })
}

export function removeConnector(id: string) {
  return request({ url: '/connector/remove', method: 'post', params: { id } })
}

export function testConnector(id: string) {
  return request({ url: '/connector/test', method: 'post', params: { id } })
}

export function getConnectorList() {
  return request({ url: '/connector/list', method: 'get' })
}
