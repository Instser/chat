# 支付API接口文档

## 概述

本文档描述了金语聊天系统的支付相关API接口，包括套餐管理、订单创建、支付处理等功能。

## 基础信息

- **基础URL**: `http://localhost:8080/api/payment`
- **认证方式**: 暂时使用固定用户ID，后续将集成JWT认证
- **响应格式**: 统一使用Result包装类
- **支持货币**: USD（美元）、CNY（人民币）

## API接口列表

### 1. 获取套餐列表

**接口地址**: `GET /packages`

**请求参数**: 无

**响应示例**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": "1234567890",
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
    }
  ]
}
```

### 2. 根据类型获取套餐

**接口地址**: `GET /packages/{type}`

**路径参数**:
- `type`: 套餐类型 (basic/premium/enterprise)

**响应格式**: 同上

### 3. 创建支付订单

**接口地址**: `POST /create`

**请求参数**:
```json
{
  "packageId": 1234567890,
  "currency": "USD",
  "paymentMethod": "alipay"
}
```

**参数说明**:
- `packageId`: 套餐ID（必填）
- `currency`: 货币类型，USD或CNY（必填）
- `paymentMethod`: 支付方式，目前支持alipay（必填）

**响应示例**:
```json
{
  "code": 200,
  "message": "支付订单创建成功",
  "data": {
    "orderNo": "JY20240101120000001",
    "paymentRequestId": "PAY20240101120000001",
    "paymentUrl": "https://open-sg-global.alipay.com/...",
    "amount": 9.99,
    "currency": "USD",
    "paymentStatus": 0,
    "success": true
  }
}
```

### 4. 查询支付状态

**接口地址**: `GET /status/{orderNo}`

**路径参数**:
- `orderNo`: 订单号

**响应示例**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": "1234567890",
    "orderNo": "JY20240101120000001",
    "userId": "1",
    "packageId": "1234567890",
    "packageName": "基础套餐",
    "amount": 9.99,
    "currency": "USD",
    "paymentMethod": "alipay",
    "paymentStatus": 1,
    "paymentStatusDesc": "已支付",
    "paymentTime": "2024-01-01T12:05:00",
    "transactionId": "2024010112050001",
    "createTime": "2024-01-01T12:00:00"
  }
}
```

### 5. 获取用户订单列表

**接口地址**: `GET /orders`

**请求参数**:
- `userId`: 用户ID（可选，默认为1）

**响应格式**: 返回订单数组，格式同查询支付状态

### 6. 取消支付订单

**接口地址**: `POST /cancel/{orderNo}`

**路径参数**:
- `orderNo`: 订单号

**响应示例**:
```json
{
  "code": 200,
  "message": "订单取消成功",
  "data": "订单取消成功"
}
```

### 7. 支付宝回调接口

**接口地址**: `POST /alipay/notify`

**说明**: 此接口由支付宝系统调用，用于接收支付结果通知

**请求头**:
- `request-time`: 请求时间戳
- `client-id`: 客户端ID
- `signature`: 签名

**响应**: 返回"SUCCESS"或"FAIL"

## 支付状态说明

| 状态码 | 状态描述 | 说明 |
|--------|----------|------|
| 0 | 未支付 | 订单已创建，等待支付 |
| 1 | 已支付 | 支付成功 |
| 2 | 已取消 | 订单已取消 |
| 3 | 已退款 | 订单已退款 |

## 错误码说明

| 错误码 | 错误信息 | 说明 |
|--------|----------|------|
| 400 | 参数错误 | 请求参数不正确 |
| 404 | 资源不存在 | 套餐或订单不存在 |
| 500 | 系统异常 | 服务器内部错误 |

## 支付流程

1. **获取套餐列表**: 调用`GET /packages`获取可用套餐
2. **创建支付订单**: 调用`POST /create`创建支付订单
3. **跳转支付页面**: 使用返回的`paymentUrl`跳转到支付宝支付页面
4. **支付完成**: 用户完成支付后，支付宝会回调通知接口
5. **查询支付状态**: 可通过`GET /status/{orderNo}`查询最新支付状态

## 配置说明

### 支付宝配置

在`application.yml`中配置支付宝相关参数：

```yaml
alipay:
  client-id: ${ALIPAY_CLIENT_ID:}
  private-key: ${ALIPAY_PRIVATE_KEY:}
  public-key: ${ALIPAY_PUBLIC_KEY:}
  sandbox: true
  return-url: http://localhost:3000/payment/success
  notify-url: http://localhost:8080/api/payment/alipay/notify
```

### 环境变量

建议通过环境变量配置敏感信息：
- `ALIPAY_CLIENT_ID`: 支付宝应用ID
- `ALIPAY_PRIVATE_KEY`: 商户私钥
- `ALIPAY_PUBLIC_KEY`: 支付宝公钥

## 注意事项

1. **沙箱环境**: 当前配置为沙箱环境，生产环境需要修改相关配置
2. **回调URL**: 支付回调URL需要公网可访问
3. **签名验证**: 所有支付回调都会进行签名验证
4. **货币支持**: 同时支持USD和CNY，根据套餐配置自动选择价格
5. **订单过期**: 订单创建后2小时自动过期
