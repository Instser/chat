-- 安全检查并删除字段的脚本
-- 先检查字段是否存在，再决定是否删除

-- ========================================
-- 步骤1: 检查当前表结构
-- ========================================

-- 查看完整表结构
DESCRIBE t_package;

-- 查看所有列名
SELECT 
    COLUMN_NAME,
    DATA_TYPE,
    IS_NULLABLE,
    COLUMN_DEFAULT,
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_package'
ORDER BY ORDINAL_POSITION;

-- ========================================
-- 步骤2: 检查目标字段是否存在
-- ========================================

-- 检查chat_count字段
SELECT 
    'chat_count字段检查' as field_check,
    CASE 
        WHEN COUNT(*) > 0 THEN CONCAT('字段存在，字段名: ', GROUP_CONCAT(COLUMN_NAME))
        ELSE '字段不存在'
    END as status
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_package' 
  AND COLUMN_NAME = 'chat_count';

-- 检查daily_chat_limit字段
SELECT 
    'daily_chat_limit字段检查' as field_check,
    CASE 
        WHEN COUNT(*) > 0 THEN CONCAT('字段存在，字段名: ', GROUP_CONCAT(COLUMN_NAME))
        ELSE '字段不存在'
    END as status
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_package' 
  AND COLUMN_NAME = 'daily_chat_limit';

-- ========================================
-- 步骤3: 检查相关索引
-- ========================================

-- 查看表的所有索引
SELECT
    INDEX_NAME,
    COLUMN_NAME,
    NON_UNIQUE,
    INDEX_TYPE,
    COMMENT
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 't_package'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;

-- 检查目标字段相关的索引
SELECT
    '目标字段相关索引' as check_type,
    INDEX_NAME,
    COLUMN_NAME,
    NON_UNIQUE
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 't_package'
  AND COLUMN_NAME IN ('chat_count', 'daily_chat_limit');

-- 查找可能的相关索引（按命名模式）
SELECT
    '可能相关的索引' as check_type,
    INDEX_NAME,
    COLUMN_NAME,
    NON_UNIQUE
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 't_package'
  AND (INDEX_NAME LIKE '%chat%' OR INDEX_NAME LIKE '%count%' OR INDEX_NAME LIKE '%limit%');

-- ========================================
-- 步骤4: 查找可能的相似字段名
-- ========================================

-- 查找包含'chat'的字段
SELECT 
    '包含chat的字段' as search_type,
    COLUMN_NAME,
    DATA_TYPE,
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_package' 
  AND COLUMN_NAME LIKE '%chat%';

-- 查找包含'limit'的字段
SELECT 
    '包含limit的字段' as search_type,
    COLUMN_NAME,
    DATA_TYPE,
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_package' 
  AND COLUMN_NAME LIKE '%limit%';

-- 查找包含'count'的字段
SELECT 
    '包含count的字段' as search_type,
    COLUMN_NAME,
    DATA_TYPE,
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_package' 
  AND COLUMN_NAME LIKE '%count%';

-- ========================================
-- 步骤5: 条件删除索引（仅在索引存在时执行）
-- ========================================

-- 使用存储过程来条件删除索引
DELIMITER $$

-- 删除chat_count相关索引的存储过程
DROP PROCEDURE IF EXISTS DropChatCountIndexes$$
CREATE PROCEDURE DropChatCountIndexes()
BEGIN
    DECLARE index_exists INT DEFAULT 0;
    DECLARE done INT DEFAULT FALSE;
    DECLARE index_name VARCHAR(64);
    DECLARE cur CURSOR FOR
        SELECT DISTINCT INDEX_NAME
        FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 't_package'
          AND COLUMN_NAME = 'chat_count'
          AND INDEX_NAME != 'PRIMARY';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO index_name;
        IF done THEN
            LEAVE read_loop;
        END IF;

        SET @sql = CONCAT('DROP INDEX ', index_name, ' ON t_package');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        SELECT CONCAT('索引 ', index_name, ' 已删除') as result;
    END LOOP;
    CLOSE cur;

    IF NOT done THEN
        SELECT 'chat_count相关索引已全部删除' as result;
    ELSE
        SELECT 'chat_count字段无相关索引' as result;
    END IF;
END$$

-- 删除daily_chat_limit相关索引的存储过程
DROP PROCEDURE IF EXISTS DropDailyChatLimitIndexes$$
CREATE PROCEDURE DropDailyChatLimitIndexes()
BEGIN
    DECLARE index_exists INT DEFAULT 0;
    DECLARE done INT DEFAULT FALSE;
    DECLARE index_name VARCHAR(64);
    DECLARE cur CURSOR FOR
        SELECT DISTINCT INDEX_NAME
        FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 't_package'
          AND COLUMN_NAME = 'daily_chat_limit'
          AND INDEX_NAME != 'PRIMARY';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO index_name;
        IF done THEN
            LEAVE read_loop;
        END IF;

        SET @sql = CONCAT('DROP INDEX ', index_name, ' ON t_package');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        SELECT CONCAT('索引 ', index_name, ' 已删除') as result;
    END LOOP;
    CLOSE cur;

    IF NOT done THEN
        SELECT 'daily_chat_limit相关索引已全部删除' as result;
    ELSE
        SELECT 'daily_chat_limit字段无相关索引' as result;
    END IF;
