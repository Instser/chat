-- ===================================================================
-- 添加套餐表的每日限制字段
-- 创建时间: 2025-06-18 03:03:00 (北京时间)
-- 说明: 为套餐表添加每日使用次数限制相关字段
-- ===================================================================

USE chat;

-- 添加每日聊天次数限制字段
ALTER TABLE `t_package` 
ADD COLUMN `daily_chat_limit` int(11) NOT NULL DEFAULT 10 COMMENT '每日聊天次数限制' AFTER `chat_count`;

-- 添加限制类型字段（daily-每日限制，total-总次数限制）
ALTER TABLE `t_package` 
ADD COLUMN `limit_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'daily' COMMENT '限制类型（daily-每日限制，total-总次数限制）' AFTER `daily_chat_limit`;

-- 更新现有套餐数据
UPDATE `t_package` SET 
    `daily_chat_limit` = CASE 
        WHEN `package_type` = 'basic' THEN 10
        WHEN `package_type` = 'premium' THEN 50
        WHEN `package_type` = 'enterprise' THEN 200
        ELSE 10
    END,
    `limit_type` = 'daily'
WHERE `deleted` = 0;

-- 查看修改后的表结构
DESCRIBE `t_package`;

-- ===================================================================
-- 创建每日使用记录表
-- ===================================================================

-- 创建每日使用记录表
CREATE TABLE `t_user_daily_usage` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `package_id` bigint(20) NOT NULL COMMENT '套餐ID',
  `usage_date` date NOT NULL COMMENT '使用日期',
  `chat_count_used` int(11) NOT NULL DEFAULT 0 COMMENT '已使用聊天次数',
  `daily_limit` int(11) NOT NULL DEFAULT 10 COMMENT '当日限制次数',
  `last_chat_time` datetime NULL DEFAULT NULL COMMENT '最后聊天时间',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_package_date`(`user_id`, `package_id`, `usage_date`) USING BTREE,
  INDEX `idx_user_date`(`user_id`, `usage_date`) USING BTREE,
  INDEX `idx_usage_date`(`usage_date`) USING BTREE,
  CONSTRAINT `fk_daily_usage_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_daily_usage_package` FOREIGN KEY (`package_id`) REFERENCES `t_package` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户每日使用记录表' ROW_FORMAT = Dynamic;
