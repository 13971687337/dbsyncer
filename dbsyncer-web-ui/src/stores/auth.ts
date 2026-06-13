import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '@/api'

export const useAuthStore = defineStore('auth', () => {
  const isAuthenticated = ref(false)
  const username = ref('')

  async function login(user: string, pwd: string) {
    const params = new URLSearchParams()
    params.append('username', user)
    params.append('password', pwd)
    const res = await api.post('/login', params)
    if (res.data && res.data.success) {
      isAuthenticated.value = true
      username.value = user
      return true
    }
    return false
  }

  async function logout() {
    await api.post('/logout')
    isAuthenticated.value = false
    username.value = ''
  }

  async function checkAuth() {
    try {
      await api.get('/monitor')
      isAuthenticated.value = true
    } catch {
      isAuthenticated.value = false
    }
  }

  return { isAuthenticated, username, login, logout, checkAuth }
})
