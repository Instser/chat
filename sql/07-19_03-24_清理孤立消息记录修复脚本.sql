-- ========================================
-- 清理孤立消息记录修复脚本
-- 创建时间：2025-07-19 03:24
-- 问题描述：删除session时未删除相关message记录，导致数据不一致
-- 修复目标：清理那些session已被删除但message仍存在的孤立记录
-- ========================================

-- 1. 查询孤立的消息记录（session已被删除但message仍存在）
SELECT 
    COUNT(*) as orphan_message_count,
    '孤立消息数量' as description
FROM t_chat_message m
LEFT JOIN t_chat_session s ON m.session_id = s.session_id
WHERE s.session_id IS NULL
  AND m.deleted = 0;

-- 2. 查看孤立消息的详细信息（前10条）
SELECT 
    m.id,
    m.message_id,
    m.session_id,
    m.user_id,
    m.role,
    m.create_time,
    '孤立消息详情' as description
FROM t_chat_message m
LEFT JOIN t_chat_session s ON m.session_id = s.session_id
WHERE s.session_id IS NULL
  AND m.deleted = 0
ORDER BY m.create_time DESC
LIMIT 10;

-- 3. 查询孤立消息关联的文件记录
SELECT 
    COUNT(*) as orphan_file_count,
    '孤立消息关联的文件数量' as description
FROM t_file_upload f
WHERE f.message_id IN (
    SELECT m.message_id
    FROM t_chat_message m
    LEFT JOIN t_chat_session s ON m.session_id = s.session_id
    WHERE s.session_id IS NULL
      AND m.deleted = 0
)
AND f.deleted = 0;

-- 4. 查询孤立消息关联的联网内容记录
SELECT 
    COUNT(*) as orphan_web_search_count,
    '孤立消息关联的联网内容数量' as description
FROM t_web_search_content w
WHERE w.message_id IN (
    SELECT m.message_id
    FROM t_chat_message m
    LEFT JOIN t_chat_session s ON m.session_id = s.session_id
    WHERE s.session_id IS NULL
      AND m.deleted = 0
)
AND w.deleted = 0;

-- ========================================
-- 执行清理操作（请谨慎执行，建议先备份数据）
-- ========================================

-- 5. 删除孤立消息关联的联网内容记录
DELETE w FROM t_web_search_content w
WHERE w.message_id IN (
    SELECT m.message_id
    FROM t_chat_message m
    LEFT JOIN t_chat_session s ON m.session_id = s.session_id
    WHERE s.session_id IS NULL
      AND m.deleted = 0
)
AND w.deleted = 0;

-- 6. 删除孤立消息关联的文件记录
DELETE f FROM t_file_upload f
WHERE f.message_id IN (
    SELECT m.message_id
    FROM t_chat_message m
    LEFT JOIN t_chat_session s ON m.session_id = s.session_id
    WHERE s.session_id IS NULL
      AND m.deleted = 0
)
AND f.deleted = 0;

-- 7. 删除孤立的消息记录
DELETE m FROM t_chat_message m
LEFT JOIN t_chat_session s ON m.session_id = s.session_id
WHERE s.session_id IS NULL
  AND m.deleted = 0;

-- ========================================
-- 验证清理结果
-- ========================================

-- 8. 验证清理后的结果
SELECT 
    COUNT(*) as remaining_orphan_count,
    '清理后剩余孤立消息数量' as description
FROM t_chat_message m
LEFT JOIN t_chat_session s ON m.session_id = s.session_id
WHERE s.session_id IS NULL
  AND m.deleted = 0;

-- 9. 统计各表的记录数量
SELECT 
    (SELECT COUNT(*) FROM t_chat_session WHERE deleted = 0) as session_count,
    (SELECT COUNT(*) FROM t_chat_message WHERE deleted = 0) as message_count,
    (SELECT COUNT(*) FROM t_file_upload WHERE deleted = 0) as file_count,
    (SELECT COUNT(*) FROM t_web_search_content WHERE deleted = 0) as web_search_count,
    '清理后各表记录统计' as description;

-- ========================================
-- 注意事项
-- ========================================
/*
1. 执行前请务必备份数据库
2. 建议在测试环境先执行验证
3. 可以分步执行，先执行查询语句确认数据
4. 删除操作不可逆，请谨慎操作
5. 如果数据量很大，建议分批删除
*/
