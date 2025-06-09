-- 创建数据库
CREATE DATABASE IF NOT EXISTS `chat` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `chat`;

-- 用户表
CREATE TABLE `t_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `email` varchar(100) NOT NULL COMMENT '用户的谷歌邮箱',
  `password` varchar(255) NOT NULL COMMENT '密码（加密存储）',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像URL',
  `role` varchar(20) DEFAULT 'user' COMMENT '角色（user/admin）',
  `status` tinyint DEFAULT '1' COMMENT '状态（0-禁用，1-启用）',
  `alipay_id` varchar(100) DEFAULT NULL COMMENT '支付宝ID，可为空，创建账号后续填充',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_email` (`email`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- AI模型表
CREATE TABLE `t_ai_model` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `model_code` varchar(50) NOT NULL COMMENT '模型代码',
  `model_name` varchar(100) NOT NULL COMMENT '模型名称',
  `description` text COMMENT '模型描述',
  `provider` varchar(50) NOT NULL COMMENT '提供商（OpenAI/Anthropic/自研等）',
  `version` varchar(50) DEFAULT NULL COMMENT '版本号',
  `token_limit` int DEFAULT '4096' COMMENT 'Token上限',
  `price_per_1k_tokens` decimal(10,6) DEFAULT '0.000000' COMMENT '每千token价格（美元）',
  `is_available` tinyint DEFAULT '1' COMMENT '是否可用（0-不可用，1-可用）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_code` (`model_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI模型表';

-- 聊天会话表
CREATE TABLE `t_chat_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` varchar(64) NOT NULL COMMENT '会话ID（UUID）',
  `title` varchar(255) DEFAULT '新对话' COMMENT '会话标题',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `model_id` bigint NOT NULL COMMENT 'AI模型ID',
  `status` tinyint DEFAULT '0' COMMENT '会话状态（0-进行中，1-已结束）',
  `last_message` text COMMENT '最后一条消息内容',
  `last_active_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间',
  `token_count` int DEFAULT '0' COMMENT '累计消耗token数',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_id` (`session_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_model_id` (`model_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天会话表';

-- 聊天消息表
CREATE TABLE `t_chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `message_id` varchar(64) NOT NULL COMMENT '消息ID（UUID）',
  `session_id` varchar(64) NOT NULL COMMENT '会话ID',
  `role` varchar(20) NOT NULL COMMENT '消息角色（user/assistant/system）',
  `content` longtext NOT NULL COMMENT '消息内容',
  `message_type` varchar(20) DEFAULT 'text' COMMENT '消息类型（text/image/file等）',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `model_id` bigint NOT NULL COMMENT 'AI模型ID',
  `token_count` int DEFAULT '0' COMMENT '消息token数',
  `status` tinyint DEFAULT '0' COMMENT '消息状态（0-发送中，1-成功，2-失败）',
  `error_message` text COMMENT '错误信息',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_id` (`message_id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表';

-- 套餐表
CREATE TABLE `t_package` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `package_name` varchar(100) NOT NULL COMMENT '套餐名称',
  `description` text COMMENT '套餐描述',
  `price` decimal(10,2) NOT NULL COMMENT '套餐价格',
  `token_amount` bigint NOT NULL COMMENT 'Token数量',
  `validity_days` int DEFAULT '365' COMMENT '有效期（天）',
  `is_available` tinyint DEFAULT '1' COMMENT '是否可用（0-不可用，1-可用）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐表';

-- 用户订单表
CREATE TABLE `t_user_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` varchar(64) NOT NULL COMMENT '订单编号',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `package_id` bigint NOT NULL COMMENT '套餐ID',
  `amount` decimal(10,2) NOT NULL COMMENT '订单金额',
  `payment_method` varchar(20) DEFAULT NULL COMMENT '支付方式（alipay/wechat/paypal等）',
  `payment_status` tinyint DEFAULT '0' COMMENT '支付状态（0-未支付，1-已支付，2-已取消，3-已退款）',
  `payment_time` datetime DEFAULT NULL COMMENT '支付时间',
  `transaction_id` varchar(100) DEFAULT NULL COMMENT '交易流水号',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户订单表';

-- 用户账户表
CREATE TABLE `t_user_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `token_balance` bigint DEFAULT '0' COMMENT 'Token余额',
  `token_used` bigint DEFAULT '0' COMMENT '已使用Token数',
  `membership_level` varchar(20) DEFAULT 'free' COMMENT '会员等级（free/basic/premium等）',
  `membership_expire_time` datetime DEFAULT NULL COMMENT '会员过期时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户账户表';

-- 文件上传表
CREATE TABLE `t_file_upload` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `file_id` varchar(64) NOT NULL COMMENT '文件ID（UUID）',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `session_id` varchar(64) DEFAULT NULL COMMENT '关联的会话ID',
  `file_name` varchar(255) NOT NULL COMMENT '文件名',
  `file_type` varchar(50) NOT NULL COMMENT '文件类型（MIME类型）',
  `file_size` bigint NOT NULL COMMENT '文件大小（字节）',
  `file_url` varchar(255) NOT NULL COMMENT '文件URL',
  `storage_path` varchar(255) NOT NULL COMMENT '存储路径',
  `status` tinyint DEFAULT '1' COMMENT '状态（0-处理中，1-可用，2-失败）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_id` (`file_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件上传表';

-- 添加外键约束
ALTER TABLE `t_chat_session` ADD CONSTRAINT `fk_session_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`);
ALTER TABLE `t_chat_session` ADD CONSTRAINT `fk_session_model` FOREIGN KEY (`model_id`) REFERENCES `t_ai_model` (`id`);

ALTER TABLE `t_chat_message` ADD CONSTRAINT `fk_message_session` FOREIGN KEY (`session_id`) REFERENCES `t_chat_session` (`session_id`);
ALTER TABLE `t_chat_message` ADD CONSTRAINT `fk_message_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`);
ALTER TABLE `t_chat_message` ADD CONSTRAINT `fk_message_model` FOREIGN KEY (`model_id`) REFERENCES `t_ai_model` (`id`);

ALTER TABLE `t_user_order` ADD CONSTRAINT `fk_order_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`);
ALTER TABLE `t_user_order` ADD CONSTRAINT `fk_order_package` FOREIGN KEY (`package_id`) REFERENCES `t_package` (`id`);

ALTER TABLE `t_user_account` ADD CONSTRAINT `fk_account_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`);

ALTER TABLE `t_file_upload` ADD CONSTRAINT `fk_file_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`);
ALTER TABLE `t_file_upload` ADD CONSTRAINT `fk_file_session` FOREIGN KEY (`session_id`) REFERENCES `t_chat_session` (`session_id`);

-- 插入初始AI模型数据
INSERT INTO `t_ai_model` (`model_code`, `model_name`, `description`, `provider`, `version`, `token_limit`, `price_per_1k_tokens`, `is_available`) VALUES
('gpt-3.5-turbo', 'GPT-3.5 Turbo', 'OpenAI的GPT-3.5 Turbo模型，适合大多数聊天场景', 'OpenAI', '3.5', 4096, 0.002000, 1),
('gpt-4', 'GPT-4', 'OpenAI的GPT-4模型，更强大的推理能力', 'OpenAI', '4.0', 8192, 0.030000, 1),
('claude-3-opus', 'Claude 3 Opus', 'Anthropic的Claude 3 Opus模型，强大的理解和创作能力', 'Anthropic', '3.0', 100000, 0.015000, 1),
('qwen-turbo', '通义千问-Turbo', '阿里云的通义千问Turbo模型，中英文能力均衡', '阿里云', '1.0', 6000, 0.001200, 1),
('qwen-plus', '通义千问-Plus', '阿里云的通义千问Plus模型，更强大的中文理解能力', '阿里云', '1.0', 8000, 0.002400, 1);

-- 插入初始套餐数据
INSERT INTO `t_package` (`package_name`, `description`, `price`, `token_amount`, `validity_days`, `is_available`) VALUES
('免费体验', '新用户免费体验套餐', 0.00, 10000, 30, 1),
('基础套餐', '满足日常聊天需求', 19.90, 100000, 30, 1),
('标准套餐', '适合中度使用者', 49.90, 300000, 30, 1),
('专业套餐', '适合重度使用者', 99.90, 800000, 30, 1),
('年度套餐', '一年无限使用', 499.00, 10000000, 365, 1);