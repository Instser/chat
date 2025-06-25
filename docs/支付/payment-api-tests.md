# 支付API测试用例

## 概述

本文档提供了前端支付API的详细测试用例，包括正常流程和异常情况的测试，方便后端开发者进行接口测试和验证。

## 测试环境

- **测试域名**: `https://api-test.example.com`
- **认证Token**: 需要先登录获取有效的Bearer Token
- **测试用户**: 建议创建专门的测试用户账号

## 测试工具

推荐使用以下工具进行API测试：
- **Postman**: 图形化接口测试工具
- **curl**: 命令行工具
- **HTTPie**: 更友好的命令行HTTP客户端

## 测试用例

### 1. 获取套餐信息API测试

#### 1.1 正常流程测试

**测试用例**: 获取Pro月付套餐信息

```bash
curl -X POST "https://api-test.example.com/api/package/info" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -d '{
    "packageId": 3,
    "currency": "CNY"
  }'
```

**期望响应**:
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
      "features": ["Unlimited messages", "Advanced AI models", ...]
    },
    "paymentMethods": [
      {"id": "alipay", "name": "支付宝", "type": "alipay", "available": true},
      {"id": "wechat", "name": "微信支付", "type": "wechat", "available": true}
    ]
  },
  "timestamp": 1640995200000
}
```

#### 1.2 异常情况测试

**测试用例1**: 无效的套餐ID
```bash
curl -X POST "https://api-test.example.com/api/package/info" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -d '{
    "packageId": 999,
    "currency": "CNY"
  }'
```

**期望响应**:
```json
{
  "code": 40001,
  "message": "套餐不存在",
  "data": null,
  "timestamp": 1640995200000
}
```

**测试用例2**: 缺少必填参数
```bash
curl -X POST "https://api-test.example.com/api/package/info" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -d '{
    "packageId": 3
  }'
```

**期望响应**:
```json
{
  "code": 400,
  "message": "缺少必填参数: currency",
  "data": null,
  "timestamp": 1640995200000
}
```

**测试用例3**: 未授权访问
```bash
curl -X POST "https://api-test.example.com/api/package/info" \
  -H "Content-Type: application/json" \
  -d '{
    "packageId": 3,
    "currency": "CNY"
  }'
```

**期望响应**:
```json
{
  "code": 401,
  "message": "未授权访问",
  "data": null,
  "timestamp": 1640995200000
}
```

### 2. 创建支付订单API测试

#### 2.1 正常流程测试

**测试用例**: 创建支付宝支付订单

```bash
curl -X POST "https://api-test.example.com/api/payment/create" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -d '{
    "packageId": 3,
    "currency": "CNY",
    "paymentMethod": "alipay"
  }'
```

**期望响应**:
```json
{
  "code": 200,
  "message": "创建支付订单成功",
  "data": {
    "success": true,
    "orderNo": "ORDER_20241219_001",
    "createdTime": "2024-12-19T10:30:00Z",
    "paymentUrl": "https://payment.example.com/pay/ORDER_20241219_001",
    "qrCodeUrl": "https://api.qrserver.com/v1/create-qr-code/?data=..."
  },
  "timestamp": 1640995200000
}
```

#### 2.2 异常情况测试

**测试用例1**: 不支持的支付方式
```bash
curl -X POST "https://api-test.example.com/api/payment/create" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -d '{
    "packageId": 3,
    "currency": "CNY",
    "paymentMethod": "invalid_method"
  }'
```

**期望响应**:
```json
{
  "code": 40002,
  "message": "支付方式不可用",
  "data": null,
  "timestamp": 1640995200000
}
```

**测试用例2**: 重复创建订单
```bash
# 先创建一个订单，然后立即再次创建相同套餐的订单
curl -X POST "https://api-test.example.com/api/payment/create" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -d '{
    "packageId": 3,
    "currency": "CNY",
    "paymentMethod": "alipay"
  }'
```

**期望响应**:
```json
{
  "code": 40003,
  "message": "订单已存在",
  "data": {
    "existingOrderNo": "ORDER_20241219_001"
  },
  "timestamp": 1640995200000
}
```

### 3. 查询支付状态API测试

#### 3.1 正常流程测试

**测试用例**: 查询待支付订单状态

```bash
curl -X GET "https://api-test.example.com/api/payment/status/ORDER_20241219_001" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

**期望响应** (待支付):
```json
{
  "code": 200,
  "message": "查询支付状态成功",
  "data": {
    "status": 0,
    "message": "待支付",
    "orderNo": "ORDER_20241219_001"
  },
  "timestamp": 1640995200000
}
```

**期望响应** (支付成功):
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

