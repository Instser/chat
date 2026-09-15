import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { AdminUserVO } from '@/types/api'
import * as authApi from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<AdminUserVO | null>(null)
  const loaded = ref(false)
  const loading = ref(false)

  const isAuthenticated = computed(() => !!user.value)
  // 管理员会话由服务端签发，能登录即管理员，故与 isAuthenticated 等价
  const isAdmin = computed(() => !!user.value)
  const displayName = computed(() => user.value?.nickname || user.value?.username || '未登录')

  async function login(username: string, password: string) {
    loading.value = true
    try {
      const data = await authApi.login(username, password)
      user.value = data
      loaded.value = true
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
      user.value = data || null
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
