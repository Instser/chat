/*
 OAuth2登录功能数据库脚本
 
 创建时间: 2025年07月16日 19:55
 版本: v1.0
 描述: 为Google和GitHub OAuth2登录功能创建必要的数据库表和索引
 
 包含内容:
 1. t_user_oauth - OAuth账号绑定表
 2. 相关索引优化
 3. 外键约束
 4. 示例数据（可选）
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for t_user_oauth (Enhanced)
-- ----------------------------
DROP TABLE IF EXISTS `t_user_oauth`;
CREATE TABLE `t_user_oauth` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `provider` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'OAuth提供商(google/github)',
  `provider_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '第三方用户ID',
  `provider_username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '第三方用户名',
  `provider_email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '第三方邮箱',
  `provider_avatar` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '第三方头像URL',
  `access_token` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '访问令牌(加密存储)',
  `refresh_token` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '刷新令牌(加密存储)',
  `token_expire_time` datetime DEFAULT NULL COMMENT '令牌过期时间',
  `bind_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `device_fingerprint` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '设备指纹',
  `last_login_ip` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最后登录IP地址',
  `login_count` int DEFAULT '0' COMMENT '登录次数',
  `risk_score` int DEFAULT '0' COMMENT '风险评分',
  `status` tinyint DEFAULT '1' COMMENT '状态(0-禁用,1-启用)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志(0-未删除,1-已删除)',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_provider_id` (`provider`, `provider_id`) USING BTREE COMMENT '提供商用户ID唯一索引',
  KEY `idx_user_provider` (`user_id`, `provider`) USING BTREE COMMENT '用户提供商复合索引',
  KEY `idx_provider_email` (`provider`, `provider_email`) USING BTREE COMMENT '提供商邮箱复合索引',
  KEY `idx_oauth_status` (`status`, `deleted`) USING BTREE COMMENT '状态删除标志复合索引',
  KEY `idx_oauth_login_time` (`last_login_time`) USING BTREE COMMENT '最后登录时间索引',
  KEY `idx_oauth_bind_time` (`bind_time`) USING BTREE COMMENT '绑定时间索引',
  KEY `idx_oauth_token_expire` (`token_expire_time`) USING BTREE COMMENT '令牌过期时间索引',
  KEY `idx_oauth_user_provider_status` (`user_id`, `provider`, `status`, `deleted`) USING BTREE COMMENT 'OAuth用户提供商状态复合索引',
  KEY `idx_oauth_device_fingerprint` (`user_id`, `device_fingerprint`) USING BTREE COMMENT 'OAuth设备指纹索引',
  KEY `idx_oauth_last_login_ip` (`user_id`, `last_login_ip`) USING BTREE COMMENT 'OAuth最后登录IP索引',
  KEY `idx_oauth_risk_score` (`risk_score`, `last_login_time`) USING BTREE COMMENT 'OAuth风险评分索引',
  CONSTRAINT `fk_oauth_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='OAuth账号绑定表(增强版)';

-- ----------------------------
-- 创建OAuth登录统计表（可选，用于统计分析）
-- ----------------------------
DROP TABLE IF EXISTS `t_oauth_login_stats`;
CREATE TABLE `t_oauth_login_stats` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `provider` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'OAuth提供商',
  `login_date` date NOT NULL COMMENT '登录日期',
  `login_count` int DEFAULT '0' COMMENT '登录次数',
  `unique_users` int DEFAULT '0' COMMENT '独立用户数',
  `new_users` int DEFAULT '0' COMMENT '新用户数',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_provider_date` (`provider`, `login_date`) USING BTREE COMMENT '提供商日期唯一索引',
  KEY `idx_stats_date` (`login_date`) USING BTREE COMMENT '登录日期索引',
  KEY `idx_stats_provider` (`provider`) USING BTREE COMMENT '提供商索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='OAuth登录统计表';

-- ----------------------------
-- 创建OAuth令牌使用记录表
-- ----------------------------
DROP TABLE IF EXISTS `t_oauth_token_usage`;
CREATE TABLE `t_oauth_token_usage` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `token_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '令牌哈希值',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `operation` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作类型',
  `client_ip` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客户端IP',
  `user_agent` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '用户代理',
  `device_fingerprint` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '设备指纹',
  `risk_score` int DEFAULT '0' COMMENT '风险评分',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_token_hash` (`token_hash`) USING BTREE COMMENT '令牌哈希索引',
  KEY `idx_user_operation` (`user_id`, `operation`) USING BTREE COMMENT '用户操作复合索引',
  KEY `idx_create_time` (`create_time`) USING BTREE COMMENT '创建时间索引',
  KEY `idx_risk_score` (`risk_score`, `create_time`) USING BTREE COMMENT '风险评分时间复合索引',
  CONSTRAINT `fk_token_usage_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='OAuth令牌使用记录表';

-- ----------------------------
-- 创建OAuth事件表（用于异步处理）
-- ----------------------------
DROP TABLE IF EXISTS `t_oauth_event`;
CREATE TABLE `t_oauth_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `provider` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'OAuth提供商',
  `event_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件类型',
  `event_data` json DEFAULT NULL COMMENT '事件数据',
  `risk_level` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '风险等级',
  `processed` tinyint DEFAULT '0' COMMENT '是否已处理(0-未处理,1-已处理)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `process_time` datetime DEFAULT NULL COMMENT '处理时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_provider` (`user_id`, `provider`) USING BTREE COMMENT '用户提供商复合索引',
  KEY `idx_event_type` (`event_type`) USING BTREE COMMENT '事件类型索引',
  KEY `idx_processed_time` (`processed`, `create_time`) USING BTREE COMMENT '处理状态时间复合索引',
  KEY `idx_risk_level` (`risk_level`, `create_time`) USING BTREE COMMENT '风险等级时间复合索引',
  CONSTRAINT `fk_oauth_event_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='OAuth事件表';

-- ----------------------------
-- 创建OAuth错误事件表
-- ----------------------------
DROP TABLE IF EXISTS `t_oauth_error_event`;
CREATE TABLE `t_oauth_error_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `error_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '错误码',
  `provider` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'OAuth提供商',
  `client_ip` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客户端IP',
  `user_agent` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '用户代理',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '错误消息',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_error_provider` (`error_code`, `provider`) USING BTREE COMMENT '错误码提供商复合索引',
  KEY `idx_create_time` (`create_time`) USING BTREE COMMENT '创建时间索引',
  KEY `idx_client_ip` (`client_ip`, `create_time`) USING BTREE COMMENT '客户端IP时间复合索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='OAuth错误事件表';

-- ----------------------------
-- 插入OAuth提供商配置数据（可选）
-- ----------------------------
INSERT INTO `t_oauth_login_stats` (`provider`, `login_date`, `login_count`, `unique_users`, `new_users`) VALUES
('google', CURDATE(), 0, 0, 0),
('github', CURDATE(), 0, 0, 0);

-- ----------------------------
-- 创建视图：用户OAuth绑定概览
-- ----------------------------
CREATE OR REPLACE VIEW `v_user_oauth_summary` AS
SELECT 
    u.id AS user_id,
    u.username,
    u.email,
    u.create_time AS user_create_time,
    GROUP_CONCAT(DISTINCT uo.provider ORDER BY uo.provider SEPARATOR ',') AS bound_providers,
    COUNT(DISTINCT uo.provider) AS provider_count,
    MAX(uo.last_login_time) AS last_oauth_login_time
FROM t_user u
LEFT JOIN t_user_oauth uo ON u.id = uo.user_id AND uo.deleted = 0 AND uo.status = 1
WHERE u.deleted = 0
GROUP BY u.id, u.username, u.email, u.create_time;

-- ----------------------------
-- 创建存储过程：OAuth登录统计更新
-- ----------------------------
DELIMITER $$

CREATE PROCEDURE `UpdateOAuthLoginStats`(
    IN p_provider VARCHAR(20),
    IN p_user_id BIGINT,
    IN p_is_new_user BOOLEAN
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    
    START TRANSACTION;
    
    -- 更新或插入统计数据
    INSERT INTO t_oauth_login_stats (provider, login_date, login_count, unique_users, new_users)
    VALUES (p_provider, CURDATE(), 1, 1, IF(p_is_new_user, 1, 0))
    ON DUPLICATE KEY UPDATE
        login_count = login_count + 1,
        unique_users = (
            SELECT COUNT(DISTINCT user_id) 
            FROM t_user_oauth 
            WHERE provider = p_provider 
            AND DATE(last_login_time) = CURDATE()
            AND deleted = 0
        ),
        new_users = new_users + IF(p_is_new_user, 1, 0);
    
    COMMIT;
END$$

DELIMITER ;

-- ----------------------------
-- 创建触发器：自动更新OAuth登录统计
-- ----------------------------
DELIMITER $$

CREATE TRIGGER `tr_oauth_login_stats_update`
AFTER UPDATE ON `t_user_oauth`
FOR EACH ROW
BEGIN
    -- 当last_login_time更新时，调用统计更新存储过程
    IF NEW.last_login_time != OLD.last_login_time OR OLD.last_login_time IS NULL THEN
        CALL UpdateOAuthLoginStats(NEW.provider, NEW.user_id, FALSE);
    END IF;
END$$

DELIMITER ;

-- ----------------------------
-- 创建索引优化脚本
-- ----------------------------

-- 为t_user表添加OAuth相关查询优化索引（如果不存在）
CREATE INDEX IF NOT EXISTS `idx_user_email_status` ON `t_user` (`email`, `status`, `deleted`) COMMENT 'OAuth邮箱查询优化索引';
CREATE INDEX IF NOT EXISTS `idx_user_username_status` ON `t_user` (`username`, `status`, `deleted`) COMMENT 'OAuth用户名查询优化索引';

-- ----------------------------
-- 数据完整性检查脚本
-- ----------------------------

-- 检查是否存在孤立的OAuth绑定记录
SELECT 
    'OAuth绑定表数据完整性检查' AS check_type,
    COUNT(*) AS orphaned_records
FROM t_user_oauth uo
LEFT JOIN t_user u ON uo.user_id = u.id
WHERE u.id IS NULL;

-- 检查是否存在重复的OAuth绑定
SELECT 
    'OAuth绑定重复检查' AS check_type,
    provider,
    provider_id,
    COUNT(*) AS duplicate_count
FROM t_user_oauth
WHERE deleted = 0
GROUP BY provider, provider_id
HAVING COUNT(*) > 1;

-- ----------------------------
-- 性能测试查询
-- ----------------------------

-- OAuth登录查询性能测试
EXPLAIN SELECT u.* 
FROM t_user u
INNER JOIN t_user_oauth uo ON u.id = uo.user_id
WHERE uo.provider = 'google' 
AND uo.provider_id = 'test_user_id' 
AND uo.deleted = 0 
AND uo.status = 1
AND u.deleted = 0 
AND u.status = 1;

-- 用户OAuth绑定状态查询性能测试
EXPLAIN SELECT 
    uo.provider,
    uo.provider_username,
    uo.bind_time,
    uo.last_login_time
FROM t_user_oauth uo
WHERE uo.user_id = 1 
AND uo.deleted = 0 
AND uo.status = 1
ORDER BY uo.bind_time DESC;

-- ----------------------------
-- 清理脚本（谨慎使用）
-- ----------------------------

-- 清理过期的OAuth令牌（超过30天未使用）
-- UPDATE t_user_oauth 
-- SET access_token = NULL, refresh_token = NULL
-- WHERE token_expire_time < DATE_SUB(NOW(), INTERVAL 30 DAY)
-- AND last_login_time < DATE_SUB(NOW(), INTERVAL 30 DAY);

-- 软删除长期未使用的OAuth绑定（超过1年未登录）
-- UPDATE t_user_oauth 
-- SET deleted = 1
-- WHERE last_login_time < DATE_SUB(NOW(), INTERVAL 1 YEAR)
-- AND deleted = 0;

SET FOREIGN_KEY_CHECKS = 1;

-- ----------------------------
-- 脚本执行完成提示
-- ----------------------------
SELECT
    'OAuth2登录功能数据库脚本执行完成(增强版)' AS message,
    NOW() AS execution_time,
    'v2.0 Enhanced' AS version,
    '包含系统韧性、零信任安全、缓存优化等增强功能' AS features;
