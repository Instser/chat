/**
 * 前端支付API类型定义
 * 
 * 此文件定义了前端支付系统中使用的所有数据类型，
 * 供后端开发者参考实现对应的数据结构。
 * 
 * @version 1.0
 * @date 2024-12-19
 */

// ============================================================================
// 通用响应格式
// ============================================================================

/**
 * API统一响应格式
 */
export interface ApiResult<T = any> {
  /** 状态码，200表示成功 */
  code: number
  /** 响应消息 */
  message: string
  /** 响应数据 */
  data: T
  /** 响应时间戳 */
  timestamp: number
}

// ============================================================================
// 套餐相关类型
// ============================================================================

/**
 * 套餐信息
 */
export interface Package {
  /** 套餐ID */
  id: number
  /** 套餐名称 */
  name: string
  /** 套餐描述 */
  description: string
  /** 价格 */
  price: number
  /** 货币类型 */
  currency: string
  /** 计费周期 */
  period: 'month' | 'year'
  /** 功能特性列表 */
  features: string[]
}

/**
 * 支付方式
 */
export interface PaymentMethod {
  /** 支付方式ID */
  id: string
  /** 显示名称 */
  name: string
  /** 支付方式类型 */
  type: string
  /** 是否可用 */
  available: boolean
}

// ============================================================================
// API请求/响应类型
// ============================================================================

/**
 * 获取套餐信息请求
 */
export interface PackageInfoRequest {
  /** 套餐ID (1-6) */
  packageId: number
  /** 货币类型，固定为"CNY" */
  currency: string
}

/**
 * 获取套餐信息响应
 */
export interface PackageInfoResponse {
  /** 套餐信息 */
  package: Package
  /** 可用支付方式列表 */
  paymentMethods: PaymentMethod[]
}

/**
 * 创建支付订单请求
 */
export interface CreatePaymentRequest {
  /** 套餐ID (1-6) */
  packageId: number
  /** 货币类型，固定为"CNY" */
  currency: string
  /** 支付方式ID */
  paymentMethod: string
}

/**
 * 创建支付订单响应
 */
export interface CreatePaymentResponse {
  /** 是否创建成功 */
  success: boolean
  /** 订单号 */
  orderNo: string
  /** 订单创建时间 (ISO 8601格式) */
  createdTime: string
  /** 支付链接 */
  paymentUrl: string
  /** 支付二维码图片URL (可选) */
  qrCodeUrl?: string
}

/**
 * 支付状态响应
 */
export interface PaymentStatusResponse {
  /** 支付状态码 */
  status: number
  /** 状态描述 */
  message: string
  /** 订单号 */
  orderNo?: string
}

// ============================================================================
// 套餐ID映射常量
// ============================================================================

/**
 * 套餐ID映射
 * 
 * 前端定义了6个不同的套餐ID，对应3个套餐的月付和年付版本
 */
export const PACKAGE_IDS = {
  /** 免费套餐 */
  FREE: {
    MONTHLY: 1,
    YEARLY: 2
  },
  /** 专业版套餐 */
  PRO: {
    MONTHLY: 3,
    YEARLY: 4
  },
  /** 高级版套餐 */
  PREMIUM: {
    MONTHLY: 5,
    YEARLY: 6
  }
} as const

/**
 * 套餐价格映射
 */
export const PACKAGE_PRICES = {
  [PACKAGE_IDS.FREE.MONTHLY]: { price: 0, period: 'month' },
  [PACKAGE_IDS.FREE.YEARLY]: { price: 0, period: 'year' },
  [PACKAGE_IDS.PRO.MONTHLY]: { price: 68, period: 'month' },
  [PACKAGE_IDS.PRO.YEARLY]: { price: 680, period: 'year' },
  [PACKAGE_IDS.PREMIUM.MONTHLY]: { price: 128, period: 'month' },
  [PACKAGE_IDS.PREMIUM.YEARLY]: { price: 1280, period: 'year' }
} as const

// ============================================================================
// 支付状态码常量
// ============================================================================

/**
 * 支付状态码
 */
export const PAYMENT_STATUS = {
  /** 待支付 */
  PENDING: 0,
  /** 支付成功 */
  SUCCESS: 200,
  /** 支付失败 */
  FAILED: 400,
  /** 支付取消 */
  CANCELLED: 401,
  /** 支付超时 */
  TIMEOUT: 408
} as const

