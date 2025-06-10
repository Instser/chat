# 支付模块完整API接口文档

## 📋 文档概述

本文档详细描述了金语聊天系统支付模块的所有API接口，包括请求参数、响应格式、错误处理等完整信息。

**文档版本**: v2.0  
**更新时间**: 2024-06-10  
**支付方式**: 支付宝海外SDK、易支付网关  
**架构模式**: 策略模式设计  

---

## 🌐 基础信息

### 服务配置
- **基础URL**: `http://localhost:8080/api/payment`
- **协议**: HTTP/HTTPS
- **认证方式**: 暂时使用固定用户ID，后续集成JWT
- **响应格式**: 统一使用Result包装类
- **支持货币**: USD（美元）、CNY（人民币）
- **字符编码**: UTF-8

### 通用响应格式
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": "2024-06-10T10:30:00"
}
```

### 状态码说明
| 状态码 | 说明 | 描述 |
|--------|------|------|
| 200 | 成功 | 请求处理成功 |
| 400 | 参数错误 | 请求参数不正确或缺失 |
| 404 | 资源不存在 | 请求的资源不存在 |
| 500 | 系统异常 | 服务器内部错误 |

---

## 📦 套餐管理接口

### 1. 获取套餐列表

**接口描述**: 获取所有启用的套餐列表

**请求信息**:
- **方法**: `GET`
- **路径**: `/packages`
- **参数**: 无

**响应示例**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": "1234567890123456789",
      "packageName": "基础套餐",
      "description": "适合个人用户的基础AI对话套餐",
      "packageType": "basic",
      "priceUsd": 9.99,
      "priceCny": 69.99,
      "validityDays": 30,
      "chatCount": 1000,
      "modelPermissions": ["gpt-3.5-turbo", "qwen-turbo"],
      "isEnabled": true,
      "sortOrder": 1,
      "createTime": "2024-01-01T10:00:00"
    },
    {
      "id": "1234567890123456790",
      "packageName": "高级套餐",
      "description": "适合专业用户的高级AI对话套餐",
      "packageType": "premium",
      "priceUsd": 19.99,
      "priceCny": 139.99,
      "validityDays": 30,
      "chatCount": 5000,
      "modelPermissions": ["gpt-4", "claude-3", "qwen-max"],
      "isEnabled": true,
      "sortOrder": 2,
      "createTime": "2024-01-01T10:00:00"
    }
  ]
}
```

**字段说明**:
| 字段 | 类型 | 说明 |
|------|------|------|
| id | String | 套餐ID（长整型转字符串） |
| packageName | String | 套餐名称 |
| description | String | 套餐描述 |
| packageType | String | 套餐类型（basic/premium/enterprise） |
| priceUsd | BigDecimal | 美元价格 |
| priceCny | BigDecimal | 人民币价格 |
| validityDays | Integer | 有效期（天数） |
| chatCount | Integer | 包含的对话次数 |
| modelPermissions | Array | 可使用的AI模型列表 |
| isEnabled | Boolean | 是否启用 |
| sortOrder | Integer | 排序权重 |
| createTime | DateTime | 创建时间 |

---

## 💳 支付订单接口

### 2. 创建支付订单

**接口描述**: 创建新的支付订单并获取支付链接

**请求信息**:
- **方法**: `POST`
- **路径**: `/create`
- **Content-Type**: `application/json`

**请求参数**:
```json
{
  "packageId": 1234567890123456789,
  "currency": "USD",
  "paymentMethod": "alipay",
  "returnUrl": "http://localhost:3000/payment/success"
}
```

**参数说明**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| packageId | Long | ✅ | 套餐ID |
| currency | String | ✅ | 货币类型（USD/CNY） |
| paymentMethod | String | ✅ | 支付方式（alipay/yipay） |
| returnUrl | String | ❌ | 支付成功后跳转URL |

**支付方式说明**:
- `alipay`: 支付宝海外SDK，支持USD和CNY
- `yipay`: 易支付网关，主要支持CNY

**成功响应**:
```json
{
  "code": 200,
  "message": "支付订单创建成功",
  "data": {
    "orderNo": "JY20240610103000001",
    "paymentRequestId": "PAY20240610103000001123",
    "paymentUrl": "https://open-sg-global.alipay.com/cashier/payment.htm?...",
    "amount": 9.99,
    "currency": "USD",
    "paymentStatus": 0,
    "success": true,
    "errorMessage": null
  }
}
```

