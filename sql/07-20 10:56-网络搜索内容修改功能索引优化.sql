-- 网络搜索内容修改功能索引优化
-- 时间: 2025-07-20 10:56
-- 版本: v1.0 - 网络搜索内容修改功能索引优化
-- 作者: instser

-- 使用数据库
USE chat;

-- 1. 创建复合索引优化查询性能
-- 用于权限验证和内容更新的复合索引
CREATE INDEX IF NOT EXISTS idx_web_search_content_message_user
ON t_web_search_content(message_id, user_id, deleted);

-- 用于消息权限验证的索引（如果t_chat_message表需要）
-- 注意：这个索引可能已经存在，如果存在会被忽略
CREATE INDEX IF NOT EXISTS idx_chat_message_user_session
ON t_chat_message(user_id, session_id, message_id);

-- 2. 分析现有索引使用情况
-- 查看t_web_search_content表的索引
SHOW INDEX FROM t_web_search_content;

-- 3. 查询执行计划分析
-- 分析权限验证查询的执行计划
EXPLAIN SELECT * FROM t_web_search_content 
WHERE message_id = 'test-message-id' AND deleted = 0;

-- 分析用户权限检查查询的执行计划
EXPLAIN SELECT * FROM t_web_search_content 
WHERE message_id = 'test-message-id' AND user_id = 1 AND deleted = 0;

-- 4. 性能优化建议
-- 如果表数据量很大，可以考虑以下额外索引：

-- 用户历史查询优化索引
CREATE INDEX IF NOT EXISTS idx_web_search_content_user_time
ON t_web_search_content(user_id, deleted, create_time DESC);

-- 会话内容查询优化索引
CREATE INDEX IF NOT EXISTS idx_web_search_content_session_time
ON t_web_search_content(session_id, deleted, create_time ASC);

-- 5. 索引维护
-- 定期分析表统计信息以优化查询计划
ANALYZE TABLE t_web_search_content;

-- 6. 监控查询
-- 可以使用以下查询监控慢查询
-- SELECT * FROM mysql.slow_log WHERE sql_text LIKE '%t_web_search_content%';

-- 7. 索引使用情况检查
-- 检查索引的使用情况
SELECT 
    TABLE_NAME,
    INDEX_NAME,
    CARDINALITY,
    SUB_PART,
    PACKED,
    NULLABLE,
    INDEX_TYPE
FROM information_schema.STATISTICS 
WHERE TABLE_SCHEMA = 'chat' 
AND TABLE_NAME = 't_web_search_content'
ORDER BY TABLE_NAME, SEQ_IN_INDEX;

-- 8. 表空间和性能统计
SELECT 
    TABLE_NAME,
    TABLE_ROWS,
    DATA_LENGTH,
    INDEX_LENGTH,
    (DATA_LENGTH + INDEX_LENGTH) AS TOTAL_SIZE
FROM information_schema.TABLES 
WHERE TABLE_SCHEMA = 'chat' 
AND TABLE_NAME = 't_web_search_content';

-- 注意事项：
-- 1. 在生产环境执行前，请先在测试环境验证
-- 2. 创建索引可能会锁表，建议在低峰期执行
-- 3. 索引会占用额外的存储空间，需要权衡查询性能和存储成本
-- 4. 定期监控索引使用情况，删除不必要的索引
