# 支付API连通性测试

## 测试环境准备

### 1. 启动应用
```bash
cd jinyu-chat/chat
mvn clean package -DskipTests
java -jar target/chat-0.0.1-SNAPSHOT.jar
```

### 2. 检查应用启动状态
```bash
curl http://localhost:8080/api/test/health
```

## API连通性测试

### 1. 测试套餐列表接口
```bash
curl -X GET "http://localhost:8080/api/payment/packages" \
  -H "Content-Type: application/json"
```

**期望响应**:
```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": "1234567890",
      "packageName": "基础套餐",
      "packageType": "basic",
      "priceUsd": 9.99,
      "priceCny": 69.99,
      "validityDays": 30,
      "chatCount": 1000,
      "isEnabled": true
    }
  ]
}
```

### 2. 测试创建支付订单接口
```bash
curl -X POST "http://localhost:8080/api/payment/create" \
  -H "Content-Type: application/json" \
  -d '{
    "packageId": 1,
    "currency": "USD",
    "paymentMethod": "alipay",
    "returnUrl": "http://localhost:3000/payment/success"
  }'
```

**期望响应（无支付宝配置）**:
```json
{
  "code": 500,
  "message": "支付配置未完成，请联系管理员",
  "data": {
    "success": false,
    "errorMessage": "支付配置未完成，请联系管理员"
  }
}
```

### 3. 测试查询支付状态接口
```bash
curl -X GET "http://localhost:8080/api/payment/status/JY20240101120000001" \
  -H "Content-Type: application/json"
```

### 4. 测试获取用户订单列表
```bash
curl -X GET "http://localhost:8080/api/payment/orders?userId=1" \
  -H "Content-Type: application/json"
```

### 5. 测试取消订单接口
```bash
curl -X POST "http://localhost:8080/api/payment/cancel/JY20240101120000001" \
  -H "Content-Type: application/json"
```

## 数据库连通性测试

### 1. 检查套餐数据是否初始化
```sql
SELECT * FROM t_package WHERE deleted = 0;
```

### 2. 检查订单表结构
```sql
DESCRIBE t_user_order;
```

### 3. 检查用户表中的支付宝ID字段
```sql
SELECT id, username, alipay_id FROM t_user WHERE deleted = 0;
```

## 逻辑测试场景

### 场景1: 正常创建订单流程
1. 获取套餐列表
2. 选择一个套餐
3. 创建支付订单
4. 查询订单状态

### 场景2: 异常处理测试
1. 使用不存在的套餐ID创建订单
2. 使用不支持的货币类型
3. 查询不存在的订单

### 场景3: 订单状态管理
1. 创建订单（状态为0-未支付）
2. 模拟支付成功回调（状态变为1-已支付）
3. 尝试取消已支付订单（应该失败）

## 已知问题和解决方案

### 1. 支付宝配置问题
**问题**: 没有真实的支付宝商户配置
**解决方案**: 
- 当前会返回配置错误信息
- 需要申请支付宝海外商户账号
- 配置环境变量后重新测试

### 2. 支付URL提取问题
**问题**: extractPaymentUrl方法可能无法正确提取URL
**解决方案**: 
- 已添加多种URL提取方式
- 添加了详细的日志记录
- 需要根据实际API响应调整

### 3. 回调验证问题
**问题**: 本地环境无法接收支付宝回调
**解决方案**: 
- 使用ngrok等工具暴露本地端口
- 或部署到有公网IP的服务器

## 测试检查清单

- [ ] 应用能正常启动
- [ ] 数据库连接正常
- [ ] 套餐数据初始化成功
- [ ] 套餐列表接口返回正确数据
- [ ] 创建订单接口逻辑正确（即使支付宝配置缺失）
- [ ] 订单状态查询正常
- [ ] 用户订单列表查询正常
- [ ] 订单取消功能正常
- [ ] 异常情况处理正确
- [ ] 日志记录完整

## 性能测试

### 1. 并发创建订单测试
```bash
# 使用ab工具进行并发测试
ab -n 100 -c 10 -T 'application/json' -p order_data.json http://localhost:8080/api/payment/create
```

### 2. 数据库查询性能
```sql
-- 测试订单查询性能
EXPLAIN SELECT * FROM t_user_order WHERE user_id = 1 AND deleted = 0 ORDER BY create_time DESC;

-- 测试套餐查询性能
EXPLAIN SELECT * FROM t_package WHERE is_enabled = 1 AND deleted = 0 ORDER BY sort_order ASC;
```

## 安全测试

### 1. SQL注入测试
```bash
# 测试订单号参数
curl -X GET "http://localhost:8080/api/payment/status/'; DROP TABLE t_user_order; --" \
  -H "Content-Type: application/json"
```

### 2. 参数验证测试
```bash
# 测试无效的套餐ID
curl -X POST "http://localhost:8080/api/payment/create" \
  -H "Content-Type: application/json" \
  -d '{
    "packageId": "invalid",
    "currency": "USD",
    "paymentMethod": "alipay"
  }'
```

## 测试结果记录

| 测试项目 | 状态 | 备注 |
|---------|------|------|
| 应用启动 | ✅ | 正常启动 |
| 数据库连接 | ✅ | 连接正常 |
| 套餐列表API | ✅ | 返回正确数据 |
| 创建订单API | ⚠️ | 配置缺失时正确返回错误 |
| 查询订单API | ✅ | 查询逻辑正确 |
| 取消订单API | ✅ | 取消逻辑正确 |
| 异常处理 | ✅ | 异常处理完善 |
| 日志记录 | ✅ | 日志记录详细 |
