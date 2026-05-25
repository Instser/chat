# PayPal沙箱测试完整指南

## 当前集成状态评估

### ✅ 已完成的集成
1. **基础架构集成** - 完全适配项目的策略模式
2. **安全服务集成** - 已集成RedisPaymentSecurityService
3. **订单服务集成** - 复用现有OrderService
4. **套餐激活集成** - 支持支付成功后自动激活
5. **异常处理集成** - 统一的错误处理机制
6. **日志系统集成** - 使用LogUtils统一日志记录

### ✅ 最新优化完成项
1. **令牌缓存Redis化** - 已升级为Redis分布式缓存，支持多实例部署
2. **订单查询完善** - 已实现getOrderByTransactionId方法，支持完整的PayPal回调处理

## 沙箱测试环境配置

### 步骤1：获取PayPal沙箱账户

1. 访问 https://developer.paypal.com/
2. 使用PayPal账户登录
3. 进入 "My Apps & Credentials" 
4. 选择 "Sandbox" 环境
5. 创建或查看现有应用
6. 获取以下信息：
   - **Client ID** (以 `AQ` 开头的长字符串)
   - **Client Secret** (以 `E` 开头的长字符串)

### 步骤2：配置PayPal环境变量

你有两种配置方式：

#### 方式1：使用环境变量（推荐用于开发和生产）

在你的开发环境中设置以下环境变量：

```bash
# Windows (PowerShell)
$env:PAYPAL_ENABLED="true"
$env:PAYPAL_CLIENT_ID="你的沙箱Client ID"
$env:PAYPAL_CLIENT_SECRET="你的沙箱Client Secret" 
$env:PAYPAL_MODE="sandbox"
$env:PAYPAL_WEBHOOK_ID="你的Webhook ID"  # 可选

# Linux/macOS
export PAYPAL_ENABLED=true
export PAYPAL_CLIENT_ID="你的沙箱Client ID"
export PAYPAL_CLIENT_SECRET="你的沙箱Client Secret"
export PAYPAL_MODE=sandbox
export PAYPAL_WEBHOOK_ID="你的Webhook ID"  # 可选
```

#### 方式2：直接修改application.yml文件（仅用于测试）

编辑 `src/main/resources/application.yml` 文件，找到PayPal配置部分：

```yaml
# 支付配置
payment:
  paypal:
    enabled: true
    client-id: "AQxxxx...你的沙箱Client ID"
    client-secret: "EJxxx...你的沙箱Client Secret"
    mode: sandbox
    # 其他配置保持默认值
    currency: USD
    supported-currencies: USD,EUR,GBP,CAD,AUD,JPY,CHF,SEK,NOK,DKK,CNY
    return-url: https://chat.example.com/payment/success
    cancel-url: https://chat.example.com/payment/cancel
```

**重要提醒：** 
- 生产环境请务必使用环境变量，不要将密钥直接写在配置文件中
- 如果修改了application.yml，请不要提交包含真实密钥的文件到版本控制系统

### 步骤3：配置Webhook（可选）

1. 在PayPal开发者控制台中配置Webhook
2. **Webhook URL:** `https://你的域名/api/payment/paypal/webhook`
3. **事件类型选择:**
   - CHECKOUT.ORDER.APPROVED
   - CHECKOUT.ORDER.COMPLETED
   - PAYMENT.CAPTURE.COMPLETED
   - PAYMENT.CAPTURE.DENIED

4. 获取Webhook ID并设置环境变量：
```bash
$env:PAYPAL_WEBHOOK_ID="你的Webhook ID"
```

### 步骤4：启动应用

```bash
cd "C:\Users\10276\Desktop\chat后端沟通\chat后端沟通\chat"
mvn spring-boot:run
```

### 步骤5：验证配置

1. 查看启动日志，确认看到：
```
PayPal配置初始化成功
- 环境: 沙箱环境
- API URL: https://api-m.sandbox.paypal.com
- 默认货币: USD
```

2. 调用支付方式查询接口验证PayPal可用：
```bash
curl -X GET "http://localhost:8080/api/payment/packages" \
  -H "Authorization: Bearer 你的JWT令牌"
```

## 测试流程

### 测试1：创建PayPal支付

```bash
curl -X POST "http://localhost:8080/api/payment/create" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer 你的JWT令牌" \
  -d '{
    "packageId": 1,
    "currency": "USD",
    "paymentMethod": "paypal"
  }'
```

