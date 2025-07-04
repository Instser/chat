-- 套餐聊天次数限制优化脚本
-- 执行时间: 2025-06-18 12:10:00
-- 目的: 优化套餐表的聊天次数限制配置，支持复合限制类型

-- 1. 修改套餐表结构，添加新的限制字段
ALTER TABLE `t_package` 
ADD COLUMN `total_chat_limit` int(11) NOT NULL DEFAULT 1000 COMMENT '总聊天次数限制（-1表示无限制）' AFTER `chat_count`,
ADD COLUMN `daily_chat_limit_new` int(11) NOT NULL DEFAULT 30 COMMENT '每日聊天次数限制（-1表示无限制）' AFTER `total_chat_limit`,
ADD COLUMN `limit_strategy` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'both' COMMENT '限制策略（daily-仅每日限制，total-仅总次数限制，both-双重限制）' AFTER `daily_chat_limit_new`;

-- 2. 数据迁移：将现有数据迁移到新字段
UPDATE `t_package` SET 
    `total_chat_limit` = `chat_count`,
    `daily_chat_limit_new` = `daily_chat_limit`,
    `limit_strategy` = CASE 
        WHEN `limit_type` = 'daily' THEN 'daily'
        WHEN `limit_type` = 'total' THEN 'total'
        ELSE 'both'
    END;

-- 3. 更新免费套餐的限制配置
UPDATE `t_package` SET 
    `total_chat_limit` = 100,
    `daily_chat_limit_new` = 30,
    `limit_strategy` = 'both'
WHERE `id` = 1 AND `package_name` = '免费体验';

-- 4. 更新其他套餐的配置
-- 基础套餐：每天50次，总共1000次
UPDATE `t_package` SET 
    `total_chat_limit` = 1000,
    `daily_chat_limit_new` = 50,
    `limit_strategy` = 'both'
WHERE `id` = 2 AND `package_name` = '基础套餐';

-- 标准套餐：每天100次，总共3000次
UPDATE `t_package` SET 
    `total_chat_limit` = 3000,
    `daily_chat_limit_new` = 100,
    `limit_strategy` = 'both'
WHERE `id` = 3 AND `package_name` = '标准套餐';

-- 专业套餐：每天200次，总共8000次
UPDATE `t_package` SET 
    `total_chat_limit` = 8000,
    `daily_chat_limit_new` = 200,
    `limit_strategy` = 'both'
WHERE `id` = 4 AND `package_name` = '专业套餐';

-- 年度套餐：无限制
UPDATE `t_package` SET 
    `total_chat_limit` = -1,
    `daily_chat_limit_new` = -1,
    `limit_strategy` = 'unlimited'
WHERE `id` = 5 AND `package_name` = '年度套餐';

-- 5. 删除旧的字段（可选，建议先测试新字段正常工作后再执行）
-- ALTER TABLE `t_package` DROP COLUMN `daily_chat_limit`;
-- ALTER TABLE `t_package` DROP COLUMN `limit_type`;

-- 6. 添加索引优化查询性能
ALTER TABLE `t_package` ADD INDEX `idx_limit_strategy` (`limit_strategy`);

-- 7. 更新套餐描述，反映新的限制策略
UPDATE `t_package` SET 
    `description` = '新用户免费体验套餐，每天30次聊天，总共100次'
WHERE `id` = 1;

UPDATE `t_package` SET 
    `description` = '满足日常聊天需求，每天50次聊天，总共1000次'
WHERE `id` = 2;

UPDATE `t_package` SET 
    `description` = '适合中度使用者，每天100次聊天，总共3000次'
WHERE `id` = 3;

UPDATE `t_package` SET 
    `description` = '适合重度使用者，每天200次聊天，总共8000次'
WHERE `id` = 4;

UPDATE `t_package` SET 
    `description` = '一年无限使用，无聊天次数限制'
WHERE `id` = 5;

-- 8. 验证数据更新结果
SELECT 
    id,
    package_name,
    description,
    total_chat_limit,
    daily_chat_limit_new,
    limit_strategy,
    is_enabled
FROM `t_package` 
WHERE deleted = 0
ORDER BY sort_order;

-- 9. 创建视图方便查询套餐限制信息
CREATE OR REPLACE VIEW `v_package_limits` AS
SELECT 
    p.id,
    p.package_name,
    p.description,
    p.price,
    p.validity_days,
    p.total_chat_limit,
    p.daily_chat_limit_new as daily_chat_limit,
    p.limit_strategy,
    CASE 
        WHEN p.limit_strategy = 'daily' THEN CONCAT('每日', p.daily_chat_limit_new, '次')
        WHEN p.limit_strategy = 'total' THEN CONCAT('总共', p.total_chat_limit, '次')
        WHEN p.limit_strategy = 'both' THEN CONCAT('每日', p.daily_chat_limit_new, '次，总共', p.total_chat_limit, '次')
        WHEN p.limit_strategy = 'unlimited' THEN '无限制'
        ELSE '未知限制'
    END as limit_description,
    p.is_enabled,
    p.sort_order
FROM `t_package` p
WHERE p.deleted = 0
ORDER BY p.sort_order;
