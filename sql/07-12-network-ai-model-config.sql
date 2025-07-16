-- 联网AI模型配置脚本
-- 创建时间：2025-07-12 18:53
-- 用途：为联网模块重构添加专用的联网AI模型配置

-- 插入联网AI模型配置
INSERT INTO `t_ai_model` (
    `model_code`, 
    `model_name`, 
    `description`, 
    `provider`, 
    `version`, 
    `token_limit`, 
    `price_per_1k_tokens`, 
    `is_available`, 
    `is_visible`, 
    `base_url`, 
    `api_key`, 
    `create_time`, 
    `update_time`, 
    `deleted`
) VALUES (
    'network-ai',
    '联网AI模型',
    '专用于联网搜索的AI模型，具备实时信息获取能力',
    'NetworkAI',
    '1.0',
    8192,
    0.001000,
    1,
    0,  -- 不可见，仅供内部使用
    'https://api.example.com',  -- 需要替换为实际的联网AI API地址
    'your-network-ai-api-key',  -- 需要替换为实际的API密钥
    NOW(),
    NOW(),
    0
);

-- 验证插入结果
SELECT * FROM `t_ai_model` WHERE `model_code` = 'network-ai';
