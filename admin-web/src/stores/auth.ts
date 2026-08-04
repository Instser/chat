import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { UserVO } from '@/types/api'
import * as authApi from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserVO | null>(null)
  const loaded = ref(false)
  const loading = ref(false)

  const isAuthenticated = computed(() => !!user.value)
  const isAdmin = computed(() => (user.value?.role || '').toLowerCase() === 'admin')
  const displayName = computed(() => user.value?.username || '未登录')

  async function login(usernameOrEmail: string, password: string) {
    loading.value = true
    try {
      const data = await authApi.login(usernameOrEmail, password)
      user.value = data
      loaded.value = true
      if ((data.role || '').toLowerCase() !== 'admin') {
        await authApi.logout().catch(() => undefined)
        user.value = null
        throw new Error('当前账号不是管理员')
      }
      return data
    } finally {
      loading.value = false
    }
  }

  async function restoreSession() {
    if (loaded.value) return user.value
    loading.value = true
    try {
      const data = await authApi.fetchMe()
      if ((data.role || '').toLowerCase() !== 'admin') {
        user.value = null
      } else {
        user.value = data
      }
      loaded.value = true
      return user.value
    } catch {
      user.value = null
      loaded.value = true
      return null
    } finally {
      loading.value = false
    }
  }

  async function logout() {
    try {
      await authApi.logout()
    } finally {
      user.value = null
      loaded.value = true
    }
  }

  function clear() {
    user.value = null
    loaded.value = true
  }

  return {
    user,
    loaded,
    loading,
    isAuthenticated,
    isAdmin,
    displayName,
    login,
    restoreSession,
    logout,
    clear,
  }
})