#### 3.2 异常情况测试

**测试用例**: 查询不存在的订单
```bash
curl -X GET "https://api-test.example.com/api/payment/status/INVALID_ORDER" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

**期望响应**:
```json
{
  "code": 404,
  "message": "订单不存在",
  "data": null,
  "timestamp": 1640995200000
}
```

## 完整流程测试

### 测试场景1: 成功支付流程

1. **获取套餐信息**
```bash
curl -X POST "https://api-test.example.com/api/package/info" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -d '{"packageId": 3, "currency": "CNY"}'
```

2. **创建支付订单**
```bash
curl -X POST "https://api-test.example.com/api/payment/create" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -d '{"packageId": 3, "currency": "CNY", "paymentMethod": "alipay"}'
```

3. **轮询支付状态** (每3秒执行一次)
```bash
curl -X GET "https://api-test.example.com/api/payment/status/ORDER_20241219_001" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

### 测试场景2: 支付失败流程

模拟支付失败的情况，确保API返回正确的错误状态码。

## 性能测试

### 并发测试

使用工具如Apache Bench或wrk进行并发测试：

```bash
# 测试获取套餐信息接口的并发性能
ab -n 1000 -c 10 -H "Authorization: Bearer YOUR_TOKEN_HERE" \
   -p package_info.json -T application/json \
   https://api-test.example.com/api/package/info
```

### 压力测试

测试在高负载情况下API的表现：
- 同时创建大量订单
- 高频率查询支付状态
- 模拟大量用户同时访问

## 测试数据准备

### 测试套餐数据

确保数据库中有以下测试套餐：

```sql
INSERT INTO packages (id, name, description, price, currency, period, features) VALUES
(1, 'Free', 'Perfect for getting started', 0, 'CNY', 'month', '["10 messages per day", "Basic AI models"]'),
(2, 'Free', 'Perfect for getting started', 0, 'CNY', 'year', '["10 messages per day", "Basic AI models"]'),
(3, 'Pro', 'Best for regular users', 68, 'CNY', 'month', '["Unlimited messages", "Advanced AI models"]'),
(4, 'Pro', 'Best for regular users', 680, 'CNY', 'year', '["Unlimited messages", "Advanced AI models"]'),
(5, 'Premium', 'For power users', 128, 'CNY', 'month', '["Everything in Pro", "Latest AI models"]'),
(6, 'Premium', 'For power users', 1280, 'CNY', 'year', '["Everything in Pro", "Latest AI models"]');
```

### 测试用户数据

创建测试用户账号，确保有不同权限级别的用户进行测试。

## 自动化测试脚本

### Postman Collection

可以创建Postman Collection包含所有测试用例，支持：
- 环境变量配置
- 自动化测试流程
- 断言验证
- 测试报告生成

### 测试脚本示例

```javascript
// 完整支付流程测试脚本
const API_BASE = 'https://api-test.example.com';
const TOKEN = 'YOUR_TOKEN_HERE';

async function testPaymentFlow() {
  // 1. 获取套餐信息
  const packageInfo = await fetch(`${API_BASE}/api/package/info`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${TOKEN}`
    },
    body: JSON.stringify({
      packageId: 3,
      currency: 'CNY'
    })
  });
  
  // 2. 创建支付订单
  const payment = await fetch(`${API_BASE}/api/payment/create`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${TOKEN}`
    },
    body: JSON.stringify({
      packageId: 3,
      currency: 'CNY',
      paymentMethod: 'alipay'
    })
  });
  
  const paymentData = await payment.json();
  const orderNo = paymentData.data.orderNo;
  
  // 3. 轮询支付状态
  const pollStatus = async () => {
    const status = await fetch(`${API_BASE}/api/payment/status/${orderNo}`, {
      headers: {
        'Authorization': `Bearer ${TOKEN}`
      }
    });
    
    const statusData = await status.json();
    console.log('Payment status:', statusData.data.status);
    
    if (statusData.data.status === 0) {
      // 继续轮询
      setTimeout(pollStatus, 3000);
    } else {
      console.log('Payment completed with status:', statusData.data.status);
    }
  };
  
  pollStatus();
}

testPaymentFlow();
```

## 注意事项

1. **测试环境隔离**: 确保测试不会影响生产数据
2. **数据清理**: 测试完成后清理测试数据
3. **错误日志**: 记录详细的错误日志便于调试
4. **监控指标**: 监控API响应时间和成功率
5. **安全测试**: 测试各种安全攻击场景

## 联系方式

如有测试相关问题，请联系：
- 前端开发团队
- QA测试团队

---

**文档版本**: v1.0  
**最后更新**: 2024-12-19
