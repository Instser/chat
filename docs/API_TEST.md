# API接口连通性测试

## 测试环境准备

### 1. 启动应用
```bash
cd jinyu-chat/chat
mvn spring-boot:run
```

### 2. 等待应用启动完成
查看日志确认应用启动成功，应该看到类似以下信息：
```
Started ChatApplication in X.XXX seconds
数据初始化完成
```

## 基础连通性测试

### 1. 健康检查
```bash
curl http://localhost:8080/api/test/health
```
**期望响应**: 
```json
{
  "code": 200,
  "message": "操作成功",
  "data": "服务正常运行"
}
```

### 2. 测试基础接口
```bash
curl http://localhost:8080/api/test/hello
```

## 支付接口测试

### 1. 获取套餐列表
```bash
curl -X GET http://localhost:8080/api/payment/packages \
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
      "description": "适合个人用户的基础AI对话套餐",
      "packageType": "basic",
      "priceUsd": 9.99,
      "priceCny": 69.99,
      "validityDays": 30,
      "chatCount": 1000,
      "modelPermissions": ["gpt-3.5-turbo", "qwen-turbo"],
      "isEnabled": true,
      "sortOrder": 1
    }
  ]
}
```

### 2. 根据类型获取套餐
```bash
curl -X GET http://localhost:8080/api/payment/packages/basic \
  -H "Content-Type: application/json"
```

### 3. 创建支付订单（无配置测试）
```bash
curl -X POST http://localhost:8080/api/payment/create \
  -H "Content-Type: application/json" \
  -d '{
    "packageId": 1,
    "currency": "USD",
    "paymentMethod": "alipay",
    "returnUrl": "http://localhost:3000/payment/success"
  }'
```

**期望响应**（配置未完成时）:
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

### 4. 查询订单状态
```bash
curl -X GET http://localhost:8080/api/payment/status/JY20240101120000001 \
  -H "Content-Type: application/json"
```

### 5. 获取用户订单列表
```bash
curl -X GET "http://localhost:8080/api/payment/orders?userId=1" \
  -H "Content-Type: application/json"
```

### 6. 取消订单
```bash
curl -X POST http://localhost:8080/api/payment/cancel/JY20240101120000001 \
  -H "Content-Type: application/json"
```

## 数据库连通性测试

### 1. 检查数据库表
连接到MySQL数据库，执行以下SQL：

```sql
-- 检查套餐表
SELECT * FROM t_package WHERE deleted = 0;

-- 检查订单表结构
DESCRIBE t_user_order;

-- 检查是否有测试数据
SELECT COUNT(*) as package_count FROM t_package WHERE deleted = 0;
SELECT COUNT(*) as order_count FROM t_user_order WHERE deleted = 0;
```

### 2. 验证数据初始化
应该看到3个默认套餐：
- 基础套餐 (basic)
- 高级套餐 (premium) 
- 企业套餐 (enterprise)

## 错误场景测试

### 1. 无效套餐ID
```bash
curl -X POST http://localhost:8080/api/payment/create \
  -H "Content-Type: application/json" \
  -d '{
    "packageId": 999999,
    "currency": "USD",
    "paymentMethod": "alipay"
  }'
```

### 2. 无效货币类型
```bash
curl -X POST http://localhost:8080/api/payment/create \
  -H "Content-Type: application/json" \
  -d '{
    "packageId": 1,
    "currency": "EUR",
    "paymentMethod": "alipay"
  }'
```

### 3. 缺少必填参数
```bash
curl -X POST http://localhost:8080/api/payment/create \
  -H "Content-Type: application/json" \
  -d '{
    "currency": "USD",
    "paymentMethod": "alipay"
  }'
```

## 日志检查

### 1. 应用启动日志
检查以下关键日志：
- 数据库连接成功
- MyBatis-Plus初始化
- 支付宝配置加载（应该显示配置不完整的警告）
- 数据初始化完成

### 2. API调用日志
每次API调用都应该有相应的日志记录，包括：
- 请求参数
- 处理结果
- 错误信息（如果有）

## 性能测试

### 1. 并发套餐查询
```bash
# 使用ab工具进行简单的并发测试
ab -n 100 -c 10 http://localhost:8080/api/payment/packages
```

### 2. 数据库连接池监控
访问Druid监控页面：
```
http://localhost:8080/api/druid/
用户名: admin
密码: 123456
```

## 预期结果

### ✅ 成功场景
1. 应用正常启动，无错误日志
2. 套餐接口返回3个默认套餐
3. 创建订单接口返回配置未完成错误（正常，因为没有支付宝配置）
4. 数据库表结构正确，有默认数据
5. 所有API接口响应格式统一

### ❌ 失败场景
1. 应用启动失败 - 检查数据库连接
2. 套餐接口返回空 - 检查数据初始化
3. API接口500错误 - 检查代码逻辑
4. 数据库连接失败 - 检查配置

## 故障排除

### 1. 应用启动失败
- 检查JDK版本（必须是17）
- 检查数据库连接配置
- 检查端口占用情况

### 2. 接口返回500错误
- 查看应用日志
- 检查数据库表是否存在
- 验证SQL语句是否正确

### 3. 数据初始化失败
- 检查数据库权限
- 验证表结构是否正确
- 查看MyBatis-Plus配置

## 下一步测试

完成基础连通性测试后，可以进行：
1. 配置真实的支付宝参数
2. 端到端支付流程测试
3. 支付回调测试
4. 压力测试
