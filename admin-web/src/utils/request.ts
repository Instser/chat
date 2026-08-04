import axios, { type AxiosError, type AxiosRequestConfig } from 'axios'
import type { ApiResult } from '@/types/api'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  withCredentials: true,
})

request.interceptors.response.use(
  (response) => {
    const payload = response.data as ApiResult
    // 部分接口在 HTTP 200 内返回业务 code
    if (payload && typeof payload.code === 'number' && payload.code !== 200) {
      if (payload.code === 401) {
        return Promise.reject(Object.assign(new Error(payload.message || '未登录'), { code: 401, payload }))
      }
      if (payload.code === 403) {
        return Promise.reject(Object.assign(new Error(payload.message || '无权限'), { code: 403, payload }))
      }
      return Promise.reject(Object.assign(new Error(payload.message || '请求失败'), { code: payload.code, payload }))
    }
    return response
  },
  (error: AxiosError<ApiResult>) => {
    const status = error.response?.status
    const message = error.response?.data?.message || error.message || '网络错误'
    if (status === 401) {
      return Promise.reject(Object.assign(new Error(message), { code: 401 }))
    }
    if (status === 403) {
      return Promise.reject(Object.assign(new Error(message), { code: 403 }))
    }
    return Promise.reject(Object.assign(new Error(message), { code: status || -1 }))
  },
)

export async function apiGet<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const res = await request.get<ApiResult<T>>(url, config)
  return res.data.data
}

export async function apiPost<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const res = await request.post<ApiResult<T>>(url, data, config)
  return res.data.data
}

export async function apiPut<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const res = await request.put<ApiResult<T>>(url, data, config)
  return res.data.data
}

export async function apiPatch<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const res = await request.patch<ApiResult<T>>(url, data, config)
  return res.data.data
}

export async function apiDelete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const res = await request.delete<ApiResult<T>>(url, config)
  return res.data.data
}

export function showApiError(error: unknown, fallback = '操作失败') {
  const msg = error instanceof Error ? error.message : fallback
  ElMessage.error(msg)
  return msg
}

export default request
