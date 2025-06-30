-- 支付模块索引修复脚本 - 简化版
-- 创建时间: 2025-06-30 16:42 (北京时间)
-- 版本: v1.0 (快速修复版)
-- 目的: 快速修复支付模块索引问题，兼容MySQL 5.7
-- 说明: 这是一个简化版本，只处理索引创建，不包含表和视图

-- =====================================================
-- 检查当前数据库和表状态
-- =====================================================

-- 显示当前数据库
SELECT DATABASE() as current_database;

-- 检查t_user_order表是否存在
SELECT 
    TABLE_NAME,
    ENGINE,
    TABLE_ROWS,
    DATA_LENGTH,
    INDEX_LENGTH
FROM INFORMATION_SCHEMA.TABLES 
WHERE TABLE_SCHEMA = DATABASE() 
AND TABLE_NAME = 't_user_order';

-- =====================================================
-- 动态创建索引（兼容MySQL 5.7）
-- =====================================================

-- 1. 检查并创建idx_order_expire_status索引
SET @index_exists = (
    SELECT COUNT(1) 
    FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 't_user_order' 
    AND INDEX_NAME = 'idx_order_expire_status'
);

SELECT CONCAT('索引 idx_order_expire_status 存在状态: ', @index_exists) as check_result;

SET @sql = IF(@index_exists = 0, 
    'CREATE INDEX `idx_order_expire_status` ON `t_user_order` (`expire_time`, `payment_status`, `deleted`)',
    'SELECT "索引 idx_order_expire_status 已存在，跳过创建" as message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 检查并创建idx_order_archive索引
SET @index_exists = (
    SELECT COUNT(1) 
    FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 't_user_order' 
    AND INDEX_NAME = 'idx_order_archive'
);

SELECT CONCAT('索引 idx_order_archive 存在状态: ', @index_exists) as check_result;

SET @sql = IF(@index_exists = 0, 
    'CREATE INDEX `idx_order_archive` ON `t_user_order` (`create_time`, `payment_status`, `deleted`)',
    'SELECT "索引 idx_order_archive 已存在，跳过创建" as message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 检查并创建idx_order_payment_time索引
SET @index_exists = (
    SELECT COUNT(1) 
    FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 't_user_order' 
    AND INDEX_NAME = 'idx_order_payment_time'
);

SELECT CONCAT('索引 idx_order_payment_time 存在状态: ', @index_exists) as check_result;

SET @sql = IF(@index_exists = 0, 
    'CREATE INDEX `idx_order_payment_time` ON `t_user_order` (`payment_time`, `payment_status`, `deleted`)',
    'SELECT "索引 idx_order_payment_time 已存在，跳过创建" as message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4. 检查并创建idx_order_method_time索引
SET @index_exists = (
    SELECT COUNT(1) 
    FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 't_user_order' 
    AND INDEX_NAME = 'idx_order_method_time'
);

SELECT CONCAT('索引 idx_order_method_time 存在状态: ', @index_exists) as check_result;

SET @sql = IF(@index_exists = 0, 
    'CREATE INDEX `idx_order_method_time` ON `t_user_order` (`payment_method`, `payment_time`, `deleted`)',
    'SELECT "索引 idx_order_method_time 已存在，跳过创建" as message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5. 检查并创建idx_order_package_time索引
SET @index_exists = (
    SELECT COUNT(1) 
    FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 't_user_order' 
    AND INDEX_NAME = 'idx_order_package_time'
);

SELECT CONCAT('索引 idx_order_package_time 存在状态: ', @index_exists) as check_result;

SET @sql = IF(@index_exists = 0, 
    'CREATE INDEX `idx_order_package_time` ON `t_user_order` (`package_id`, `payment_time`, `deleted`)',
    'SELECT "索引 idx_order_package_time 已存在，跳过创建" as message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================
-- 验证索引创建结果
-- =====================================================

-- 显示t_user_order表的所有索引
SELECT 
    INDEX_NAME as '索引名称',
    COLUMN_NAME as '字段名称',
    SEQ_IN_INDEX as '字段顺序',
    NON_UNIQUE as '是否唯一',
    INDEX_TYPE as '索引类型'
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE() 
AND TABLE_NAME = 't_user_order'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;

-- 统计索引数量
SELECT 
    COUNT(DISTINCT INDEX_NAME) as total_indexes,
    COUNT(*) as total_index_columns
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE() 
AND TABLE_NAME = 't_user_order';

-- =====================================================
-- 性能测试建议
-- =====================================================

-- 测试过期订单查询性能
-- EXPLAIN SELECT * FROM t_user_order 
-- WHERE expire_time < NOW() AND payment_status = 0 AND deleted = 0 
-- LIMIT 10;

-- 测试支付时间查询性能  
-- EXPLAIN SELECT * FROM t_user_order 
-- WHERE payment_time >= '2025-06-01' AND payment_status = 1 AND deleted = 0
-- ORDER BY payment_time DESC 
-- LIMIT 10;

-- 测试套餐订单查询性能
-- EXPLAIN SELECT * FROM t_user_order 
-- WHERE package_id = 1 AND payment_time >= '2025-06-01' AND deleted = 0
-- ORDER BY payment_time DESC;

-- =====================================================
-- 执行完成提示
-- =====================================================

SELECT 
    '支付模块索引修复完成' as message,
    NOW() as execution_time,
    'v1.0 简化版' as version,
    'MySQL 5.7兼容' as compatibility,
    '仅处理索引创建' as scope;

-- 建议后续操作
SELECT 
    '建议执行性能测试' as next_step_1,
    '监控索引使用情况' as next_step_2,
    '定期分析慢查询日志' as next_step_3;
