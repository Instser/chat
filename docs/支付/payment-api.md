# 前端支付API文档

## 概述

本文档描述了前端套餐支付系统的API接口规范，包括套餐信息获取、支付订单创建和支付状态查询等功能。

## 基础信息

- **API Base URL**: `/api`
- **认证方式**: Bearer Token (通过 Authorization 头或 Cookie)
- **数据格式**: JSON
- **字符编码**: UTF-8

## 通用响应格式

所有API响应都遵循统一的格式：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}, 
  "timestamp": 1640995200000
}
```

### 响应字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| code | number | 状态码，200表示成功 |
| message | string | 响应消息 |
| data | object | 响应数据，具体结构见各接口说明 |
| timestamp | number | 响应时间戳 |

## 套餐ID映射

前端定义了6个不同的套餐ID，对应3个套餐的月付和年付版本：

| 套餐类型 | 月付ID | 年付ID | 月付价格 | 年付价格 | 说明 |
|----------|--------|--------|----------|----------|------|
| Free | 1 | 2 | ¥0 | ¥0 | 免费套餐 |
| Pro | 3 | 4 | ¥68 | ¥680 | 专业版（年付2个月免费） |
| Premium | 5 | 6 | ¥128 | ¥1280 | 高级版（年付2个月免费） |

## API接口详情

### 1. 获取套餐信息和支付方式

**接口地址**: `POST /api/package/info`

**请求参数**:
```json
{
  "packageId": 3,
  "currency": "CNY"
}
```

**请求参数说明**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| packageId | number | 是 | 套餐ID（1-6） |
| currency | string | 是 | 货币类型，固定为"CNY" |

**响应示例**:
```json
{
  "code": 200,
  "message": "获取套餐信息成功",
  "data": {
    "package": {
      "id": 3,
      "name": "Pro",
      "description": "Best for regular users",
      "price": 68,
      "currency": "CNY",
      "period": "month",
      "features": [
        "Unlimited messages",
        "Advanced AI models",
        "Priority support",
        "Mobile & web access",
        "Chat history export",
        "Custom prompts"
      ]
    },
    "paymentMethods": [
      {
        "id": "alipay",
        "name": "支付宝",
        "type": "alipay",
        "available": true
      },
      {
        "id": "wechat",
        "name": "微信支付",
        "type": "wechat",
        "available": true
      }
    ]
  },
  "timestamp": 1640995200000
}
```

**响应数据说明**:

**package对象**:
| 字段 | 类型 | 说明 |
|------|------|------|
| id | number | 套餐ID |
| name | string | 套餐名称 |
| description | string | 套餐描述 |
| price | number | 价格 |
| currency | string | 货币类型 |
| period | string | 计费周期（"month"或"year"） |
| features | string[] | 功能特性列表 |

**paymentMethods数组元素**:
| 字段 | 类型 | 说明 |
|------|------|------|
| id | string | 支付方式ID |
| name | string | 支付方式显示名称 |
| type | string | 支付方式类型 |
| available | boolean | 是否可用 |

### 2. 创建支付订单

**接口地址**: `POST /api/payment/create`

**请求参数**:
```json
{
  "packageId": 3,
  "currency": "CNY",
  "paymentMethod": "alipay"
}
```

**请求参数说明**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| packageId | number | 是 | 套餐ID |
| currency | string | 是 | 货币类型 |
| paymentMethod | string | 是 | 支付方式ID |

**响应示例**:
```json
{
  "code": 200,
  "message": "创建支付订单成功",
  "data": {
    "success": true,
    "orderNo": "ORDER_20241219_001",
    "createdTime": "2024-12-19T10:30:00Z",
    "paymentUrl": "https://payment.example.com/pay/ORDER_20241219_001",
    "qrCodeUrl": "https://api.qrserver.com/v1/create-qr-code/?data=https://payment.example.com/pay/ORDER_20241219_001"
  },
  "timestamp": 1640995200000
}
```

**响应数据说明**:
| 字段 | 类型 | 说明 |
|------|------|------|
| success | boolean | 是否创建成功 |
| orderNo | string | 订单号 |
| createdTime | string | 订单创建时间（ISO 8601格式） |
| paymentUrl | string | 支付链接 |
| qrCodeUrl | string | 支付二维码图片URL（可选） |

### 3. 查询支付状态

**接口地址**: `GET /api/payment/status/{orderNo}`

**路径参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| orderNo | string | 是 | 订单号 |

**请求示例**: `GET /api/payment/status/ORDER_20241219_001`

**响应示例**:
```json
{
  "code": 200,
  "message": "查询支付状态成功",
  "data": {
    "status": 200,
    "message": "支付成功",
    "orderNo": "ORDER_20241219_001"
  },
  "timestamp": 1640995200000
}
```

**响应数据说明**:
| 字段 | 类型 | 说明 |
|------|------|------|
| status | number | 支付状态码 |
| message | string | 状态描述 |
| orderNo | string | 订单号 |

**支付状态码说明**:
| 状态码 | 说明 | 前端处理 |
|--------|------|----------|
| 0 | 待支付 | 继续轮询 |
| 200 | 支付成功 | 显示成功页面，3秒后跳转到套餐页面 |
| 其他 | 支付失败 | 显示失败页面，提示联系管理员 |

## 前端行为说明

### 支付流程

1. **套餐选择**: 用户在套餐页面选择月付/年付，点击"Upgrade Now"
2. **获取套餐信息**: 前端调用`/api/package/info`获取套餐详情和支付方式
3. **支付方式选择**: 用户在支付页面选择支付方式
4. **创建订单**: 前端调用`/api/payment/create`创建支付订单
5. **显示支付界面**: 显示订单时间和二维码
6. **状态轮询**: 前端每3秒调用`/api/payment/status/{orderNo}`查询支付状态
7. **结果处理**: 根据状态码显示成功/失败页面

### 轮询机制

- **轮询间隔**: 3秒
- **轮询超时**: 10分钟
- **停止条件**: 收到非0状态码或超时

### 错误处理

前端会处理以下错误情况：
- 网络请求失败
- API返回错误状态码
- 支付超时
- 用户取消支付

## 认证要求

所有API接口都需要用户认证，后端应验证：
- 用户登录状态
- 用户权限
- 请求来源

## 安全考虑

1. **参数验证**: 验证packageId范围（1-6）
2. **重复订单**: 防止重复创建相同套餐的订单
3. **金额校验**: 验证前端传递的金额与后端套餐价格一致
4. **支付回调**: 确保支付状态更新的安全性

## 测试建议

建议后端提供以下测试环境：
- 测试套餐（价格为0.01元）
- 模拟支付成功/失败场景
- 支付状态变更的webhook测试

## 错误码说明

### HTTP状态码
| 状态码 | 说明 |
|--------|------|
| 200 | 请求成功 |
| 400 | 请求参数错误 |
| 401 | 未授权/登录过期 |
| 403 | 权限不足 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

### 业务错误码
| 错误码 | 说明 | 前端处理 |
|--------|------|----------|
| 40001 | 套餐不存在 | 提示用户并返回套餐页面 |
| 40002 | 支付方式不可用 | 刷新支付方式列表 |
| 40003 | 订单已存在 | 提示用户或跳转到现有订单 |
| 40004 | 用户余额不足 | 提示充值 |
| 50001 | 支付服务异常 | 提示稍后重试 |

## 请求头要求

```http
Content-Type: application/json
Authorization: Bearer {token}
User-Agent: ToUse-Frontend/1.0
```

## 完整请求示例

### 1. 获取套餐信息
```bash
curl -X POST "https://api.example.com/api/package/info" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your_token_here" \
  -d '{
    "packageId": 3,
    "currency": "CNY"
  }'
