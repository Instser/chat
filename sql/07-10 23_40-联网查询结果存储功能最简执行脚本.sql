-- ========================================
-- 联网查询结果存储功能最简执行脚本
-- 创建时间: 2025年7月10日 23:40 (北京时间)
-- 版本: v2.0.0-websearch-storage-minimal
-- 描述: 最简化版本，直接执行，适合快速部署
-- ========================================

-- 使用chat数据库
USE chat;

-- ========================================
-- 第一步：为t_chat_message表添加has_web_search字段
-- ========================================

-- 检查字段是否存在，如果不存在则添加
SET @column_exists = (
    SELECT COUNT(*) 
    FROM information_schema.columns 
    WHERE table_schema = DATABASE() 
      AND table_name = 't_chat_message' 
      AND column_name = 'has_web_search'
);

-- 如果字段不存在，则添加
SET @sql = IF(@column_exists = 0, 
    'ALTER TABLE t_chat_message ADD COLUMN has_web_search TINYINT(1) DEFAULT 0 COMMENT ''是否使用联网查询（0-未使用，1-已使用）'';',
    'SELECT ''has_web_search字段已存在'' as message;'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 为现有数据设置默认值
UPDATE t_chat_message SET has_web_search = 0 WHERE has_web_search IS NULL;

-- ========================================
-- 第二步：创建t_web_search_content表
-- ========================================

-- 检查表是否存在
SET @table_exists = (
    SELECT COUNT(*) 
    FROM information_schema.tables 
    WHERE table_schema = DATABASE() 
      AND table_name = 't_web_search_content'
);

-- 如果表不存在，则创建
SET @sql = IF(@table_exists = 0, 
    'CREATE TABLE t_web_search_content (
      id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT ''主键ID'',
      web_search_id VARCHAR(64) NOT NULL COMMENT ''联网内容ID（UUID）'',
      user_id BIGINT NOT NULL COMMENT ''用户ID'',
      session_id VARCHAR(64) NOT NULL COMMENT ''会话ID'',
      message_id VARCHAR(64) NOT NULL COMMENT ''消息ID'',
      query_text TEXT NOT NULL COMMENT ''查询内容'',
      content JSON NOT NULL COMMENT ''联网查询结果JSON（存储results数组，包含url、title、content[500字符]、favicon）'',
      result_count INT DEFAULT 0 COMMENT ''结果数量'',
      status TINYINT DEFAULT 1 COMMENT ''状态（0-失败，1-成功）'',
      create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'',
      update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'',
      deleted TINYINT DEFAULT 0 COMMENT ''逻辑删除标志（0-未删除，1-已删除）'',
      UNIQUE KEY uk_message_id (message_id),
      KEY idx_user_id (user_id),
      KEY idx_session_id (session_id),
      KEY idx_web_search_id (web_search_id),
      KEY idx_user_session_time (user_id, session_id, create_time),
      KEY idx_status_time (status, create_time),
      KEY idx_deleted_time (deleted, create_time),
      KEY idx_user_deleted_time (user_id, deleted, create_time DESC),
      KEY idx_session_deleted_time (session_id, deleted, create_time ASC)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''联网查询内容表'';',
    'SELECT ''t_web_search_content表已存在'' as message;'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ========================================
-- 第三步：为t_chat_message表添加索引
-- ========================================

-- 添加联网查询相关索引
SET @index1_exists = (
    SELECT COUNT(*) 
    FROM information_schema.statistics 
    WHERE table_schema = DATABASE() 
      AND table_name = 't_chat_message' 
      AND index_name = 'idx_web_search_session'
);

SET @sql = IF(@index1_exists = 0, 
    'ALTER TABLE t_chat_message ADD INDEX idx_web_search_session (has_web_search, session_id, create_time);',
    'SELECT ''idx_web_search_session索引已存在'' as message;'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index2_exists = (
    SELECT COUNT(*) 
    FROM information_schema.statistics 
    WHERE table_schema = DATABASE() 
      AND table_name = 't_chat_message' 
      AND index_name = 'idx_web_search_user'
);

SET @sql = IF(@index2_exists = 0, 
    'ALTER TABLE t_chat_message ADD INDEX idx_web_search_user (user_id, has_web_search, create_time);',
    'SELECT ''idx_web_search_user索引已存在'' as message;'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ========================================
-- 第四步：验证创建结果
-- ========================================

-- 验证has_web_search字段
SELECT 'has_web_search字段验证' as check_type;
SELECT 
    COLUMN_NAME as field_name,
    DATA_TYPE as data_type,
    COLUMN_DEFAULT as default_value,
    COLUMN_COMMENT as comment
FROM information_schema.columns 
WHERE table_schema = DATABASE() 
  AND table_name = 't_chat_message' 
  AND column_name = 'has_web_search';

-- 验证t_web_search_content表
SELECT 't_web_search_content表验证' as check_type;
SELECT 
    COUNT(*) as field_count,
    'fields created' as status
FROM information_schema.columns 
WHERE table_schema = DATABASE() 
  AND table_name = 't_web_search_content';

-- 验证索引
SELECT '索引验证' as check_type;
SELECT 
    INDEX_NAME as index_name,
    'created' as status
FROM information_schema.statistics 
WHERE table_schema = DATABASE() 
  AND table_name = 't_chat_message'
  AND INDEX_NAME LIKE '%web_search%'
GROUP BY INDEX_NAME;

-- ========================================
-- 完成提示
-- ========================================

SELECT '✅ 联网查询结果存储功能基础结构创建完成' as status;
SELECT CONCAT('创建时间: ', NOW()) as completion_time;

-- 后续步骤提示
SELECT 
    '后续步骤' as next_steps,
    '1. 如需外键约束，请运行外键约束修复脚本
2. 更新应用代码使用新功能
3. 运行测试验证功能正常' as instructions;
