-- 移除废弃字段脚本
-- 执行时间: 2025-07-01 14:00:00
-- 目的: 移除chat_count和daily_chat_limit字段，完全使用model_permissions替代
-- 注意: 执行前请确保所有套餐都已正确配置model_permissions字段

-- 1. 数据备份（可选，建议执行）
-- CREATE TABLE IF NOT EXISTS t_package_backup_20250701 AS 
-- SELECT * FROM t_package WHERE deleted = 0;

-- 2. 验证model_permissions配置完整性
-- 检查是否有套餐缺少model_permissions配置
SELECT 
    id, 
    package_name, 
    package_type,
    model_permissions,
    CASE 
        WHEN model_permissions IS NULL OR model_permissions = '' THEN '缺少配置'
        WHEN JSON_VALID(model_permissions) = 0 THEN '配置格式错误'
        ELSE '配置正常'
    END as config_status
FROM t_package 
WHERE deleted = 0
ORDER BY id;

-- 3. 更新用户账户表中每日限制套餐的总次数为无限制
-- 将使用每日限制套餐的用户账户总次数设置为-1（无限制）
UPDATE t_user_account ua
JOIN t_package p ON ua.package_id = p.id
SET ua.chat_count_remaining = -1
WHERE p.limit_type = 'daily' 
  AND p.deleted = 0 
  AND ua.deleted = 0
  AND ua.chat_count_remaining != -1;

-- 4. 记录更新的用户数量
SELECT 
    COUNT(*) as updated_users,
    'Updated users with daily limit packages to unlimited total chats' as description
FROM t_user_account ua
JOIN t_package p ON ua.package_id = p.id
WHERE p.limit_type = 'daily' 
  AND p.deleted = 0 
  AND ua.deleted = 0
  AND ua.chat_count_remaining = -1;

-- 5. 验证每日限制套餐的用户账户状态
SELECT 
    p.id as package_id,
    p.package_name,
    p.limit_type,
    COUNT(ua.id) as user_count,
    SUM(CASE WHEN ua.chat_count_remaining = -1 THEN 1 ELSE 0 END) as unlimited_users,
    SUM(CASE WHEN ua.chat_count_remaining != -1 THEN 1 ELSE 0 END) as limited_users
FROM t_package p
LEFT JOIN t_user_account ua ON p.id = ua.package_id AND ua.deleted = 0
WHERE p.deleted = 0 AND p.limit_type = 'daily'
GROUP BY p.id, p.package_name, p.limit_type
ORDER BY p.id;

-- 6. 删除废弃字段（谨慎执行）
-- 注意：执行前请确保应用程序已经部署新版本，不再使用这些字段

-- 删除chat_count字段
ALTER TABLE t_package DROP COLUMN chat_count;

-- 删除daily_chat_limit字段  
ALTER TABLE t_package DROP COLUMN daily_chat_limit;

-- 7. 验证字段删除结果（如果执行了删除操作）
DESCRIBE t_package;

-- 8. 清理相关索引（如果存在）
-- 先查看现有索引
SELECT
    INDEX_NAME,
    COLUMN_NAME,
    TABLE_NAME
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 't_package'
  AND INDEX_NAME IN ('idx_package_chat_count', 'idx_package_daily_limit');

-- 删除索引（如果存在的话，可能这些索引并不存在）
-- DROP INDEX idx_package_chat_count ON t_package;
-- DROP INDEX idx_package_daily_limit ON t_package;

-- 9. 优化表结构
OPTIMIZE TABLE t_package;

-- 10. 验证最终状态
SELECT 
    'Package table structure after cleanup' as description,
    COUNT(*) as total_packages,
    SUM(CASE WHEN model_permissions IS NOT NULL AND model_permissions != '' THEN 1 ELSE 0 END) as configured_packages,
    SUM(CASE WHEN limit_type = 'daily' THEN 1 ELSE 0 END) as daily_limit_packages,
    SUM(CASE WHEN limit_type = 'total' THEN 1 ELSE 0 END) as total_limit_packages
FROM t_package 
WHERE deleted = 0;

-- 11. 验证用户账户状态
SELECT 
    'User account status after cleanup' as description,
    COUNT(*) as total_accounts,
    SUM(CASE WHEN chat_count_remaining = -1 THEN 1 ELSE 0 END) as unlimited_accounts,
    SUM(CASE WHEN chat_count_remaining > 0 THEN 1 ELSE 0 END) as limited_accounts,
    SUM(CASE WHEN chat_count_remaining = 0 THEN 1 ELSE 0 END) as exhausted_accounts
FROM t_user_account 
WHERE deleted = 0 AND package_id IS NOT NULL;

-- 12. 生成迁移报告
SELECT 
    '=== 迁移完成报告 ===' as report_section,
    NOW() as completion_time;

SELECT 
    'Daily limit packages' as category,
    COUNT(*) as count,
    GROUP_CONCAT(CONCAT(id, ':', package_name) SEPARATOR ', ') as details
FROM t_package 
WHERE deleted = 0 AND limit_type = 'daily'
UNION ALL
SELECT 
    'Total limit packages' as category,
    COUNT(*) as count,
    GROUP_CONCAT(CONCAT(id, ':', package_name) SEPARATOR ', ') as details
FROM t_package 
WHERE deleted = 0 AND limit_type = 'total'
UNION ALL
SELECT 
    'Unlimited packages' as category,
    COUNT(*) as count,
    GROUP_CONCAT(CONCAT(id, ':', package_name) SEPARATOR ', ') as details
FROM t_package 
WHERE deleted = 0 AND limit_type = 'unlimited';

-- 执行说明：
-- 1. 首先执行验证查询（步骤2），确保所有套餐都有正确的model_permissions配置
-- 2. 执行用户账户更新（步骤3-5），将每日限制套餐用户的总次数设为无限制
-- 3. 部署新版本应用程序
-- 4. 验证应用程序运行正常后，再考虑执行字段删除操作（步骤6）
-- 5. 执行最终验证（步骤10-12）

-- 回滚方案：
-- 如果需要回滚，可以使用备份表恢复数据：
-- INSERT INTO t_package SELECT * FROM t_package_backup_20250701 WHERE id NOT IN (SELECT id FROM t_package);
