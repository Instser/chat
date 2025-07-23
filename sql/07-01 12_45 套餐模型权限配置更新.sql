-- =====================================================
-- 套餐模型权限配置更新脚本
-- 创建时间: 2025-07-01 12:45:00
-- 说明: 为现有套餐配置模型权限JSON数据
-- =====================================================

-- 查看当前套餐数据
SELECT id, package_name, package_type, model_permissions FROM t_package ORDER BY id;

-- =====================================================
-- 基础套餐 (basic) 模型权限配置
-- =====================================================
UPDATE `t_package` SET `model_permissions` = '{
  "STANDARD": {
    "daily_limit": 300,
    "description": "标准模型每日300次"
  },
  "PERFORMANCE": {
    "daily_limit": 20,
    "description": "性能模型每日20次"
  },
  "BETA_MODEL": {
    "daily_limit": 5,
    "description": "测试模型每日5次"
  }
}' WHERE `package_type` = 'basic';

-- =====================================================
-- 中级套餐 (medium) 模型权限配置
-- =====================================================
UPDATE `t_package` SET `model_permissions` = '{
  "STANDARD": {
    "daily_limit": 500,
    "description": "标准模型每日500次"
  },
  "PERFORMANCE": {
    "daily_limit": 300,
    "description": "性能模型每日300次"
  },
  "BETA_MODEL": {
    "daily_limit": 20,
    "description": "测试模型每日20次"
  }
}' WHERE `package_type` = 'medium';

-- =====================================================
-- 高级套餐 (high) 模型权限配置
-- =====================================================
UPDATE `t_package` SET `model_permissions` = '{
  "STANDARD": {
    "daily_limit": -1,
    "description": "标准模型无限制"
  },
  "PERFORMANCE": {
    "daily_limit": -1,
    "description": "性能模型无限制"
  },
  "BETA_MODEL": {
    "daily_limit": -1,
    "description": "测试模型无限制"
  }
}' WHERE `package_type` = 'high';

-- =====================================================
-- 如果有其他套餐类型，可以根据需要添加配置
-- =====================================================

-- 免费套餐 (free) - 如果存在
UPDATE `t_package` SET `model_permissions` = '{
  "STANDARD": {
    "daily_limit": 10,
    "description": "标准模型每日10次"
  },
  "PERFORMANCE": {
    "daily_limit": 0,
    "description": "性能模型不可用"
  },
  "BETA_MODEL": {
    "daily_limit": 0,
    "description": "测试模型不可用"
  }
}' WHERE `package_type` = 'free';

-- 专业套餐 (professional) - 如果存在
UPDATE `t_package` SET `model_permissions` = '{
  "STANDARD": {
    "daily_limit": 1000,
    "description": "标准模型每日1000次"
  },
  "PERFORMANCE": {
    "daily_limit": 500,
    "description": "性能模型每日500次"
  },
  "BETA_MODEL": {
    "daily_limit": 50,
    "description": "测试模型每日50次"
  }
}' WHERE `package_type` = 'professional';

-- 企业套餐 (enterprise) - 如果存在
UPDATE `t_package` SET `model_permissions` = '{
  "STANDARD": {
    "daily_limit": -1,
    "description": "标准模型无限制"
  },
  "PERFORMANCE": {
    "daily_limit": -1,
    "description": "性能模型无限制"
  },
  "BETA_MODEL": {
    "daily_limit": -1,
    "description": "测试模型无限制"
  }
}' WHERE `package_type` = 'enterprise';

-- =====================================================
-- 验证更新结果
-- =====================================================
SELECT 
    id,
    package_name,
    package_type,
    JSON_PRETTY(model_permissions) as formatted_permissions
FROM t_package 
WHERE model_permissions IS NOT NULL
ORDER BY 
    CASE package_type 
        WHEN 'free' THEN 1
        WHEN 'basic' THEN 2
        WHEN 'medium' THEN 3
        WHEN 'high' THEN 4
        WHEN 'professional' THEN 5
        WHEN 'enterprise' THEN 6
        ELSE 7
    END, id;

-- =====================================================
-- 检查JSON格式是否正确
-- =====================================================
SELECT 
    id,
    package_name,
    package_type,
    CASE 
        WHEN JSON_VALID(model_permissions) = 1 THEN 'Valid JSON'
        ELSE 'Invalid JSON'
    END as json_status,
    model_permissions
FROM t_package 
WHERE model_permissions IS NOT NULL;

-- =====================================================
-- 测试JSON解析 - 获取特定模型的限制
-- =====================================================
SELECT 
    id,
    package_name,
    package_type,
    JSON_EXTRACT(model_permissions, '$.STANDARD.daily_limit') as standard_limit,
    JSON_EXTRACT(model_permissions, '$.PERFORMANCE.daily_limit') as performance_limit,
    JSON_EXTRACT(model_permissions, '$.BETA_MODEL.daily_limit') as beta_limit
FROM t_package 
WHERE model_permissions IS NOT NULL
ORDER BY package_type, id;
