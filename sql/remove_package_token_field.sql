-- ===================================================================
-- 删除套餐表的token_amount字段
-- 创建时间: 2025-06-18 03:03:00 (北京时间)
-- 说明: 移除t_package表中不再使用的token_amount字段
-- ===================================================================

USE chat;

-- 检查字段是否存在
SELECT 
    COLUMN_NAME,
    DATA_TYPE,
    IS_NULLABLE,
    COLUMN_DEFAULT,
    COLUMN_COMMENT
FROM 
    INFORMATION_SCHEMA.COLUMNS 
WHERE 
    TABLE_SCHEMA = 'chat' 
    AND TABLE_NAME = 't_package' 
    AND COLUMN_NAME = 'token_amount';

-- 删除token_amount字段
ALTER TABLE `t_package` DROP COLUMN `token_amount`;

-- 验证字段已删除
SELECT 
    COLUMN_NAME,
    DATA_TYPE,
    IS_NULLABLE,
    COLUMN_DEFAULT,
    COLUMN_COMMENT
FROM 
    INFORMATION_SCHEMA.COLUMNS 
WHERE 
    TABLE_SCHEMA = 'chat' 
    AND TABLE_NAME = 't_package' 
ORDER BY 
    ORDINAL_POSITION;

-- 显示修改后的表结构
DESCRIBE `t_package`;
