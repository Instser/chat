-- =====================================================
-- 修复模型权限验证问题 - 2025-09-02
-- 目的：解决套餐1不支持模型qwen3-235b-a22b的问题
-- =====================================================

-- 1. 更新AI模型表中的model_name字段为模型等级
-- =====================================================
UPDATE `t_ai_model` SET `model_name` = 'STANDARD' 
WHERE `model_code` = 'qwen3-235b-a22b';

UPDATE `t_ai_model` SET `model_name` = 'STANDARD' 
WHERE `model_code` = 'gpt-3.5-turbo';

UPDATE `t_ai_model` SET `model_name` = 'PERFORMANCE' 
WHERE `model_code` = 'gpt-4';

-- 2. 更新套餐模型权限配置，将BETA_MODEL改为BEST_MODEL
-- =====================================================

-- 更新基础套餐配置
UPDATE `t_package` SET `model_permissions` = JSON_OBJECT(
    'STANDARD', JSON_OBJECT('daily_limit', 300, 'description', '标准模型每日300次'),
    'PERFORMANCE', JSON_OBJECT('daily_limit', 20, 'description', '性能模型每日20次'),
    'BEST_MODEL', JSON_OBJECT('daily_limit', 5, 'description', '顶级模型每日5次')
) WHERE `package_type` = 'basic';

-- 更新中级套餐配置
UPDATE `t_package` SET `model_permissions` = JSON_OBJECT(
    'STANDARD', JSON_OBJECT('daily_limit', 500, 'description', '标准模型每日500次'),
    'PERFORMANCE', JSON_OBJECT('daily_limit', 300, 'description', '性能模型每日300次'),
    'BEST_MODEL', JSON_OBJECT('daily_limit', 20, 'description', '顶级模型每日20次')
) WHERE `package_type` = 'medium';

-- 更新高级套餐配置
UPDATE `t_package` SET `model_permissions` = JSON_OBJECT(
    'STANDARD', JSON_OBJECT('daily_limit', -1, 'description', '标准模型无限制'),
    'PERFORMANCE', JSON_OBJECT('daily_limit', -1, 'description', '性能模型无限制'),
    'BEST_MODEL', JSON_OBJECT('daily_limit', -1, 'description', '顶级模型无限制')
) WHERE `package_type` = 'high';

-- 更新其他套餐类型（如果存在）
UPDATE `t_package` SET `model_permissions` = JSON_REPLACE(
    COALESCE(`model_permissions`, '{}'),
    '$.BETA_MODEL', JSON_OBJECT('daily_limit', JSON_EXTRACT(`model_permissions`, '$.BETA_MODEL.daily_limit'), 'description', '顶级模型')
) WHERE JSON_EXTRACT(`model_permissions`, '$.BETA_MODEL') IS NOT NULL;

-- 然后重命名JSON键
UPDATE `t_package` SET `model_permissions` = JSON_REMOVE(
    JSON_SET(`model_permissions`, '$.BEST_MODEL', JSON_EXTRACT(`model_permissions`, '$.BETA_MODEL')),
    '$.BETA_MODEL'
) WHERE JSON_EXTRACT(`model_permissions`, '$.BETA_MODEL') IS NOT NULL;

-- 3. 更新用户每日使用记录表中的模型名称
-- =====================================================
-- 将已有的使用记录从旧的模型标识更新为新的模型等级
UPDATE `t_user_daily_usage` SET `model_name` = 'STANDARD' 
WHERE `model_name` IN ('qwen3-235b-a22b', 'gpt-3.5-turbo', '通义千问', 'GPT-3.5 Turbo');

UPDATE `t_user_daily_usage` SET `model_name` = 'PERFORMANCE' 
WHERE `model_name` IN ('gpt-4', 'GPT-4');

UPDATE `t_user_daily_usage` SET `model_name` = 'BEST_MODEL' 
WHERE `model_name` IN ('BETA_MODEL', 'beta_model', 'Beta Model');

-- 4. 验证更新结果
-- =====================================================

-- 检查AI模型表的更新结果
SELECT 
    model_code,
    model_name as model_level,
    description,
    provider,
    is_available,
    is_visible
FROM `t_ai_model` 
WHERE deleted = 0
ORDER BY model_name, model_code;

-- 检查套餐权限配置的更新结果
SELECT 
    id,
    package_name,
    package_type,
    JSON_PRETTY(model_permissions) as formatted_permissions
FROM `t_package` 
WHERE deleted = 0
ORDER BY package_type, id;

-- 检查使用记录表的更新结果
SELECT 
    model_name as model_level,
    COUNT(*) as record_count,
    MIN(usage_date) as earliest_date,
    MAX(usage_date) as latest_date
FROM `t_user_daily_usage`
GROUP BY model_name
ORDER BY model_name;

-- 验证JSON格式
SELECT 
    id,
    package_name,
    CASE 
        WHEN JSON_VALID(model_permissions) = 1 THEN 'Valid JSON'
        ELSE 'Invalid JSON'
    END as json_status
FROM `t_package` 
WHERE model_permissions IS NOT NULL;

-- =====================================================
-- 完成提示
-- =====================================================
SELECT 
    '模型权限验证问题修复完成' as status,
    '已将模型代码映射到模型等级进行权限验证' as description,
    'qwen3-235b-a22b现在映射为STANDARD等级' as fix_detail,
    NOW() as update_time;