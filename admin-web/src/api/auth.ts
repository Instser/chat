import { apiGet, apiPost, apiPut } from '@/utils/request'
import type { AdminUserVO } from '@/types/api'

/** 管理员登录（与前台共用 t_user 账号，但会话独立：写入 admin-token Cookie，不影响前台 auth-token） */
export function login(username: string, password: string) {
  return apiPost<AdminUserVO>('/admin/auth/login', { username, password })
}

/** 获取当前登录管理员；未登录时后端返回 HTTP 401 */
export function fetchMe() {
  return apiGet<AdminUserVO>('/admin/auth/me')
}

/** 退出登录，清除 admin-token Cookie */
export function logout() {
  return apiPost<void>('/admin/auth/logout')
}

/** 修改当前管理员密码 */
export function changePassword(oldPassword: string, newPassword: string) {
  return apiPut<void>('/admin/auth/change-password', { oldPassword, newPassword })
}
