-- =====================================================
-- 套餐模型权限管理系统 - 数据库升级脚本
-- 创建时间: 2025-07-01 12:19:00
-- 版本: v1.0
-- 说明: 最小化数据库修改，支持模型维度的使用限制
-- =====================================================

-- 1. 为 t_user_daily_usage 表添加 model_name 字段
-- =====================================================
ALTER TABLE `t_user_daily_usage` 
ADD COLUMN `model_name` varchar(50) NOT NULL DEFAULT 'STANDARD' COMMENT '模型名称(STANDARD/PERFORMANCE/BETA_MODEL)' AFTER `package_id`;

-- 2. 删除原有的唯一索引
-- =====================================================
ALTER TABLE `t_user_daily_usage` 
DROP INDEX `uk_user_package_date`;

-- 3. 创建新的唯一索引，支持模型维度
-- =====================================================
ALTER TABLE `t_user_daily_usage` 
ADD UNIQUE KEY `uk_user_package_model_date` (`user_id`, `package_id`, `model_name`, `usage_date`);

-- 4. 添加模型名称索引，优化查询性能
-- =====================================================
ALTER TABLE `t_user_daily_usage` 
ADD INDEX `idx_model_name` (`model_name`);

-- 5. 更新现有套餐的 model_permissions 配置为JSON格式
-- =====================================================

-- 更新基础套餐配置
UPDATE `t_package` SET `model_permissions` = JSON_OBJECT(
    'STANDARD', JSON_OBJECT('daily_limit', 300, 'description', '标准模型每日300次'),
    'PERFORMANCE', JSON_OBJECT('daily_limit', 20, 'description', '性能模型每日20次'),
    'BETA_MODEL', JSON_OBJECT('daily_limit', 5, 'description', '测试模型每日5次')
) WHERE `package_type` = 'basic' AND (`model_permissions` IS NULL OR `model_permissions` = '' OR `model_permissions` = '[]');

-- 更新中级套餐配置
UPDATE `t_package` SET `model_permissions` = JSON_OBJECT(
    'STANDARD', JSON_OBJECT('daily_limit', 500, 'description', '标准模型每日500次'),
    'PERFORMANCE', JSON_OBJECT('daily_limit', 300, 'description', '性能模型每日300次'),
    'BETA_MODEL', JSON_OBJECT('daily_limit', 20, 'description', '测试模型每日20次')
) WHERE `package_type` = 'medium' AND (`model_permissions` IS NULL OR `model_permissions` = '' OR `model_permissions` = '[]');

-- 更新高级套餐配置
UPDATE `t_package` SET `model_permissions` = JSON_OBJECT(
    'STANDARD', JSON_OBJECT('daily_limit', -1, 'description', '标准模型无限制'),
    'PERFORMANCE', JSON_OBJECT('daily_limit', -1, 'description', '性能模型无限制'),
    'BETA_MODEL', JSON_OBJECT('daily_limit', -1, 'description', '测试模型无限制')
) WHERE `package_type` = 'high' AND (`model_permissions` IS NULL OR `model_permissions` = '' OR `model_permissions` = '[]');

-- 6. 为现有的使用记录补充 model_name 字段（向后兼容）
-- =====================================================
-- 现有记录默认为 STANDARD 模型，已通过 DEFAULT 值处理

-- 7. 验证升级结果的查询语句
-- =====================================================

-- 检查表结构是否正确
SELECT 
    COLUMN_NAME,
    COLUMN_TYPE,
    IS_NULLABLE,
    COLUMN_DEFAULT,
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_user_daily_usage' 
  AND COLUMN_NAME = 'model_name';

-- 检查索引是否创建成功
SHOW INDEX FROM `t_user_daily_usage` WHERE Key_name IN ('uk_user_package_model_date', 'idx_model_name');

-- 检查套餐配置是否更新成功
SELECT 
    id,
    package_name,
    package_type,
    JSON_PRETTY(model_permissions) as model_permissions_formatted
FROM `t_package` 
WHERE package_type IN ('basic', 'medium', 'high')
ORDER BY package_type, id;

-- 8. 回滚脚本（如果需要）
-- =====================================================
/*
-- 回滚步骤（请谨慎使用）

-- 删除新增的索引
ALTER TABLE `t_user_daily_usage` DROP INDEX `uk_user_package_model_date`;
ALTER TABLE `t_user_daily_usage` DROP INDEX `idx_model_name`;

-- 恢复原有索引
ALTER TABLE `t_user_daily_usage` ADD UNIQUE KEY `uk_user_package_date` (`user_id`, `package_id`, `usage_date`);

-- 删除 model_name 字段
ALTER TABLE `t_user_daily_usage` DROP COLUMN `model_name`;

-- 恢复原有的 model_permissions 格式（如果需要）
UPDATE `t_package` SET `model_permissions` = '[]' WHERE package_type IN ('basic', 'medium', 'high');
*/

-- =====================================================
-- 升级完成提示
-- =====================================================
SELECT 
    '数据库升级完成' as status,
    '已添加model_name字段支持模型维度使用限制' as description,
    NOW() as upgrade_time;
