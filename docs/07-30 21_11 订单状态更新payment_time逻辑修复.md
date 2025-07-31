# 07-30 21:11 订单状态更新payment_time逻辑修复

## 问题概述

在订单状态自动处理时发现了一个严重的逻辑错误：当订单状态从0（待支付）变更为2（已取消）时，系统仍然会设置`payment_time`字段，这在业务逻辑上是错误的。用户并没有实际完成支付，不应该记录支付时间。

## 问题分析

### 🚨 原有错误逻辑

#### 1. **OrderServiceImpl.updatePaymentStatus()**
```java
// 错误：无论什么状态变更都设置当前时间为payment_time
LocalDateTime now = LocalDateTime.now();
int result = baseMapper.updatePaymentStatus(orderNo, paymentStatus, transactionId, now, now);
```

#### 2. **UserOrderDao.updatePaymentStatus()**
```sql
-- 错误：总是更新payment_time字段
UPDATE t_user_order SET 
    payment_status = #{paymentStatus}, 
    transaction_id = #{transactionId}, 
    payment_time = #{paymentTime},     -- 这里总是被设置
    update_time = #{updateTime} 
WHERE order_no = #{orderNo} AND deleted = 0
```

### 🔍 问题影响

#### 1. **数据逻辑错误**
- 订单状态为2（已取消）但有`payment_time`
- 订单状态为3（已退款）时`payment_time`可能被覆盖
- 数据分析时会产生错误的统计结果

#### 2. **业务逻辑混乱**
- 支付时间应该只在实际支付成功时设置
- 取消、退款等操作不应该影响原始支付时间

#### 3. **统计报表错误**
```sql
-- 这种查询会包含错误的数据
SELECT DATE(payment_time) as payment_date,
       COUNT(CASE WHEN payment_status = 2 THEN 1 END) as canceled_orders
FROM t_user_order 
WHERE payment_time IS NOT NULL  -- 包含了不应该有payment_time的取消订单
```

## 修复方案

### 🔧 技术实现

#### 1. **OrderService接口扩展**
```java
// 原有方法保持兼容性
boolean updatePaymentStatus(String orderNo, Integer paymentStatus, String transactionId);

// 新增方法，明确控制是否设置支付时间
boolean updatePaymentStatus(String orderNo, Integer paymentStatus, String transactionId, boolean setPaymentTime);
```

#### 2. **UserOrderDao新增方法**
```java
// 原有方法：总是设置支付时间
@Update("UPDATE t_user_order SET payment_status = #{paymentStatus}, transaction_id = #{transactionId}, " +
        "payment_time = #{paymentTime}, update_time = #{updateTime} WHERE order_no = #{orderNo} AND deleted = 0")
int updatePaymentStatus(String orderNo, Integer paymentStatus, String transactionId,
                       LocalDateTime paymentTime, LocalDateTime updateTime);

// 新增方法：不设置支付时间
@Update("UPDATE t_user_order SET payment_status = #{paymentStatus}, transaction_id = #{transactionId}, " +
        "update_time = #{updateTime} WHERE order_no = #{orderNo} AND deleted = 0")
int updatePaymentStatusWithoutTime(String orderNo, Integer paymentStatus, String transactionId,
                                  LocalDateTime updateTime);
```

#### 3. **OrderServiceImpl智能逻辑**
```java
@Override
public boolean updatePaymentStatus(String orderNo, Integer paymentStatus, String transactionId) {
    // 默认行为：只有支付成功时才设置支付时间
    return updatePaymentStatus(orderNo, paymentStatus, transactionId, paymentStatus == 1);
}

@Override
public boolean updatePaymentStatus(String orderNo, Integer paymentStatus, String transactionId, boolean setPaymentTime) {
    return distributedLockService.executeOrderLock(orderNo, () -> {
        LocalDateTime now = LocalDateTime.now();
        int result;
        
        if (setPaymentTime && paymentStatus == 1) {
            // 支付成功时设置支付时间
            result = baseMapper.updatePaymentStatus(orderNo, paymentStatus, transactionId, now, now);
        } else {
            // 其他状态变更不设置支付时间
            result = baseMapper.updatePaymentStatusWithoutTime(orderNo, paymentStatus, transactionId, now);
        }
        
        return result > 0;
    });
}
```

