# 海外支付宝SDK集成部署指南

## 概述

本指南详细说明如何配置和部署集成了海外支付宝SDK的金语聊天系统。

## 前置条件

### 1. 系统要求
- Java 17 或更高版本
- MySQL 8.0 或更高版本
- Maven 3.6 或更高版本

### 2. 支付宝商户账号
- 支付宝海外商户账号
- 应用ID (Client ID)
- RSA密钥对（商户私钥和支付宝公钥）

## 配置步骤

### 1. 数据库配置

#### 执行SQL脚本
```bash
# 执行支付相关表结构创建
mysql -u your_username -p your_database < sql/payment_tables.sql
```

#### 验证表结构
确保以下表已创建：
- `t_package` - 套餐表
- `t_user_order` - 用户订单表（已更新）
- `t_payment_config` - 支付配置表
- `t_user_account` - 用户账户表

### 2. 支付宝配置

#### 获取商户信息
1. 登录支付宝开放平台：https://open.alipay.com/
2. 创建应用并获取应用ID (Client ID)
3. 生成RSA密钥对
4. 上传公钥到支付宝平台
5. 获取支付宝公钥

#### 生成RSA密钥对
```bash
# 生成私钥
openssl genrsa -out rsa_private_key.pem 2048

# 生成公钥
openssl rsa -in rsa_private_key.pem -pubout -out rsa_public_key.pem

# 转换为PKCS8格式（Java使用）
openssl pkcs8 -topk8 -inform PEM -in rsa_private_key.pem -outform PEM -nocrypt -out rsa_private_key_pkcs8.pem
```

### 3. 环境变量配置

#### 开发环境
创建 `.env` 文件：
```bash
# 支付宝配置
ALIPAY_CLIENT_ID=your_client_id
ALIPAY_PRIVATE_KEY=your_private_key_content
ALIPAY_PUBLIC_KEY=alipay_public_key_content
ALIPAY_SANDBOX=true
ALIPAY_RETURN_URL=http://localhost:3000/payment/success
ALIPAY_NOTIFY_URL=http://your-domain.com/api/payment/alipay/notify
```

#### 生产环境
```bash
# 支付宝配置
ALIPAY_CLIENT_ID=your_production_client_id
ALIPAY_PRIVATE_KEY=your_production_private_key
ALIPAY_PUBLIC_KEY=alipay_production_public_key
ALIPAY_SANDBOX=false
ALIPAY_RETURN_URL=https://your-domain.com/payment/success
ALIPAY_NOTIFY_URL=https://your-domain.com/api/payment/alipay/notify
```

### 4. 应用配置

#### application.yml 配置
```yaml
alipay:
  client-id: ${ALIPAY_CLIENT_ID:}
  private-key: ${ALIPAY_PRIVATE_KEY:}
  public-key: ${ALIPAY_PUBLIC_KEY:}
  sandbox: ${ALIPAY_SANDBOX:true}
  return-url: ${ALIPAY_RETURN_URL:http://localhost:3000/payment/success}
  notify-url: ${ALIPAY_NOTIFY_URL:http://localhost:8080/api/payment/alipay/notify}
```

## 部署步骤

### 1. 编译项目
```bash
mvn clean package -DskipTests
```

### 2. 运行应用
```bash
# 开发环境
java -jar target/chat-0.0.1-SNAPSHOT.jar

# 生产环境
java -jar -Dspring.profiles.active=prod target/chat-0.0.1-SNAPSHOT.jar
```

### 3. 验证部署

#### 检查应用启动
```bash
curl http://localhost:8080/api/test/health
```

#### 检查套餐接口
```bash
curl http://localhost:8080/api/payment/packages
```

#### 检查支付配置
查看日志确认支付宝配置是否正确加载。

## 测试流程

### 1. 沙箱环境测试

#### 创建测试订单
```bash
curl -X POST http://localhost:8080/api/payment/create \
  -H "Content-Type: application/json" \
  -d '{
    "packageId": 1,
    "currency": "USD",
    "paymentMethod": "alipay"
  }'
```

#### 模拟支付回调
```bash
curl -X POST http://localhost:8080/api/payment/alipay/notify \
  -H "Content-Type: application/json" \
  -H "request-time: 1640995200000" \
  -H "client-id: your_client_id" \
  -H "signature: test_signature" \
  -d '{"paymentRequestId":"PAY123","paymentStatus":"SUCCESS","paymentId":"TXN123"}'
```

### 2. 生产环境测试

1. 确保回调URL可公网访问
2. 使用真实支付宝账号进行小额测试
3. 验证支付回调处理
4. 检查订单状态更新

## 监控和维护

### 1. 日志监控
- 支付请求日志
- 支付回调日志
- 错误日志

### 2. 数据库监控
- 订单状态统计
- 支付成功率
- 异常订单排查

### 3. 性能监控
- API响应时间
- 数据库连接池状态
- 内存使用情况

## 故障排除

### 1. 常见问题

#### 支付配置错误
- 检查环境变量是否正确设置
- 验证RSA密钥格式
- 确认应用ID和密钥匹配

#### 回调验证失败
- 检查回调URL是否可访问
- 验证签名算法
- 确认支付宝公钥正确

#### 订单创建失败
- 检查套餐配置
- 验证货币类型支持
- 确认数据库连接正常

### 2. 调试技巧

#### 启用详细日志
```yaml
logging:
  level:
    com.instser.chat.service.serviceimpl.PaymentServiceImpl: DEBUG
    com.alipay.global: DEBUG
```

#### 检查配置加载
```java
@Autowired
private AlipayConfig alipayConfig;

// 在启动时检查配置
log.info("Alipay config complete: {}", alipayConfig.isConfigComplete());
```

## 安全注意事项

1. **密钥安全**: 私钥不要提交到代码仓库
2. **HTTPS**: 生产环境必须使用HTTPS
3. **签名验证**: 所有回调必须验证签名
4. **访问控制**: 限制支付接口的访问权限
5. **日志脱敏**: 敏感信息不要记录到日志

## 性能优化

1. **数据库索引**: 确保订单表有适当的索引
2. **连接池**: 合理配置数据库连接池
3. **缓存**: 对套餐信息进行缓存
4. **异步处理**: 支付回调处理考虑异步化

## 扩展功能

1. **多支付方式**: 支持微信支付、PayPal等
2. **订单管理**: 增加订单查询、退款功能
3. **报表统计**: 支付数据分析和报表
4. **通知系统**: 支付成功通知用户
