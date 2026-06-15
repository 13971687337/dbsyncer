import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, logout as logoutApi, getInfo as getInfoApi } from '@/api/login'
import { getToken, setToken, removeToken } from '@/utils/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(getToken() || '')
  const id = ref<string>('')
  const name = ref<string>('')
  const nickName = ref<string>('')
  const roles = ref<string[]>([])
  const permissions = ref<string[]>([])

  async function login(username: string, password: string): Promise<void> {
    const res = await loginApi(username, password) as any
    setToken(res.data.token)
    token.value = res.data.token
  }

  async function getInfo(): Promise<void> {
    const res = await getInfoApi() as any
    const user = res.data.user
    id.value = user.userId
    name.value = user.userName
    nickName.value = user.nickName || user.userName
    roles.value = res.data.roles || []
    permissions.value = res.data.permissions || []
  }

  async function doLogout(): Promise<void> {
    try {
      await logoutApi()
    } catch {
      // ignore backend errors
    }
    token.value = ''
    roles.value = []
    permissions.value = []
    removeToken()
  }

  function resetState(): void {
    token.value = ''
    roles.value = []
    permissions.value = []
    removeToken()
  }

  return { token, id, name, nickName, roles, permissions, login, getInfo, doLogout, resetState }
})
