import axios from 'axios'

const api = axios.create({
  baseURL: '',
  withCredentials: true,
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
})

api.interceptors.response.use(
  (response) => {
    const data = response.data
    if (data && data.success === false) {
      return Promise.reject(new Error(data.message || 'Request failed'))
    }
    return response
  },
  (error) => {
    if (error.response?.status === 401) {
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export interface RestResult<T = any> {
  success: boolean
  data: T
  message: string
  status: number
}

export interface PagingData<T = any> {
  total: number
  pageNum: number
  pageSize: number
  data: T[]
}

export default api
