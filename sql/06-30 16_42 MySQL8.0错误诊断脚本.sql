-- MySQL 8.0错误诊断脚本
-- 创建时间: 2025-06-30 16:42 (北京时间)
-- 版本: v1.0
-- 目的: 诊断MySQL 8.0中CREATE INDEX IF NOT EXISTS语法错误的原因

-- =====================================================
-- 1. 环境信息检查
-- =====================================================

-- 检查MySQL版本
SELECT 
    VERSION() as mysql_version,
    @@version_comment as version_comment,
    @@sql_mode as sql_mode;

-- 检查当前数据库和字符集
SELECT 
    DATABASE() as current_database,
    @@character_set_database as db_charset,
    @@collation_database as db_collation;

-- 检查用户权限
SHOW GRANTS FOR CURRENT_USER();

-- =====================================================
-- 2. 表结构检查
-- =====================================================

-- 检查t_user_order表是否存在
SELECT 
    TABLE_NAME,
    ENGINE,
    VERSION,
    ROW_FORMAT,
    TABLE_ROWS,
    DATA_LENGTH,
    INDEX_LENGTH,
    CREATE_TIME,
    UPDATE_TIME,
    TABLE_COLLATION
FROM INFORMATION_SCHEMA.TABLES 
WHERE TABLE_SCHEMA = DATABASE() 
AND TABLE_NAME = 't_user_order';

-- 检查t_user_order表的字段结构
SELECT 
    COLUMN_NAME,
    DATA_TYPE,
    IS_NULLABLE,
    COLUMN_DEFAULT,
    COLUMN_KEY,
    EXTRA,
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
AND TABLE_NAME = 't_user_order'
ORDER BY ORDINAL_POSITION;

-- 检查t_user_order表现有索引
SELECT 
    INDEX_NAME,
    COLUMN_NAME,
    SEQ_IN_INDEX,
    NON_UNIQUE,
    INDEX_TYPE,
    COMMENT
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE() 
AND TABLE_NAME = 't_user_order'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;

-- =====================================================
-- 3. 语法测试
-- =====================================================

-- 测试CREATE INDEX IF NOT EXISTS语法支持
-- 注意：这个测试会创建一个临时索引，然后删除

-- 创建测试索引
CREATE INDEX IF NOT EXISTS `test_idx_temp` ON `t_user_order` (`id`);

-- 检查测试索引是否创建成功
SELECT 
    'test_idx_temp' as test_index,
    COUNT(*) as index_exists
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE() 
AND TABLE_NAME = 't_user_order'
AND INDEX_NAME = 'test_idx_temp';

-- 删除测试索引
DROP INDEX IF EXISTS `test_idx_temp` ON `t_user_order`;

-- 验证测试索引已删除
SELECT 
    'test_idx_temp' as test_index,
    COUNT(*) as index_exists_after_drop
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE() 
AND TABLE_NAME = 't_user_order'
AND INDEX_NAME = 'test_idx_temp';

-- =====================================================
-- 4. 逐个测试目标索引创建
-- =====================================================

-- 测试1: idx_order_expire_status
SELECT '开始测试 idx_order_expire_status 索引创建' as test_step;

-- 检查字段是否存在
SELECT 
    COLUMN_NAME,
    DATA_TYPE,
    IS_NULLABLE
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
AND TABLE_NAME = 't_user_order'
AND COLUMN_NAME IN ('expire_time', 'payment_status', 'deleted');

-- 尝试创建索引
CREATE INDEX IF NOT EXISTS `idx_order_expire_status` 
ON `t_user_order` (`expire_time`, `payment_status`, `deleted`);

-- 验证索引创建结果
SELECT 
    'idx_order_expire_status' as index_name,
    COUNT(*) as created_successfully
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE() 
AND TABLE_NAME = 't_user_order'
AND INDEX_NAME = 'idx_order_expire_status';

-- 测试2: idx_order_archive
SELECT '开始测试 idx_order_archive 索引创建' as test_step;

CREATE INDEX IF NOT EXISTS `idx_order_archive` 
ON `t_user_order` (`create_time`, `payment_status`, `deleted`);

SELECT 
    'idx_order_archive' as index_name,
    COUNT(*) as created_successfully
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE() 
AND TABLE_NAME = 't_user_order'
AND INDEX_NAME = 'idx_order_archive';

-- 测试3: idx_order_payment_time
SELECT '开始测试 idx_order_payment_time 索引创建' as test_step;

CREATE INDEX IF NOT EXISTS `idx_order_payment_time` 
ON `t_user_order` (`payment_time`, `payment_status`, `deleted`);

SELECT 
    'idx_order_payment_time' as index_name,
    COUNT(*) as created_successfully
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE() 
AND TABLE_NAME = 't_user_order'
AND INDEX_NAME = 'idx_order_payment_time';

-- 测试4: idx_order_method_time
SELECT '开始测试 idx_order_method_time 索引创建' as test_step;

CREATE INDEX IF NOT EXISTS `idx_order_method_time` 
ON `t_user_order` (`payment_method`, `payment_time`, `deleted`);

SELECT 
    'idx_order_method_time' as index_name,
    COUNT(*) as created_successfully
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE() 
AND TABLE_NAME = 't_user_order'
AND INDEX_NAME = 'idx_order_method_time';

-- 测试5: idx_order_package_time
SELECT '开始测试 idx_order_package_time 索引创建' as test_step;

CREATE INDEX IF NOT EXISTS `idx_order_package_time` 
ON `t_user_order` (`package_id`, `payment_time`, `deleted`);

SELECT 
    'idx_order_package_time' as index_name,
    COUNT(*) as created_successfully
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE() 
AND TABLE_NAME = 't_user_order'
AND INDEX_NAME = 'idx_order_package_time';

-- =====================================================
-- 5. 最终结果汇总
-- =====================================================

-- 显示所有新创建的索引
SELECT 
    '最终索引创建结果' as summary_title,
    INDEX_NAME as index_name,
    GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) as index_columns,
    INDEX_TYPE as index_type
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE() 
AND TABLE_NAME = 't_user_order'
AND INDEX_NAME LIKE 'idx_order_%'
GROUP BY INDEX_NAME, INDEX_TYPE
ORDER BY INDEX_NAME;

-- 统计索引数量
SELECT 
    COUNT(DISTINCT INDEX_NAME) as total_indexes_created,
    COUNT(*) as total_index_columns
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE() 
AND TABLE_NAME = 't_user_order'
AND INDEX_NAME LIKE 'idx_order_%';

-- =====================================================
-- 6. 性能测试建议
-- =====================================================

-- 显示可以进行的性能测试
SELECT 
    '性能测试建议' as test_category,
    'EXPLAIN SELECT * FROM t_user_order WHERE expire_time < NOW() AND payment_status = 0 AND deleted = 0 LIMIT 10' as test_sql_1,
    'EXPLAIN SELECT * FROM t_user_order WHERE payment_time >= DATE_SUB(NOW(), INTERVAL 30 DAY) AND payment_status = 1 AND deleted = 0' as test_sql_2,
    'EXPLAIN SELECT * FROM t_user_order WHERE package_id = 1 AND payment_time >= DATE_SUB(NOW(), INTERVAL 7 DAY) AND deleted = 0' as test_sql_3;

-- 诊断完成提示
SELECT 
    'MySQL 8.0 索引诊断完成' as message,
    NOW() as execution_time,
    VERSION() as mysql_version,
    '如果所有测试通过，说明语法没有问题' as conclusion;
