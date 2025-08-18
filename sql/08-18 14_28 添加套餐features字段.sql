-- ========================================
-- 套餐表添加features字段
-- 创建时间: 2025-08-18 14:28
-- 版本: v1.0
-- 描述: 为t_package表添加features字段，用于存储套餐功能特性列表
-- ========================================

-- 检查字段是否已存在
SELECT COUNT(*) as field_exists 
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_package' 
  AND COLUMN_NAME = 'features';

-- 添加features字段
ALTER TABLE `t_package` 
ADD COLUMN `features` JSON COMMENT '套餐功能特性列表，JSON格式存储' 
AFTER `model_permissions`;

-- 为现有数据设置默认的features值
UPDATE `t_package` 
SET `features` = JSON_ARRAY('Standard features') 
WHERE `features` IS NULL;

-- 根据套餐类型设置不同的features
-- 基础套餐 (basic)
UPDATE `t_package` 
SET `features` = JSON_ARRAY(
    'Basic AI chat functionality',
    'Standard model access',
    'Daily usage limits',
    'Email support'
) 
WHERE `package_type` = 'basic';

-- 中级套餐 (medium)
UPDATE `t_package` 
SET `features` = JSON_ARRAY(
    'Enhanced AI chat functionality',
    'Standard and Performance model access',
    'Higher daily usage limits',
    'Priority email support',
    'Chat history export'
) 
WHERE `package_type` = 'medium';

-- 高级套餐 (high)
UPDATE `t_package` 
SET `features` = JSON_ARRAY(
    'Premium AI chat functionality',
    'All model access including Beta models',
    'Unlimited daily usage',
    '24/7 priority support',
    'Advanced chat history management',
    'API access',
    'Custom integrations'
) 
WHERE `package_type` = 'high';

-- 验证字段添加结果
SELECT 
    id,
    package_name,
    package_type,
    features
FROM `t_package` 
WHERE `deleted` = 0
ORDER BY `sort_order`;

-- 显示表结构确认
DESCRIBE `t_package`;

COMMIT;