// ============================================================================
// 错误码常量
// ============================================================================

/**
 * 业务错误码
 */
export const ERROR_CODES = {
  /** 套餐不存在 */
  PACKAGE_NOT_FOUND: 40001,
  /** 支付方式不可用 */
  PAYMENT_METHOD_UNAVAILABLE: 40002,
  /** 订单已存在 */
  ORDER_EXISTS: 40003,
  /** 用户余额不足 */
  INSUFFICIENT_BALANCE: 40004,
  /** 支付服务异常 */
  PAYMENT_SERVICE_ERROR: 50001
} as const

// ============================================================================
// 前端行为说明
// ============================================================================

/**
 * 前端支付流程说明
 * 
 * 1. 用户在套餐页面选择月付/年付，点击"Upgrade Now"
 * 2. 前端根据选择的计费周期确定packageId，跳转到支付页面
 * 3. 支付页面调用 /api/package/info 获取套餐详情和支付方式
 * 4. 用户选择支付方式，点击"Confirm Subscription"
 * 5. 前端调用 /api/payment/create 创建支付订单
 * 6. 显示订单信息和支付二维码
 * 7. 前端每3秒调用 /api/payment/status/{orderNo} 轮询支付状态
 * 8. 根据返回的状态码处理结果：
 *    - status = 0: 继续轮询
 *    - status = 200: 显示成功页面，3秒后跳转到套餐页面
 *    - 其他状态码: 显示失败页面，提示联系管理员
 */

/**
 * 前端轮询配置
 */
export const POLLING_CONFIG = {
  /** 轮询间隔 (毫秒) */
  INTERVAL: 3000,
  /** 轮询超时时间 (毫秒) */
  TIMEOUT: 600000, // 10分钟
  /** 成功后跳转延迟 (毫秒) */
  SUCCESS_REDIRECT_DELAY: 3000
} as const

// ============================================================================
// 示例数据
// ============================================================================

/**
 * 获取套餐信息请求示例
 */
export const EXAMPLE_PACKAGE_INFO_REQUEST: PackageInfoRequest = {
  packageId: 3,
  currency: "CNY"
}

/**
 * 获取套餐信息响应示例
 */
export const EXAMPLE_PACKAGE_INFO_RESPONSE: ApiResult<PackageInfoResponse> = {
  code: 200,
  message: "获取套餐信息成功",
  data: {
    package: {
      id: 3,
      name: "Pro",
      description: "Best for regular users",
      price: 68,
      currency: "CNY",
      period: "month",
      features: [
        "Unlimited messages",
        "Advanced AI models",
        "Priority support",
        "Mobile & web access",
        "Chat history export",
        "Custom prompts"
      ]
    },
    paymentMethods: [
      {
        id: "alipay",
        name: "AliPay",
        type: "alipay",
        available: true
      },
      {
        id: "wechat",
        name: "WeChatPay",
        type: "wechat",
        available: true
      }
    ]
  },
  timestamp: 1640995200000
}

/**
 * 创建支付订单请求示例
 */
export const EXAMPLE_CREATE_PAYMENT_REQUEST: CreatePaymentRequest = {
  packageId: 3,
  currency: "CNY",
  paymentMethod: "alipay"
}

/**
 * 创建支付订单响应示例
 */
export const EXAMPLE_CREATE_PAYMENT_RESPONSE: ApiResult<CreatePaymentResponse> = {
  code: 200,
  message: "创建支付订单成功",
  data: {
    success: true,
    orderNo: "ORDER_20241219_001",
    createdTime: "2024-12-19T10:30:00Z",
    paymentUrl: "https://payment.example.com/pay/ORDER_20241219_001",
    qrCodeUrl: "https://api.qrserver.com/v1/create-qr-code/?data=https://payment.example.com/pay/ORDER_20241219_001"
  },
  timestamp: 1640995200000
}

/**
 * 支付状态响应示例
 */
export const EXAMPLE_PAYMENT_STATUS_RESPONSE: ApiResult<PaymentStatusResponse> = {
  code: 200,
  message: "查询支付状态成功",
  data: {
    status: 200,
    message: "支付成功",
    orderNo: "ORDER_20241219_001"
  },
  timestamp: 1640995200000
}
