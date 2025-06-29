/*
 MySQL 8.0 索引优化脚本 - 简单版
 
 直接创建索引，忽略已存在的错误
 创建时间: 2025-06-29 20:52 (北京时间)
 版本: v1.3 - 简单版
 
 说明: 如果索引已存在会报错，但不影响其他索引创建
*/

-- ========================================
-- 降序索引优化（MySQL 8.0真正支持降序存储）
-- ========================================

-- 1. 聊天消息时间降序索引 - 优化ORDER BY create_time DESC查询
CREATE INDEX idx_chat_msg_time_desc ON t_chat_message (create_time DESC, deleted);

-- 2. 会话活跃时间降序索引 - 优化会话列表按活跃时间排序
CREATE INDEX idx_session_active_desc ON t_chat_session (last_active_time DESC, deleted);

-- 3. 用户订单时间降序索引 - 优化订单历史查询
CREATE INDEX idx_user_order_time_desc ON t_user_order (create_time DESC, payment_status);

-- ========================================
-- 复合查询优化索引
-- ========================================

-- 4. 用户消息时间优化索引 - 优化用户聊天记录查询
CREATE INDEX idx_chat_msg_user_time_opt ON t_chat_message (user_id, create_time DESC, deleted);

-- 5. 用户会话活跃时间优化索引 - 优化用户会话列表查询
CREATE INDEX idx_session_user_active_opt ON t_chat_session (user_id, last_active_time DESC, deleted);

-- 6. 会话消息角色索引 - 优化按会话和角色查询消息
CREATE INDEX idx_chat_msg_session_role ON t_chat_message (session_id, role, create_time DESC);

-- ========================================
-- 覆盖索引优化（避免回表查询）
-- ========================================

-- 7. 文件查询覆盖索引 - 覆盖文件列表查询的所有字段
CREATE INDEX idx_file_user_cover ON t_file_upload (user_id, deleted, file_name, file_type, create_time);

-- 8. 订单查询覆盖索引 - 覆盖订单查询的主要字段
CREATE INDEX idx_order_user_cover ON t_user_order (user_id, payment_status, create_time, amount);

-- 9. 用户消息统计覆盖索引 - 支持用户消息统计查询
CREATE INDEX idx_chat_msg_user_stats ON t_chat_message (user_id, deleted, session_id, create_time, role);

-- ========================================
-- MySQL 8.0函数索引
-- ========================================

-- 10. 消息日期函数索引 - 优化按日期统计消息查询
CREATE INDEX idx_chat_msg_date_func ON t_chat_message ((DATE(create_time)), user_id);

-- 11. 订单日期函数索引 - 优化按日期统计订单查询
CREATE INDEX idx_order_date_func ON t_user_order ((DATE(create_time)), payment_status);

-- 12. 用户使用日期索引 - 优化用户每日使用统计
CREATE INDEX idx_usage_date_user ON t_user_daily_usage (usage_date DESC, user_id, chat_count_used);

-- ========================================
-- 关联查询优化索引
-- ========================================

-- 13. 消息附件关联索引 - 优化查询带附件的消息
CREATE INDEX idx_chat_msg_attachments_opt ON t_chat_message (has_attachments, session_id, create_time DESC);

-- 14. 文件会话关联索引 - 优化按会话查询文件列表
CREATE INDEX idx_file_session_time ON t_file_upload (session_id, create_time DESC, deleted);

-- 15. 模型消息统计索引 - 优化AI模型使用统计查询
CREATE INDEX idx_chat_msg_model_stats ON t_chat_message (model_id, create_time DESC, deleted, token_count);

-- ========================================
-- 测试用不可见索引（MySQL 8.0特性）
-- ========================================

-- 16. 消息状态测试索引（不可见） - 用于性能测试
CREATE INDEX idx_test_msg_status ON t_chat_message (status, user_id, create_time) INVISIBLE;

-- 17. 文件类型测试索引（不可见） - 用于性能测试
CREATE INDEX idx_test_file_type ON t_file_upload (file_type, user_id, create_time) INVISIBLE;

-- ========================================
-- 统计信息更新
-- ========================================

ANALYZE TABLE t_chat_message;
ANALYZE TABLE t_chat_session;
ANALYZE TABLE t_file_upload;
ANALYZE TABLE t_user_order;
ANALYZE TABLE t_user_daily_usage;
ANALYZE TABLE t_user;
ANALYZE TABLE t_ai_model;
ANALYZE TABLE t_package;

-- ========================================
-- 验证索引创建结果
-- ========================================

SELECT '🎉 MySQL 8.0索引优化执行完成!' AS status;
SELECT CONCAT('执行时间: ', NOW()) AS execution_time;

-- 显示新创建的索引
SELECT 
    table_name AS '表名',
    index_name AS '索引名',
    CASE 
        WHEN index_name LIKE '%_desc' THEN '降序索引'
        WHEN index_name LIKE '%_opt' THEN '查询优化索引'
        WHEN index_name LIKE '%_cover' THEN '覆盖索引'
        WHEN index_name LIKE '%_func' THEN '函数索引'
        WHEN index_name LIKE '%_stats' THEN '统计索引'
        WHEN index_name LIKE 'idx_test_%' THEN '测试索引'
        ELSE '关联查询索引'
    END AS '索引类型'
FROM information_schema.statistics 
WHERE table_schema = 'chat' 
  AND index_name IN (
    'idx_chat_msg_time_desc',
    'idx_session_active_desc', 
    'idx_user_order_time_desc',
    'idx_chat_msg_user_time_opt',
    'idx_session_user_active_opt',
    'idx_chat_msg_session_role',
    'idx_file_user_cover',
    'idx_order_user_cover',
    'idx_chat_msg_user_stats',
    'idx_chat_msg_date_func',
    'idx_order_date_func',
    'idx_usage_date_user',
    'idx_chat_msg_attachments_opt',
    'idx_file_session_time',
    'idx_chat_msg_model_stats',
    'idx_test_msg_status',
    'idx_test_file_type'
  )
GROUP BY table_name, index_name
ORDER BY table_name, index_name;

-- ========================================
-- 性能测试建议
-- ========================================

SELECT '性能测试建议:' AS test_info;
SELECT '1. 测试用户聊天记录查询:' AS test1;
SELECT 'EXPLAIN SELECT * FROM t_chat_message WHERE user_id = 1 AND deleted = 0 ORDER BY create_time DESC LIMIT 20;' AS sql1;

SELECT '2. 测试会话列表查询:' AS test2;
SELECT 'EXPLAIN SELECT * FROM t_chat_session WHERE user_id = 1 AND deleted = 0 ORDER BY last_active_time DESC LIMIT 10;' AS sql2;

SELECT '3. 测试日期统计查询:' AS test3;
SELECT 'EXPLAIN SELECT DATE(create_time), COUNT(*) FROM t_chat_message WHERE user_id = 1 GROUP BY DATE(create_time);' AS sql3;

SELECT '4. 测试文件列表查询:' AS test4;
SELECT 'EXPLAIN SELECT file_name, file_type, create_time FROM t_file_upload WHERE user_id = 1 AND deleted = 0;' AS sql4;

-- ========================================
-- 完成提示
-- ========================================

SELECT '✅ 索引优化完成，建议重启应用以获得最佳性能!' AS final_message;
