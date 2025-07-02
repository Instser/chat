-- 快速检查表结构的脚本

-- 1. 查看完整表结构
DESCRIBE t_package;

-- 2. 查看所有字段详情
SELECT 
    ORDINAL_POSITION as position,
    COLUMN_NAME as field_name,
    DATA_TYPE as type,
    IS_NULLABLE as nullable,
    COLUMN_DEFAULT as default_value,
    COLUMN_COMMENT as comment
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_package'
ORDER BY ORDINAL_POSITION;

-- 3. 专门检查目标字段
SELECT 
    'Target Fields Check' as check_type,
    COLUMN_NAME,
    DATA_TYPE,
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_package' 
  AND COLUMN_NAME IN ('chat_count', 'daily_chat_limit');

-- 4. 查找相似字段名
SELECT 
    'Similar Fields' as check_type,
    COLUMN_NAME,
    DATA_TYPE
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_package' 
  AND (COLUMN_NAME LIKE '%chat%' OR COLUMN_NAME LIKE '%count%' OR COLUMN_NAME LIKE '%limit%');

-- 5. 检查表的所有索引
SELECT
    'All Indexes' as check_type,
    INDEX_NAME,
    COLUMN_NAME,
    NON_UNIQUE,
    INDEX_TYPE
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 't_package'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;

-- 6. 检查目标字段相关索引
SELECT
    'Target Field Indexes' as check_type,
    INDEX_NAME,
    COLUMN_NAME,
    NON_UNIQUE
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 't_package'
  AND COLUMN_NAME IN ('chat_count', 'daily_chat_limit');

-- 7. 查看当前套餐数据
SELECT
    id,
    package_name,
    limit_type,
    model_permissions
FROM t_package
WHERE deleted = 0
ORDER BY id;
