/*
 Navicat Premium Dump SQL

 Source Server         : dwnc的chat
 Source Server Type    : MySQL
 Source Server Version : 80040 (8.0.40)
 Source Host           : 203.0.113.10:3306
 Source Schema         : chat

 Target Server Type    : MySQL
 Target Server Version : 80040 (8.0.40)
 File Encoding         : 65001

 Date: 29/06/2025 15:43:45
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for t_ai_model
-- ----------------------------
DROP TABLE IF EXISTS `t_ai_model`;
CREATE TABLE `t_ai_model` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `model_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型代码',
  `model_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型名称',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '模型描述',
  `provider` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '提供商（OpenAI/Anthropic/自研等）',
  `version` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '版本号',
  `token_limit` int DEFAULT '4096' COMMENT 'Token上限',
  `price_per_1k_tokens` decimal(10,6) DEFAULT '0.000000' COMMENT '每千token价格（美元）',
  `is_available` tinyint DEFAULT '1' COMMENT '是否可用（0-不可用，1-可用）',
  `base_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'API基础URL',
  `api_key` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'API密钥',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_code` (`model_code`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI模型表';

-- ----------------------------
-- Records of t_ai_model
-- ----------------------------
BEGIN;
INSERT INTO `t_ai_model` (`id`, `model_code`, `model_name`, `description`, `provider`, `version`, `token_limit`, `price_per_1k_tokens`, `is_available`, `base_url`, `api_key`, `create_time`, `update_time`, `deleted`) VALUES (1, 'qwen3-235b-a22b', 'STANDARD', 'Standard model, for daily use.', 'OpenAI', '3.0', 4096, 0.002000, 1, 'http://203.0.113.15:3000/v1/chat/completions', 'a2366021253', '2025-06-05 21:15:38', '2025-06-21 19:22:53', 0);
INSERT INTO `t_ai_model` (`id`, `model_code`, `model_name`, `description`, `provider`, `version`, `token_limit`, `price_per_1k_tokens`, `is_available`, `base_url`, `api_key`, `create_time`, `update_time`, `deleted`) VALUES (2, 'gemini-2.5-flash', 'PERFORMANCE', 'High-performance model, excels at complex problems.', 'Gemini', '2.5', 8192, 0.030000, 1, 'http://181.215.6.137:8321/v1/chat/completions', 'your-google-api-key', '2025-06-05 21:15:38', '2025-06-25 22:09:21', 0);
INSERT INTO `t_ai_model` (`id`, `model_code`, `model_name`, `description`, `provider`, `version`, `token_limit`, `price_per_1k_tokens`, `is_available`, `base_url`, `api_key`, `create_time`, `update_time`, `deleted`) VALUES (3, 'reverse:claude-sonnet-4-2025051', 'BETA MODLE', 'Test model. stability is subontimal.', 'Anthropic', '4.0', 100000, 0.015000, 1, 'https://yourapi.cn/v1/chat/completions', 'your-api-key', '2025-06-05 21:15:38', '2025-06-29 14:32:30', 0);
COMMIT;

-- ----------------------------
-- Table structure for t_chat_message
-- ----------------------------
DROP TABLE IF EXISTS `t_chat_message`;
CREATE TABLE `t_chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `message_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息ID（UUID）',
  `session_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话ID',
  `role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息角色（user/assistant/system）',
  `content` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息内容',
  `message_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'text' COMMENT '消息类型（text/image/file等）',
  `has_attachments` tinyint(1) DEFAULT '0' COMMENT '是否包含附件（0-无附件，1-有附件）',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `model_id` bigint NOT NULL COMMENT 'AI模型ID',
  `token_count` int DEFAULT '0' COMMENT '消息token数',
  `status` tinyint DEFAULT '0' COMMENT '消息状态（0-发送中，1-成功，2-失败）',
  `error_message` text COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_id` (`message_id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `fk_message_model` (`model_id`),
  KEY `idx_chat_msg_session_time` (`session_id`,`create_time`,`deleted`),
  KEY `idx_chat_msg_user_time` (`user_id`,`create_time`,`deleted`),
  KEY `idx_chat_msg_user_date` (`user_id`,`deleted`,`create_time`),
  KEY `idx_chat_msg_session_role` (`session_id`,`role`,`deleted`),
  KEY `idx_chat_msg_model_time` (`model_id`,`create_time`,`deleted`),
  KEY `idx_chat_msg_attachments` (`has_attachments`,`session_id`,`create_time`),
  KEY `idx_chat_msg_user_attachments` (`user_id`,`has_attachments`,`create_time`),
  CONSTRAINT `fk_message_model` FOREIGN KEY (`model_id`) REFERENCES `t_ai_model` (`id`),
  CONSTRAINT `fk_message_session` FOREIGN KEY (`session_id`) REFERENCES `t_chat_session` (`session_id`),
  CONSTRAINT `fk_message_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1939210387968438275 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表';

-- ----------------------------
-- Table structure for t_chat_session
-- ----------------------------
DROP TABLE IF EXISTS `t_chat_session`;
CREATE TABLE `t_chat_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话ID（UUID）',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT '新对话' COMMENT '会话标题',
  `model_id` bigint NOT NULL COMMENT 'AI模型ID',
  `message_count` int DEFAULT '0' COMMENT '消息数量',
  `last_active_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_id` (`session_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `fk_session_model` (`model_id`),
  KEY `idx_chat_session_user_time` (`user_id`,`last_active_time`,`deleted`),
  KEY `idx_chat_session_user_create` (`user_id`,`create_time`,`deleted`),
  CONSTRAINT `fk_session_model` FOREIGN KEY (`model_id`) REFERENCES `t_ai_model` (`id`),
  CONSTRAINT `fk_session_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1939210387968438275 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天会话表';

-- ----------------------------
-- Table structure for t_package
-- ----------------------------
DROP TABLE IF EXISTS `t_package`;
CREATE TABLE `t_package` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '套餐名称',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '套餐描述',
  `price_usd` decimal(10,2) NOT NULL COMMENT '美元价格',
  `price_cny` decimal(10,2) NOT NULL COMMENT '人民币价格',
  `validity_days` int NOT NULL COMMENT '有效期（天）',
  `chat_limit` int NOT NULL COMMENT '聊天次数限制',
  `model_permissions` json DEFAULT NULL COMMENT '模型权限配置（JSON格式）',
  `is_active` tinyint DEFAULT '1' COMMENT '是否激活（0-未激活，1-激活）',
  `sort_order` int DEFAULT '0' COMMENT '排序顺序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`),
  KEY `idx_package_active_sort` (`is_active`,`sort_order`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐表';

-- ----------------------------
-- Table structure for t_upload
-- ----------------------------
DROP TABLE IF EXISTS `t_upload`;
CREATE TABLE `t_upload` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `file_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件ID（UUID）',
  `filename` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件名',
  `original_filename` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原始文件名',
  `file_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件类型',
  `file_size` bigint NOT NULL COMMENT '文件大小（字节）',
  `content` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件内容',
  `user_id` bigint NOT NULL COMMENT '上传用户ID',
  `message_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联消息ID',
  `session_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联会话ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_id` (`file_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_message_id` (`message_id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_upload_user_time` (`user_id`,`create_time`,`deleted`),
  CONSTRAINT `fk_upload_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1939210387968438275 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件上传表';

-- ----------------------------
-- Table structure for t_user
-- ----------------------------
DROP TABLE IF EXISTS `t_user`;
CREATE TABLE `t_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名',
  `email` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '邮箱',
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码（加密）',
  `nickname` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像URL',
  `status` tinyint DEFAULT '1' COMMENT '状态（0-禁用，1-正常）',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`),
  KEY `idx_user_status_time` (`status`,`last_login_time`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=1939210387968438275 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ----------------------------
-- Table structure for t_user_order
-- ----------------------------
DROP TABLE IF EXISTS `t_user_order`;
CREATE TABLE `t_user_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订单ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `package_id` bigint NOT NULL COMMENT '套餐ID',
  `amount` decimal(10,2) NOT NULL COMMENT '订单金额',
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '货币类型（USD/CNY）',
  `payment_method` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '支付方式',
  `payment_status` tinyint DEFAULT '0' COMMENT '支付状态（0-未支付，1-已支付，2-已取消，3-已退款）',
  `payment_time` datetime DEFAULT NULL COMMENT '支付时间',
  `trade_no` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '第三方交易号',
  `qr_code_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '支付二维码URL',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_id` (`order_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `fk_order_package` (`package_id`),
  KEY `idx_user_order_status_time` (`user_id`,`payment_status`,`create_time`,`deleted`),
  KEY `idx_user_order_payment_time` (`payment_status`,`payment_time`,`deleted`),
  CONSTRAINT `fk_order_package` FOREIGN KEY (`package_id`) REFERENCES `t_package` (`id`),
  CONSTRAINT `fk_order_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1939210387968438275 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户订单表';

-- ----------------------------
-- Table structure for t_user_package
-- ----------------------------
DROP TABLE IF EXISTS `t_user_package`;
CREATE TABLE `t_user_package` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `package_id` bigint NOT NULL COMMENT '套餐ID',
  `chat_used` int DEFAULT '0' COMMENT '已使用聊天次数',
  `chat_limit` int NOT NULL COMMENT '聊天次数限制',
  `expire_time` datetime NOT NULL COMMENT '过期时间',
  `is_active` tinyint DEFAULT '1' COMMENT '是否激活（0-未激活，1-激活）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `fk_user_package_package` (`package_id`),
  KEY `idx_user_package_active_expire` (`user_id`,`is_active`,`expire_time`,`deleted`),
  KEY `idx_user_package_expire_time` (`expire_time`,`is_active`,`deleted`),
  CONSTRAINT `fk_user_package_package` FOREIGN KEY (`package_id`) REFERENCES `t_package` (`id`),
  CONSTRAINT `fk_user_package_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1939210387968438275 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户套餐表';

SET FOREIGN_KEY_CHECKS = 1;
