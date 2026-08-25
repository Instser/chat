-- ============================================================
-- Flyway V1 基线脚本（完整建库结构）
-- ============================================================
-- 作用：在空数据库上创建全部业务表结构（17 张表）
--       对已有库：baseline-on-migrate 触发后，此脚本被标记为基线版本 1，不会执行
--       对空库：Flyway 从 V1 开始执行，自动建出全部表结构
--
-- 来源：整理自 sql/chat.sql（16 张表）+ 05-26 审计表 + 08-03 增量字段
--   - t_chat_message 增加 reasoning 列（2026-08-03 增量）
--   - t_operation_audit 含 2026-08-03 管理员审计字段
-- 本脚本仅含建表结构（CREATE TABLE + 索引 + 外键），不含业务数据
--
-- 注意：此脚本不含 DROP TABLE / INSERT 语句
--       新环境启动时 Flyway 执行本脚本即可建出全部表
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 1. 管理员操作日志表
-- ----------------------------
CREATE TABLE `t_admin_operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `admin_id` bigint NOT NULL COMMENT '管理员ID',
  `operation` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作类型',
  `target_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '目标类型',
  `target_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '目标ID',
  `details` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '操作详情',
  `ip_address` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'IP地址',
  `user_agent` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户代理',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_admin_id` (`admin_id`),
  KEY `idx_operation` (`operation`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员操作日志表';

-- ----------------------------
-- 2. 管理员用户表
-- ----------------------------
CREATE TABLE `t_admin_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码',
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'admin' COMMENT '角色',
  `permissions` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '权限列表(JSON)',
  `status` tinyint DEFAULT '1' COMMENT '状态(0-禁用,1-启用)',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员用户表';

-- ----------------------------
-- 3. AI模型表
-- ----------------------------
CREATE TABLE `t_ai_model` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `model_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型代码',
  `model_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型名称',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '模型描述',
  `provider` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '提供商（OpenAI/Anthropic/自研等）',
  `version` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '版本号',
  `token_limit` int DEFAULT '4096' COMMENT 'Token上限',
  `price_per_1k_tokens` decimal(10,6) DEFAULT '0.000000' COMMENT '每千token价格（美元）',
  `is_available` tinyint DEFAULT '1' COMMENT '是否可用（0-不可用，1-可用）',
  `is_visible` tinyint DEFAULT '1' COMMENT '是否可见（0-不可见，1-可见）',
  `base_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'API基础URL',
  `api_key` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'API密钥',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_model_code` (`model_code`) USING BTREE,
  KEY `idx_model_availability` (`is_available`,`is_visible`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='AI模型表';

-- ----------------------------
-- 4. OAuth2审计日志表
-- ----------------------------
CREATE TABLE `t_oauth2_audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL COMMENT '用户ID',
  `provider` varchar(20) NOT NULL COMMENT 'OAuth提供商',
  `action` varchar(50) NOT NULL COMMENT '操作类型',
  `status` varchar(20) NOT NULL COMMENT '操作状态',
  `client_ip` varchar(45) DEFAULT NULL COMMENT '客户端IP',
  `user_agent` varchar(500) DEFAULT NULL COMMENT '用户代理',
  `error_code` varchar(50) DEFAULT NULL COMMENT '错误代码',
  `error_message` varchar(500) DEFAULT NULL COMMENT '错误信息',
  `request_data` json DEFAULT NULL COMMENT '请求数据',
  `response_data` json DEFAULT NULL COMMENT '响应数据',
  `execution_time` int DEFAULT NULL COMMENT '执行时间(毫秒)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_user_id` (`user_id`) COMMENT '用户ID索引',
  KEY `idx_provider` (`provider`) COMMENT '提供商索引',
  KEY `idx_action` (`action`) COMMENT '操作类型索引',
  KEY `idx_status` (`status`) COMMENT '操作状态索引',
  KEY `idx_client_ip` (`client_ip`) COMMENT '客户端IP索引',
  KEY `idx_user_action_time` (`user_id`,`action`,`create_time`) COMMENT '用户操作时间复合索引',
  KEY `idx_provider_status_time` (`provider`,`status`,`create_time`) COMMENT '提供商状态时间复合索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='OAuth2审计日志表';

-- ----------------------------
-- 5. 套餐表
-- ----------------------------
CREATE TABLE `t_package` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `package_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '套餐名称',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '套餐描述',
  `package_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'basic' COMMENT '套餐类型',
  `price` decimal(10,2) NOT NULL COMMENT '套餐价格',
  `discount_price` decimal(10,2) DEFAULT NULL COMMENT '优惠价格（人民币）',
  `validity_days` int DEFAULT '365' COMMENT '有效期（天）',
  `limit_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'daily' COMMENT '限制类型（daily-每日限制，total-总次数限制）',
  `model_permissions` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '模型权限',
  `is_enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `sort_order` int DEFAULT '0' COMMENT '排序权重',
  `is_available` tinyint DEFAULT '1' COMMENT '是否可用（0-不可用，1-可用）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_package_limit_type` (`limit_type`,`deleted`),
  KEY `idx_package_list` (`deleted`,`is_enabled`,`is_available`,`sort_order`),
  KEY `idx_discount_price` (`discount_price`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='套餐表';

-- ----------------------------
-- 6. 系统设置表
-- ----------------------------
CREATE TABLE `t_system_setting` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `setting_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '设置键名',
  `setting_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '设置值',
  `setting_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'string' COMMENT '设置类型（string/number/boolean/array）',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '设置描述',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `setting_key` (`setting_key`),
  KEY `idx_setting_type` (`setting_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统设置表';

-- ----------------------------
-- 7. 支付审计日志表
-- ----------------------------
CREATE TABLE `t_payment_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订单号',
  `operation_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作类型(CREATE/PAY/CANCEL/REFUND/QUERY/SYNC/CALLBACK)',
  `old_status` int DEFAULT NULL COMMENT '原状态',
  `new_status` int DEFAULT NULL COMMENT '新状态',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `operator_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'SYSTEM' COMMENT '操作人类型(USER/SYSTEM/ADMIN/CALLBACK)',
  `operation_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作原因',
  `operation_details` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '操作详情(JSON格式)',
  `client_ip` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客户端IP',
  `user_agent` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户代理',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志(0-未删除，1-已删除)',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_audit_order_time` (`order_no`,`create_time`,`deleted`) USING BTREE,
  KEY `idx_audit_type_time` (`operation_type`,`create_time`,`deleted`) USING BTREE,
  KEY `idx_audit_operator_time` (`operator_id`,`create_time`,`deleted`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='支付审计日志表';

-- ----------------------------
-- 8. 用户表
-- ----------------------------
CREATE TABLE `t_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名',
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户的谷歌邮箱',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码（加密存储）',
  `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像URL',
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'user' COMMENT '角色（user/admin）',
  `status` tinyint DEFAULT '1' COMMENT '状态（0-禁用，1-启用）',
  `alipay_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '支付宝ID，可为空，创建账号后续填充',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_email` (`email`) USING BTREE,
  UNIQUE KEY `uk_username` (`username`) USING BTREE,
  KEY `idx_user_email_status` (`email`,`status`,`deleted`),
  KEY `idx_user_username_status` (`username`,`status`,`deleted`),
  KEY `idx_status_role` (`status`,`role`) COMMENT '状态和角色复合索引，用于用户管理',
  KEY `idx_status_deleted` (`status`,`deleted`) COMMENT '用户状态查询优化索引',
  KEY `idx_create_time` (`create_time`) COMMENT '创建时间索引，用于统计分析',
  KEY `idx_last_login_time` (`last_login_time`) COMMENT '最后登录时间索引，用于活跃度分析'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='用户表';

-- ----------------------------
-- 9. 用户账户表
-- ----------------------------
CREATE TABLE `t_user_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `package_id` bigint DEFAULT NULL COMMENT '套餐ID',
  `chat_count_remaining` int DEFAULT '0' COMMENT '剩余对话次数',
  `package_expire_time` datetime DEFAULT NULL COMMENT '套餐过期时间',
  `total_chat_count` int DEFAULT '0' COMMENT '总对话次数',
  `total_amount_spent` decimal(10,2) DEFAULT '0.00' COMMENT '总消费金额',
  `token_balance` bigint DEFAULT '0' COMMENT 'Token余额',
  `token_used` bigint DEFAULT '0' COMMENT '已使用Token数',
  `membership_level` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'free' COMMENT '会员等级（free/basic/premium等）',
  `membership_expire_time` datetime DEFAULT NULL COMMENT '会员过期时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_id` (`user_id`) USING BTREE,
  KEY `idx_account_package` (`package_id`,`deleted`),
  KEY `idx_account_expire` (`package_expire_time`,`deleted`),
  KEY `idx_membership_level` (`membership_level`) COMMENT '会员等级索引',
  KEY `idx_create_time` (`create_time`) COMMENT '账户创建时间索引',
  CONSTRAINT `fk_account_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='用户账户表';

-- ----------------------------
-- 10. 用户每日使用记录表
-- ----------------------------
CREATE TABLE `t_user_daily_usage` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `model_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'STANDARD' COMMENT '模型名称(STANDARD/PERFORMANCE/BETA_MODEL)',
  `usage_date` date NOT NULL COMMENT '使用日期',
  `chat_count_used` int DEFAULT '0' COMMENT '已使用聊天次数',
  `last_chat_time` datetime DEFAULT NULL COMMENT '最后聊天时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_model_date` (`user_id`,`model_name`,`usage_date`) USING BTREE COMMENT '用户模型日期唯一索引',
  KEY `idx_usage_date` (`usage_date`) USING BTREE COMMENT '使用日期索引',
  KEY `idx_user_model` (`user_id`,`model_name`) USING BTREE COMMENT '用户模型复合索引',
  KEY `idx_last_chat_time` (`last_chat_time`) USING BTREE COMMENT '最后聊天时间索引',
  KEY `idx_usage_date_user` (`usage_date` DESC,`user_id`,`chat_count_used`) USING BTREE COMMENT '日期用户使用量复合索引',
  CONSTRAINT `fk_daily_usage_user_new` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='用户每日使用记录表(优化版)';

-- ----------------------------
-- 11. OAuth2用户绑定表
-- ----------------------------
CREATE TABLE `t_user_oauth` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `provider` varchar(20) NOT NULL COMMENT 'OAuth提供商',
  `provider_id` varchar(100) NOT NULL COMMENT '第三方用户ID',
  `email` varchar(100) NOT NULL COMMENT '第三方邮箱',
  `name` varchar(100) DEFAULT NULL COMMENT '第三方用户名',
  `avatar` varchar(500) DEFAULT NULL COMMENT '第三方头像URL',
  `access_token` text COMMENT '访问令牌(AES加密)',
  `refresh_token` text COMMENT '刷新令牌(AES加密)',
  `expires_at` datetime DEFAULT NULL COMMENT '令牌过期时间',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `login_count` int DEFAULT '0' COMMENT '登录次数',
  `status` tinyint DEFAULT '1' COMMENT '状态:1=正常,0=禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider_id` (`provider`,`provider_id`),
  KEY `idx_user_provider` (`user_id`,`provider`),
  KEY `idx_email` (`email`),
  KEY `idx_email_provider` (`email`,`provider`) COMMENT 'OAuth邮箱提供商复合索引',
  KEY `idx_status` (`status`) COMMENT 'OAuth状态索引',
  KEY `idx_last_login_time` (`last_login_time`) COMMENT 'OAuth最后登录时间索引',
  KEY `idx_create_time` (`create_time`) COMMENT 'OAuth创建时间索引',
  CONSTRAINT `fk_oauth_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='OAuth2用户绑定表';

-- ----------------------------
-- 12. 用户订单表
-- ----------------------------
CREATE TABLE `t_user_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订单编号',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `package_id` bigint NOT NULL COMMENT '套餐ID',
  `amount` decimal(10,2) NOT NULL COMMENT '订单金额',
  `currency` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'USD' COMMENT '货币类型',
  `payment_method` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '支付方式（alipay/wechat/paypal等）',
  `payment_status` tinyint DEFAULT '0' COMMENT '支付状态（0-未支付，1-已支付，2-已取消，3-已退款）',
  `payment_time` datetime DEFAULT NULL COMMENT '支付时间',
  `transaction_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '交易流水号',
  `payment_request_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '支付请求ID',
  `payment_url` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '支付URL',
  `expire_time` datetime DEFAULT NULL COMMENT '过期时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_order_no` (`order_no`) USING BTREE,
  KEY `fk_order_package` (`package_id`) USING BTREE,
  KEY `idx_user_order_status_time` (`user_id`,`payment_status`,`create_time`),
  KEY `idx_user_order_time_desc` (`create_time` DESC,`payment_status`),
  KEY `idx_order_user_cover` (`user_id`,`payment_status`,`create_time`,`amount`),
  KEY `idx_order_expire_status` (`expire_time`,`payment_status`,`deleted`),
  KEY `idx_order_archive` (`create_time`,`payment_status`,`deleted`),
  KEY `idx_order_payment_time` (`payment_time`,`payment_status`,`deleted`),
  KEY `idx_order_method_time` (`payment_method`,`payment_time`,`deleted`),
  KEY `idx_order_package_time` (`package_id`,`payment_time`,`deleted`),
  KEY `idx_order_date_func` ((cast(`create_time` as date)),`payment_status`),
  CONSTRAINT `fk_order_package` FOREIGN KEY (`package_id`) REFERENCES `t_package` (`id`),
  CONSTRAINT `fk_order_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='用户订单表';

-- ----------------------------
-- 13. 通用运维操作审计表（含 2026-08-03 管理员审计字段）
-- ----------------------------
CREATE TABLE `t_operation_audit` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `operator`        VARCHAR(64)   DEFAULT NULL COMMENT '操作人（用户名/系统）',
  `operator_id`     BIGINT        DEFAULT NULL COMMENT '操作人ID',
  `operator_role`   VARCHAR(32)   DEFAULT NULL COMMENT '操作时角色快照',
  `operation_type`  VARCHAR(32)   NOT NULL COMMENT '操作类型（CACHE_REFRESH/LOG_LEVEL_CHANGE/CONFIG_CHANGE等）',
  `target`          VARCHAR(128)  DEFAULT NULL COMMENT '操作对象（缓存key/接口路径/配置项等）',
  `target_detail`   TEXT          DEFAULT NULL COMMENT '操作对象详情（JSON）',
  `result`          VARCHAR(16)   DEFAULT NULL COMMENT '操作结果（SUCCESS/FAILED）',
  `client_ip`       VARCHAR(45)   DEFAULT NULL COMMENT '客户端IP',
  `trace_id`        VARCHAR(64)   DEFAULT NULL COMMENT '调用链ID',
  `request_method`  VARCHAR(16)   DEFAULT NULL COMMENT 'HTTP 方法',
  `request_path`    VARCHAR(255)  DEFAULT NULL COMMENT '请求路径',
  `user_agent`      VARCHAR(512)  DEFAULT NULL COMMENT 'User-Agent',
  `request_summary` VARCHAR(1000) DEFAULT NULL COMMENT '脱敏请求摘要',
  `old_value`       TEXT          DEFAULT NULL COMMENT '变更前值(白名单字段)',
  `new_value`       TEXT          DEFAULT NULL COMMENT '变更后值(白名单字段)',
  `failure_reason`  VARCHAR(500)  DEFAULT NULL COMMENT '失败摘要',
  `duration_ms`     BIGINT        DEFAULT NULL COMMENT '执行耗时毫秒',
  `risk_level`      VARCHAR(16)   DEFAULT NULL COMMENT 'LOW/MEDIUM/HIGH/CRITICAL',
  `created_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted`         TINYINT       DEFAULT 0 COMMENT '逻辑删除标记(0:未删除,1:已删除)',
  PRIMARY KEY (`id`),
  KEY `idx_operation_type` (`operation_type`),
  KEY `idx_operator` (`operator`),
  KEY `idx_created_time` (`created_time`),
  KEY `idx_trace_id` (`trace_id`),
  KEY `idx_op_audit_created_deleted` (`created_time`, `deleted`),
  KEY `idx_op_audit_operator_time` (`operator_id`, `created_time`),
  KEY `idx_op_audit_type_time` (`operation_type`, `created_time`),
  KEY `idx_op_audit_target_time` (`target`, `created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通用运维操作审计表';

-- ----------------------------
-- 14. 聊天会话表
-- ----------------------------
CREATE TABLE `t_chat_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话ID（UUID）',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '新对话' COMMENT '会话标题',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `model_id` bigint NOT NULL COMMENT 'AI模型ID',
  `status` tinyint DEFAULT '0' COMMENT '会话状态（0-进行中，1-已结束）',
  `last_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '最后一条消息内容',
  `last_active_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间',
  `token_count` int DEFAULT '0' COMMENT '累计消耗token数',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_session_id` (`session_id`) USING BTREE,
  KEY `idx_model_id` (`model_id`) USING BTREE,
  KEY `idx_session_active_desc` (`last_active_time` DESC,`deleted`),
  KEY `idx_session_user_active_opt` (`user_id`,`last_active_time` DESC,`deleted`),
  CONSTRAINT `fk_session_model` FOREIGN KEY (`model_id`) REFERENCES `t_ai_model` (`id`),
  CONSTRAINT `fk_session_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='聊天会话表';

-- ----------------------------
-- 15. 聊天消息表（含 2026-08-03 reasoning 推理列）
-- ----------------------------
CREATE TABLE `t_chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `message_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息ID（UUID）',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话ID',
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息角色（user/assistant/system）',
  `content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息内容',
  `reasoning` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '推理思考内容（仅展示/审计，不回喂模型）',
  `message_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'text' COMMENT '消息类型（text/image/file等）',
  `has_attachments` tinyint(1) DEFAULT '0' COMMENT '是否包含附件（0-无附件，1-有附件）',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `model_id` bigint NOT NULL COMMENT 'AI模型ID',
  `token_count` int DEFAULT '0' COMMENT '消息token数',
  `status` tinyint DEFAULT '0' COMMENT '消息状态（0-发送中，1-成功，2-失败）',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  `has_web_search` tinyint(1) DEFAULT '0' COMMENT '是否使用联网查询（0-未使用，1-已使用）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_message_id` (`message_id`) USING BTREE,
  KEY `fk_message_model` (`model_id`) USING BTREE,
  KEY `idx_chat_msg_session_time` (`session_id`,`create_time`,`deleted`),
  KEY `idx_chat_msg_user_date` (`user_id`,`deleted`,`create_time`),
  KEY `idx_chat_msg_user_attachments` (`user_id`,`has_attachments`,`create_time`),
  KEY `idx_chat_msg_time_desc` (`create_time` DESC,`deleted`),
  KEY `idx_chat_msg_user_time_opt` (`user_id`,`create_time` DESC,`deleted`),
  KEY `idx_chat_msg_session_role` (`session_id`,`role`,`create_time` DESC),
  KEY `idx_chat_msg_user_stats` (`user_id`,`deleted`,`session_id`,`create_time`,`role`),
  KEY `idx_chat_msg_attachments_opt` (`has_attachments`,`session_id`,`create_time` DESC),
  KEY `idx_chat_msg_model_stats` (`model_id`,`create_time` DESC,`deleted`,`token_count`),
  KEY `idx_test_msg_status` (`status`,`user_id`,`create_time`) /*!80000 INVISIBLE */,
  KEY `idx_web_search_session` (`has_web_search`,`session_id`,`create_time`),
  KEY `idx_web_search_user` (`user_id`,`has_web_search`,`create_time`),
  KEY `idx_chat_msg_date_func` ((cast(`create_time` as date)),`user_id`),
  CONSTRAINT `fk_message_model` FOREIGN KEY (`model_id`) REFERENCES `t_ai_model` (`id`),
  CONSTRAINT `fk_message_session` FOREIGN KEY (`session_id`) REFERENCES `t_chat_session` (`session_id`),
  CONSTRAINT `fk_message_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='聊天消息表';

-- ----------------------------
-- 16. 文件上传表
-- ----------------------------
CREATE TABLE `t_file_upload` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `file_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件ID（UUID）',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联的会话ID',
  `message_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联的消息ID',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件名',
  `file_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件类型（MIME类型）',
  `file_size` bigint NOT NULL COMMENT '文件大小（字节）',
  `content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '文件内容（用于存储文本文件或小文件的完整内容）',
  `status` tinyint DEFAULT '1' COMMENT '状态（0-处理中，1-可用，2-失败）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_file_id` (`file_id`) USING BTREE,
  KEY `idx_message_id` (`message_id`) USING BTREE COMMENT '消息ID索引',
  KEY `idx_user_message_deleted` (`user_id`,`message_id`,`deleted`) USING BTREE COMMENT '用户消息文件查询优化索引',
  KEY `idx_session_message_time` (`session_id`,`message_id`,`create_time`) USING BTREE COMMENT '会话文件时间查询优化索引',
  KEY `idx_type_status_time` (`file_type`,`status`,`create_time`) USING BTREE COMMENT '文件类型状态查询优化索引',
  KEY `idx_file_user_cover` (`user_id`,`deleted`,`file_name`,`file_type`,`create_time`),
  KEY `idx_file_session_time` (`session_id`,`create_time` DESC,`deleted`),
  CONSTRAINT `fk_file_message` FOREIGN KEY (`message_id`) REFERENCES `t_chat_message` (`message_id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_file_session` FOREIGN KEY (`session_id`) REFERENCES `t_chat_session` (`session_id`),
  CONSTRAINT `fk_file_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='文件上传表 - 支持文件内容存储和消息关联';

-- ----------------------------
-- 17. 联网查询内容表
-- ----------------------------
CREATE TABLE `t_web_search_content` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `web_search_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '联网内容ID（UUID）',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话ID',
  `message_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息ID',
  `content` json NOT NULL COMMENT '联网查询结果JSON（存储results数组，包含url、title、content[500字符]、favicon）',
  `result_count` int DEFAULT '0' COMMENT '结果数量',
  `status` tinyint DEFAULT '1' COMMENT '状态（0-失败，1-成功）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_id` (`message_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_web_search_id` (`web_search_id`),
  KEY `idx_user_session_time` (`user_id`,`session_id`,`create_time`),
  KEY `idx_status_time` (`status`,`create_time`),
  KEY `idx_deleted_time` (`deleted`,`create_time`),
  KEY `idx_user_deleted_time` (`user_id`,`deleted`,`create_time` DESC),
  KEY `idx_session_deleted_time` (`session_id`,`deleted`,`create_time`),
  CONSTRAINT `fk_web_search_message` FOREIGN KEY (`message_id`) REFERENCES `t_chat_message` (`message_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_web_search_session` FOREIGN KEY (`session_id`) REFERENCES `t_chat_session` (`session_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_web_search_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='联网查询内容表';

SET FOREIGN_KEY_CHECKS = 1;