END$$

DELIMITER ;

-- 执行索引删除操作
CALL DropChatCountIndexes();
CALL DropDailyChatLimitIndexes();

-- 清理存储过程
DROP PROCEDURE IF EXISTS DropChatCountIndexes;
DROP PROCEDURE IF EXISTS DropDailyChatLimitIndexes;

-- ========================================
-- 步骤6: 条件删除字段（仅在字段存在时执行）
-- ========================================

-- 使用存储过程来条件删除字段
DELIMITER $$

-- 删除chat_count字段的存储过程
DROP PROCEDURE IF EXISTS DropChatCountColumn$$
CREATE PROCEDURE DropChatCountColumn()
BEGIN
    DECLARE column_exists INT DEFAULT 0;
    
    SELECT COUNT(*) INTO column_exists
    FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() 
      AND TABLE_NAME = 't_package' 
      AND COLUMN_NAME = 'chat_count';
    
    IF column_exists > 0 THEN
        ALTER TABLE t_package DROP COLUMN chat_count;
        SELECT 'chat_count字段已删除' as result;
    ELSE
        SELECT 'chat_count字段不存在，无需删除' as result;
    END IF;
END$$

-- 删除daily_chat_limit字段的存储过程
DROP PROCEDURE IF EXISTS DropDailyChatLimitColumn$$
CREATE PROCEDURE DropDailyChatLimitColumn()
BEGIN
    DECLARE column_exists INT DEFAULT 0;
    
    SELECT COUNT(*) INTO column_exists
    FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() 
      AND TABLE_NAME = 't_package' 
      AND COLUMN_NAME = 'daily_chat_limit';
    
    IF column_exists > 0 THEN
        ALTER TABLE t_package DROP COLUMN daily_chat_limit;
        SELECT 'daily_chat_limit字段已删除' as result;
    ELSE
        SELECT 'daily_chat_limit字段不存在，无需删除' as result;
    END IF;
END$$

DELIMITER ;

-- 执行删除操作
CALL DropChatCountColumn();
CALL DropDailyChatLimitColumn();

-- 清理存储过程
DROP PROCEDURE IF EXISTS DropChatCountColumn;
DROP PROCEDURE IF EXISTS DropDailyChatLimitColumn;

-- ========================================
-- 步骤7: 验证删除结果
-- ========================================

-- 检查索引删除结果
SELECT
    '索引删除检查' as check_type,
    CASE
        WHEN COUNT(*) = 0 THEN '✅ 目标字段相关索引已全部删除'
        ELSE CONCAT('❌ 仍有', COUNT(*), '个相关索引未删除: ', GROUP_CONCAT(DISTINCT INDEX_NAME))
    END as status
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 't_package'
  AND COLUMN_NAME IN ('chat_count', 'daily_chat_limit')
  AND INDEX_NAME != 'PRIMARY';

-- 再次检查字段是否存在
SELECT 
    '删除后字段检查' as check_type,
    CASE 
        WHEN COUNT(*) = 0 THEN '✅ 目标字段已全部删除'
        ELSE CONCAT('❌ 仍有', COUNT(*), '个字段未删除: ', GROUP_CONCAT(COLUMN_NAME))
    END as status
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_package' 
  AND COLUMN_NAME IN ('chat_count', 'daily_chat_limit');

-- 查看最终表结构
DESCRIBE t_package;

-- ========================================
-- 步骤6: 验证数据完整性
-- ========================================

-- 验证套餐数据
SELECT 
    id,
    package_name,
    limit_type,
    model_permissions,
    validity_days,
    price
FROM t_package 
WHERE deleted = 0
ORDER BY id;

-- 验证model_permissions配置
SELECT 
    id,
    package_name,
    limit_type,
    CASE 
        WHEN model_permissions IS NULL OR model_permissions = '' THEN '❌ 缺少配置'
        WHEN JSON_VALID(model_permissions) = 0 THEN '❌ 配置格式错误'
        ELSE '✅ 配置正常'
    END as config_status,
    JSON_EXTRACT(model_permissions, '$.STANDARD.daily_limit') as standard_daily_limit
FROM t_package 
WHERE deleted = 0
ORDER BY id;

-- ========================================
-- 步骤7: 优化表
-- ========================================

OPTIMIZE TABLE t_package;

-- 完成提示
SELECT 
    '=== 字段删除操作完成 ===' as completion_message,
    NOW() as completion_time;
