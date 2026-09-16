import { apiDelete, apiGet, apiPatch, apiPost, apiPut } from '@/utils/request'
import type {
  AdminModel,
  AdminModelKey,
  AdminOrder,
  AdminPackage,
  AdminUserSummary,
  LogFile,
  LogTail,
  PageResult,
} from '@/types/api'

export function getDashboardOverview() {
  return apiGet<Record<string, unknown>>('/admin/dashboard/overview')
}

export function getDashboardHealth() {
  return apiGet<Record<string, unknown>>('/admin/dashboard/health')
}

export function listUsers(params: Record<string, unknown>) {
  return apiGet<PageResult<AdminUserSummary>>('/admin/users', { params })
}

export function getUser(userId: string) {
  return apiGet(`/admin/users/${userId}`)
}

export function updateUserStatus(userId: string, status: number, reason: string) {
  return apiPatch(`/admin/users/${userId}/status`, { status, reason })
}

export function updateUserRole(userId: string, role: string, reason: string) {
  return apiPatch(`/admin/users/${userId}/role`, { role, reason })
}

export function listPackages() {
  return apiGet<AdminPackage[]>('/admin/packages')
}

export function createPackage(data: Record<string, unknown>) {
  return apiPost<AdminPackage>('/admin/packages', data)
}

export function updatePackage(id: string, data: Record<string, unknown>) {
  return apiPut<AdminPackage>(`/admin/packages/${id}`, data)
}

export function updatePackageStatus(id: string, status: number, reason: string) {
  return apiPatch<AdminPackage>(`/admin/packages/${id}/status`, { status, reason })
}

export function listModels() {
  return apiGet<AdminModel[]>('/admin/models')
}

/** 新建/编辑模型请求体（weight = 行级轮询权重，1-100，默认 100） */
export interface AdminModelPayload {
  modelCode?: string
  modelName?: string
  provider?: string
  description?: string
  baseUrl?: string
  reason?: string
  weight?: number
}

export function createModel(data: AdminModelPayload) {
  return apiPost<AdminModel>('/admin/models', data)
}

export function updateModel(id: string, data: AdminModelPayload) {
  return apiPut<AdminModel>(`/admin/models/${id}`, data)
}

/** 清零该行模型的调用统计（成功/失败次数、连续失败数、最近错误） */
export function resetModelStats(modelId: string) {
  return apiPost<AdminModel>(`/admin/models/${modelId}/reset-stats`)
}

export function updateModelAvailability(id: string, status: number, reason: string) {
  return apiPatch<AdminModel>(`/admin/models/${id}/availability`, { status, reason })
}

export function updateModelVisibility(id: string, status: number, reason: string) {
  return apiPatch<AdminModel>(`/admin/models/${id}/visibility`, { status, reason })
}

export function updateModelCredential(id: string, data: { baseUrl?: string; apiKey: string; reason?: string }) {
  return apiPut<AdminModel>(`/admin/models/${id}/credential`, data)
}

export function testModel(id: string) {
  return apiPost<Record<string, unknown>>(`/admin/models/${id}/test`)
}

// ===== 模型密钥池（某行模型下的多个 API Key） =====

/** 新增密钥请求体（weight = 轮询权重，1-100，默认 100） */
export interface AdminModelKeyPayload {
  apiKey?: string
  weight?: number
  remark?: string
  status?: number
}

/** 列出该行模型下的全部密钥 */
export function listModelKeys(modelId: string) {
  return apiGet<AdminModelKey[]>(`/admin/models/${modelId}/keys`)
}

/** 为该行模型新增一个密钥 */
export function addModelKey(modelId: string, data: AdminModelKeyPayload) {
  return apiPost<AdminModelKey>(`/admin/models/${modelId}/keys`, data)
}

/** 修改密钥（权重/状态/备注） */
export function updateModelKey(keyId: string, data: AdminModelKeyPayload) {
  return apiPut<AdminModelKey>(`/admin/models/keys/${keyId}`, data)
}

/** 删除密钥 */
export function deleteModelKey(keyId: string) {
  return apiDelete<null>(`/admin/models/keys/${keyId}`)
}

/** 清零某个密钥的调用统计（成功/失败次数、连续失败数、最近错误） */
export function resetModelKeyStats(keyId: string) {
  return apiPost<AdminModelKey>(`/admin/models/keys/${keyId}/reset-stats`)
}

export function listOrders(params: Record<string, unknown>) {
  return apiGet<PageResult<AdminOrder>>('/admin/orders', { params })
}

export function getOrder(orderNo: string) {
  return apiGet(`/admin/orders/${orderNo}`)
}

export function syncOrder(orderNo: string, reason: string) {
  return apiPost(`/admin/orders/${orderNo}/sync`, { reason })
}

export function batchSyncOrders(maxCount: number, reason: string) {
  return apiPost('/admin/orders/sync', { maxCount, reason })
}

export function cancelOrder(orderNo: string, reason: string) {
  return apiPost(`/admin/orders/${orderNo}/cancel`, { reason })
}

export function getPaymentAnalytics(params?: { startDate?: string; endDate?: string }) {
  return apiGet('/admin/payment/analytics', { params })
}

export function listOperationAudits(params: Record<string, unknown>) {
  return apiGet('/admin/audits/operations', { params })
}

export function listPaymentAudits(params: Record<string, unknown>) {
  return apiGet('/admin/audits/payments', { params })
}

export function listOauthAudits(params: Record<string, unknown>) {
  return apiGet('/admin/audits/oauth', { params })
}

export function getOpsVersion() {
  return apiGet('/admin/ops/version')
}

export function getOpsHealth() {
  return apiGet('/admin/ops/health')
}

export function getOpsMetrics() {
  return apiGet('/admin/ops/metrics/summary')
}

export function getOpsMonitoring(range: string = '7d', probeModels = false) {
  return apiGet<Record<string, unknown>>('/admin/ops/monitoring', {
    params: { range, probeModels },
  })
}

/** 手动真实可聊探测全部可用模型（不随自动刷新） */
export function probeOpsModels() {
  return apiGet<Record<string, unknown>>('/admin/ops/monitoring/models-probe')
}

export function getOpsCacheSummary() {
  return apiGet('/admin/ops/cache/summary')
}

export function evictUserPackageCache(reason: string) {
  return apiPost('/admin/ops/cache/user-package/evict-all', { reason })
}

export function listLoggers() {
  return apiGet<Record<string, string>>('/admin/ops/loggers')
}

export function updateLogger(loggerName: string, level: string, reason: string) {
  return apiPut(`/admin/ops/loggers/${encodeURIComponent(loggerName)}`, null, {
    params: { level, reason },
  })
}

export function applyLoggerPreset(preset: string, reason: string) {
  return apiPut('/admin/ops/logging/preset', null, { params: { preset, reason } })
}

export function analyzeError(errorLog: string, contextInfo?: string) {
  return apiPost('/admin/ops/ai/analyze-error', { errorLog, contextInfo })
}

export function resetLogger(loggerName: string, reason: string) {
  return apiDelete(`/admin/ops/loggers/${encodeURIComponent(loggerName)}`, {
    params: { reason },
  })
}

// ===== 日志查看 =====

export function listLogFiles() {
  return apiGet<LogFile[]>('/admin/log-viewer/files')
}

export function tailLog(params: {
  file: string
  lines?: number
  traceId?: string
  keyword?: string
  level?: string
}) {
  return apiGet<LogTail>('/admin/log-viewer/tail', { params })
}
