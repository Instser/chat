-- 支付模块完善SQL脚本 - MySQL 8.0优化版
-- 创建时间: 2025-06-30 16:42 (北京时间)
-- 版本: v2.0 (MySQL 8.0优化版)
-- 目的: 创建支付审计表和相关索引，完善支付模块功能
-- 优化: 针对MySQL 8.0进行语法和性能优化

-- =====================================================
-- 环境检查
-- =====================================================

-- 检查MySQL版本
SELECT VERSION() as mysql_version;

-- 检查当前数据库
SELECT DATABASE() as current_database;

-- 检查t_user_order表是否存在
SELECT 
    TABLE_NAME,
    ENGINE,
    TABLE_ROWS,
    DATA_LENGTH,
    INDEX_LENGTH
FROM INFORMATION_SCHEMA.TABLES 
WHERE TABLE_SCHEMA = DATABASE() 
AND TABLE_NAME = 't_user_order';

-- =====================================================
-- 1. 创建支付审计日志表
-- =====================================================

CREATE TABLE IF NOT EXISTS `t_payment_audit` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订单号',
    `operation_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作类型(CREATE/PAY/CANCEL/REFUND/QUERY/SYNC/CALLBACK)',
    `old_status` int DEFAULT NULL COMMENT '原状态',
    `new_status` int DEFAULT NULL COMMENT '新状态',
    `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
    `operator_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'SYSTEM' COMMENT '操作人类型(USER/SYSTEM/ADMIN/CALLBACK)',
    `operation_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作原因',
    `operation_details` json DEFAULT NULL COMMENT '操作详情(JSON格式)',
    `client_ip` varchar(45) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客户端IP',
    `user_agent` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户代理',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志(0-未删除，1-已删除)',
    PRIMARY KEY (`id`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_operation_type` (`operation_type`),
    KEY `idx_operator_id` (`operator_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_audit_order_time` (`order_no`, `create_time`, `deleted`),
    KEY `idx_audit_type_time` (`operation_type`, `create_time`, `deleted`),
    KEY `idx_audit_operator_time` (`operator_id`, `create_time`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付审计日志表';

-- =====================================================
-- 2. 为订单表添加索引（MySQL 8.0原生语法）
-- =====================================================

-- 过期订单查询索引
CREATE INDEX IF NOT EXISTS `idx_order_expire_status` 
ON `t_user_order` (`expire_time`, `payment_status`, `deleted`);

-- 归档查询索引
CREATE INDEX IF NOT EXISTS `idx_order_archive` 
ON `t_user_order` (`create_time`, `payment_status`, `deleted`);

-- 支付时间分析索引
CREATE INDEX IF NOT EXISTS `idx_order_payment_time` 
ON `t_user_order` (`payment_time`, `payment_status`, `deleted`);

-- 支付方式分析索引
CREATE INDEX IF NOT EXISTS `idx_order_method_time` 
ON `t_user_order` (`payment_method`, `payment_time`, `deleted`);

-- 套餐分析索引
CREATE INDEX IF NOT EXISTS `idx_order_package_time` 
ON `t_user_order` (`package_id`, `payment_time`, `deleted`);

-- =====================================================
-- 3. 创建分析视图（MySQL 8.0优化）
-- =====================================================

-- 支付数据分析视图
CREATE OR REPLACE VIEW `v_payment_analytics` AS
SELECT 
    DATE(payment_time) as payment_date,
    payment_method,
    payment_status,
    COUNT(*) as order_count,
    SUM(amount) as total_amount,
    AVG(amount) as avg_amount,
    MIN(amount) as min_amount,
    MAX(amount) as max_amount,
    STDDEV(amount) as amount_stddev
FROM t_user_order 
WHERE deleted = 0 
    AND payment_time IS NOT NULL
GROUP BY DATE(payment_time), payment_method, payment_status;

-- 每日支付统计视图
CREATE OR REPLACE VIEW `v_daily_payment_stats` AS
SELECT 
    DATE(payment_time) as payment_date,
    COUNT(CASE WHEN payment_status = 1 THEN 1 END) as successful_orders,
    COUNT(CASE WHEN payment_status = 0 THEN 1 END) as pending_orders,
    COUNT(CASE WHEN payment_status = 2 THEN 1 END) as canceled_orders,
    COUNT(CASE WHEN payment_status = 3 THEN 1 END) as refunded_orders,
    COUNT(*) as total_orders,
    SUM(CASE WHEN payment_status = 1 THEN amount ELSE 0 END) as total_revenue,
    ROUND(COUNT(CASE WHEN payment_status = 1 THEN 1 END) * 100.0 / COUNT(*), 2) as success_rate
FROM t_user_order 
WHERE deleted = 0 
    AND payment_time IS NOT NULL
GROUP BY DATE(payment_time)
ORDER BY payment_date DESC;

-- 套餐销售统计视图
CREATE OR REPLACE VIEW `v_package_sales_stats` AS
SELECT 
    p.id as package_id,
    p.package_name,
    p.package_type,
    COUNT(o.id) as total_orders,
    COUNT(CASE WHEN o.payment_status = 1 THEN 1 END) as successful_orders,
    SUM(CASE WHEN o.payment_status = 1 THEN o.amount ELSE 0 END) as total_revenue,
    AVG(CASE WHEN o.payment_status = 1 THEN o.amount END) as avg_order_amount,
    ROUND(COUNT(CASE WHEN o.payment_status = 1 THEN 1 END) * 100.0 / COUNT(o.id), 2) as success_rate
FROM t_package p
LEFT JOIN t_user_order o ON p.id = o.package_id AND o.deleted = 0
WHERE p.deleted = 0
GROUP BY p.id, p.package_name, p.package_type
ORDER BY total_revenue DESC;

-- 用户支付行为分析视图
CREATE OR REPLACE VIEW `v_user_payment_behavior` AS
SELECT 
    user_id,
    COUNT(*) as total_orders,
    COUNT(CASE WHEN payment_status = 1 THEN 1 END) as successful_orders,
    SUM(CASE WHEN payment_status = 1 THEN amount ELSE 0 END) as total_spent,
    AVG(CASE WHEN payment_status = 1 THEN amount END) as avg_order_amount,
    MIN(create_time) as first_order_time,
    MAX(payment_time) as last_payment_time,
    ROUND(COUNT(CASE WHEN payment_status = 1 THEN 1 END) * 100.0 / COUNT(*), 2) as success_rate,
    DATEDIFF(MAX(payment_time), MIN(create_time)) as customer_lifetime_days
FROM t_user_order 
WHERE deleted = 0
GROUP BY user_id
HAVING total_orders > 0
ORDER BY total_spent DESC;

-- =====================================================
-- 4. 创建系统配置表（MySQL 8.0优化）
-- =====================================================

CREATE TABLE IF NOT EXISTS `t_system_config` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `config_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置键',
    `config_value` json NOT NULL COMMENT '配置值(JSON格式)',
    `config_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'STRING' COMMENT '配置类型(STRING/NUMBER/JSON/BOOLEAN)',
    `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '配置描述',
    `is_enabled` tinyint DEFAULT '1' COMMENT '是否启用',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除标志',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`),
    KEY `idx_config_enabled` (`is_enabled`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- =====================================================
-- 5. 插入默认配置（MySQL 8.0 JSON格式）
-- =====================================================

INSERT IGNORE INTO `t_system_config` (`config_key`, `config_value`, `config_type`, `description`) VALUES
('exchange_rate.usd_to_cny', '7.20', 'NUMBER', 'USD到CNY的汇率'),
('exchange_rate.cny_to_usd', '0.139', 'NUMBER', 'CNY到USD的汇率'),
('payment.frequency_limit', '5', 'NUMBER', '10分钟内最大支付次数'),
('payment.daily_amount_limit', '10000.00', 'NUMBER', '每日最大支付金额(CNY)'),
('payment.security_enabled', 'true', 'BOOLEAN', '是否启用支付安全验证'),
('payment.ip_whitelist_enabled', 'true', 'BOOLEAN', '是否启用IP白名单验证'),
('payment.methods', '["alipay", "wechat", "yipay"]', 'JSON', '支持的支付方式'),
('payment.timeout_minutes', '30', 'NUMBER', '支付超时时间(分钟)');

-- =====================================================
-- 验证创建结果
-- =====================================================

-- 显示创建的索引
SELECT 
    TABLE_NAME as '表名',
    INDEX_NAME as '索引名称',
    GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) as '索引字段',
    INDEX_TYPE as '索引类型',
    NON_UNIQUE as '是否唯一'
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE() 
AND TABLE_NAME IN ('t_user_order', 't_payment_audit', 't_system_config')
AND INDEX_NAME != 'PRIMARY'
GROUP BY TABLE_NAME, INDEX_NAME, INDEX_TYPE, NON_UNIQUE
ORDER BY TABLE_NAME, INDEX_NAME;

-- 显示创建的视图
SELECT 
    TABLE_NAME as '视图名称',
    TABLE_COMMENT as '视图说明'
FROM INFORMATION_SCHEMA.TABLES 
WHERE TABLE_SCHEMA = DATABASE() 
AND TABLE_TYPE = 'VIEW'
AND TABLE_NAME LIKE 'v_%'
ORDER BY TABLE_NAME;

-- 执行完成提示
SELECT 
    '支付模块完善SQL执行完成 (MySQL 8.0优化版)' as message,
    NOW() as execution_time,
    'v2.0' as version,
    'MySQL 8.0优化' as optimization;
