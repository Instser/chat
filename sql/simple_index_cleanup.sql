-- 简单的索引清理脚本
-- 先查看，再手动删除存在的索引

-- ========================================
-- 1. 查看需要删除的索引是否存在
-- ========================================

SELECT 
    '需要删除的索引检查' as check_type,
    TABLE_NAME,
    INDEX_NAME,
    CASE 
        WHEN COUNT(*) > 0 THEN '✅ 存在'
        ELSE '❌ 不存在'
    END as status
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND INDEX_NAME IN (
    'idx_chat_msg_attachments',
    'idx_chat_msg_user_time',
    'idx_chat_msg_model_time',
    'idx_chat_session_user_active',
    'idx_test_file_type',
    'idx_create_time',
    'idx_operation_type',
    'idx_operator_id',
    'idx_user_order_time_status',
    'idx_model_name'
  )
GROUP BY TABLE_NAME, INDEX_NAME
ORDER BY TABLE_NAME, INDEX_NAME;

-- ========================================
-- 2. 根据上面的结果，手动删除存在的索引
-- ========================================

-- 请根据上面查询结果，取消注释存在的索引进行删除：

-- DROP INDEX idx_chat_msg_user_time ON t_chat_message;
-- DROP INDEX idx_chat_msg_model_time ON t_chat_message;
-- DROP INDEX idx_chat_session_user_active ON t_chat_session;
-- DROP INDEX idx_test_file_type ON t_file_upload;
-- DROP INDEX idx_create_time ON t_payment_audit;
-- DROP INDEX idx_operation_type ON t_payment_audit;
-- DROP INDEX idx_operator_id ON t_payment_audit;
-- DROP INDEX idx_user_order_time_status ON t_user_order;
-- DROP INDEX idx_model_name ON t_user_daily_usage;

-- ========================================
-- 3. 添加缺失的重要索引
-- ========================================

-- 检查关键索引是否已存在
SELECT 
    '关键索引检查' as check_type,
    INDEX_NAME,
    TABLE_NAME,
    CASE 
        WHEN COUNT(*) > 0 THEN '✅ 已存在'
        ELSE '❌ 需要创建'
    END as status
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND INDEX_NAME IN (
    'idx_package_limit_type',
    'idx_package_list',
    'idx_account_package',
    'idx_account_expire'
  )
GROUP BY TABLE_NAME, INDEX_NAME
ORDER BY TABLE_NAME, INDEX_NAME;

-- 根据上面结果，创建不存在的索引：
-- CREATE INDEX idx_package_limit_type ON t_package (limit_type, deleted);
-- CREATE INDEX idx_package_list ON t_package (deleted, is_enabled, is_available, sort_order);
-- CREATE INDEX idx_account_package ON t_user_account (package_id, deleted);
-- CREATE INDEX idx_account_expire ON t_user_account (package_expire_time, deleted);

-- ========================================
-- 4. 最终验证
-- ========================================

-- 查看优化后的索引统计
SELECT 
    TABLE_NAME,
    COUNT(*) as index_count
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE()
GROUP BY TABLE_NAME
ORDER BY index_count DESC;

-- 完成提示
SELECT '📋 请根据检查结果手动执行相应的DROP INDEX和CREATE INDEX语句' as instruction;
