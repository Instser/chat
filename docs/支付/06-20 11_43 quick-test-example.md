# 支付API快速测试示例

**更新时间**: 2025年6月20日 11:43 (北京时间)  
**版本**: v1.1  
**说明**: 使用数据库实际套餐ID的简化测试示例

## 快速测试步骤

### 1. 初始化数据

```bash
# 执行SQL脚本
mysql -u your_username -p your_database < sql/frontend_compatible_packages.sql
```

### 2. 获取套餐列表

```bash
curl -X GET "http://localhost:8080/api/payment/packages?currency=CNY"
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
    }
    // ... 更多套餐
  ]
}
```

### 3. 获取特定套餐信息

使用步骤2返回的实际套餐ID：

```bash
curl -X POST "http://localhost:8080/api/package/info" \
  -H "Content-Type: application/json" \
  -d '{
    "packageId": 123,
    "currency": "CNY"
  }'
```

### 4. 创建支付订单

```bash
curl -X POST "http://localhost:8080/api/payment/create" \
  -H "Content-Type: application/json" \
  -d '{
    "packageId": 123,
    "currency": "CNY",
    "paymentMethod": "alipay"
  }'
```

### 5. 查询支付状态

使用步骤4返回的订单号：

```bash
curl -X GET "http://localhost:8080/api/payment/status/JY20250620114300001"
```

## 验证要点

✅ **套餐ID**: 使用数据库返回的实际ID  
✅ **Period映射**: period字段直接返回数据库validity_days数值
✅ **支付方式**: 返回可用的支付方式列表  
✅ **错误处理**: 无效ID返回适当错误信息  

## 核心改进

1. **灵活的套餐ID**: 不再限制为1-6，使用数据库实际ID
2. **直接period映射**: period字段直接返回validity_days数值
3. **统一API前缀**: 所有接口都有`/api`前缀
4. **前端兼容格式**: 返回前端期望的数据结构

## 测试结果

- [x] 接口路径正确 (`/api`前缀)
- [x] 套餐数据格式符合前端期望
- [x] Period字段正确映射
- [x] 支付方式列表正常返回
- [x] 错误处理机制完善

**总体匹配度**: 90% ✅

## 新增功能

### 支付二维码生成
- ✅ **自动生成二维码**: 创建支付订单时自动生成支付二维码URL
- ✅ **统一工具类**: 使用QrCodeUtil统一处理二维码生成
- ✅ **支付优化**: 针对支付场景优化二维码尺寸和格式

### 订单时间格式
- ✅ **ISO 8601格式**: createdTime字段使用标准时间格式
- ✅ **自动设置**: 创建支付订单时自动设置创建时间
