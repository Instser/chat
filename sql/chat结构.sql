/*
 Navicat Premium Dump SQL

 Source Server         : 华为云chat
 Source Server Type    : MySQL
 Source Server Version : 50743 (5.7.43-log)
 Source Host           : 203.0.113.13:3306
 Source Schema         : chat

 Target Server Type    : MySQL
 Target Server Version : 50743 (5.7.43-log)
 File Encoding         : 65001

 Date: 25/06/2025 16:54:16
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for t_ai_model
-- ----------------------------
DROP TABLE IF EXISTS `t_ai_model`;
CREATE TABLE `t_ai_model` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `model_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型代码',
  `model_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型名称',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '模型描述',
  `provider` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '提供商（OpenAI/Anthropic/自研等）',
  `version` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '版本号',
  `token_limit` int(11) DEFAULT '4096' COMMENT 'Token上限',
  `price_per_1k_tokens` decimal(10,6) DEFAULT '0.000000' COMMENT '每千token价格（美元）',
  `is_available` tinyint(4) DEFAULT '1' COMMENT '是否可用（0-不可用，1-可用）',
  `base_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'API基础URL',
  `api_key` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'API密钥',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_model_code` (`model_code`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='AI模型表';

-- ----------------------------
-- Table structure for t_chat_message
-- ----------------------------
DROP TABLE IF EXISTS `t_chat_message`;
CREATE TABLE `t_chat_message` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `message_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息ID（UUID）',
  `session_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话ID',
  `role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息角色（user/assistant/system）',
  `content` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息内容',
  `message_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'text' COMMENT '消息类型（text/image/file等）',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `model_id` bigint(20) NOT NULL COMMENT 'AI模型ID',
  `token_count` int(11) DEFAULT '0' COMMENT '消息token数',
  `status` tinyint(4) DEFAULT '0' COMMENT '消息状态（0-发送中，1-成功，2-失败）',
  `error_message` text COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_message_id` (`message_id`) USING BTREE,
  KEY `idx_session_id` (`session_id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `fk_message_model` (`model_id`) USING BTREE,
  KEY `idx_chat_msg_session_time` (`session_id`,`create_time`,`deleted`),
  KEY `idx_chat_msg_user_time` (`user_id`,`create_time`,`deleted`),
  KEY `idx_chat_msg_user_date` (`user_id`,`deleted`,`create_time`),
  KEY `idx_chat_msg_session_role` (`session_id`,`role`,`deleted`),
  KEY `idx_chat_msg_model_time` (`model_id`,`create_time`,`deleted`),
  CONSTRAINT `fk_message_model` FOREIGN KEY (`model_id`) REFERENCES `t_ai_model` (`id`),
  CONSTRAINT `fk_message_session` FOREIGN KEY (`session_id`) REFERENCES `t_chat_session` (`session_id`),
  CONSTRAINT `fk_message_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1937796478589292547 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='聊天消息表';

-- ----------------------------
-- Table structure for t_chat_session
-- ----------------------------
DROP TABLE IF EXISTS `t_chat_session`;
CREATE TABLE `t_chat_session` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话ID（UUID）',
  `title` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '新对话' COMMENT '会话标题',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `model_id` bigint(20) NOT NULL COMMENT 'AI模型ID',
  `status` tinyint(4) DEFAULT '0' COMMENT '会话状态（0-进行中，1-已结束）',
  `last_message` text COLLATE utf8mb4_unicode_ci COMMENT '最后一条消息内容',
  `last_active_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间',
  `token_count` int(11) DEFAULT '0' COMMENT '累计消耗token数',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_session_id` (`session_id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_model_id` (`model_id`) USING BTREE,
  KEY `idx_chat_session_user_active` (`user_id`,`last_active_time`,`deleted`),
  CONSTRAINT `fk_session_model` FOREIGN KEY (`model_id`) REFERENCES `t_ai_model` (`id`),
  CONSTRAINT `fk_session_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1937796447052320771 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='聊天会话表';

-- ----------------------------
-- Table structure for t_file_upload
-- ----------------------------
DROP TABLE IF EXISTS `t_file_upload`;
CREATE TABLE `t_file_upload` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `file_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件ID（UUID）',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `session_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联的会话ID',
  `file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件名',
  `file_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件类型（MIME类型）',
  `file_size` bigint(20) NOT NULL COMMENT '文件大小（字节）',
  `file_url` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件URL',
  `storage_path` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '存储路径',
  `status` tinyint(4) DEFAULT '1' COMMENT '状态（0-处理中，1-可用，2-失败）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_file_id` (`file_id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_session_id` (`session_id`) USING BTREE,
  CONSTRAINT `fk_file_session` FOREIGN KEY (`session_id`) REFERENCES `t_chat_session` (`session_id`),
  CONSTRAINT `fk_file_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='文件上传表';

-- ----------------------------
-- Table structure for t_package
-- ----------------------------
DROP TABLE IF EXISTS `t_package`;
CREATE TABLE `t_package` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `package_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '套餐名称',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '套餐描述',
  `package_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'basic' COMMENT '套餐类型',
  `price_usd` decimal(10,2) DEFAULT NULL COMMENT '价格（美元）',
  `price_cny` decimal(10,2) DEFAULT NULL COMMENT '价格（人民币）',
  `price` decimal(10,2) NOT NULL COMMENT '套餐价格',
  `validity_days` int(11) DEFAULT '365' COMMENT '有效期（天）',
  `chat_count` int(11) NOT NULL DEFAULT '1000' COMMENT '对话次数',
  `daily_chat_limit` int(11) NOT NULL DEFAULT '10' COMMENT '每日聊天次数限制',
  `limit_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'daily' COMMENT '限制类型（daily-每日限制，total-总次数限制）',
  `model_permissions` text COLLATE utf8mb4_unicode_ci COMMENT '模型权限',
  `is_enabled` tinyint(4) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `sort_order` int(11) DEFAULT '0' COMMENT '排序权重',
  `is_available` tinyint(4) DEFAULT '1' COMMENT '是否可用（0-不可用，1-可用）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='套餐表';

-- ----------------------------
-- Table structure for t_user
-- ----------------------------
DROP TABLE IF EXISTS `t_user`;
CREATE TABLE `t_user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名',
  `email` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户的谷歌邮箱',
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码（加密存储）',
  `avatar` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像URL',
  `role` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'user' COMMENT '角色（user/admin）',
  `status` tinyint(4) DEFAULT '1' COMMENT '状态（0-禁用，1-启用）',
  `alipay_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '支付宝ID，可为空，创建账号后续填充',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_email` (`email`) USING BTREE,
  UNIQUE KEY `uk_username` (`username`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1934386506688880643 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='用户表';

-- ----------------------------
-- Table structure for t_user_account
-- ----------------------------
DROP TABLE IF EXISTS `t_user_account`;
CREATE TABLE `t_user_account` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `package_id` bigint(20) DEFAULT NULL COMMENT '套餐ID',
  `chat_count_remaining` int(11) DEFAULT '0' COMMENT '剩余对话次数',
  `package_expire_time` datetime DEFAULT NULL COMMENT '套餐过期时间',
  `total_chat_count` int(11) DEFAULT '0' COMMENT '总对话次数',
  `total_amount_spent` decimal(10,2) DEFAULT '0.00' COMMENT '总消费金额',
  `token_balance` bigint(20) DEFAULT '0' COMMENT 'Token余额',
  `token_used` bigint(20) DEFAULT '0' COMMENT '已使用Token数',
  `membership_level` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'free' COMMENT '会员等级（free/basic/premium等）',
  `membership_expire_time` datetime DEFAULT NULL COMMENT '会员过期时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_id` (`user_id`) USING BTREE,
  CONSTRAINT `fk_account_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1934650944186384387 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='用户账户表';

-- ----------------------------
-- Table structure for t_user_daily_usage
-- ----------------------------
DROP TABLE IF EXISTS `t_user_daily_usage`;
CREATE TABLE `t_user_daily_usage` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `package_id` bigint(20) NOT NULL COMMENT '套餐ID',
  `usage_date` date NOT NULL COMMENT '使用日期',
  `chat_count_used` int(11) NOT NULL DEFAULT '0' COMMENT '已使用聊天次数',
  `daily_limit` int(11) NOT NULL DEFAULT '10' COMMENT '当日限制次数',
  `last_chat_time` datetime DEFAULT NULL COMMENT '最后聊天时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_package_date` (`user_id`,`package_id`,`usage_date`) USING BTREE,
  KEY `idx_user_date` (`user_id`,`usage_date`) USING BTREE,
  KEY `idx_usage_date` (`usage_date`) USING BTREE,
  KEY `fk_daily_usage_package` (`package_id`),
  KEY `idx_last_chat_time` (`last_chat_time`),
  CONSTRAINT `fk_daily_usage_package` FOREIGN KEY (`package_id`) REFERENCES `t_package` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_daily_usage_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=283 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='用户每日使用记录表';

-- ----------------------------
-- Table structure for t_user_order
-- ----------------------------
DROP TABLE IF EXISTS `t_user_order`;
CREATE TABLE `t_user_order` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订单编号',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `package_id` bigint(20) NOT NULL COMMENT '套餐ID',
  `amount` decimal(10,2) NOT NULL COMMENT '订单金额',
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT 'USD' COMMENT '货币类型',
  `payment_method` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '支付方式（alipay/wechat/paypal等）',
  `payment_status` tinyint(4) DEFAULT '0' COMMENT '支付状态（0-未支付，1-已支付，2-已取消，3-已退款）',
  `payment_time` datetime DEFAULT NULL COMMENT '支付时间',
  `transaction_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '交易流水号',
  `payment_request_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '支付请求ID',
  `payment_url` text COLLATE utf8mb4_unicode_ci COMMENT '支付URL',
  `expire_time` datetime DEFAULT NULL COMMENT '过期时间',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(4) DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_order_no` (`order_no`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `fk_order_package` (`package_id`) USING BTREE,
  KEY `idx_user_order_status_time` (`user_id`,`payment_status`,`create_time`),
  KEY `idx_user_order_time_status` (`create_time`,`payment_status`),
  CONSTRAINT `fk_order_package` FOREIGN KEY (`package_id`) REFERENCES `t_package` (`id`),
  CONSTRAINT `fk_order_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1936373657663758338 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='用户订单表';

SET FOREIGN_KEY_CHECKS = 1;
