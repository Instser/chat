-- 验证字段删除结果的脚本

-- 1. 检查字段是否已删除
SELECT 
    '检查字段删除状态' as description,
    CASE 
        WHEN COUNT(*) = 0 THEN '字段已成功删除'
        ELSE CONCAT('仍有', COUNT(*), '个字段未删除')
    END as status
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_package' 
  AND COLUMN_NAME IN ('chat_count', 'daily_chat_limit');

-- 2. 显示当前表结构
SELECT 
    '当前表结构' as description;

DESCRIBE t_package;

-- 3. 验证套餐数据完整性
SELECT 
    '套餐数据验证' as description,
    COUNT(*) as total_packages,
    SUM(CASE WHEN model_permissions IS NOT NULL AND model_permissions != '' THEN 1 ELSE 0 END) as configured_packages,
    SUM(CASE WHEN limit_type = 'daily' THEN 1 ELSE 0 END) as daily_limit_packages
FROM t_package 
WHERE deleted = 0;

-- 4. 检查model_permissions配置状态
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

-- 5. 验证每日限制套餐用户状态
SELECT 
    '每日限制套餐用户状态' as description,
    COUNT(*) as total_users,
    SUM(CASE WHEN ua.chat_count_remaining = -1 THEN 1 ELSE 0 END) as unlimited_users,
    SUM(CASE WHEN ua.chat_count_remaining > 0 THEN 1 ELSE 0 END) as limited_users
FROM t_user_account ua
JOIN t_package p ON ua.package_id = p.id
WHERE p.limit_type = 'daily' 
  AND ua.deleted = 0 
  AND p.deleted = 0;

-- 6. 显示删除完成状态
SELECT 
    '=== 字段删除验证完成 ===' as result,
    NOW() as verification_time;
