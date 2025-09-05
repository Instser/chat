-- v2.0 并发安全优化 - 数据库结构更新 (MySQL兼容版本)
-- 创建时间: 2025-09-04 23:30
-- 说明: 为 t_user_account 表添加版本控制字段，支持乐观锁
-- 修复: 移除 IF NOT EXISTS 语法，提供兼容的MySQL语句

-- 检查版本字段是否已存在的安全方式
SET @exist_check = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_user_account'
      AND COLUMN_NAME = 'version'
);

-- 只有在字段不存在时才添加
SET @sql = CASE 
    WHEN @exist_check = 0 THEN 
        'ALTER TABLE t_user_account ADD COLUMN version BIGINT DEFAULT 0 NOT NULL COMMENT \'版本号，用于乐观锁控制\';'
    ELSE 
        'SELECT "version字段已存在，跳过添加" as message;'
END;

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查版本索引是否已存在
SET @index_exist_check = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_user_account'
      AND INDEX_NAME = 'idx_user_account_version'
);

-- 只有在索引不存在时才创建
SET @index_sql = CASE 
    WHEN @index_exist_check = 0 THEN 
        'CREATE INDEX idx_user_account_version ON t_user_account(user_id, version);'
    ELSE 
        'SELECT "idx_user_account_version索引已存在，跳过创建" as message;'
END;

PREPARE index_stmt FROM @index_sql;
EXECUTE index_stmt;
DEALLOCATE PREPARE index_stmt;

-- 更新现有记录的版本号（仅更新NULL值）
UPDATE t_user_account 
SET version = 0 
WHERE version IS NULL;

-- 验证更新结果
SELECT 
    'v2.0并发安全优化 - 数据库升级完成' as status,
    COUNT(*) as total_records,
    COUNT(CASE WHEN version = 0 THEN 1 END) as version_zero_records,
    COUNT(CASE WHEN version IS NOT NULL THEN 1 END) as version_not_null_records,
    MIN(version) as min_version,
    MAX(version) as max_version
FROM t_user_account;

-- 显示表结构变更
DESCRIBE t_user_account;

-- 验证索引创建
SHOW INDEX FROM t_user_account WHERE Key_name = 'idx_user_account_version';