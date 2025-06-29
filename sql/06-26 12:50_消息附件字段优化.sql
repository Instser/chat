-- 消息附件字段优化SQL脚本
-- 📅 创建时间: 2025-06-26 12:50:00
-- 👤 创建人员: Augment Agent
-- 📊 脚本版本: v1.0

-- 为t_chat_message表添加has_attachments字段，用于标识消息是否包含附件
-- 这样可以提高查询效率，避免每次都需要关联查询t_file_upload表

-- 检查字段是否已存在，避免重复添加
SET @column_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_chat_message'
    AND COLUMN_NAME = 'has_attachments'
);

-- 如果字段不存在，则添加字段
SET @sql = IF(@column_exists = 0,
    'ALTER TABLE t_chat_message ADD COLUMN has_attachments TINYINT(1) DEFAULT 0 COMMENT ''是否包含附件（0-无附件，1-有附件）'' AFTER message_type',
    'SELECT ''字段has_attachments已存在，跳过添加'' AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 为has_attachments字段创建索引，提高查询效率
SET @index_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_chat_message'
    AND INDEX_NAME = 'idx_chat_msg_attachments'
);

SET @sql = IF(@index_exists = 0,
    'CREATE INDEX idx_chat_msg_attachments ON t_chat_message (has_attachments, session_id, create_time)',
    'SELECT ''索引idx_chat_msg_attachments已存在，跳过创建'' AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 创建复合索引，优化带附件消息的查询
SET @index_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_chat_message'
    AND INDEX_NAME = 'idx_chat_msg_user_attachments'
);

SET @sql = IF(@index_exists = 0,
    'CREATE INDEX idx_chat_msg_user_attachments ON t_chat_message (user_id, has_attachments, create_time)',
    'SELECT ''索引idx_chat_msg_user_attachments已存在，跳过创建'' AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 根据现有数据更新has_attachments字段
-- 通过关联t_file_upload表来确定哪些消息有附件
UPDATE t_chat_message cm
SET has_attachments = 1
WHERE EXISTS (
    SELECT 1 
    FROM t_file_upload fu 
    WHERE fu.message_id = cm.message_id 
    AND fu.deleted = 0
);

-- 显示更新结果
SELECT 
    COUNT(*) as total_messages,
    SUM(CASE WHEN has_attachments = 1 THEN 1 ELSE 0 END) as messages_with_attachments,
    SUM(CASE WHEN has_attachments = 0 THEN 1 ELSE 0 END) as messages_without_attachments
FROM t_chat_message 
WHERE deleted = 0;

-- 验证数据一致性
SELECT 
    'Data Consistency Check' as check_type,
    COUNT(*) as inconsistent_count
FROM t_chat_message cm
WHERE cm.deleted = 0
AND (
    (cm.has_attachments = 1 AND NOT EXISTS (
        SELECT 1 FROM t_file_upload fu 
        WHERE fu.message_id = cm.message_id AND fu.deleted = 0
    ))
    OR
    (cm.has_attachments = 0 AND EXISTS (
        SELECT 1 FROM t_file_upload fu 
        WHERE fu.message_id = cm.message_id AND fu.deleted = 0
    ))
);

-- 输出执行完成信息
SELECT 
    '消息附件字段优化完成' as status,
    NOW() as completion_time,
    'has_attachments字段已添加，索引已创建，数据已更新' as details;
