-- 套餐表添加优惠价格字段脚本
-- 执行时间: 2025-07-09 22:02:00
-- 目的: 为套餐表添加优惠价格字段，用于存储套餐的优惠价格

-- 1. 添加优惠价格字段
ALTER TABLE `t_package` 
ADD COLUMN `discount_price` decimal(10,2) DEFAULT NULL COMMENT '优惠价格（人民币）' AFTER `price`;

-- 2. 为优惠价格字段添加索引（可选，用于查询优化）
ALTER TABLE `t_package` ADD INDEX `idx_discount_price` (`discount_price`);

-- 3. 验证字段添加结果
SELECT 
    COLUMN_NAME,
    DATA_TYPE,
    IS_NULLABLE,
    COLUMN_DEFAULT,
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 't_package' 
    AND COLUMN_NAME = 'discount_price';

-- 4. 查看表结构确认
DESCRIBE t_package;

-- 5. 示例：更新部分套餐的优惠价格（可选）
-- UPDATE `t_package` SET `discount_price` = 8.88 WHERE `id` = 1;
-- UPDATE `t_package` SET `discount_price` = 28.88 WHERE `id` = 2;
-- UPDATE `t_package` SET `discount_price` = 58.88 WHERE `id` = 3;

-- 注意事项：
-- 1. discount_price字段允许为NULL，表示该套餐没有优惠价格
-- 2. 当discount_price不为NULL时，表示该套餐有优惠价格
-- 3. 前端可以根据discount_price是否为NULL来决定是否显示优惠价格
-- 4. 优惠价格应该小于原价格，建议在业务逻辑中添加验证
