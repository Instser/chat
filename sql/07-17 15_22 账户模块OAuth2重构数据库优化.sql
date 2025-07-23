-- 账户模块OAuth2重构数据库优化 - 精确版
-- 时间: 2025年07月17日 15:22
-- 版本: v1.3 - 基于chat2精确优化版
-- 目的: 基于chat2数据库结构，精确优化索引，提升查询性能
-- 说明: 移除冗余索引，添加缺失的关键索引，优化复合索引结构

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================
-- 1. 用户表优化 (基于chat2现有结构)
-- =====================================================

-- chat2中t_user表现有索引：
-- UNIQUE KEY `uk_email` (`email`)
-- UNIQUE KEY `uk_username` (`username`) 
-- KEY `idx_user_email_status` (`email`,`status`,`deleted`)
-- KEY `idx_user_username_status` (`username`,`status`,`deleted`)

-- 添加缺失的关键索引
ALTER TABLE t_user ADD INDEX idx_status_role (status, role) COMMENT '状态和角色复合索引，用于用户管理';
ALTER TABLE t_user ADD INDEX idx_status_deleted (status, deleted) COMMENT '用户状态查询优化索引';
ALTER TABLE t_user ADD INDEX idx_create_time (create_time) COMMENT '创建时间索引，用于统计分析';
ALTER TABLE t_user ADD INDEX idx_last_login_time (last_login_time) COMMENT '最后登录时间索引，用于活跃度分析';

-- =====================================================
-- 2. OAuth表优化 (基于chat2现有结构)
-- =====================================================

-- chat2中t_user_oauth表现有索引：
-- UNIQUE KEY `uk_provider_id` (`provider`,`provider_id`)
-- KEY `idx_user_provider` (`user_id`,`provider`)
-- KEY `idx_email` (`email`)

-- 添加缺失的关键索引
ALTER TABLE t_user_oauth ADD INDEX idx_email_provider (email, provider) COMMENT 'OAuth邮箱提供商复合索引';
ALTER TABLE t_user_oauth ADD INDEX idx_status (status) COMMENT 'OAuth状态索引';
ALTER TABLE t_user_oauth ADD INDEX idx_last_login_time (last_login_time) COMMENT 'OAuth最后登录时间索引';
ALTER TABLE t_user_oauth ADD INDEX idx_create_time (create_time) COMMENT 'OAuth创建时间索引';

-- =====================================================
-- 3. 用户账户表优化 (基于chat2现有结构)
-- =====================================================

-- chat2中t_user_account表现有索引：
-- UNIQUE KEY `uk_user_id` (`user_id`)
-- KEY `idx_account_package` (`package_id`,`deleted`)
-- KEY `idx_account_expire` (`package_expire_time`,`deleted`)

-- 添加缺失的关键索引
ALTER TABLE t_user_account ADD INDEX idx_membership_level (membership_level) COMMENT '会员等级索引';
ALTER TABLE t_user_account ADD INDEX idx_create_time (create_time) COMMENT '账户创建时间索引';

-- =====================================================
-- 4. OAuth审计日志表优化 (基于chat2现有结构)
-- =====================================================

-- chat2中t_oauth2_audit_log表现有索引：
-- KEY `idx_user_action` (`user_id`,`action`)
-- KEY `idx_provider_status` (`provider`,`status`)
-- KEY `idx_create_time` (`create_time`)

-- 移除现有的简单复合索引，添加包含时间维度的优化索引
DROP INDEX idx_user_action ON t_oauth2_audit_log;
DROP INDEX idx_provider_status ON t_oauth2_audit_log;

-- 添加优化的复合索引
ALTER TABLE t_oauth2_audit_log ADD INDEX idx_user_id (user_id) COMMENT '用户ID索引';
ALTER TABLE t_oauth2_audit_log ADD INDEX idx_provider (provider) COMMENT '提供商索引';
ALTER TABLE t_oauth2_audit_log ADD INDEX idx_action (action) COMMENT '操作类型索引';
ALTER TABLE t_oauth2_audit_log ADD INDEX idx_status (status) COMMENT '操作状态索引';
ALTER TABLE t_oauth2_audit_log ADD INDEX idx_client_ip (client_ip) COMMENT '客户端IP索引';
ALTER TABLE t_oauth2_audit_log ADD INDEX idx_user_action_time (user_id, action, create_time) COMMENT '用户操作时间复合索引';
ALTER TABLE t_oauth2_audit_log ADD INDEX idx_provider_status_time (provider, status, create_time) COMMENT '提供商状态时间复合索引';

-- =====================================================
-- 5. 系统设置表优化 (基于chat2现有结构)
-- =====================================================

-- chat2中t_system_setting表现有索引：
-- UNIQUE KEY `setting_key` (`setting_key`)
-- KEY `idx_setting_key` (`setting_key`)  -- 这个是冗余的
-- KEY `idx_setting_type` (`setting_type`)

-- 移除冗余索引
DROP INDEX idx_setting_key ON t_system_setting;

-- =====================================================
-- 6. 性能优化和数据完整性检查
-- =====================================================

-- 分析表统计信息
ANALYZE TABLE t_user;
ANALYZE TABLE t_user_oauth;
ANALYZE TABLE t_user_account;
ANALYZE TABLE t_oauth2_audit_log;
ANALYZE TABLE t_system_setting;

-- 检查孤立的用户账户记录
SELECT ua.user_id, ua.id as account_id 
FROM t_user_account ua 
LEFT JOIN t_user u ON ua.user_id = u.id 
WHERE u.id IS NULL;

-- 检查没有账户记录的用户
SELECT u.id, u.username, u.email 
FROM t_user u 
LEFT JOIN t_user_account ua ON u.id = ua.user_id 
WHERE ua.user_id IS NULL;

-- 检查OAuth绑定但用户不存在的记录
SELECT o.user_id, o.provider, o.email 
FROM t_user_oauth o 
LEFT JOIN t_user u ON o.user_id = u.id 
WHERE u.id IS NULL;

-- =====================================================
-- 7. 验证索引创建结果
-- =====================================================

-- 验证索引是否创建成功
SELECT 
    TABLE_NAME,
    INDEX_NAME,
    COLUMN_NAME,
    INDEX_COMMENT
FROM information_schema.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME IN ('t_user', 't_user_oauth', 't_user_account', 't_oauth2_audit_log', 't_system_setting')
  AND INDEX_NAME LIKE 'idx_%'
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;

-- 检查索引数量统计
SELECT 
    TABLE_NAME,
    COUNT(*) as index_count
FROM information_schema.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME IN ('t_user', 't_user_oauth', 't_user_account', 't_oauth2_audit_log', 't_system_setting')
GROUP BY TABLE_NAME
ORDER BY TABLE_NAME;

SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================
-- 优化总结
-- =====================================================
/*
本次优化内容：

新增索引：
1. t_user表：4个新索引（状态角色、状态删除、创建时间、最后登录时间）
2. t_user_oauth表：4个新索引（邮箱提供商、状态、最后登录时间、创建时间）
3. t_user_account表：2个新索引（会员等级、创建时间）
4. t_oauth2_audit_log表：5个新索引（用户ID、提供商、操作类型、状态、客户端IP）

优化索引：
1. t_oauth2_audit_log表：替换2个简单复合索引为包含时间维度的优化索引

移除冗余索引：
1. t_system_setting表：移除idx_setting_key冗余索引

预期效果：
- 提升用户管理查询性能
- 优化OAuth认证和审计查询
- 提升时间范围查询性能
- 减少索引维护开销
*/
