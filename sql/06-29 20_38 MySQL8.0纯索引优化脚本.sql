/*
 MySQL 8.0 纯索引优化脚本
 
 只创建索引，不修改表结构和字段
 创建时间: 2025-06-29 20:38 (北京时间)
 版本: v1.0
 
 说明: 利用MySQL 8.0新特性创建高性能索引
*/

-- ========================================
-- MySQL 8.0 降序索引优化
-- ========================================

SELECT '🚀 创建MySQL 8.0降序索引...' AS step;

-- 1. 聊天消息时间降序索引
-- 作用: 优化 ORDER BY create_time DESC 查询，MySQL 8.0真正支持降序存储
DROP INDEX IF EXISTS idx_chat_msg_time_desc ON t_chat_message;
CREATE INDEX idx_chat_msg_time_desc
ON t_chat_message (create_time DESC, deleted)
COMMENT '消息时间降序索引-优化时间倒序查询性能';

-- 2. 会话活跃时间降序索引
-- 作用: 优化会话列表按最后活跃时间倒序排列
DROP INDEX IF EXISTS idx_session_active_desc ON t_chat_session;
CREATE INDEX idx_session_active_desc
ON t_chat_session (last_active_time DESC, deleted)
COMMENT '会话活跃时间降序索引-优化会话列表查询';

-- 3. 用户订单时间降序索引
-- 作用: 优化订单历史查询，按创建时间倒序
DROP INDEX IF EXISTS idx_user_order_time_desc ON t_user_order;
CREATE INDEX idx_user_order_time_desc
ON t_user_order (create_time DESC, payment_status)
COMMENT '订单时间降序索引-优化订单历史查询';

-- ========================================
-- 复合查询优化索引
-- ========================================

SELECT '📊 创建复合查询优化索引...' AS step;

-- 4. 用户消息时间优化索引
-- 作用: 优化用户聊天记录查询，支持用户ID过滤+时间倒序
DROP INDEX IF EXISTS idx_chat_msg_user_time_opt ON t_chat_message;
CREATE INDEX idx_chat_msg_user_time_opt
ON t_chat_message (user_id, create_time DESC, deleted)
COMMENT '用户消息时间优化索引-用户聊天记录查询';

-- 5. 用户会话活跃时间优化索引
-- 作用: 优化用户会话列表查询，支持用户ID过滤+活跃时间倒序
DROP INDEX IF EXISTS idx_session_user_active_opt ON t_chat_session;
CREATE INDEX idx_session_user_active_opt
ON t_chat_session (user_id, last_active_time DESC, deleted)
COMMENT '用户会话活跃时间优化索引-用户会话列表查询';

-- 6. 会话消息角色索引
-- 作用: 优化按会话ID和消息角色查询，如获取某会话的用户消息或AI回复
DROP INDEX IF EXISTS idx_chat_msg_session_role ON t_chat_message;
CREATE INDEX idx_chat_msg_session_role
ON t_chat_message (session_id, role, create_time DESC)
COMMENT '会话消息角色索引-按角色过滤消息';

-- ========================================
-- 覆盖索引优化
-- ========================================

SELECT '🎯 创建覆盖索引...' AS step;

-- 7. 文件查询覆盖索引
-- 作用: 覆盖文件列表查询的所有字段，避免回表查询
DROP INDEX IF EXISTS idx_file_user_cover ON t_file_upload;
CREATE INDEX idx_file_user_cover
ON t_file_upload (user_id, deleted, file_name, file_type, create_time)
COMMENT '文件查询覆盖索引-避免回表查询提升性能';

-- 8. 订单查询覆盖索引
-- 作用: 覆盖订单查询的主要字段，减少磁盘I/O
DROP INDEX IF EXISTS idx_order_user_cover ON t_user_order;
CREATE INDEX idx_order_user_cover
ON t_user_order (user_id, payment_status, create_time, amount)
COMMENT '订单查询覆盖索引-减少回表查询';

-- 9. 用户消息统计覆盖索引
-- 作用: 支持用户消息统计查询，包含会话ID、时间、角色等信息
DROP INDEX IF EXISTS idx_chat_msg_user_stats ON t_chat_message;
CREATE INDEX idx_chat_msg_user_stats
ON t_chat_message (user_id, deleted, session_id, create_time, role)
COMMENT '用户消息统计覆盖索引-支持复杂统计查询';

-- ========================================
-- MySQL 8.0 函数索引
-- ========================================

SELECT '✨ 创建MySQL 8.0函数索引...' AS step;

-- 10. 消息日期函数索引
-- 作用: 优化按日期统计消息的查询，如每日消息量统计
DROP INDEX IF EXISTS idx_chat_msg_date_func ON t_chat_message;
CREATE INDEX idx_chat_msg_date_func
ON t_chat_message ((DATE(create_time)), user_id)
COMMENT '消息日期函数索引-优化按日期统计查询';

-- 11. 订单日期函数索引
-- 作用: 优化按日期统计订单的查询，如每日订单量和收入统计
DROP INDEX IF EXISTS idx_order_date_func ON t_user_order;
CREATE INDEX idx_order_date_func
ON t_user_order ((DATE(create_time)), payment_status)
COMMENT '订单日期函数索引-优化按日期统计查询';

-- 12. 用户使用日期索引
-- 作用: 优化用户每日使用情况统计
DROP INDEX IF EXISTS idx_usage_date_user ON t_user_daily_usage;
CREATE INDEX idx_usage_date_user
ON t_user_daily_usage (usage_date DESC, user_id, chat_count_used)
COMMENT '用户使用日期索引-优化使用统计查询';