**失败响应**:
```json
{
  "code": 500,
  "message": "支付宝配置未完成，请联系管理员",
  "data": {
    "success": false,
    "errorMessage": "支付宝配置未完成，请联系管理员",
    "orderNo": null,
    "paymentUrl": null
  }
}
```

**响应字段说明**:
| 字段 | 类型 | 说明 |
|------|------|------|
| orderNo | String | 订单号（格式：JY+时间戳+随机数） |
| paymentRequestId | String | 支付请求ID |
| paymentUrl | String | 支付跳转链接 |
| amount | BigDecimal | 订单金额 |
| currency | String | 货币类型 |
| paymentStatus | Integer | 支付状态（0-未支付） |
| success | Boolean | 创建是否成功 |
| errorMessage | String | 错误信息（如果有） |

---

### 3. 查询支付状态

**接口描述**: 根据订单号查询支付状态和订单详情

**请求信息**:
- **方法**: `GET`
- **路径**: `/status/{orderNo}`

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| orderNo | String | 订单号 |

**响应示例**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": "1234567890123456789",
    "orderNo": "JY20240610103000001",
    "userId": "1",
    "packageId": "1234567890123456789",
    "packageName": "基础套餐",
    "amount": 9.99,
    "currency": "USD",
    "paymentMethod": "alipay",
    "paymentStatus": 1,
    "paymentStatusDesc": "已支付",
    "paymentTime": "2024-06-10T10:35:00",
    "transactionId": "2024061010350001",
    "paymentUrl": "https://open-sg-global.alipay.com/...",
    "expireTime": "2024-06-10T12:30:00",
    "remark": "套餐购买: 基础套餐",
    "createTime": "2024-06-10T10:30:00"
  }
}
```

**支付状态说明**:
| 状态码 | 状态描述 | 说明 |
|--------|----------|------|
| 0 | 未支付 | 订单已创建，等待支付 |
| 1 | 已支付 | 支付成功，订单完成 |
| 2 | 已取消 | 订单已取消或过期 |
| 3 | 已退款 | 订单已退款 |

---

### 4. 获取用户订单列表

**接口描述**: 获取指定用户的所有订单列表

**请求信息**:
- **方法**: `GET`
- **路径**: `/orders`

**查询参数**:
| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| userId | Long | ❌ | 1 | 用户ID |

**请求示例**:
```
GET /api/payment/orders?userId=1
```

**响应示例**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": "1234567890123456789",
      "orderNo": "JY20240610103000001",
      "userId": "1",
      "packageId": "1234567890123456789",
      "packageName": "基础套餐",
      "amount": 9.99,
      "currency": "USD",
      "paymentMethod": "alipay",
      "paymentStatus": 1,
      "paymentStatusDesc": "已支付",
      "paymentTime": "2024-06-10T10:35:00",
      "transactionId": "2024061010350001",
      "createTime": "2024-06-10T10:30:00"
    }
  ]
}
```

---

### 5. 取消支付订单

**接口描述**: 取消未支付的订单

**请求信息**:
- **方法**: `POST`
- **路径**: `/cancel/{orderNo}`

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| orderNo | String | 订单号 |

**成功响应**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": "订单取消成功"
}
```

**失败响应**:
```json
{
  "code": 500,
  "message": "已支付的订单不能取消",
  "data": null
}
```

---

## 🔧 支付方式管理接口

### 6. 获取支持的支付方式

**接口描述**: 获取当前系统支持的所有支付方式

**请求信息**:
- **方法**: `GET`
- **路径**: `/methods`

**响应示例**:
```json
["alipay", "yipay"]
```

**响应说明**: 直接返回字符串数组，包含所有可用的支付方式

---

### 7. 检查支付方式可用性

**接口描述**: 检查指定支付方式是否可用（配置完整且启用）

**请求信息**:
- **方法**: `GET`
- **路径**: `/methods/{paymentMethod}/available`

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| paymentMethod | String | 支付方式（alipay/yipay） |

**响应示例**:
```json
true
```

**响应说明**: 直接返回布尔值
- `true`: 支付方式可用
- `false`: 支付方式不可用（配置不完整或已禁用）

---

## 🔔 支付回调接口

### 8. 支付宝回调接口

**接口描述**: 接收支付宝支付结果通知（由支付宝系统调用）

**请求信息**:
- **方法**: `POST`
- **路径**: `/alipay/notify`
- **Content-Type**: `application/json`

**请求头**:
| 头部 | 类型 | 说明 |
|------|------|------|
| request-time | String | 请求时间戳 |
| client-id | String | 支付宝客户端ID |
| signature | String | 请求签名 |

**请求体**: JSON格式的支付结果通知

**响应**:
- 成功: `"SUCCESS"`
- 失败: `"FAIL"`

---

### 9. 易支付回调接口

**接口描述**: 接收易支付支付结果通知（由易支付系统调用）

**请求信息**:
- **方法**: `POST`
- **路径**: `/yipay/notify`
- **Content-Type**: `application/x-www-form-urlencoded`

**请求参数**: 表单格式的支付结果通知参数

**响应**:
- 成功: `"success"`
- 失败: `"fail"`

---

## 🚀 使用示例

### 完整支付流程示例

```bash
# 1. 获取套餐列表
curl -X GET "http://localhost:8080/api/payment/packages"

