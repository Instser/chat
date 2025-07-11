-- ================================================================
-- 移除联网内容表query_text字段
-- 创建时间: 2025-07-11 09:49 (北京时间)
-- 版本: v1.0
-- 说明: 根据用户需求，移除t_web_search_content表中的query_text字段
--       因为查询文本不需要存储，只需要存储搜索结果即可
-- ================================================================

-- 检查当前数据库
SELECT DATABASE() as current_database;

-- 检查表是否存在
SELECT COUNT(*) as table_exists 
FROM information_schema.tables 
WHERE table_schema = DATABASE() 
AND table_name = 't_web_search_content';

-- 检查query_text字段是否存在
SELECT COUNT(*) as column_exists 
FROM information_schema.columns 
WHERE table_schema = DATABASE() 
AND table_name = 't_web_search_content' 
AND column_name = 'query_text';

-- 显示当前表结构（执行前）
SHOW CREATE TABLE t_web_search_content;

-- 备份当前数据（可选，如果需要保留query_text数据）
-- CREATE TABLE t_web_search_content_backup_20250711 AS 
-- SELECT * FROM t_web_search_content;

-- 移除query_text字段
ALTER TABLE t_web_search_content DROP COLUMN query_text;

-- 验证字段已被移除
SELECT COUNT(*) as column_exists_after_drop 
FROM information_schema.columns 
WHERE table_schema = DATABASE() 
AND table_name = 't_web_search_content' 
AND column_name = 'query_text';

-- 显示修改后的表结构
SHOW CREATE TABLE t_web_search_content;

-- 显示表的所有字段
DESCRIBE t_web_search_content;

-- 验证表数据完整性
SELECT 
    COUNT(*) as total_records,
    COUNT(DISTINCT message_id) as unique_messages,
    COUNT(DISTINCT user_id) as unique_users,
    COUNT(DISTINCT session_id) as unique_sessions
FROM t_web_search_content 
WHERE deleted = 0;

-- 检查是否有空的content字段
SELECT COUNT(*) as empty_content_records
FROM t_web_search_content 
WHERE (content IS NULL OR content = '' OR content = '[]') 
AND deleted = 0;

-- 显示最近的几条记录以验证数据完整性
SELECT 
    id,
    web_search_id,
    user_id,
    session_id,
    message_id,
    LEFT(content, 100) as content_preview,
    result_count,
    status,
    create_time
FROM t_web_search_content 
WHERE deleted = 0 
ORDER BY create_time DESC 
LIMIT 5;

-- 执行完成提示
SELECT '联网内容表query_text字段移除完成' as status,
       NOW() as completion_time,
       '2025-07-11 09:49' as script_version;
