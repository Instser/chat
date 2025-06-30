-- =====================================================
-- 用户订单逻辑优化数据库修改脚本
-- 创建时间: 2025-06-30 17:09
-- 版本: v1.2.0-order-optimization
-- 说明: 支持用户发起新订单时自动作废旧订单的功能
-- =====================================================

-- 检查数据库连接
SELECT 'Database connection test' AS status, NOW() AS current_time;

-- =====================================================
-- 1. 验证现有表结构
-- =====================================================

-- 检查t_user_order表结构
DESCRIBE t_user_order;

-- 验证订单状态字段说明
SELECT 
    'Order Status Mapping' AS info,
    '0=未支付, 1=已支付, 2=已取消, 3=已退款' AS status_mapping;

-- =====================================================
-- 2. 数据库索引优化（如果需要）
-- =====================================================

-- 检查是否存在用户ID和支付状态的复合索引
SELECT 
    INDEX_NAME,
    COLUMN_NAME,
    SEQ_IN_INDEX
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_user_order' 
  AND COLUMN_NAME IN ('user_id', 'payment_status', 'expire_time')
ORDER BY INDEX_NAME, SEQ_IN_INDEX;

-- 如果不存在，创建复合索引以优化批量取消订单的性能
-- 注意：这个索引可能已经存在，如果存在会报错但不影响功能
CREATE INDEX IF NOT EXISTS idx_user_order_cancel 
ON t_user_order (user_id, payment_status, expire_time, deleted);

-- =====================================================
-- 3. 验证现有数据
-- =====================================================

-- 查看当前订单状态分布
SELECT 
    payment_status,
    CASE 
        WHEN payment_status = 0 THEN '未支付'
        WHEN payment_status = 1 THEN '已支付'
        WHEN payment_status = 2 THEN '已取消'
        WHEN payment_status = 3 THEN '已退款'
        ELSE '未知状态'
    END AS status_name,
    COUNT(*) AS count
FROM t_user_order 
WHERE deleted = 0
GROUP BY payment_status
ORDER BY payment_status;

-- 查看有多个进行中订单的用户
SELECT 
    user_id,
    COUNT(*) AS pending_orders_count,
    GROUP_CONCAT(order_no ORDER BY create_time DESC) AS order_numbers
FROM t_user_order 
WHERE payment_status = 0 
  AND expire_time > NOW() 
  AND deleted = 0
GROUP BY user_id
HAVING COUNT(*) > 1
ORDER BY pending_orders_count DESC;

-- =====================================================
-- 4. 测试新增的SQL方法
-- =====================================================

-- 测试批量取消用户待支付订单的SQL（仅查询，不执行更新）
-- 这个查询模拟了cancelUserPendingOrders方法的逻辑
SELECT 
    id,
    order_no,
    user_id,
    payment_status,
    expire_time,
    create_time,
    '将被取消' AS action
FROM t_user_order 
WHERE user_id = 1  -- 示例用户ID，实际使用时会是动态参数
  AND payment_status = 0 
  AND expire_time > NOW() 
  AND deleted = 0;

-- =====================================================
-- 5. 数据一致性检查
-- =====================================================

-- 检查是否有过期但未取消的订单
SELECT 
    COUNT(*) AS expired_unpaid_orders,
    'These orders should be handled by scheduled tasks' AS note
FROM t_user_order 
WHERE payment_status = 0 
  AND expire_time < NOW() 
  AND deleted = 0;

-- 检查是否有逻辑删除标记异常的订单
SELECT 
    COUNT(*) AS deleted_orders_count,
    'Logically deleted orders' AS note
FROM t_user_order 
WHERE deleted = 1;

-- =====================================================
-- 6. 性能测试查询
-- =====================================================

-- 测试按用户ID查询待支付订单的性能
EXPLAIN SELECT * FROM t_user_order 
WHERE user_id = 1 
  AND payment_status = 0 
  AND expire_time > NOW() 
  AND deleted = 0;

-- 测试批量更新操作的执行计划
EXPLAIN UPDATE t_user_order 
SET payment_status = 2, update_time = NOW() 
WHERE user_id = 1 
  AND payment_status = 0 
  AND expire_time > NOW() 
  AND deleted = 0;

-- =====================================================
-- 7. 监控查询
-- =====================================================

-- 创建监控视图：用户订单统计
CREATE OR REPLACE VIEW v_user_order_stats AS
SELECT 
    user_id,
    COUNT(*) AS total_orders,
    SUM(CASE WHEN payment_status = 0 AND expire_time > NOW() THEN 1 ELSE 0 END) AS pending_orders,
    SUM(CASE WHEN payment_status = 1 THEN 1 ELSE 0 END) AS paid_orders,
    SUM(CASE WHEN payment_status = 2 THEN 1 ELSE 0 END) AS cancelled_orders,
    MAX(create_time) AS last_order_time
FROM t_user_order 
WHERE deleted = 0
GROUP BY user_id;

-- 查看用户订单统计
SELECT * FROM v_user_order_stats 
WHERE pending_orders > 0 
ORDER BY pending_orders DESC, last_order_time DESC 
LIMIT 10;

-- =====================================================
-- 8. 验证脚本完成
-- =====================================================

SELECT 
    'Order Logic Optimization Database Script Completed' AS status,
    '2025-06-30 17:09' AS completion_time,
    'v1.2.0-order-optimization' AS version,
    'Ready for application deployment' AS next_step;

-- =====================================================
-- 脚本说明
-- =====================================================
/*
本脚本主要用于验证和优化用户订单逻辑相关的数据库操作：

1. 验证现有表结构和数据完整性
2. 创建必要的索引以优化批量取消订单的性能
3. 提供监控查询和视图便于运维管理
4. 测试新增SQL方法的性能和正确性

主要优化点：
- 支持用户发起新订单时自动取消旧的待支付订单
- 保持原有的频率限制和安全机制
- 使用数据库级别的原子操作保证数据一致性
- 添加必要的索引提升查询和更新性能

注意事项：
- 所有更新操作都是原子性的，确保数据一致性
- 只取消未支付且未过期的订单，已支付订单不受影响
- 保留完整的操作日志便于问题追踪和审计
*/