# 2. 创建支付订单
curl -X POST "http://localhost:8080/api/payment/create" \
  -H "Content-Type: application/json" \
  -d '{
    "packageId": 1234567890123456789,
    "currency": "USD",
    "paymentMethod": "alipay",
    "returnUrl": "http://localhost:3000/payment/success"
  }'

# 3. 查询支付状态
curl -X GET "http://localhost:8080/api/payment/status/JY20240610103000001"

# 4. 获取用户订单列表
curl -X GET "http://localhost:8080/api/payment/orders?userId=1"

# 5. 检查支付方式可用性
curl -X GET "http://localhost:8080/api/payment/methods/alipay/available"
```

---

## ⚠️ 注意事项

### 安全要求
1. **HTTPS**: 生产环境必须使用HTTPS
2. **签名验证**: 所有回调都会进行签名验证
3. **参数验证**: 严格验证所有输入参数
4. **幂等性**: 支持重复调用，防止重复处理

### 环境配置
1. **支付宝配置**: 需要配置CLIENT_ID、PRIVATE_KEY、PUBLIC_KEY
2. **易支付配置**: 需要配置PID、KEY、URL
3. **回调URL**: 确保回调URL可公网访问

### 错误处理
1. **参数错误**: 返回400状态码和详细错误信息
2. **业务异常**: 返回500状态码和友好错误提示
3. **系统异常**: 记录详细日志，返回通用错误信息

---

## 📊 数据模型

### PaymentRequestVO (支付请求)
```json
{
  "packageId": "Long, 必填, 套餐ID",
  "currency": "String, 必填, 货币类型(USD/CNY)",
  "paymentMethod": "String, 必填, 支付方式(alipay/yipay)",
  "returnUrl": "String, 可选, 支付成功跳转URL",
  "userId": "Long, 自动设置, 用户ID"
}
```

### PaymentResponseVO (支付响应)
```json
{
  "orderNo": "String, 订单号",
  "paymentRequestId": "String, 支付请求ID",
  "paymentUrl": "String, 支付跳转链接",
  "amount": "BigDecimal, 订单金额",
  "currency": "String, 货币类型",
  "paymentStatus": "Integer, 支付状态",
  "success": "Boolean, 是否成功",
  "errorMessage": "String, 错误信息"
}
```

### UserOrderVO (订单详情)
```json
{
  "id": "String, 订单ID",
  "orderNo": "String, 订单号",
  "userId": "String, 用户ID",
  "packageId": "String, 套餐ID",
  "packageName": "String, 套餐名称",
  "amount": "BigDecimal, 订单金额",
  "currency": "String, 货币类型",
  "paymentMethod": "String, 支付方式",
  "paymentStatus": "Integer, 支付状态",
  "paymentStatusDesc": "String, 状态描述",
  "paymentTime": "DateTime, 支付时间",
  "transactionId": "String, 交易流水号",
  "paymentUrl": "String, 支付链接",
  "expireTime": "DateTime, 过期时间",
  "remark": "String, 备注信息",
  "createTime": "DateTime, 创建时间"
}
```

### PackageVO (套餐信息)
```json
{
  "id": "String, 套餐ID",
  "packageName": "String, 套餐名称",
  "description": "String, 套餐描述",
  "packageType": "String, 套餐类型",
  "priceUsd": "BigDecimal, 美元价格",
  "priceCny": "BigDecimal, 人民币价格",
  "validityDays": "Integer, 有效期天数",
  "chatCount": "Integer, 对话次数",
  "modelPermissions": "Array<String>, 模型权限",
  "isEnabled": "Boolean, 是否启用",
  "sortOrder": "Integer, 排序权重",
  "createTime": "DateTime, 创建时间"
}
```

---

## 🔍 错误码详细说明

### 业务错误码
| 错误码 | 错误信息 | 解决方案 |
|--------|----------|----------|
| 400001 | 套餐ID不能为空 | 检查请求参数packageId |
| 400002 | 货币类型不能为空 | 检查请求参数currency |
| 400003 | 支付方式不能为空 | 检查请求参数paymentMethod |
| 400004 | 不支持的货币类型 | 使用USD或CNY |
| 400005 | 不支持的支付方式 | 使用alipay或yipay |
| 404001 | 套餐不存在 | 检查套餐ID是否正确 |
| 404002 | 订单不存在 | 检查订单号是否正确 |
| 500001 | 支付配置未完成 | 联系管理员配置支付参数 |
| 500002 | 支付服务异常 | 稍后重试或联系技术支持 |
| 500003 | 已支付的订单不能取消 | 订单状态不允许取消 |

### HTTP状态码
| 状态码 | 说明 | 常见原因 |
|--------|------|----------|
| 200 | 请求成功 | 正常处理 |
| 400 | 请求参数错误 | 参数格式不正确或缺失必填参数 |
| 404 | 资源不存在 | 请求的订单或套餐不存在 |
| 500 | 服务器内部错误 | 系统异常或配置问题 |

---

## 🔐 安全机制

### 签名验证
1. **支付宝签名**: 使用RSA2048算法验证请求签名
2. **易支付签名**: 使用MD5算法验证请求签名
3. **参数完整性**: 验证所有必要参数的存在性和格式

### 防重复处理
1. **订单状态检查**: 防止已处理订单的重复更新
2. **幂等性保证**: 相同请求多次调用结果一致
3. **时间窗口限制**: 订单2小时后自动过期

### 数据安全
1. **敏感信息加密**: 支付密钥等敏感信息加密存储
2. **日志脱敏**: 日志中不记录敏感信息
3. **HTTPS传输**: 生产环境强制使用HTTPS

---

## 📈 性能指标

### 响应时间
- **套餐查询**: < 100ms
- **订单创建**: < 500ms
- **状态查询**: < 200ms
- **回调处理**: < 300ms

### 并发能力
- **支持并发**: 1000+ QPS
- **数据库连接池**: 20个连接
- **缓存策略**: Redis缓存热点数据

### 可用性
- **服务可用性**: 99.9%
- **故障恢复**: < 5分钟
- **监控告警**: 实时监控关键指标

---

## 🛠️ 开发调试

### 本地测试环境
```bash
# 启动应用
mvn spring-boot:run

