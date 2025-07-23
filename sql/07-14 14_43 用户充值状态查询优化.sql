-- =====================================================
-- 用户充值状态查询优化脚本
-- 创建时间: 2025-07-14 14:43:00
-- 版本: v1.0
-- 说明: 优化用户充值状态判定的数据库查询性能
-- =====================================================

-- 1. 检查现有索引状态
-- =====================================================
SELECT 
    TABLE_NAME,
    INDEX_NAME,
    COLUMN_NAME,
    SEQ_IN_INDEX,
    CARDINALITY
FROM information_schema.STATISTICS 
WHERE TABLE_SCHEMA = 'chat' 
  AND TABLE_NAME = 't_user_order'
  AND INDEX_NAME LIKE '%user%'
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;

-- 2. 分析现有查询性能
-- =====================================================

-- 测试用户充值状态查询性能
EXPLAIN SELECT COUNT(*) FROM t_user_order 
WHERE user_id = 1 AND payment_status = 1 AND deleted = 0;

-- 测试用户非免费套餐查询性能
EXPLAIN SELECT COUNT(*) FROM t_user_order 
WHERE user_id = 1 AND payment_status = 1 AND package_id != 1 AND deleted = 0;

-- 测试用户首次支付时间查询性能
EXPLAIN SELECT MIN(payment_time) FROM t_user_order 
WHERE user_id = 1 AND payment_status = 1 AND deleted = 0;

-- 测试用户总支付金额查询性能
EXPLAIN SELECT COALESCE(SUM(amount), 0) FROM t_user_order 
WHERE user_id = 1 AND payment_status = 1 AND deleted = 0;

-- 3. 创建优化索引（如果不存在）
-- =====================================================

-- 检查并创建用户支付状态专用索引
SET @index_exists = (
    SELECT COUNT(*) 
    FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = 'chat' 
      AND TABLE_NAME = 't_user_order' 
      AND INDEX_NAME = 'idx_user_payment_status'
);

SET @sql_create_payment_index = CASE 
    WHEN @index_exists = 0 THEN 
        'CREATE INDEX idx_user_payment_status ON t_user_order (user_id, payment_status, deleted, package_id, payment_time, amount)'
    ELSE 
        'SELECT "索引 idx_user_payment_status 已存在" as message'
END;

PREPARE stmt FROM @sql_create_payment_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查并创建用户套餐支付状态索引
SET @package_index_exists = (
    SELECT COUNT(*) 
    FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = 'chat' 
      AND TABLE_NAME = 't_user_order' 
      AND INDEX_NAME = 'idx_user_package_payment'
);

SET @sql_create_package_index = CASE 
    WHEN @package_index_exists = 0 THEN 
        'CREATE INDEX idx_user_package_payment ON t_user_order (user_id, package_id, payment_status, deleted, payment_time)'
    ELSE 
        'SELECT "索引 idx_user_package_payment 已存在" as message'
END;

PREPARE stmt FROM @sql_create_package_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4. 验证索引创建结果
-- =====================================================
SELECT 
    INDEX_NAME,
    COLUMN_NAME,
    SEQ_IN_INDEX,
    CARDINALITY,
    INDEX_TYPE
FROM information_schema.STATISTICS 
WHERE TABLE_SCHEMA = 'chat' 
  AND TABLE_NAME = 't_user_order'
  AND INDEX_NAME IN ('idx_user_payment_status', 'idx_user_package_payment')
ORDER BY INDEX_NAME, SEQ_IN_INDEX;

-- 5. 性能测试查询
-- =====================================================

-- 测试优化后的查询性能
EXPLAIN SELECT COUNT(*) FROM t_user_order 
WHERE user_id = 1 AND payment_status = 1 AND deleted = 0;

-- 6. 创建用户充值状态统计视图
-- =====================================================
CREATE OR REPLACE VIEW v_user_payment_status AS
SELECT 
    u.id as user_id,
    u.username,
    u.email,
    ua.package_id as current_package_id,
    ua.package_expire_time,
    COALESCE(order_stats.total_orders, 0) as total_orders,
    COALESCE(order_stats.paid_orders, 0) as paid_orders,
    COALESCE(order_stats.total_amount, 0) as total_paid_amount,
    order_stats.first_payment_time,
    order_stats.last_payment_time,
    CASE 
        WHEN order_stats.paid_orders = 0 THEN 'NEVER_PAID'
        WHEN order_stats.paid_orders > 0 AND order_stats.paid_non_free_orders = 0 THEN 'FREE_ONLY'
        WHEN ua.package_id IS NOT NULL AND ua.package_id != 1 AND ua.package_expire_time > NOW() THEN 'ACTIVE_PAID'
        ELSE 'INACTIVE_PAID'
    END as payment_status_category
