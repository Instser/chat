-- 安全的字段删除脚本
-- 执行时间: 2025-07-01 16:10:00
-- 目的: 安全地删除chat_count和daily_chat_limit字段

-- 1. 检查当前表结构
DESCRIBE t_package;

-- 2. 检查字段是否存在
SELECT 
    COLUMN_NAME,
    DATA_TYPE,
    IS_NULLABLE,
    COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_package' 
  AND COLUMN_NAME IN ('chat_count', 'daily_chat_limit');

-- 3. 检查是否有相关索引
SELECT 
    INDEX_NAME,
    COLUMN_NAME,
    NON_UNIQUE
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_package' 
  AND COLUMN_NAME IN ('chat_count', 'daily_chat_limit');

-- 4. 备份当前数据（可选）
-- CREATE TABLE t_package_backup_before_drop AS SELECT * FROM t_package;

-- 5. 删除字段
-- 注意：请确保应用程序已经部署新版本，不再使用这些字段

-- 删除chat_count字段
ALTER TABLE t_package DROP COLUMN chat_count;

-- 删除daily_chat_limit字段
ALTER TABLE t_package DROP COLUMN daily_chat_limit;

-- 6. 验证删除结果
DESCRIBE t_package;

-- 7. 检查删除后的表结构
SELECT 
    COLUMN_NAME,
    DATA_TYPE,
    IS_NULLABLE,
    COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_package' 
ORDER BY ORDINAL_POSITION;

-- 8. 优化表
OPTIMIZE TABLE t_package;

-- 9. 验证套餐数据完整性
SELECT 
    id,
    package_name,
    limit_type,
    model_permissions,
    CASE 
        WHEN model_permissions IS NULL OR model_permissions = '' THEN '缺少配置'
        WHEN JSON_VALID(model_permissions) = 0 THEN '配置格式错误'
        ELSE '配置正常'
    END as config_status
FROM t_package 
WHERE deleted = 0
ORDER BY id;

-- 10. 验证应用程序兼容性
-- 执行完成后，请重启应用程序并验证以下功能：
-- - 套餐列表查询
-- - 套餐详情查询  
-- - 用户套餐激活
-- - 聊天限制检查
-- - 聊天使用记录

-- 执行说明：
-- 1. 请确保应用程序已经部署了新版本代码
-- 2. 建议在维护窗口期间执行
-- 3. 执行前可以先备份数据
-- 4. 执行后需要重启应用程序
-- 5. 验证所有相关功能正常工作

-- 回滚方案（如果需要）：
-- 如果删除后发现问题，可以重新添加字段：
-- ALTER TABLE t_package ADD COLUMN chat_count INT DEFAULT 1000 COMMENT '包含的AI对话次数';
-- ALTER TABLE t_package ADD COLUMN daily_chat_limit INT DEFAULT 30 COMMENT '每日聊天次数限制';
-- 然后从备份表恢复数据或重新设置默认值