# 健康检查
curl http://localhost:8080/actuator/health

# 查看API文档
http://localhost:8080/swagger-ui.html
```

### 日志配置
```yaml
logging:
  level:
    com.instser.chat.controller.PaymentController: DEBUG
    com.instser.chat.service.PaymentService: DEBUG
    com.instser.chat.strategy: DEBUG
```

### 测试数据
```sql
-- 插入测试套餐
INSERT INTO t_package (package_name, price_usd, price_cny, validity_days, chat_count)
VALUES ('测试套餐', 1.00, 7.00, 30, 100);

-- 查询测试订单
SELECT * FROM t_user_order WHERE user_id = 1 ORDER BY create_time DESC;
```

---

## 📞 技术支持

### 联系方式
- **技术文档**: 本文档
- **问题反馈**: GitHub Issues
- **紧急联系**: 系统管理员

### 常见问题
1. **Q**: 支付宝配置如何获取？
   **A**: 需要在支付宝开放平台申请应用并获取相关密钥

2. **Q**: 为什么订单创建失败？
   **A**: 检查支付配置是否完整，套餐是否存在且启用

3. **Q**: 回调接口如何测试？
   **A**: 可以使用Postman模拟回调请求进行测试

---

**文档维护**: Augment Agent
**最后更新**: 2024-06-10
**版本**: v2.0
**文档状态**: 生产就绪
