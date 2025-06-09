-- 支付相关表结构
-- 创建时间: 2024-01-01
-- 说明: 海外支付宝SDK集成相关的数据库表

-- 套餐表
CREATE TABLE IF NOT EXISTS `t_package` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `package_name` varchar(100) NOT NULL COMMENT '套餐名称',
  `description` text COMMENT '套餐描述',
  `package_type` varchar(50) NOT NULL COMMENT '套餐类型（basic/premium/enterprise等）',
  `price_usd` decimal(10,2) NOT NULL COMMENT '价格（美元）',
  `price_cny` decimal(10,2) DEFAULT NULL COMMENT '价格（人民币）',
  `validity_days` int NOT NULL DEFAULT '30' COMMENT '有效期（天数）',
  `chat_count` int NOT NULL DEFAULT '1000' COMMENT '包含的AI对话次数（-1表示无限制）',
  `model_permissions` json COMMENT '包含的AI模型权限（JSON格式存储模型ID列表）',
  `is_enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用（0-禁用，1-启用）',
  `sort_order` int DEFAULT '0' COMMENT '排序权重',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`),
  KEY `idx_package_type` (`package_type`),
  KEY `idx_is_enabled` (`is_enabled`),
  KEY `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐表';

-- 更新用户订单表结构（添加新字段）
ALTER TABLE `t_user_order` 
ADD COLUMN `currency` varchar(10) DEFAULT 'USD' COMMENT '货币类型（USD/CNY）' AFTER `amount`,
ADD COLUMN `payment_request_id` varchar(100) DEFAULT NULL COMMENT '支付请求ID（发送给支付宝的请求ID）' AFTER `transaction_id`,
ADD COLUMN `payment_url` text DEFAULT NULL COMMENT '支付跳转URL' AFTER `payment_request_id`,
ADD COLUMN `expire_time` datetime DEFAULT NULL COMMENT '订单过期时间' AFTER `payment_url`,
ADD COLUMN `remark` varchar(500) DEFAULT NULL COMMENT '备注信息' AFTER `expire_time`;

-- 添加索引
ALTER TABLE `t_user_order` 
ADD INDEX `idx_payment_request_id` (`payment_request_id`),
ADD INDEX `idx_currency` (`currency`),
ADD INDEX `idx_expire_time` (`expire_time`);

-- 插入默认套餐数据
INSERT INTO `t_package` (`package_name`, `description`, `package_type`, `price_usd`, `price_cny`, `validity_days`, `chat_count`, `model_permissions`, `is_enabled`, `sort_order`) VALUES
('基础套餐', '适合个人用户的基础AI对话套餐，包含1000次对话', 'basic', 9.99, 69.99, 30, 1000, '["gpt-3.5-turbo", "qwen-turbo"]', 1, 1),
('高级套餐', '适合专业用户的高级AI对话套餐，包含5000次对话', 'premium', 19.99, 139.99, 30, 5000, '["gpt-4", "claude-3", "qwen-max"]', 1, 2),
('企业套餐', '适合企业用户的无限制AI对话套餐', 'enterprise', 99.99, 699.99, 30, -1, '["gpt-4", "claude-3", "qwen-max", "all"]', 1, 3);

-- 创建支付配置表（可选，用于存储支付宝配置）
CREATE TABLE IF NOT EXISTS `t_payment_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_key` varchar(100) NOT NULL COMMENT '配置键',
  `config_value` text COMMENT '配置值',
  `config_desc` varchar(200) COMMENT '配置描述',
  `is_enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付配置表';

-- 插入默认支付配置（示例，实际使用时需要填入真实配置）
INSERT INTO `t_payment_config` (`config_key`, `config_value`, `config_desc`, `is_enabled`) VALUES
('alipay.client_id', '', '支付宝应用ID（客户端ID）', 1),
('alipay.private_key', '', '商户私钥', 1),
('alipay.public_key', '', '支付宝公钥', 1),
('alipay.sandbox', 'true', '是否沙箱环境', 1),
('alipay.return_url', 'http://localhost:3000/payment/success', '支付成功后跳转URL', 1),
('alipay.notify_url', 'http://localhost:8080/api/payment/alipay/notify', '支付回调通知URL', 1);

-- 创建用户账户表（用于记录用户的套餐使用情况）
CREATE TABLE IF NOT EXISTS `t_user_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `package_id` bigint DEFAULT NULL COMMENT '当前套餐ID',
  `chat_count_remaining` int DEFAULT '0' COMMENT '剩余对话次数',
  `package_expire_time` datetime DEFAULT NULL COMMENT '套餐过期时间',
  `total_chat_count` int DEFAULT '0' COMMENT '总对话次数',
  `total_amount_spent` decimal(10,2) DEFAULT '0.00' COMMENT '总消费金额',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  KEY `idx_package_id` (`package_id`),
  KEY `idx_package_expire_time` (`package_expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户账户表';

-- 为现有用户创建账户记录
INSERT INTO `t_user_account` (`user_id`, `chat_count_remaining`, `total_chat_count`)
SELECT `id`, 100, 0 FROM `t_user` WHERE `deleted` = 0
ON DUPLICATE KEY UPDATE `user_id` = `user_id`;

-- 添加外键约束
ALTER TABLE `t_user_account` ADD CONSTRAINT `fk_account_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`);
ALTER TABLE `t_user_account` ADD CONSTRAINT `fk_account_package` FOREIGN KEY (`package_id`) REFERENCES `t_package` (`id`);
