import { apiGet, apiPost } from '@/utils/request'
import type { UserVO } from '@/types/api'

export function login(usernameOrEmail: string, password: string) {
  return apiPost<UserVO>('/auth/login', { usernameOrEmail, password })
}

export function fetchMe() {
  return apiGet<UserVO>('/auth/me')
}

export function logout() {
  return apiPost<void>('/auth/logout')
}
