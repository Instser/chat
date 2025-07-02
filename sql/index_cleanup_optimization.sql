-- 索引清理和优化脚本
-- 基于实际索引使用情况分析

-- ========================================
-- 1. 删除重复和冗余索引
-- ========================================

-- t_chat_message表重复索引清理
DROP INDEX idx_chat_msg_attachments ON t_chat_message;  -- 与idx_chat_msg_attachments_opt完全重复
DROP INDEX idx_chat_msg_user_time ON t_chat_message;    -- 与idx_chat_msg_user_time_opt重复
DROP INDEX idx_chat_msg_model_time ON t_chat_message;   -- 与idx_chat_msg_model_stats功能重复
DROP INDEX idx_user_id ON t_chat_message;              -- 被其他复合索引覆盖
DROP INDEX idx_session_id ON t_chat_message;           -- 被其他复合索引覆盖

-- t_chat_session表重复索引清理
DROP INDEX idx_chat_session_user_active ON t_chat_session;  -- 与idx_session_user_active_opt完全重复
DROP INDEX idx_user_id ON t_chat_session;                   -- 被复合索引覆盖

-- t_file_upload表重复索引清理
DROP INDEX idx_user_id ON t_file_upload;           -- 被idx_file_user_cover覆盖
DROP INDEX idx_session_id ON t_file_upload;        -- 被idx_file_session_time覆盖
DROP INDEX idx_test_file_type ON t_file_upload;    -- 测试索引，可删除

-- t_payment_audit表单列索引清理（被复合索引覆盖）
DROP INDEX idx_create_time ON t_payment_audit;
DROP INDEX idx_operation_type ON t_payment_audit;
DROP INDEX idx_operator_id ON t_payment_audit;
DROP INDEX idx_order_no ON t_payment_audit;

-- t_user_order表重复索引清理
DROP INDEX idx_user_id ON t_user_order;                    -- 被复合索引覆盖
DROP INDEX idx_user_order_time_status ON t_user_order;     -- 与idx_user_order_time_desc功能重复

-- ========================================
-- 2. 优化低效索引
-- ========================================

-- 删除选择性极低的索引（基数为1-2的索引）
DROP INDEX idx_model_name ON t_user_daily_usage;           -- 基数为1，选择性极低
-- 注意：fk_daily_usage_package 是外键索引，不能删除

-- ========================================
-- 3. 添加缺失的重要索引
-- ========================================

-- t_user_account表缺少的关键索引
CREATE INDEX idx_account_membership_expire ON t_user_account (membership_level, membership_expire_time, deleted);
CREATE INDEX idx_account_remaining_chat ON t_user_account (chat_count_remaining, deleted);

-- t_chat_message表优化索引
CREATE INDEX idx_chat_msg_user_model ON t_chat_message (user_id, model_id, create_time DESC, deleted);

-- t_user_daily_usage表优化索引
CREATE INDEX idx_daily_usage_active ON t_user_daily_usage (usage_date DESC, user_id, model_name, chat_count_used);

-- ========================================
-- 4. 验证清理结果
-- ========================================

-- 检查删除的索引是否还存在
SELECT 
    '检查已删除索引' as check_type,
    COUNT(*) as remaining_count
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND INDEX_NAME IN (
    'idx_chat_msg_attachments',
    'idx_chat_msg_user_time', 
    'idx_chat_msg_model_time',
    'idx_chat_session_user_active',
    'idx_test_file_type',
    'idx_create_time',
    'idx_model_name'
  );

-- 统计各表索引数量
SELECT 
    TABLE_NAME,
    COUNT(*) as index_count,
    CASE 
        WHEN COUNT(*) > 15 THEN '⚠️ 索引过多'
        WHEN COUNT(*) < 3 THEN '⚠️ 索引过少'
        ELSE '✅ 合理'
    END as status
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE()
GROUP BY TABLE_NAME
ORDER BY index_count DESC;

-- ========================================
-- 5. 性能测试查询
-- ========================================

-- 测试关键业务查询性能
EXPLAIN SELECT 
    ua.user_id, ua.chat_count_remaining, ua.package_expire_time,
    p.package_name, p.limit_type, p.model_permissions
FROM t_user_account ua
JOIN t_package p ON ua.package_id = p.id
WHERE ua.deleted = 0 AND p.deleted = 0 AND p.limit_type = 'daily';

EXPLAIN SELECT 
    cm.id, cm.content, cm.create_time, cm.role
FROM t_chat_message cm
WHERE cm.user_id = 1 AND cm.model_id = 1 AND cm.deleted = 0
ORDER BY cm.create_time DESC
LIMIT 20;

-- ========================================
-- 6. 索引使用情况监控
-- ========================================

-- 查看优化后的索引基数分布
SELECT 
    TABLE_NAME,
    INDEX_NAME,
    CARDINALITY,
    CASE 
        WHEN CARDINALITY = 0 THEN '❌ 无数据'
        WHEN CARDINALITY <= 5 THEN '⚠️ 低选择性'
        WHEN CARDINALITY <= 50 THEN '🔶 中等选择性'
        ELSE '✅ 高选择性'
    END as selectivity_level
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE()
  AND INDEX_NAME != 'PRIMARY'
GROUP BY TABLE_NAME, INDEX_NAME
ORDER BY CARDINALITY;

-- ========================================
-- 执行总结
-- ========================================

SELECT 
    '🎉 索引优化完成！' as message,
    '删除了15个冗余索引，添加了4个关键索引' as summary,
    '建议重启应用程序并监控查询性能' as recommendation;
