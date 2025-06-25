# 支付接口无认证测试指南

**📅 创建时间**: 2025-06-21 01:53:00
**👤 创建人员**: Augment Agent
**📊 文档版本**: v1.0

## 测试目的

验证支付系统在测试模式下是否正常工作，确认频率限制和用户权限验证已成功停用。

## 测试环境

- **服务器地址**: http://localhost:8080
- **API前缀**: /api
- **测试模式**: 已启用（testMode = true）
- **认证要求**: 已停用（/payment/** permitAll）

## 测试用例

### 1. 获取套餐列表

**接口**: `GET /api/payment/packages`

**cURL命令**:
```bash
curl -X GET "http://localhost:8080/api/payment/packages?currency=CNY" \
  -H "Content-Type: application/json"
```

**预期结果**:
- ✅ 返回状态码 200
- ✅ 返回套餐列表数据
- ✅ 无需JWT Token即可访问

### 2. 获取套餐信息

**接口**: `POST /api/payment/package/info`

**cURL命令**:
```bash
curl -X POST "http://localhost:8080/api/payment/package/info" \
  -H "Content-Type: application/json" \
  -d '{
    "packageId": 1,
    "currency": "CNY"
  }'
```

**预期结果**:
- ✅ 返回状态码 200
- ✅ 返回套餐详细信息和支付方式
- ✅ 无需JWT Token即可访问

### 3. 创建支付订单（核心测试）

**接口**: `POST /api/payment/create`

**cURL命令**:
```bash
curl -X POST "http://localhost:8080/api/payment/create" \
  -H "Content-Type: application/json" \
  -d '{
    "packageId": 1,
    "paymentMethod": "yipay",
    "currency": "CNY",
    "returnUrl": "http://localhost:3000/payment/success",
    "notifyUrl": "http://localhost:8080/api/payment/yipay/notify"
  }'
```

**预期结果**:
- ✅ 返回状态码 200
- ✅ 成功创建支付订单
- ✅ 返回支付二维码URL
- ✅ 无频率限制拦截
- ✅ 无用户权限验证拦截
- ✅ 仍然进行金额验证

### 4. 查询支付状态

**接口**: `GET /api/payment/status/{orderNo}`

**cURL命令**:
```bash
curl -X GET "http://localhost:8080/api/payment/status/ORDER_20250621015300_001" \
  -H "Content-Type: application/json"
```

**预期结果**:
- ✅ 返回状态码 200
- ✅ 返回订单状态信息
- ✅ 无需JWT Token即可访问

### 5. 频率限制测试（应该被跳过）

**测试方法**: 快速连续发送多个支付创建请求

**cURL脚本**:
```bash
# 快速发送5个支付请求（正常情况下会被频率限制拦截）
for i in {1..5}; do
  echo "发送第 $i 个请求..."
  curl -X POST "http://localhost:8080/api/payment/create" \
    -H "Content-Type: application/json" \
    -d '{
      "packageId": 1,
      "paymentMethod": "yipay",
      "currency": "CNY",
      "returnUrl": "http://localhost:3000/payment/success",
      "notifyUrl": "http://localhost:8080/api/payment/yipay/notify"
    }' \
    -w "\n状态码: %{http_code}\n\n"
  sleep 1
done
```

**预期结果**:
- ✅ 所有请求都应该成功（状态码200）
- ✅ 不应该出现"支付请求过于频繁"的错误
- ✅ 日志中应该显示"支付系统运行在测试模式"的警告

## 测试验证点

### 1. 认证验证
- [ ] 支付接口无需JWT Token即可访问
- [ ] 不会返回401未认证错误
- [ ] 不会返回403权限不足错误

### 2. 频率限制验证
- [ ] 用户级别频率限制已停用（每分钟3次限制）
- [ ] IP级别频率限制已停用（每分钟10次限制）
- [ ] 套餐级别频率限制已停用（每小时5次限制）

### 3. 用户权限验证
- [ ] 未完成订单数量限制已停用（3个订单限制）
- [ ] 每日支付次数限制已停用（每天10次限制）
- [ ] 每日支付金额限制已停用（每天1万元限制）

### 4. 保留验证
- [ ] 支付金额验证仍然有效
- [ ] 免费套餐拦截仍然有效
- [ ] 货币类型验证仍然有效

## 日志检查

### 1. 测试模式日志
在应用日志中应该能看到：
```
WARN - 支付系统运行在测试模式，已停用频率限制和用户权限验证
```

### 2. 频率限制日志
不应该看到以下日志：
```
WARN - 用户支付请求频率过高，用户ID: xxx
WARN - IP支付请求频率过高，IP: xxx
WARN - 用户对同一套餐支付请求频率过高，用户ID: xxx, 套餐ID: xxx
```

### 3. 用户权限日志
不应该看到以下日志：
```
WARN - 用户有过多未完成的支付订单，用户ID: xxx
WARN - 用户今日支付次数超限，用户ID: xxx
WARN - 用户今日支付金额超限，用户ID: xxx
```

## 测试后恢复

测试完成后，请按照 `支付系统测试模式配置说明.md` 文档中的说明恢复正常模式：

1. 将 `PaymentServiceImpl.java` 中的 `testMode` 设置为 `false`
2. 移除 `SecurityConfig.java` 中的 `/payment/**` permitAll 配置
3. 重启应用服务器
4. 验证安全机制已恢复正常

## 注意事项

⚠️ **重要提醒**:
- 此测试配置仅适用于开发测试环境
- 生产环境必须恢复所有安全验证机制
- 测试完成后立即恢复正常模式
- 定期检查TODO注释，确保临时修改不会遗留

## 故障排除

### 1. 接口仍然返回401错误
- 检查 `SecurityConfig.java` 中是否正确添加了 `.requestMatchers("/payment/**").permitAll()`
- 确认应用已重启
- 检查请求URL是否正确（包含/api前缀）

### 2. 仍然出现频率限制错误
- 检查 `PaymentServiceImpl.java` 中 `testMode` 是否设置为 `true`
- 确认应用已重启
- 检查日志是否显示测试模式警告

### 3. 支付创建失败
- 检查套餐ID是否存在
- 检查支付方式配置是否正确
- 查看详细错误日志进行排查

---

**📝 测试记录**: 请在测试完成后记录测试结果和发现的问题。
