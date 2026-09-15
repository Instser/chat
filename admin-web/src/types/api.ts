export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
  timestamp?: number
  traceId?: string
}

export interface PageResult<T> {
  items: T[]
  page: number
  size: number
  total: number
  pages: number
}

/** 管理员账号（能登录管理后台即管理员，无 role 字段） */
export interface AdminUserVO {
  id: number
  username: string
  nickname?: string | null
  lastLoginTime?: string | null
}

export interface AdminUserSummary {
  id: string
  username: string
  email?: string
  role: string
  status: number
  lastLoginTime?: string
  createdTime?: string
  packageId?: string
  chatCountRemaining?: number
  packageExpireTime?: string
}

export interface AdminPackage {
  id: string
  packageName: string
  description?: string
  packageType: string
  price: number
  discountPrice?: number
  validityDays: number
  limitType?: string
  modelPermissions?: string
  features?: string
  isEnabled: number
  sortOrder?: number
  chatCount?: number
  dailyChatLimit?: number
}

export interface AdminModel {
  id: string
  modelCode: string
  modelName: string
  description?: string
  provider?: string
  version?: string
  tokenLimit?: number
  pricePer1kTokens?: number
  isAvailable: number
  isVisible: number
  baseUrl?: string
  apiKeyConfigured: boolean
  apiKeyMasked?: string
  /** 行级轮询权重（1-100，默认 100） */
  weight: number
  /** 有效权重 = max(1, 权重 − 20 × 连续失败数) */
  effectiveWeight: number
  successCount: number
  failCount: number
  consecutiveFails: number
  lastError?: string | null
  lastUsedTime?: string | null
}

export interface AdminOrder {
  id: string
  orderNo: string
  userId: string
  username?: string
  packageId?: string
  amount: number
  currency: string
  paymentMethod?: string
  paymentStatus: number
  paymentTime?: string
  transactionId?: string
  createTime?: string
}

export interface LogFile {
  fileName: string
  exists: boolean
  size?: number
  lastModified?: number
}

export interface LogTail {
  fileName: string
  totalRead: number
  filtered: number
  lines: string[]
}
