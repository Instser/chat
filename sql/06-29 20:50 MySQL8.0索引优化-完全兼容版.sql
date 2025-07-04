/*
 MySQL 8.0 索引优化脚本 - 完全兼容版
 
 解决所有语法兼容性问题，适用于所有MySQL 8.0版本
 创建时间: 2025-06-29 20:50 (北京时间)
 版本: v1.2 - 完全兼容版
*/

-- ========================================
-- 第一部分: 降序索引优化
-- ========================================

-- 1. 聊天消息时间降序索引
SET @index_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = 'chat' AND table_name = 't_chat_message' AND index_name = 'idx_chat_msg_time_desc');
SET @sql = IF(@index_exists > 0, 'DROP INDEX idx_chat_msg_time_desc ON t_chat_message', 'SELECT "索引不存在，无需删除" as message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
CREATE INDEX idx_chat_msg_time_desc ON t_chat_message (create_time DESC, deleted);

-- 2. 会话活跃时间降序索引
SET @index_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = 'chat' AND table_name = 't_chat_session' AND index_name = 'idx_session_active_desc');
SET @sql = IF(@index_exists > 0, 'DROP INDEX idx_session_active_desc ON t_chat_session', 'SELECT "索引不存在，无需删除" as message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
CREATE INDEX idx_session_active_desc ON t_chat_session (last_active_time DESC, deleted);

-- 3. 用户订单时间降序索引
SET @index_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = 'chat' AND table_name = 't_user_order' AND index_name = 'idx_user_order_time_desc');
SET @sql = IF(@index_exists > 0, 'DROP INDEX idx_user_order_time_desc ON t_user_order', 'SELECT "索引不存在，无需删除" as message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
CREATE INDEX idx_user_order_time_desc ON t_user_order (create_time DESC, payment_status);

-- ========================================
-- 第二部分: 复合查询优化索引
-- ========================================

-- 4. 用户消息时间优化索引
SET @index_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = 'chat' AND table_name = 't_chat_message' AND index_name = 'idx_chat_msg_user_time_opt');
SET @sql = IF(@index_exists > 0, 'DROP INDEX idx_chat_msg_user_time_opt ON t_chat_message', 'SELECT "索引不存在，无需删除" as message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
CREATE INDEX idx_chat_msg_user_time_opt ON t_chat_message (user_id, create_time DESC, deleted);

-- 5. 用户会话活跃时间优化索引
SET @index_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = 'chat' AND table_name = 't_chat_session' AND index_name = 'idx_session_user_active_opt');
SET @sql = IF(@index_exists > 0, 'DROP INDEX idx_session_user_active_opt ON t_chat_session', 'SELECT "索引不存在，无需删除" as message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
CREATE INDEX idx_session_user_active_opt ON t_chat_session (user_id, last_active_time DESC, deleted);

-- 6. 会话消息角色索引
SET @index_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = 'chat' AND table_name = 't_chat_message' AND index_name = 'idx_chat_msg_session_role');
SET @sql = IF(@index_exists > 0, 'DROP INDEX idx_chat_msg_session_role ON t_chat_message', 'SELECT "索引不存在，无需删除" as message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
CREATE INDEX idx_chat_msg_session_role ON t_chat_message (session_id, role, create_time DESC);

-- ========================================
-- 第三部分: 覆盖索引优化
-- ========================================

-- 7. 文件查询覆盖索引
SET @index_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = 'chat' AND table_name = 't_file_upload' AND index_name = 'idx_file_user_cover');
SET @sql = IF(@index_exists > 0, 'DROP INDEX idx_file_user_cover ON t_file_upload', 'SELECT "索引不存在，无需删除" as message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
CREATE INDEX idx_file_user_cover ON t_file_upload (user_id, deleted, file_name, file_type, create_time);

-- 8. 订单查询覆盖索引
SET @index_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = 'chat' AND table_name = 't_user_order' AND index_name = 'idx_order_user_cover');
SET @sql = IF(@index_exists > 0, 'DROP INDEX idx_order_user_cover ON t_user_order', 'SELECT "索引不存在，无需删除" as message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
CREATE INDEX idx_order_user_cover ON t_user_order (user_id, payment_status, create_time, amount);

-- 9. 用户消息统计覆盖索引
SET @index_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = 'chat' AND table_name = 't_chat_message' AND index_name = 'idx_chat_msg_user_stats');
SET @sql = IF(@index_exists > 0, 'DROP INDEX idx_chat_msg_user_stats ON t_chat_message', 'SELECT "索引不存在，无需删除" as message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
CREATE INDEX idx_chat_msg_user_stats ON t_chat_message (user_id, deleted, session_id, create_time, role);

-- ========================================
-- 第四部分: MySQL 8.0函数索引
-- ========================================

-- 10. 消息日期函数索引
SET @index_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = 'chat' AND table_name = 't_chat_message' AND index_name = 'idx_chat_msg_date_func');
SET @sql = IF(@index_exists > 0, 'DROP INDEX idx_chat_msg_date_func ON t_chat_message', 'SELECT "索引不存在，无需删除" as message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
CREATE INDEX idx_chat_msg_date_func ON t_chat_message ((DATE(create_time)), user_id);

-- 11. 订单日期函数索引
SET @index_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = 'chat' AND table_name = 't_user_order' AND index_name = 'idx_order_date_func');
SET @sql = IF(@index_exists > 0, 'DROP INDEX idx_order_date_func ON t_user_order', 'SELECT "索引不存在，无需删除" as message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
CREATE INDEX idx_order_date_func ON t_user_order ((DATE(create_time)), payment_status);

-- 12. 用户使用日期索引
SET @index_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = 'chat' AND table_name = 't_user_daily_usage' AND index_name = 'idx_usage_date_user');
SET @sql = IF(@index_exists > 0, 'DROP INDEX idx_usage_date_user ON t_user_daily_usage', 'SELECT "索引不存在，无需删除" as message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
CREATE INDEX idx_usage_date_user ON t_user_daily_usage (usage_date DESC, user_id, chat_count_used);

-- ========================================
-- 第五部分: 关联查询优化索引
-- ========================================

-- 13. 消息附件关联索引
SET @index_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = 'chat' AND table_name = 't_chat_message' AND index_name = 'idx_chat_msg_attachments_opt');
SET @sql = IF(@index_exists > 0, 'DROP INDEX idx_chat_msg_attachments_opt ON t_chat_message', 'SELECT "索引不存在，无需删除" as message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
CREATE INDEX idx_chat_msg_attachments_opt ON t_chat_message (has_attachments, session_id, create_time DESC);

-- 14. 文件会话关联索引
SET @index_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = 'chat' AND table_name = 't_file_upload' AND index_name = 'idx_file_session_time');
SET @sql = IF(@index_exists > 0, 'DROP INDEX idx_file_session_time ON t_file_upload', 'SELECT "索引不存在，无需删除" as message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
CREATE INDEX idx_file_session_time ON t_file_upload (session_id, create_time DESC, deleted);

-- 15. 模型消息统计索引
SET @index_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = 'chat' AND table_name = 't_chat_message' AND index_name = 'idx_chat_msg_model_stats');
SET @sql = IF(@index_exists > 0, 'DROP INDEX idx_chat_msg_model_stats ON t_chat_message', 'SELECT "索引不存在，无需删除" as message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
CREATE INDEX idx_chat_msg_model_stats ON t_chat_message (model_id, create_time DESC, deleted, token_count);

-- ========================================
-- 第六部分: 测试用不可见索引
-- ========================================

-- 16. 消息状态测试索引（不可见）
SET @index_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = 'chat' AND table_name = 't_chat_message' AND index_name = 'idx_test_msg_status');
SET @sql = IF(@index_exists > 0, 'DROP INDEX idx_test_msg_status ON t_chat_message', 'SELECT "索引不存在，无需删除" as message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
CREATE INDEX idx_test_msg_status ON t_chat_message (status, user_id, create_time) INVISIBLE;

-- 17. 文件类型测试索引（不可见）
SET @index_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = 'chat' AND table_name = 't_file_upload' AND index_name = 'idx_test_file_type');
SET @sql = IF(@index_exists > 0, 'DROP INDEX idx_test_file_type ON t_file_upload', 'SELECT "索引不存在，无需删除" as message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
CREATE INDEX idx_test_file_type ON t_file_upload (file_type, user_id, create_time) INVISIBLE;

-- ========================================
-- 第七部分: 统计信息更新
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
-- 第八部分: 验证索引创建
-- ========================================

SELECT '🎉 MySQL 8.0索引优化完成!' AS status;
SELECT CONCAT('优化完成时间: ', NOW()) AS completion_time;
SELECT '新增17个优化索引' AS summary;

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
