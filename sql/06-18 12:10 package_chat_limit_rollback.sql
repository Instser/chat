-- 套餐聊天次数限制回滚脚本
-- 执行时间: 2025-06-18 12:10:00
-- 目的: 回滚套餐表的聊天次数限制优化，恢复到原始状态

-- 1. 删除创建的视图
DROP VIEW IF EXISTS `v_package_limits`;

-- 2. 删除添加的索引
ALTER TABLE `t_package` DROP INDEX IF EXISTS `idx_limit_strategy`;

-- 3. 恢复原始套餐描述
UPDATE `t_package` SET 
    `description` = '新用户免费体验套餐'
WHERE `id` = 1;

UPDATE `t_package` SET 
    `description` = '满足日常聊天需求'
WHERE `id` = 2;

UPDATE `t_package` SET 
    `description` = '适合中度使用者'
WHERE `id` = 3;

UPDATE `t_package` SET 
    `description` = '适合重度使用者'
WHERE `id` = 4;

UPDATE `t_package` SET 
    `description` = '一年无限使用'
WHERE `id` = 5;

-- 4. 删除新添加的字段
ALTER TABLE `t_package` DROP COLUMN IF EXISTS `limit_strategy`;
ALTER TABLE `t_package` DROP COLUMN IF EXISTS `daily_chat_limit_new`;
ALTER TABLE `t_package` DROP COLUMN IF EXISTS `total_chat_limit`;

-- 5. 验证回滚结果
SELECT 
    id,
    package_name,
    description,
    chat_count,
    daily_chat_limit,
    limit_type,
    is_enabled
FROM `t_package` 
WHERE deleted = 0
ORDER BY sort_order;
