# 支付API测试指南

**更新时间**: 2025年6月20日 11:43 (北京时间)
**版本**: v1.1
**目的**: 验证前后端支付API的兼容性
**更新**: 移除固定套餐ID限制，使用数据库实际ID

## 测试前准备

### 1. 初始化测试数据

执行以下SQL脚本初始化符合前端期望的套餐数据：

```bash
# 在项目根目录执行
mysql -u your_username -p your_database < sql/frontend_compatible_packages.sql
```

### 2. 启动后端服务

```bash
mvn spring-boot:run
```

## API测试用例

### 1. 获取套餐列表

**接口**: `GET /api/payment/packages`

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/payment/packages?currency=CNY" \
  -H "Content-Type: application/json"
```

**期望响应**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 123,
      "name": "Free",
      "description": "Basic features for new users",
      "price": 0.00,
      "currency": "CNY",
      "period": 30,
      "features": ["gpt-3.5-turbo"]
    },
    {
      "id": 124,
      "name": "Free",
      "description": "Basic features for new users",
      "price": 0.00,
      "currency": "CNY",
      "period": 365,
      "features": ["gpt-3.5-turbo"]
    }
    // ... 其他套餐（ID为数据库实际ID）
  ],
  "timestamp": 1640995200000
}
```

### 2. 获取套餐信息和支付方式

**接口**: `POST /api/package/info`

**请求示例**:
```bash
curl -X POST "http://localhost:8080/api/package/info" \
  -H "Content-Type: application/json" \
  -d '{
    "packageId": 125,
    "currency": "CNY"
  }'
```

**期望响应**:
```json
{
  "code": 200,
  "message": "获取套餐信息成功",
  "data": {
    "packageInfo": {
      "id": 125,
      "name": "Pro",
      "description": "Best for regular users",
      "price": 68.00,
      "currency": "CNY",
      "period": 30,
      "features": [
        "gpt-3.5-turbo",
        "gpt-4",
        "qwen-turbo"
      ]
    },
    "paymentMethods": [
      {
        "id": "alipay",
        "name": "AliPay",
        "type": "alipay",
        "available": true
      },
      {
        "id": "wechat",
        "name": "WeChatPay",
        "type": "wechat",
        "available": true
      }
    ]
  },
  "timestamp": 1640995200000
}
```

### 3. 创建支付订单

**接口**: `POST /api/payment/create`

**请求示例**:
```bash
curl -X POST "http://localhost:8080/api/payment/create" \
  -H "Content-Type: application/json" \
  -d '{
    "packageId": 125,
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
    "orderNo": "JY20250620114300001",
    "paymentRequestId": "PAY20250620114300001",
    "paymentUrl": "https://payment.example.com/pay/...",
    "amount": 68.00,
    "currency": "CNY",
    "paymentStatus": 0,
    "createdTime": "2025-06-20T11:43:00.000Z",
    "qrCodeUrl": "https://api.qrserver.com/v1/create-qr-code/?size=250x250&data=..."
  },
  "timestamp": 1640995200000
}
```

### 4. 查询支付状态 (简化格式)

**接口**: `GET /api/payment/status/{orderNo}`

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/payment/status/JY20250620114300001" \
  -H "Content-Type: application/json"
```

**期望响应**:
```json
{
  "code": 200,
  "message": "查询支付状态成功",
  "data": {
    "status": 0,
    "message": "待支付",
    "orderNo": "JY20250620114300001"
  },
  "timestamp": 1640995200000
}
```

### 5. 获取订单详细信息 (完整格式)

**接口**: `GET /api/payment/order/{orderNo}`

**请求示例**:
```bash
curl -X GET "http://localhost:8080/api/payment/order/JY20250620114300001" \
  -H "Content-Type: application/json"
```

**期望响应**:
```json
{
  "code": 200,
  "message": "获取订单详情成功",
  "data": {
    "id": "123456789",
    "orderNo": "JY20250620114300001",
    "userId": "1",
    "packageId": "125",
    "packageName": "Pro",
    "amount": 68.00,
    "currency": "CNY",
    "paymentMethod": "alipay",
    "paymentStatus": 0,
    "paymentStatusDesc": "未支付"
  },
  "timestamp": 1640995200000
}
```

## 验证要点

### 1. 套餐ID验证

验证数据库中的套餐ID是否正确返回：

| 数据库ID | 套餐类型 | 周期 | 验证方法 |
|----------|----------|------|----------|
| 实际ID | Free | month | 调用package/info接口 |
| 实际ID | Free | year | 调用package/info接口 |
| 实际ID | Pro | month | 调用package/info接口 |
| 实际ID | Pro | year | 调用package/info接口 |
| 实际ID | Premium | month | 调用package/info接口 |
| 实际ID | Premium | year | 调用package/info接口 |

### 2. Period字段映射验证

验证validity_days是否正确映射为period：

- period字段直接返回数据库中的validity_days数值
- 例如：validity_days = 30 → period = 30
- 例如：validity_days = 365 → period = 365

### 3. 支付方式验证

验证是否返回可用的支付方式列表：

- 支付方式ID和名称是否正确
- available字段是否为true
- 是否包含支付宝和微信支付

### 4. 错误处理验证

测试错误场景：

```bash
# 测试无效套餐ID
curl -X POST "http://localhost:8080/api/package/info" \
  -H "Content-Type: application/json" \
  -d '{"packageId": 99999, "currency": "CNY"}'

# 期望返回错误信息
```

## 常见问题排查

### 1. 套餐不存在

**问题**: 调用package/info接口返回"套餐不存在"

**排查**:
1. 检查数据库中是否有对应ID的套餐数据
2. 检查套餐的is_enabled字段是否为1
3. 检查getPackageByFrontendId方法是否正确查询数据库

### 2. 支付方式为空

**问题**: paymentMethods数组为空

**排查**:
1. 检查PaymentStrategyFactory是否正确配置
2. 检查支付配置是否完整
3. 检查getAvailablePaymentMethods方法

### 3. Period字段错误

**问题**: period字段值不正确

**排查**:
1. 检查套餐的validity_days字段值
2. 检查period字段是否直接返回validity_days数值

## 测试结果记录

| 测试项 | 状态 | 备注 |
|--------|------|------|
| 获取套餐列表 | ⬜ | |
| 获取套餐信息 | ⬜ | |
| 创建支付订单 | ⬜ | |
| 查询支付状态(简化) | ⬜ | 新增简化格式接口 |
| 获取订单详情(完整) | ⬜ | 保留完整格式接口 |
| 套餐ID查询 | ⬜ | |
| Period字段映射 | ⬜ | |
| 支付方式列表 | ⬜ | |
| 错误处理 | ⬜ | |

**测试完成时间**: ___________  
**测试人员**: ___________  
**总体评价**: ___________