```

### 2. 创建支付订单
```bash
curl -X POST "https://api.example.com/api/payment/create" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your_token_here" \
  -d '{
    "packageId": 3,
    "currency": "CNY",
    "paymentMethod": "alipay"
  }'
```

### 3. 查询支付状态
```bash
curl -X GET "https://api.example.com/api/payment/status/ORDER_20241219_001" \
  -H "Authorization: Bearer your_token_here"
```

## 数据库设计建议

### 套餐表 (packages)
```sql
CREATE TABLE packages (
  id INT PRIMARY KEY,
  name VARCHAR(50) NOT NULL,
  description TEXT,
  price DECIMAL(10,2) NOT NULL,
  currency VARCHAR(3) DEFAULT 'CNY',
  period ENUM('month', 'year') NOT NULL,
  features JSON,
  status TINYINT DEFAULT 1,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 订单表 (orders)
```sql
CREATE TABLE orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(32) UNIQUE NOT NULL,
  user_id BIGINT NOT NULL,
  package_id INT NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  payment_method VARCHAR(20) NOT NULL,
  payment_url TEXT,
  qr_code_url TEXT,
  status ENUM('pending', 'paid', 'failed', 'cancelled') DEFAULT 'pending',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  paid_at TIMESTAMP NULL
);
```

## 支付回调处理

后端需要处理第三方支付平台的回调，更新订单状态：

```javascript
// 伪代码示例
async function handlePaymentCallback(callbackData) {
  const { orderNo, status, amount } = callbackData;

  // 验证回调签名
  if (!verifySignature(callbackData)) {
    throw new Error('Invalid signature');
  }

  // 更新订单状态
  await updateOrderStatus(orderNo, status);

  // 如果支付成功，激活用户套餐
  if (status === 'paid') {
    await activateUserPackage(orderNo);
  }
}
```

## 性能优化建议

1. **缓存套餐信息**: 套餐信息变化不频繁，可以缓存
2. **订单号生成**: 使用分布式ID生成器
3. **支付状态查询**: 考虑使用Redis缓存最近的查询结果
4. **数据库索引**: 在order_no、user_id、created_at等字段上建立索引

## 监控指标

建议监控以下指标：
- API响应时间
- 支付成功率
- 订单创建量
- 支付方式使用分布
- 错误率统计

## 联系方式

如有API对接问题，请联系前端开发团队。

---

**文档版本**: v1.0
**最后更新**: 2024-12-19
**维护者**: 前端开发团队
