-- ========================================
-- 检查has_web_search字段是否存在
-- 创建时间: 2025年7月11日 11:06 (北京时间)
-- 描述: 检查t_chat_message表中的has_web_search字段状态
-- ========================================

-- 使用chat数据库
USE chat;

-- 检查has_web_search字段是否存在
SELECT 
    'has_web_search字段检查' as check_type,
    CASE 
        WHEN COUNT(*) > 0 THEN CONCAT('✅ 字段存在，数据类型: ', DATA_TYPE, ', 默认值: ', IFNULL(COLUMN_DEFAULT, 'NULL'), ', 注释: ', COLUMN_COMMENT)
        ELSE '❌ 字段不存在'
    END as status
FROM information_schema.columns 
WHERE table_schema = DATABASE() 
  AND table_name = 't_chat_message' 
  AND column_name = 'has_web_search';

-- 如果字段不存在，则添加
SET @column_exists = (
    SELECT COUNT(*) 
    FROM information_schema.columns 
    WHERE table_schema = DATABASE() 
      AND table_name = 't_chat_message' 
      AND column_name = 'has_web_search'
);

-- 如果字段不存在，则添加
SET @sql = IF(@column_exists = 0, 
    'ALTER TABLE t_chat_message ADD COLUMN has_web_search TINYINT(1) DEFAULT 0 COMMENT ''是否使用联网查询（0-未使用，1-已使用）'' AFTER has_attachments;',
    'SELECT ''has_web_search字段已存在，无需添加'' as message;'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 再次检查字段状态
SELECT 
    'has_web_search字段最终状态' as check_type,
    COLUMN_NAME as field_name,
    DATA_TYPE as data_type,
    COLUMN_DEFAULT as default_value,
    COLUMN_COMMENT as comment
FROM information_schema.columns 
WHERE table_schema = DATABASE() 
  AND table_name = 't_chat_message' 
  AND column_name = 'has_web_search';

-- 检查现有数据中has_web_search字段的值分布
SELECT 
    'has_web_search数据分布' as check_type,
    has_web_search,
    COUNT(*) as count,
    CONCAT(ROUND(COUNT(*) * 100.0 / (SELECT COUNT(*) FROM t_chat_message WHERE deleted = 0), 2), '%') as percentage
FROM t_chat_message 
WHERE deleted = 0
GROUP BY has_web_search
ORDER BY has_web_search;

-- 检查是否有网络搜索内容表
SELECT 
    't_web_search_content表检查' as check_type,
    CASE 
        WHEN COUNT(*) > 0 THEN '✅ 表存在'
        ELSE '❌ 表不存在'
    END as status
FROM information_schema.tables 
WHERE table_schema = DATABASE() 
  AND table_name = 't_web_search_content';

-- 如果网络搜索内容表存在，检查数据
SELECT 
    '网络搜索内容数据统计' as check_type,
    COUNT(*) as total_records,
    COUNT(DISTINCT message_id) as unique_messages,
    COUNT(DISTINCT user_id) as unique_users
FROM t_web_search_content 
WHERE deleted = 0;

SELECT '✅ has_web_search字段检查完成' as status;
