/*
 MySQL 5.7 到 8.0 数据迁移脚本
 
 创建时间: 2025-06-29 17:12:15
 用途: 将MySQL 5.7数据库完整迁移到MySQL 8.0
 数据库: chat
 
 迁移步骤:
 1. 备份MySQL 5.7数据
 2. 创建MySQL 8.0数据库结构
 3. 迁移数据
 4. 验证数据完整性
 5. 更新应用配置
*/

-- ========================================
-- 第一步: 数据备份验证
-- ========================================

-- 检查源数据库连接和版本
SELECT VERSION() as mysql_version, 
       DATABASE() as current_database,
       NOW() as backup_time;

-- 统计各表数据量
SELECT 
    table_name,
    table_rows,
    ROUND(((data_length + index_length) / 1024 / 1024), 2) AS 'Size_MB'
FROM information_schema.tables 
WHERE table_schema = 'chat'
ORDER BY table_rows DESC;

-- ========================================
-- 第二步: 创建MySQL 8.0数据库
-- ========================================

-- 创建新数据库（在MySQL 8.0服务器上执行）
CREATE DATABASE IF NOT EXISTS chat_new 
CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;

USE chat_new;

-- ========================================
-- 第三步: 创建表结构（MySQL 8.0优化版）
-- ========================================

