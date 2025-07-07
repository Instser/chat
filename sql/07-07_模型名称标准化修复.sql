-- =====================================================
-- 模型名称标准化修复脚本
-- 创建时间: 2025-07-07 21:30
-- 目的: 修正AI模型表中的model_name字段，确保与套餐配置中的键名一致
-- =====================================================

-- 查看当前模型配置
SELECT 
    id,
    model_code,
    model_name,
    description,
    provider,
    is_available
FROM t_ai_model 
WHERE deleted = 0
ORDER BY id;

-- =====================================================
-- 修正模型名称，确保与套餐权限配置一致
-- =====================================================

-- 修正BETA MODEL为BETA_MODEL（如果存在）
UPDATE t_ai_model 
SET model_name = 'BETA_MODEL'
WHERE model_name IN ('BETA MODEL', 'BETA MODLE', 'BETA_MODLE')
  AND deleted = 0;

-- 修正其他可能的模型名称标准化
UPDATE t_ai_model 
SET model_name = 'STANDARD'
WHERE model_name IN ('GPT-4', 'GPT-3.5 Turbo', 'STANDARD MODEL', 'Standard')
  AND deleted = 0;

UPDATE t_ai_model 
SET model_name = 'PERFORMANCE'
WHERE model_name IN ('PERFORMANCE MODEL', 'Performance', 'HIGH_PERFORMANCE')
  AND deleted = 0;

-- =====================================================
-- 验证修正结果
-- =====================================================
SELECT 
    id,
    model_code,
    model_name,
    description,
    provider,
    is_available,
    CASE 
        WHEN model_name IN ('STANDARD', 'PERFORMANCE', 'BETA_MODEL') THEN '✓ 标准格式'
        ELSE '⚠ 需要检查'
    END as name_status
FROM t_ai_model 
WHERE deleted = 0
ORDER BY id;

-- =====================================================
-- 检查套餐配置是否支持这些模型
-- =====================================================
SELECT 
    p.id as package_id,
    p.package_name,
    p.package_type,
    JSON_EXTRACT(p.model_permissions, '$.STANDARD.daily_limit') as standard_limit,
    JSON_EXTRACT(p.model_permissions, '$.PERFORMANCE.daily_limit') as performance_limit,
    JSON_EXTRACT(p.model_permissions, '$.BETA_MODEL.daily_limit') as beta_limit
FROM t_package p
WHERE p.deleted = 0
  AND p.model_permissions IS NOT NULL
ORDER BY p.id;

-- =====================================================
-- 清理可能存在的错误使用记录
-- =====================================================
-- 注意：这个操作会删除使用了错误模型名称的记录
-- 如果需要保留历史数据，请先备份

-- 查看可能需要清理的记录
SELECT 
    model_name,
    COUNT(*) as record_count
FROM t_user_daily_usage 
WHERE model_name NOT IN ('STANDARD', 'PERFORMANCE', 'BETA_MODEL')
GROUP BY model_name;

-- 如果确认需要清理，取消下面的注释
-- DELETE FROM t_user_daily_usage 
-- WHERE model_name NOT IN ('STANDARD', 'PERFORMANCE', 'BETA_MODEL');

-- =====================================================
-- 完成提示
-- =====================================================
SELECT 
    '模型名称标准化修复完成' as status,
    NOW() as completion_time;
