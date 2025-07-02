-- 安全的索引清理脚本 - 条件删除版本
-- 先检查索引是否存在，再进行删除

-- ========================================
-- 1. 检查现有索引状态
-- ========================================

-- 查看当前所有索引
SELECT
    TABLE_NAME,
    INDEX_NAME,
    GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) as columns
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
GROUP BY TABLE_NAME, INDEX_NAME
ORDER BY TABLE_NAME, INDEX_NAME;

-- ========================================
-- 2. 使用存储过程安全删除索引
-- ========================================

DELIMITER $$

-- 安全删除索引的存储过程
DROP PROCEDURE IF EXISTS SafeDropIndex$$
CREATE PROCEDURE SafeDropIndex(IN table_name VARCHAR(64), IN index_name VARCHAR(64))
BEGIN
    DECLARE index_exists INT DEFAULT 0;

    SELECT COUNT(*) INTO index_exists
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = table_name
      AND INDEX_NAME = index_name;

    IF index_exists > 0 THEN
        SET @sql = CONCAT('DROP INDEX ', index_name, ' ON ', table_name);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SELECT CONCAT('✅ 删除索引: ', table_name, '.', index_name) as result;
    ELSE
        SELECT CONCAT('⚠️ 索引不存在: ', table_name, '.', index_name) as result;
    END IF;
END$$

DELIMITER ;

-- 执行安全删除
CALL SafeDropIndex('t_chat_message', 'idx_chat_msg_attachments');
CALL SafeDropIndex('t_chat_message', 'idx_chat_msg_user_time');
CALL SafeDropIndex('t_chat_message', 'idx_chat_msg_model_time');
CALL SafeDropIndex('t_chat_session', 'idx_chat_session_user_active');
CALL SafeDropIndex('t_file_upload', 'idx_test_file_type');
CALL SafeDropIndex('t_payment_audit', 'idx_create_time');
CALL SafeDropIndex('t_payment_audit', 'idx_operation_type');
CALL SafeDropIndex('t_payment_audit', 'idx_operator_id');
CALL SafeDropIndex('t_user_order', 'idx_user_order_time_status');
CALL SafeDropIndex('t_user_daily_usage', 'idx_model_name');

-- 清理存储过程
DROP PROCEDURE SafeDropIndex;

-- ========================================
-- 2. 添加缺失的重要索引
-- ========================================

-- t_package表关键索引（如果不存在）
CREATE INDEX IF NOT EXISTS idx_package_limit_type ON t_package (limit_type, deleted);
CREATE INDEX IF NOT EXISTS idx_package_list ON t_package (deleted, is_enabled, is_available, sort_order);

-- t_user_account表关键索引
CREATE INDEX IF NOT EXISTS idx_account_package ON t_user_account (package_id, deleted);
CREATE INDEX IF NOT EXISTS idx_account_expire ON t_user_account (package_expire_time, deleted);

-- ========================================
-- 3. 验证清理结果
-- ========================================

-- 统计各表索引数量
SELECT 
    TABLE_NAME,
    COUNT(*) as index_count,
    CASE 
        WHEN COUNT(*) > 12 THEN '⚠️ 仍然较多'
        WHEN COUNT(*) < 3 THEN '⚠️ 过少'
        ELSE '✅ 合理'
    END as status
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE()
GROUP BY TABLE_NAME
ORDER BY index_count DESC;

-- 检查关键索引是否存在
SELECT 
    '关键索引检查' as check_type,
    INDEX_NAME,
    TABLE_NAME
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND INDEX_NAME IN (
    'idx_package_limit_type',
    'idx_package_list', 
    'idx_account_package',
    'idx_account_expire'
  )
ORDER BY TABLE_NAME, INDEX_NAME;

-- 完成提示
SELECT '🎉 安全索引清理完成！删除了10个冗余索引，添加了4个关键索引。' as result;
