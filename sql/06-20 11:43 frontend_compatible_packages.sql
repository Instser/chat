-- 前端兼容套餐数据初始化脚本
-- 创建符合前端期望的套餐数据，使用数据库自增ID
-- 执行时间: 2025-06-20 11:43:00

-- 清理现有套餐数据（可选）
-- DELETE FROM t_package WHERE deleted = 0;

-- 插入前端期望的套餐数据
-- 前端将使用数据库返回的实际ID，不再固定为1-6

-- Free套餐 - 月付
INSERT INTO t_package (
    package_name, description, package_type, 
    price_usd, price_cny, validity_days, 
    chat_count, daily_chat_limit, limit_type,
    model_permissions, is_enabled, sort_order
) VALUES (
    'Free', 'Basic features for new users', 'free',
    0.00, 0.00, 30,
    100, 10, 'both',
    '["gpt-3.5-turbo"]', 1, 1
);

-- Free套餐 - 年付
INSERT INTO t_package (
    package_name, description, package_type,
    price_usd, price_cny, validity_days,
    chat_count, daily_chat_limit, limit_type,
    model_permissions, is_enabled, sort_order
) VALUES (
    'Free', 'Basic features for new users', 'free',
    0.00, 0.00, 365,
    1200, 10, 'both',
    '["gpt-3.5-turbo"]', 1, 2
);

-- Pro套餐 - 月付
INSERT INTO t_package (
    package_name, description, package_type,
    price_usd, price_cny, validity_days,
    chat_count, daily_chat_limit, limit_type,
    model_permissions, is_enabled, sort_order
) VALUES (
    'Pro', 'Best for regular users', 'basic',
    9.99, 68.00, 30,
    1000, 50, 'both',
    '["gpt-3.5-turbo", "gpt-4", "qwen-turbo"]', 1, 3
);

-- Pro套餐 - 年付
INSERT INTO t_package (
    package_name, description, package_type,
    price_usd, price_cny, validity_days,
    chat_count, daily_chat_limit, limit_type,
    model_permissions, is_enabled, sort_order
) VALUES (
    'Pro', 'Best for regular users', 'basic',
    99.99, 680.00, 365,
    12000, 50, 'both',
    '["gpt-3.5-turbo", "gpt-4", "qwen-turbo"]', 1, 4
);

-- Premium套餐 - 月付
INSERT INTO t_package (
    package_name, description, package_type,
    price_usd, price_cny, validity_days,
    chat_count, daily_chat_limit, limit_type,
    model_permissions, is_enabled, sort_order
) VALUES (
    'Premium', 'Advanced features for power users', 'premium',
    19.99, 128.00, 30,
    5000, 100, 'both',
    '["gpt-3.5-turbo", "gpt-4", "claude-3", "qwen-max"]', 1, 5
);

-- Premium套餐 - 年付
INSERT INTO t_package (
    package_name, description, package_type,
    price_usd, price_cny, validity_days,
    chat_count, daily_chat_limit, limit_type,
    model_permissions, is_enabled, sort_order
) VALUES (
    'Premium', 'Advanced features for power users', 'premium',
    199.99, 1280.00, 365,
    60000, 100, 'both',
    '["gpt-3.5-turbo", "gpt-4", "claude-3", "qwen-max"]', 1, 6
);

-- 查看插入的数据
SELECT 
    id,
    package_name,
    package_type,
    validity_days,
    CASE 
        WHEN validity_days >= 365 THEN 'year'
        ELSE 'month'
    END as period,
    price_cny,
    is_enabled,
    sort_order
FROM t_package 
WHERE deleted = 0 
ORDER BY sort_order;

-- 验证套餐数据
SELECT
    id,
    package_name,
    package_type,
    validity_days as period,  -- period字段直接返回validity_days数值
    price_cny,
    is_enabled
FROM t_package
WHERE deleted = 0
ORDER BY sort_order;
