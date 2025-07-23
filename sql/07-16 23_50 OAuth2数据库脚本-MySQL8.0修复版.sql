/*
 OAuth2渐进式集成方案 - MySQL 8.0修复版数据库脚本
 
 创建时间: 2025年07月16日 23:50 (北京时间)
 版本: v2.1 MySQL 8.0 Compatible
 设计原则: 零破坏性、完全兼容、渐进式集成、安全优先
 
 修复内容:
 1. 修复索引创建语法问题
 2. 优化MySQL 8.0兼容性
 3. 简化索引创建逻辑
 
 包含内容:
 1. t_user_oauth - OAuth账号绑定表
 2. t_oauth2_state - OAuth状态验证表
 3. t_oauth2_audit_log - OAuth审计日志表
 4. 相关索引优化
 5. 外键约束
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for t_user_oauth
-- ----------------------------
DROP TABLE IF EXISTS `t_user_oauth`;
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
  UNIQUE KEY `uk_provider_id` (`provider`, `provider_id`),
  KEY `idx_user_provider` (`user_id`, `provider`),
  KEY `idx_email` (`email`),
  CONSTRAINT `fk_oauth_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth2用户绑定表';

-- ----------------------------
-- Table structure for t_oauth2_state
-- ----------------------------
DROP TABLE IF EXISTS `t_oauth2_state`;
CREATE TABLE `t_oauth2_state` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `state_token` varchar(128) NOT NULL COMMENT '状态令牌',
  `provider` varchar(20) NOT NULL COMMENT 'OAuth提供商',
  `client_ip` varchar(45) DEFAULT NULL COMMENT '客户端IP',
  `user_agent` varchar(500) DEFAULT NULL COMMENT '用户代理',
  `expires_at` datetime NOT NULL COMMENT '过期时间',
  `used` tinyint DEFAULT '0' COMMENT '是否已使用:1=已使用,0=未使用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_state_token` (`state_token`),
  KEY `idx_provider_expires` (`provider`, `expires_at`),
  KEY `idx_expires_used` (`expires_at`, `used`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth2状态验证表';

-- ----------------------------
-- Table structure for t_oauth2_audit_log
-- ----------------------------
DROP TABLE IF EXISTS `t_oauth2_audit_log`;
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
  KEY `idx_user_action` (`user_id`, `action`),
  KEY `idx_provider_status` (`provider`, `status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth2审计日志表';

-- ----------------------------
-- 为t_user表添加OAuth相关查询优化索引（安全方式）
-- ----------------------------

-- 检查并创建邮箱查询索引
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
     WHERE table_schema = DATABASE() 
     AND table_name = 't_user' 
     AND index_name = 'idx_user_email_status') = 0,
    'CREATE INDEX idx_user_email_status ON t_user (email, status, deleted)',
    'SELECT "索引 idx_user_email_status 已存在" AS message'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查并创建用户名查询索引
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
     WHERE table_schema = DATABASE() 
     AND table_name = 't_user' 
     AND index_name = 'idx_user_username_status') = 0,
    'CREATE INDEX idx_user_username_status ON t_user (username, status, deleted)',
    'SELECT "索引 idx_user_username_status 已存在" AS message'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------
-- 创建定时清理任务（可选，需要开启事件调度器）
-- ----------------------------

-- 检查事件调度器是否开启
SELECT 
    @@event_scheduler AS event_scheduler_status,
    CASE 
        WHEN @@event_scheduler = 'ON' THEN '事件调度器已开启，将创建清理任务'
        ELSE '事件调度器未开启，请手动执行: SET GLOBAL event_scheduler = ON;'
    END AS message;

-- 清理过期状态令牌的事件（每小时执行一次）
DROP EVENT IF EXISTS `cleanup_oauth2_states`;
DELIMITER $$
CREATE EVENT IF NOT EXISTS `cleanup_oauth2_states`
ON SCHEDULE EVERY 1 HOUR
STARTS CURRENT_TIMESTAMP
DO
BEGIN
  DELETE FROM t_oauth2_state
  WHERE expires_at < NOW() OR (used = 1 AND create_time < DATE_SUB(NOW(), INTERVAL 24 HOUR));
END$$
DELIMITER ;

-- 清理旧审计日志的事件（每天执行一次，保留90天）
DROP EVENT IF EXISTS `cleanup_oauth2_audit_logs`;
DELIMITER $$
CREATE EVENT IF NOT EXISTS `cleanup_oauth2_audit_logs`
ON SCHEDULE EVERY 1 DAY
STARTS CURRENT_TIMESTAMP
DO
BEGIN
  DELETE FROM t_oauth2_audit_log
  WHERE create_time < DATE_SUB(NOW(), INTERVAL 90 DAY);
END$$
DELIMITER ;

-- ----------------------------
-- 数据完整性检查
-- ----------------------------

-- 检查是否存在孤立的OAuth绑定记录
SELECT 
    'OAuth绑定表数据完整性检查' AS check_type,
    COUNT(*) AS orphaned_records,
    CASE 
        WHEN COUNT(*) = 0 THEN '✅ 数据完整性正常'
        ELSE '⚠️ 发现孤立记录，需要清理'
    END AS status
FROM t_user_oauth uo
LEFT JOIN t_user u ON uo.user_id = u.id
WHERE u.id IS NULL;

-- 检查是否存在重复的OAuth绑定
SELECT 
    'OAuth绑定重复检查' AS check_type,
    COUNT(*) AS total_duplicates,
    CASE 
        WHEN COUNT(*) = 0 THEN '✅ 无重复绑定'
        ELSE '⚠️ 发现重复绑定，需要处理'
    END AS status
FROM (
    SELECT provider, provider_id, COUNT(*) as cnt
    FROM t_user_oauth
    WHERE deleted = 0
    GROUP BY provider, provider_id
    HAVING COUNT(*) > 1
) AS duplicates;

-- 检查索引创建情况
SELECT 
    '索引创建检查' AS check_type,
    table_name,
    index_name,
    '✅ 索引已创建' AS status
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE table_schema = DATABASE() 
AND table_name IN ('t_user_oauth', 't_oauth2_state', 't_oauth2_audit_log', 't_user')
AND index_name IN ('uk_provider_id', 'idx_user_provider', 'idx_email', 'uk_state_token', 
                   'idx_provider_expires', 'idx_expires_used', 'idx_user_action', 
                   'idx_provider_status', 'idx_create_time', 'idx_user_email_status', 
                   'idx_user_username_status')
ORDER BY table_name, index_name;

SET FOREIGN_KEY_CHECKS = 1;

-- ----------------------------
-- 脚本执行完成提示
-- ----------------------------
SELECT
    '🎉 OAuth2渐进式集成方案数据库脚本执行完成' AS message,
    NOW() AS execution_time,
    'v2.1 MySQL 8.0 Compatible' AS version,
    '✅ 修复索引语法问题，优化MySQL 8.0兼容性' AS features;

-- ----------------------------
-- 手动清理命令（如果事件调度器未开启）
-- ----------------------------
/*
-- 手动清理过期状态令牌
DELETE FROM t_oauth2_state
WHERE expires_at < NOW() OR (used = 1 AND create_time < DATE_SUB(NOW(), INTERVAL 24 HOUR));

-- 手动清理旧审计日志（保留90天）
DELETE FROM t_oauth2_audit_log
WHERE create_time < DATE_SUB(NOW(), INTERVAL 90 DAY);
*/
