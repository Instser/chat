-- =====================================================
-- 审计功能完善数据库脚本
-- 生成时间: 2025年7月31日 19:55 (北京时间)
-- 版本标识: v1.0.0-审计功能完善
-- 数据库: MySQL 8.0
-- =====================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================
-- 1. 用户操作审计表 (优先级: P0)
-- =====================================================
DROP TABLE IF EXISTS `t_user_operation_audit`;
CREATE TABLE `t_user_operation_audit` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `operation_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作类型(LOGIN/LOGOUT/REGISTER/UPDATE_PROFILE/CHANGE_PASSWORD/ACTIVATE_PACKAGE等)',
    `operation_module` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作模块(AUTH/PROFILE/PACKAGE/ACCOUNT等)',
    `old_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '原值(JSON格式)',
    `new_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '新值(JSON格式)',
    `operation_result` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'SUCCESS' COMMENT '操作结果(SUCCESS/FAILED/PARTIAL)',
    `client_ip` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '客户端IP',
    `user_agent` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '用户代理',
    `session_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '会话ID',
    `operation_details` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '操作详情(JSON格式)',
    `error_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志',
    PRIMARY KEY (`id`),
    KEY `idx_user_operation` (`user_id`, `operation_type`),
    KEY `idx_operation_time` (`operation_type`, `create_time`),
    KEY `idx_client_ip` (`client_ip`, `create_time`),
    KEY `idx_operation_module` (`operation_module`, `create_time`),
    KEY `idx_operation_result` (`operation_result`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户操作审计表';

-- =====================================================
-- 2. 安全事件审计表 (优先级: P0)
-- =====================================================
DROP TABLE IF EXISTS `t_security_event_audit`;
CREATE TABLE `t_security_event_audit` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `event_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件类型(SUSPICIOUS_LOGIN/RATE_LIMIT_EXCEEDED/MALICIOUS_REQUEST/BRUTE_FORCE等)',
    `severity_level` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '严重程度(LOW/MEDIUM/HIGH/CRITICAL)',
    `user_id` bigint COMMENT '相关用户ID',
    `client_ip` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '客户端IP',
    `user_agent` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '用户代理',
    `event_source` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '事件来源(API/WEB/SYSTEM/EXTERNAL)',
    `event_description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '事件描述',
    `event_details` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '事件详情(JSON格式)',
    `is_blocked` tinyint DEFAULT 0 COMMENT '是否已拦截(0-未拦截,1-已拦截)',
    `handle_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'PENDING' COMMENT '处理状态(PENDING/INVESTIGATING/RESOLVED/IGNORED)',
    `handle_result` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '处理结果',
    `handler_id` bigint COMMENT '处理人ID',
    `handle_time` datetime COMMENT '处理时间',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志',
    PRIMARY KEY (`id`),
    KEY `idx_event_type` (`event_type`, `severity_level`),
    KEY `idx_user_security` (`user_id`, `create_time`),
    KEY `idx_ip_security` (`client_ip`, `create_time`),
    KEY `idx_severity_time` (`severity_level`, `create_time`),
    KEY `idx_handle_status` (`handle_status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='安全事件审计表';

-- =====================================================
-- 3. 聊天内容审计表 (优先级: P1)
-- =====================================================
DROP TABLE IF EXISTS `t_chat_audit`;
CREATE TABLE `t_chat_audit` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `session_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '会话ID',
    `message_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '消息ID',
    `audit_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '审计类型(CONTENT_FILTER/RATE_LIMIT/SENSITIVE_CONTENT/ABNORMAL_BEHAVIOR等)',
    `audit_result` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '审计结果(PASS/BLOCK/WARNING)',
    `risk_level` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '风险等级(LOW/MEDIUM/HIGH)',
    `content_summary` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '内容摘要',
    `filter_reason` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '过滤原因',
    `audit_details` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '审计详情(JSON格式)',
    `client_ip` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '客户端IP',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志',
    PRIMARY KEY (`id`),
    KEY `idx_user_audit` (`user_id`, `audit_type`),
    KEY `idx_session_audit` (`session_id`, `create_time`),
    KEY `idx_audit_result` (`audit_result`, `risk_level`),
    KEY `idx_audit_time` (`audit_type`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天内容审计表';

-- =====================================================
-- 4. 文件操作审计表 (优先级: P1)
-- =====================================================
DROP TABLE IF EXISTS `t_file_operation_audit`;
CREATE TABLE `t_file_operation_audit` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `file_id` bigint COMMENT '文件ID',
    `operation_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作类型(UPLOAD/DOWNLOAD/DELETE/ACCESS/SCAN)',
    `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '文件名',
    `file_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '文件类型',
    `file_size` bigint COMMENT '文件大小(字节)',
    `operation_result` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'SUCCESS' COMMENT '操作结果(SUCCESS/FAILED/BLOCKED)',
    `security_scan_result` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '安全扫描结果(SAFE/SUSPICIOUS/MALICIOUS)',
    `block_reason` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '拦截原因',
    `client_ip` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '客户端IP',
    `user_agent` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '用户代理',
    `operation_details` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '操作详情(JSON格式)',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志',
    PRIMARY KEY (`id`),
    KEY `idx_user_file_op` (`user_id`, `operation_type`),
    KEY `idx_file_operation` (`file_id`, `operation_type`),
    KEY `idx_operation_result` (`operation_result`, `create_time`),
    KEY `idx_security_scan` (`security_scan_result`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件操作审计表';

-- =====================================================
-- 5. 系统配置审计表 (优先级: P2)
-- =====================================================
DROP TABLE IF EXISTS `t_system_config_audit`;
CREATE TABLE `t_system_config_audit` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `operator_id` bigint COMMENT '操作人ID',
    `operator_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'ADMIN' COMMENT '操作人类型(ADMIN/SYSTEM/USER)',
    `config_module` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置模块(AI_MODEL/SYSTEM_SETTING/CACHE_CONFIG/TASK_CONFIG等)',
    `config_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置键',
    `operation_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作类型(CREATE/UPDATE/DELETE/ENABLE/DISABLE)',
    `old_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '原值(JSON格式)',
    `new_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '新值(JSON格式)',
    `change_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '变更原因',
    `operation_result` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'SUCCESS' COMMENT '操作结果',
    `client_ip` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '客户端IP',
    `user_agent` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '用户代理',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志',
    PRIMARY KEY (`id`),
    KEY `idx_config_module` (`config_module`, `config_key`),
    KEY `idx_operator_config` (`operator_id`, `operation_type`),
    KEY `idx_operation_time` (`operation_type`, `create_time`),
    KEY `idx_config_key` (`config_key`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置审计表';

-- =====================================================
-- 6. 创建审计数据清理定时任务相关索引优化
-- =====================================================

-- 为审计表创建时间分区索引，便于定期清理
ALTER TABLE `t_user_operation_audit` ADD KEY `idx_cleanup_time` (`create_time`, `deleted`);
ALTER TABLE `t_security_event_audit` ADD KEY `idx_cleanup_time` (`create_time`, `deleted`);
ALTER TABLE `t_chat_audit` ADD KEY `idx_cleanup_time` (`create_time`, `deleted`);
ALTER TABLE `t_file_operation_audit` ADD KEY `idx_cleanup_time` (`create_time`, `deleted`);
ALTER TABLE `t_system_config_audit` ADD KEY `idx_cleanup_time` (`create_time`, `deleted`);

-- =====================================================
-- 7. 审计表数据保留策略说明
-- =====================================================

/*
数据保留策略建议:
1. 用户操作审计: 保留2年，重要操作(登录/密码修改)保留5年
2. 安全事件审计: 保留5年，高危事件永久保留
3. 聊天内容审计: 保留1年，违规内容保留3年
4. 文件操作审计: 保留1年，安全事件保留3年
5. 系统配置审计: 保留3年，关键配置变更永久保留

定时清理任务将在OptimizedScheduledTaskService中实现
*/

SET FOREIGN_KEY_CHECKS = 1;
