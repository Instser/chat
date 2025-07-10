-- ========================================
-- 外键约束修复脚本
-- 创建时间: 2025年7月10日 23:40 (北京时间)
-- 描述: 专门用于修复外键约束问题的脚本
-- ========================================

-- 使用chat数据库
USE chat;

-- ========================================
-- 第一部分：检查现有外键约束
-- ========================================

SELECT '检查现有外键约束' as step;

-- 检查t_web_search_content表的外键约束
SELECT 
    CONSTRAINT_NAME as constraint_name,
    TABLE_NAME as table_name,
    COLUMN_NAME as column_name,
    REFERENCED_TABLE_NAME as referenced_table,
    REFERENCED_COLUMN_NAME as referenced_column
FROM information_schema.KEY_COLUMN_USAGE 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_web_search_content'
  AND REFERENCED_TABLE_NAME IS NOT NULL;

-- ========================================
-- 第二部分：删除可能存在的外键约束
-- ========================================

SELECT '删除现有外键约束（如果存在）' as step;

-- 删除可能存在的外键约束
SET @sql = (
    SELECT CONCAT('ALTER TABLE t_web_search_content DROP FOREIGN KEY ', CONSTRAINT_NAME, ';')
    FROM information_schema.KEY_COLUMN_USAGE 
    WHERE TABLE_SCHEMA = DATABASE() 
      AND TABLE_NAME = 't_web_search_content'
      AND CONSTRAINT_NAME = 'fk_web_search_user'
    LIMIT 1
);
SET @sql = IFNULL(@sql, 'SELECT "fk_web_search_user约束不存在" as message;');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT CONCAT('ALTER TABLE t_web_search_content DROP FOREIGN KEY ', CONSTRAINT_NAME, ';')
    FROM information_schema.KEY_COLUMN_USAGE 
    WHERE TABLE_SCHEMA = DATABASE() 
      AND TABLE_NAME = 't_web_search_content'
      AND CONSTRAINT_NAME = 'fk_web_search_session'
    LIMIT 1
);
SET @sql = IFNULL(@sql, 'SELECT "fk_web_search_session约束不存在" as message;');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT CONCAT('ALTER TABLE t_web_search_content DROP FOREIGN KEY ', CONSTRAINT_NAME, ';')
    FROM information_schema.KEY_COLUMN_USAGE 
    WHERE TABLE_SCHEMA = DATABASE() 
      AND TABLE_NAME = 't_web_search_content'
      AND CONSTRAINT_NAME = 'fk_web_search_message'
    LIMIT 1
);
SET @sql = IFNULL(@sql, 'SELECT "fk_web_search_message约束不存在" as message;');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ========================================
-- 第三部分：重新创建外键约束
-- ========================================

SELECT '重新创建外键约束' as step;

-- 创建用户外键约束
ALTER TABLE t_web_search_content 
ADD CONSTRAINT fk_web_search_user 
FOREIGN KEY (user_id) REFERENCES t_user (id) 
ON DELETE RESTRICT ON UPDATE CASCADE;

-- 创建会话外键约束
ALTER TABLE t_web_search_content 
ADD CONSTRAINT fk_web_search_session 
FOREIGN KEY (session_id) REFERENCES t_chat_session (session_id) 
ON DELETE RESTRICT ON UPDATE CASCADE;

-- 创建消息外键约束
ALTER TABLE t_web_search_content 
ADD CONSTRAINT fk_web_search_message 
FOREIGN KEY (message_id) REFERENCES t_chat_message (message_id) 
ON DELETE CASCADE ON UPDATE CASCADE;

-- ========================================
-- 第四部分：验证外键约束
-- ========================================

SELECT '验证外键约束创建结果' as step;

-- 验证外键约束基本信息
SELECT 
    CONSTRAINT_NAME as constraint_name,
    TABLE_NAME as table_name,
    COLUMN_NAME as column_name,
    REFERENCED_TABLE_NAME as referenced_table,
    REFERENCED_COLUMN_NAME as referenced_column
FROM information_schema.KEY_COLUMN_USAGE 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_web_search_content'
  AND REFERENCED_TABLE_NAME IS NOT NULL
ORDER BY constraint_name;

-- 验证外键约束详细规则
SELECT 
    CONSTRAINT_NAME as constraint_name,
    DELETE_RULE as delete_rule,
    UPDATE_RULE as update_rule
FROM information_schema.REFERENTIAL_CONSTRAINTS 
WHERE CONSTRAINT_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_web_search_content'
ORDER BY constraint_name;

-- ========================================
-- 第五部分：数据一致性检查
-- ========================================

SELECT '数据一致性检查' as step;

-- 检查是否有孤立的用户ID
SELECT 
    '孤立用户ID检查' as check_type,
    COUNT(*) as orphaned_count
FROM t_web_search_content w
LEFT JOIN t_user u ON w.user_id = u.id
WHERE u.id IS NULL AND w.deleted = 0;

-- 检查是否有孤立的会话ID
SELECT 
    '孤立会话ID检查' as check_type,
    COUNT(*) as orphaned_count
FROM t_web_search_content w
LEFT JOIN t_chat_session s ON w.session_id = s.session_id
WHERE s.session_id IS NULL AND w.deleted = 0;

-- 检查是否有孤立的消息ID
SELECT 
    '孤立消息ID检查' as check_type,
    COUNT(*) as orphaned_count
FROM t_web_search_content w
LEFT JOIN t_chat_message m ON w.message_id = m.message_id
WHERE m.message_id IS NULL AND w.deleted = 0;

-- ========================================
-- 第六部分：完成提示
-- ========================================

SELECT '✅ 外键约束修复完成' as status;
SELECT CONCAT('修复时间: ', NOW()) as completion_time;

-- 提供清理建议（如果有孤立数据）
SELECT 
    '数据清理建议' as advice_type,
    '如果上述检查发现孤立数据，请先清理这些数据再重新运行外键约束创建脚本' as recommendations;