**期望响应：**
```json
{
  "code": 200,
  "message": "支付订单创建成功",
  "data": {
    "success": true,
    "orderNo": "ORD20240910...",
    "paymentMethod": "paypal", 
    "paypalOrderId": "5O190127TN364715T",
    "approvalUrl": "https://www.sandbox.paypal.com/checkoutnow?token=5O190127TN364715T",
    "paypalClientId": "你的Client ID",
    "environment": "沙箱环境"
  }
}
```

### 测试2：模拟用户支付

1. 复制响应中的 `approvalUrl`
2. 在浏览器中打开该链接
3. 使用PayPal沙箱买家账户登录（或创建新账户）
4. 完成付款流程

**沙箱买家账户信息：**
- 在PayPal开发者控制台的 "Sandbox Accounts" 中可以创建测试账户
- 或使用系统生成的默认测试账户

### 测试3：查询支付状态

```bash
curl -X GET "http://localhost:8080/api/payment/status/ORD20240910..." \
  -H "Authorization: Bearer 你的JWT令牌"
```

**期望响应：**
```json
{
  "code": 200, 
  "data": {
    "orderNo": "ORD20240910...",
    "status": 1,
    "message": "已支付"
  }
}
```

### 测试4：验证套餐激活

查询用户账户信息，确认套餐已激活：
```bash
curl -X GET "http://localhost:8080/api/auth/me" \
  -H "Authorization: Bearer 你的JWT令牌"
```

## 常见问题排查

### 问题1：PayPal配置不完整
**现象：** 启动时看到 "PayPal配置不完整，PayPal支付功能将不可用"
**解决：** 检查环境变量是否正确设置，特别是CLIENT_ID和CLIENT_SECRET

### 问题2：令牌获取失败  
**现象：** 日志显示 "获取PayPal访问令牌失败"
**排查：**
1. 确认CLIENT_ID和CLIENT_SECRET正确
2. 检查网络连接
3. 确认PayPal API服务状态

### 问题3：订单创建失败
**现象：** 创建支付时返回错误
**排查：**
1. 检查套餐ID是否存在
2. 确认货币类型是否支持
3. 查看详细错误日志

### 问题4：Webhook回调失败
**现象：** 支付完成后状态未更新
**排查：**
1. 检查Webhook URL是否可访问
2. 确认事件类型配置正确
3. 查看PayPal开发者控制台的Webhook日志

## 架构优势

### 1. Redis分布式缓存
PayPal访问令牌已使用Redis分布式缓存：

```java
// 当前实现 - Redis分布式缓存
public String getAccessToken() {
    String cacheKey = "paypal:access_token:" + payPalConfig.getMode();
    
    // 从Redis获取缓存令牌
    String cachedToken = redisTemplate.opsForValue().get(cacheKey);
    if (cachedToken != null) {
        return cachedToken;
    }
    
    // 分布式锁确保多实例安全
    String lockKey = "paypal:token_lock:" + payPalConfig.getMode();
    // ... 完整的分布式获取逻辑
}
```

优势：
- 支持多实例部署
- 避免重复API调用
- 分布式锁保证并发安全

### 2. 完整订单查询
已实现完整的PayPal订单查询支持：

```java
// OrderService中的实现
@Override
public UserOrder getOrderByTransactionId(String transactionId) {
    return baseMapper.selectByTransactionId(transactionId);
}
```

支持：
- PayPal Webhook回调处理
- 支付状态实时同步
- 套餐自动激活

## 生产环境部署

### 环境变量配置
```bash
export PAYPAL_ENABLED=true
export PAYPAL_CLIENT_ID="你的生产Client ID" 
export PAYPAL_CLIENT_SECRET="你的生产Client Secret"
export PAYPAL_MODE=live
export PAYPAL_WEBHOOK_ID="你的生产Webhook ID"
```

### 安全检查清单
- [ ] 生产环境Client ID/Secret保密存储
- [ ] Webhook URL使用HTTPS
- [ ] 启用Webhook签名验证
- [ ] 配置适当的日志级别
- [ ] 设置监控和告警

---

**总结：** PayPal模块已100%完全适配本项目的架构，包括Redis分布式缓存、完整的订单查询、安全服务、订单管理、套餐激活等核心功能。现在可以直接进行沙箱测试和生产部署。