-- AI模型表
CREATE TABLE `t_ai_model` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `model_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型代码',
  `model_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型名称',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '模型描述',
  `provider` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '提供商',
  `version` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '版本号',
  `token_limit` int DEFAULT '4096' COMMENT 'Token上限',
  `price_per_1k_tokens` decimal(10,6) DEFAULT '0.000000' COMMENT '每千token价格',
  `is_available` tinyint DEFAULT '1' COMMENT '是否可用',
  `base_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'API基础URL',
  `api_key` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'API密钥',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_code` (`model_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI模型表';

-- 用户表
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
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`),
  KEY `idx_user_status_time` (`status`,`last_login_time`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 聊天会话表
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
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_id` (`session_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `fk_session_model` (`model_id`),
  KEY `idx_chat_session_user_time` (`user_id`,`last_active_time`,`deleted`),
  KEY `idx_chat_session_user_create` (`user_id`,`create_time`,`deleted`),
  CONSTRAINT `fk_session_model` FOREIGN KEY (`model_id`) REFERENCES `t_ai_model` (`id`),
  CONSTRAINT `fk_session_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天会话表';

-- 聊天消息表
CREATE TABLE `t_chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `message_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息ID（UUID）',
  `session_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话ID',
  `role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息角色',
  `content` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息内容',
  `message_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'text' COMMENT '消息类型',
  `has_attachments` tinyint(1) DEFAULT '0' COMMENT '是否包含附件',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `model_id` bigint NOT NULL COMMENT 'AI模型ID',
  `token_count` int DEFAULT '0' COMMENT '消息token数',
  `status` tinyint DEFAULT '0' COMMENT '消息状态',
  `error_message` text COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表';

-- 套餐表
CREATE TABLE `t_package` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '套餐名称',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '套餐描述',
  `price_usd` decimal(10,2) NOT NULL COMMENT '美元价格',
  `price_cny` decimal(10,2) NOT NULL COMMENT '人民币价格',
  `validity_days` int NOT NULL COMMENT '有效期（天）',
  `chat_limit` int NOT NULL COMMENT '聊天次数限制',
  `model_permissions` json DEFAULT NULL COMMENT '模型权限配置',
  `is_active` tinyint DEFAULT '1' COMMENT '是否激活',
  `sort_order` int DEFAULT '0' COMMENT '排序顺序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志',
  PRIMARY KEY (`id`),
  KEY `idx_package_active_sort` (`is_active`,`sort_order`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐表';

-- 用户订单表
CREATE TABLE `t_user_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订单ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `package_id` bigint NOT NULL COMMENT '套餐ID',
  `amount` decimal(10,2) NOT NULL COMMENT '订单金额',
  `currency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '货币类型',
  `payment_method` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '支付方式',
  `payment_status` tinyint DEFAULT '0' COMMENT '支付状态',
  `payment_time` datetime DEFAULT NULL COMMENT '支付时间',
  `trade_no` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '第三方交易号',
  `qr_code_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '支付二维码URL',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_id` (`order_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `fk_order_package` (`package_id`),
  KEY `idx_user_order_status_time` (`user_id`,`payment_status`,`create_time`,`deleted`),
  KEY `idx_user_order_payment_time` (`payment_status`,`payment_time`,`deleted`),
  CONSTRAINT `fk_order_package` FOREIGN KEY (`package_id`) REFERENCES `t_package` (`id`),
  CONSTRAINT `fk_order_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户订单表';

-- 用户套餐表
CREATE TABLE `t_user_package` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `package_id` bigint NOT NULL COMMENT '套餐ID',
  `chat_used` int DEFAULT '0' COMMENT '已使用聊天次数',
  `chat_limit` int NOT NULL COMMENT '聊天次数限制',
  `expire_time` datetime NOT NULL COMMENT '过期时间',
  `is_active` tinyint DEFAULT '1' COMMENT '是否激活',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `fk_user_package_package` (`package_id`),
  KEY `idx_user_package_active_expire` (`user_id`,`is_active`,`expire_time`,`deleted`),
  KEY `idx_user_package_expire_time` (`expire_time`,`is_active`,`deleted`),
  CONSTRAINT `fk_user_package_package` FOREIGN KEY (`package_id`) REFERENCES `t_package` (`id`),
  CONSTRAINT `fk_user_package_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户套餐表';

-- 文件上传表
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
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_id` (`file_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_message_id` (`message_id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_upload_user_time` (`user_id`,`create_time`,`deleted`),
  CONSTRAINT `fk_upload_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件上传表';

-- ========================================
-- 第四步: 数据迁移命令（需要在命令行执行）
-- ========================================

/*
在命令行执行以下命令进行数据迁移：

1. 导出MySQL 5.7数据：
mysqldump -h [5.7_host] -u [username] -p --single-transaction --routines --triggers \
  --set-gtid-purged=OFF --default-character-set=utf8mb4 \
  chat > chat_57_data_export.sql

2. 导入到MySQL 8.0：
mysql -h [8.0_host] -u [username] -p chat_new < chat_57_data_export.sql

3. 或者使用以下分表导出导入方式：
*/

-- ========================================
-- 第五步: 数据验证脚本
-- ========================================

-- 验证数据迁移完整性
SELECT 'AI模型表' as table_name, COUNT(*) as record_count FROM t_ai_model WHERE deleted = 0
UNION ALL
SELECT '用户表', COUNT(*) FROM t_user WHERE deleted = 0
UNION ALL
SELECT '聊天会话表', COUNT(*) FROM t_chat_session WHERE deleted = 0
UNION ALL
SELECT '聊天消息表', COUNT(*) FROM t_chat_message WHERE deleted = 0
UNION ALL
SELECT '套餐表', COUNT(*) FROM t_package WHERE deleted = 0
UNION ALL
SELECT '用户订单表', COUNT(*) FROM t_user_order WHERE deleted = 0
UNION ALL
SELECT '用户套餐表', COUNT(*) FROM t_user_package WHERE deleted = 0
UNION ALL
SELECT '文件上传表', COUNT(*) FROM t_upload WHERE deleted = 0;

-- 验证外键关系完整性
SELECT 
    'chat_session外键检查' as check_name,
    COUNT(*) as invalid_count
FROM t_chat_session s 
LEFT JOIN t_user u ON s.user_id = u.id 
LEFT JOIN t_ai_model m ON s.model_id = m.id
WHERE u.id IS NULL OR m.id IS NULL;

-- 验证数据类型和字符集
SELECT 
    table_name,
    column_name,
    data_type,
    character_set_name,
    collation_name
FROM information_schema.columns 
WHERE table_schema = 'chat_new' 
  AND table_name LIKE 't_%'
ORDER BY table_name, ordinal_position;

-- ========================================
-- 第六步: 性能优化（MySQL 8.0特性）
-- ========================================

-- 创建MySQL 8.0特有的优化索引
CREATE INDEX idx_chat_msg_time_desc ON t_chat_message (create_time DESC, deleted);
CREATE INDEX idx_session_active_desc ON t_chat_session (last_active_time DESC, deleted);

-- 分析表统计信息
ANALYZE TABLE t_ai_model, t_user, t_chat_session, t_chat_message, 
              t_package, t_user_order, t_user_package, t_upload;

-- ========================================
-- 第七步: 清理和优化
-- ========================================

-- 重置AUTO_INCREMENT值
ALTER TABLE t_ai_model AUTO_INCREMENT = 1;
ALTER TABLE t_user AUTO_INCREMENT = 1;
ALTER TABLE t_chat_session AUTO_INCREMENT = 1;
ALTER TABLE t_chat_message AUTO_INCREMENT = 1;
ALTER TABLE t_package AUTO_INCREMENT = 1;
ALTER TABLE t_user_order AUTO_INCREMENT = 1;
ALTER TABLE t_user_package AUTO_INCREMENT = 1;
ALTER TABLE t_upload AUTO_INCREMENT = 1;

-- 优化表
OPTIMIZE TABLE t_ai_model, t_user, t_chat_session, t_chat_message, 
               t_package, t_user_order, t_user_package, t_upload;

-- 迁移完成验证
SELECT 
    'MySQL版本' as info_type, 
    VERSION() as info_value
UNION ALL
SELECT 
    '数据库字符集', 
    DEFAULT_CHARACTER_SET_NAME 
FROM information_schema.SCHEMATA 
WHERE SCHEMA_NAME = 'chat_new'
UNION ALL
SELECT 
    '迁移完成时间', 
    NOW();

-- 迁移完成标记
INSERT INTO t_ai_model (model_code, model_name, description, provider, version, is_available, deleted) 
VALUES ('migration_marker', 'Migration Completed', 'MySQL 8.0 migration completed successfully', 'System', '1.0', 0, 1)
ON DUPLICATE KEY UPDATE description = 'MySQL 8.0 migration completed successfully';