FROM t_user u
LEFT JOIN t_user_account ua ON u.id = ua.user_id AND ua.deleted = 0
LEFT JOIN (
    SELECT 
        user_id,
        COUNT(*) as total_orders,
        SUM(CASE WHEN payment_status = 1 THEN 1 ELSE 0 END) as paid_orders,
        SUM(CASE WHEN payment_status = 1 AND package_id != 1 THEN 1 ELSE 0 END) as paid_non_free_orders,
        SUM(CASE WHEN payment_status = 1 THEN amount ELSE 0 END) as total_amount,
        MIN(CASE WHEN payment_status = 1 THEN payment_time END) as first_payment_time,
        MAX(CASE WHEN payment_status = 1 THEN payment_time END) as last_payment_time
    FROM t_user_order 
    WHERE deleted = 0
    GROUP BY user_id
) order_stats ON u.id = order_stats.user_id
WHERE u.deleted = 0;

-- 7. 测试视图查询性能
-- =====================================================

-- 查询从未充值的用户
EXPLAIN SELECT * FROM v_user_payment_status 
WHERE payment_status_category = 'NEVER_PAID' 
LIMIT 10;

-- 查询只使用免费套餐的用户
EXPLAIN SELECT * FROM v_user_payment_status 
WHERE payment_status_category = 'FREE_ONLY' 
LIMIT 10;

-- 查询活跃付费用户
EXPLAIN SELECT * FROM v_user_payment_status 
WHERE payment_status_category = 'ACTIVE_PAID' 
LIMIT 10;

-- 8. 创建便捷查询函数
-- =====================================================

-- 创建用户充值状态检查函数
DELIMITER //
CREATE FUNCTION IF NOT EXISTS fn_user_payment_status(p_user_id BIGINT) 
RETURNS VARCHAR(20)
READS SQL DATA
DETERMINISTIC
BEGIN
    DECLARE v_paid_orders INT DEFAULT 0;
    DECLARE v_paid_non_free_orders INT DEFAULT 0;
    DECLARE v_current_package_id BIGINT DEFAULT NULL;
    DECLARE v_package_expire_time DATETIME DEFAULT NULL;
    
    -- 获取用户支付订单统计
    SELECT 
        COALESCE(SUM(CASE WHEN payment_status = 1 THEN 1 ELSE 0 END), 0),
        COALESCE(SUM(CASE WHEN payment_status = 1 AND package_id != 1 THEN 1 ELSE 0 END), 0)
    INTO v_paid_orders, v_paid_non_free_orders
    FROM t_user_order 
    WHERE user_id = p_user_id AND deleted = 0;
    
    -- 获取用户当前套餐信息
    SELECT package_id, package_expire_time
    INTO v_current_package_id, v_package_expire_time
    FROM t_user_account 
    WHERE user_id = p_user_id AND deleted = 0;
    
    -- 判断用户状态
    IF v_paid_orders = 0 THEN
        RETURN 'NEVER_PAID';
    ELSEIF v_paid_non_free_orders = 0 THEN
        RETURN 'FREE_ONLY';
    ELSEIF v_current_package_id IS NOT NULL AND v_current_package_id != 1 
           AND v_package_expire_time > NOW() THEN
        RETURN 'ACTIVE_PAID';
    ELSE
        RETURN 'INACTIVE_PAID';
    END IF;
END //
DELIMITER ;

-- 9. 测试函数性能
-- =====================================================
SELECT fn_user_payment_status(1) as user_1_status;

-- 10. 数据统计报告
-- =====================================================
SELECT 
    payment_status_category,
    COUNT(*) as user_count,
    ROUND(COUNT(*) * 100.0 / (SELECT COUNT(*) FROM t_user WHERE deleted = 0), 2) as percentage
FROM v_user_payment_status
GROUP BY payment_status_category
ORDER BY user_count DESC;

-- 显示优化完成信息
SELECT 
    '用户充值状态查询优化完成' as message,
    NOW() as completion_time,
    'v1.0' as version;