-- ========================================
-- 附件和关联查询优化索引
-- ========================================

SELECT '🔗 创建关联查询优化索引...' AS step;

-- 13. 消息附件关联索引
-- 作用: 优化查询带附件的消息
DROP INDEX IF EXISTS idx_chat_msg_attachments_opt ON t_chat_message;
CREATE INDEX idx_chat_msg_attachments_opt
ON t_chat_message (has_attachments, session_id, create_time DESC)
COMMENT '消息附件关联索引-优化附件消息查询';

-- 14. 文件会话关联索引
-- 作用: 优化按会话查询文件列表
DROP INDEX IF EXISTS idx_file_session_time ON t_file_upload;
CREATE INDEX idx_file_session_time
ON t_file_upload (session_id, create_time DESC, deleted)
COMMENT '文件会话关联索引-按会话查询文件';

-- 15. 模型消息统计索引
-- 作用: 优化AI模型使用统计查询
DROP INDEX IF EXISTS idx_chat_msg_model_stats ON t_chat_message;
CREATE INDEX idx_chat_msg_model_stats
ON t_chat_message (model_id, create_time DESC, deleted, token_count)
COMMENT '模型消息统计索引-AI模型使用统计';

-- ========================================
-- MySQL 8.0 不可见索引（用于测试）
-- ========================================

SELECT '🧪 创建测试用不可见索引...' AS step;

-- 16. 消息状态测试索引（不可见）
-- 作用: 用于测试按消息状态查询的性能，不影响现有查询计划
DROP INDEX IF EXISTS idx_test_msg_status ON t_chat_message;
CREATE INDEX idx_test_msg_status
ON t_chat_message (status, user_id, create_time) INVISIBLE
COMMENT '消息状态测试索引-性能测试用';

-- 17. 文件类型测试索引（不可见）
-- 作用: 用于测试按文件类型查询的性能
DROP INDEX IF EXISTS idx_test_file_type ON t_file_upload;
CREATE INDEX idx_test_file_type
ON t_file_upload (file_type, user_id, create_time) INVISIBLE
COMMENT '文件类型测试索引-性能测试用';

-- ========================================
-- 统计信息更新
-- ========================================

SELECT '📈 更新表统计信息...' AS step;

-- 更新所有相关表的统计信息，帮助优化器选择最佳执行计划
ANALYZE TABLE t_chat_message;
ANALYZE TABLE t_chat_session;
ANALYZE TABLE t_file_upload;
ANALYZE TABLE t_user_order;
ANALYZE TABLE t_user_daily_usage;
ANALYZE TABLE t_user;
ANALYZE TABLE t_ai_model;
ANALYZE TABLE t_package;

-- ========================================
-- 索引创建验证
-- ========================================

SELECT '✅ 验证新创建的索引...' AS step;

-- 显示所有新创建的优化索引
SELECT 
    table_name AS '表名',
    index_name AS '索引名',
    CASE 
        WHEN index_name LIKE '%_desc' THEN '降序索引'
        WHEN index_name LIKE '%_opt' THEN '查询优化索引'
        WHEN index_name LIKE '%_cover' THEN '覆盖索引'
        WHEN index_name LIKE '%_func' THEN '函数索引'
        WHEN index_name LIKE '%_stats' THEN '统计索引'
        WHEN index_name LIKE 'idx_test_%' THEN '测试索引(不可见)'
        ELSE '关联查询索引'
    END AS '索引类型',
    CASE 
        WHEN is_visible = 'YES' THEN '可见'
        ELSE '不可见'
    END AS '可见性'
FROM information_schema.statistics s
LEFT JOIN information_schema.statistics_extensions se ON s.index_name = se.index_name
WHERE s.table_schema = 'chat' 
  AND s.index_name IN (
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

SELECT '🔍 性能测试建议' AS test_recommendations;

-- 显示可以测试的查询示例
SELECT '以下查询将受益于新索引:' AS query_examples;
SELECT '1. 用户聊天记录查询 (使用 idx_chat_msg_user_time_opt)' AS example;
SELECT '   SELECT * FROM t_chat_message WHERE user_id = ? ORDER BY create_time DESC LIMIT 20;' AS sql_example;

SELECT '2. 用户会话列表查询 (使用 idx_session_user_active_opt)' AS example;
SELECT '   SELECT * FROM t_chat_session WHERE user_id = ? ORDER BY last_active_time DESC LIMIT 10;' AS sql_example;

SELECT '3. 每日消息统计查询 (使用 idx_chat_msg_date_func)' AS example;
SELECT '   SELECT DATE(create_time), COUNT(*) FROM t_chat_message WHERE user_id = ? GROUP BY DATE(create_time);' AS sql_example;

SELECT '4. 文件列表查询 (使用 idx_file_user_cover)' AS example;
SELECT '   SELECT file_name, file_type, create_time FROM t_file_upload WHERE user_id = ? AND deleted = 0;' AS sql_example;

-- ========================================
-- 完成报告
-- ========================================

SELECT '🎉 MySQL 8.0索引优化完成!' AS completion_status;
SELECT 
    '优化完成时间' AS item,
    NOW() AS value
UNION ALL
SELECT 
    '新增索引数量',
    '17个'
UNION ALL
SELECT 
    '降序索引数量',
    '3个'
UNION ALL
SELECT 
    '覆盖索引数量', 
    '3个'
UNION ALL
SELECT 
    '函数索引数量',
    
    '3个'
UNION ALL
SELECT 
    '测试索引数量',
    '2个(不可见)';

SELECT '建议: 重启应用以获得最佳性能表现' AS final_recommendation;