### 📊 状态变更逻辑表

| 原状态 | 新状态 | 状态描述 | 是否设置payment_time | 说明 |
|--------|--------|----------|---------------------|------|
| 0 | 1 | 待支付 → 已支付 | ✅ 是 | 用户完成支付，记录支付时间 |
| 0 | 2 | 待支付 → 已取消 | ❌ 否 | 订单取消，无需支付时间 |
| 1 | 3 | 已支付 → 已退款 | ❌ 否 | 保持原支付时间不变 |
| 任意 | 任意 | 其他状态变更 | ❌ 否 | 只有首次支付成功才设置 |

## 测试验证

### 🧪 新增测试接口

#### 订单状态更新测试
```
POST /payment/admin/test/update-order-status/{orderNo}
参数：
- newStatus: 新的支付状态
- transactionId: 交易号（可选）

返回：
- updateSuccess: 更新是否成功
- orderBefore: 更新前的订单状态
- orderAfter: 更新后的订单状态
- paymentTimeSet: 是否设置了支付时间
- statusDescription: 状态描述
```

### 📋 测试用例

#### 1. **测试取消订单不设置支付时间**
```bash
# 创建待支付订单后取消
POST /payment/admin/test/update-order-status/ORDER_TEST_001
{
  "newStatus": 2,
  "transactionId": null
}

# 期望结果：
# - paymentTimeSet: false
# - orderAfter.paymentTime: null
```

#### 2. **测试支付成功设置支付时间**
```bash
# 订单支付成功
POST /payment/admin/test/update-order-status/ORDER_TEST_002
{
  "newStatus": 1,
  "transactionId": "TXN_123456"
}

# 期望结果：
# - paymentTimeSet: true
# - orderAfter.paymentTime: 当前时间
```

#### 3. **测试退款不影响支付时间**
```bash
# 已支付订单申请退款
POST /payment/admin/test/update-order-status/ORDER_TEST_003
{
  "newStatus": 3,
  "transactionId": "TXN_123456"
}

# 期望结果：
# - paymentTimeSet: false（不重新设置）
# - orderAfter.paymentTime: 保持原支付时间
```

## 影响范围

### ✅ 正面影响

1. **数据准确性**
   - 支付时间只在实际支付时设置
   - 取消、退款操作不会产生错误的支付时间
   - 统计报表数据更加准确

2. **业务逻辑清晰**
   - 状态变更逻辑更加合理
   - 支付时间语义明确
   - 便于后续业务扩展

3. **向后兼容**
   - 原有API调用方式保持不变
   - 默认行为符合预期
   - 不影响现有功能

### ⚠️ 注意事项

1. **历史数据**
   - 已存在的错误数据需要手动清理
   - 建议运行数据修复脚本

2. **监控告警**
   - 关注状态更新的成功率
   - 监控支付时间设置的准确性

## 数据修复建议

### 🔧 清理历史错误数据

```sql
-- 清理已取消订单的错误支付时间
UPDATE t_user_order 
SET payment_time = NULL 
WHERE payment_status = 2 
  AND payment_time IS NOT NULL 
  AND deleted = 0;

-- 清理已退款订单中不合理的支付时间
-- （如果退款时间早于支付时间，说明支付时间被错误覆盖）
UPDATE t_user_order 
SET payment_time = create_time  -- 使用创建时间作为临时值，需要人工核实
WHERE payment_status = 3 
  AND payment_time > update_time 
  AND deleted = 0;
```

## 总结

这次修复解决了订单状态更新时`payment_time`字段设置不当的问题，确保：

1. **只有支付成功时才设置支付时间**
2. **取消、退款等操作不会错误设置支付时间**
3. **保持API向后兼容性**
4. **提供测试接口验证修复效果**

修复后的逻辑更加符合业务语义，为后续的数据分析和业务扩展提供了准确的数据基础。
