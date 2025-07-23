-- 支付模块完善SQL脚本
-- 创建时间: 2025-06-29 21:20 (北京时间)
-- 版本: v1.0
-- 目的: 创建支付审计表和相关索引，完善支付模块功能

-- 1. 创建支付审计日志表
CREATE TABLE IF NOT EXISTS `t_payment_audit` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订单号',
    `operation_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作类型(CREATE/PAY/CANCEL/REFUND/QUERY/SYNC/CALLBACK)',
    `old_status` int(11) DEFAULT NULL COMMENT '原状态',
    `new_status` int(11) DEFAULT NULL COMMENT '新状态',
    `operator_id` bigint(20) DEFAULT NULL COMMENT '操作人ID',
    `operator_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'SYSTEM' COMMENT '操作人类型(USER/SYSTEM/ADMIN/CALLBACK)',
    `operation_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作原因',
    `operation_details` text COLLATE utf8mb4_unicode_ci COMMENT '操作详情(JSON格式)',
    `client_ip` varchar(45) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客户端IP',
    `user_agent` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户代理',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` tinyint(4) DEFAULT '0' COMMENT '逻辑删除标志(0-未删除，1-已删除)',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_order_no` (`order_no`) USING BTREE,
    KEY `idx_operation_type` (`operation_type`) USING BTREE,
    KEY `idx_operator_id` (`operator_id`) USING BTREE,
    KEY `idx_create_time` (`create_time`) USING BTREE,
    KEY `idx_audit_order_time` (`order_no`, `create_time`, `deleted`) USING BTREE,
    KEY `idx_audit_type_time` (`operation_type`, `create_time`, `deleted`) USING BTREE,
    KEY `idx_audit_operator_time` (`operator_id`, `create_time`, `deleted`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='支付审计日志表';

-- 2. 为订单表添加过期订单查询的索引（如果不存在）
CREATE INDEX IF NOT EXISTS `idx_order_expire_status` ON `t_user_order` (`expire_time`, `payment_status`, `deleted`);

-- 3. 为订单表添加归档查询的索引（如果不存在）
CREATE INDEX IF NOT EXISTS `idx_order_archive` ON `t_user_order` (`create_time`, `payment_status`, `deleted`);

-- 4. 为订单表添加支付分析相关的索引（如果不存在）
CREATE INDEX IF NOT EXISTS `idx_order_payment_time` ON `t_user_order` (`payment_time`, `payment_status`, `deleted`);
CREATE INDEX IF NOT EXISTS `idx_order_method_time` ON `t_user_order` (`payment_method`, `payment_time`, `deleted`);
CREATE INDEX IF NOT EXISTS `idx_order_package_time` ON `t_user_order` (`package_id`, `payment_time`, `deleted`);

-- 5. 插入一些示例审计数据（可选，用于测试）
-- INSERT INTO `t_payment_audit` (`order_no`, `operation_type`, `old_status`, `new_status`, `operator_type`, `operation_reason`, `operation_details`) VALUES
-- ('JY202506290001', 'CREATE', NULL, 0, 'USER', '用户创建支付订单', '{"packageId": 1, "amount": 68.00, "currency": "CNY"}'),
-- ('JY202506290001', 'PAY', 0, 1, 'CALLBACK', '支付宝回调支付成功', '{"transactionId": "2025062922001234567890", "paymentTime": "2025-06-29T21:20:00"}');

-- 6. 创建支付数据分析视图（可选）
CREATE OR REPLACE VIEW `v_payment_analytics` AS
SELECT 
    DATE(payment_time) as payment_date,
    payment_method,
    payment_status,
    COUNT(*) as order_count,
    SUM(amount) as total_amount,
    AVG(amount) as avg_amount,
    MIN(amount) as min_amount,
    MAX(amount) as max_amount
FROM t_user_order 
WHERE deleted = 0 
    AND payment_time IS NOT NULL
GROUP BY DATE(payment_time), payment_method, payment_status;

-- 7. 创建每日支付统计视图
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

-- 8. 创建套餐销售统计视图
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

-- 9. 创建用户支付行为分析视图
CREATE OR REPLACE VIEW `v_user_payment_behavior` AS
SELECT 
    user_id,
    COUNT(*) as total_orders,
    COUNT(CASE WHEN payment_status = 1 THEN 1 END) as successful_orders,
    SUM(CASE WHEN payment_status = 1 THEN amount ELSE 0 END) as total_spent,
    AVG(CASE WHEN payment_status = 1 THEN amount END) as avg_order_amount,
    MIN(create_time) as first_order_time,
    MAX(payment_time) as last_payment_time,
    ROUND(COUNT(CASE WHEN payment_status = 1 THEN 1 END) * 100.0 / COUNT(*), 2) as success_rate
FROM t_user_order 
WHERE deleted = 0
GROUP BY user_id
HAVING total_orders > 0
ORDER BY total_spent DESC;

-- 10. 添加配置表用于存储汇率等动态配置（可选）
CREATE TABLE IF NOT EXISTS `t_system_config` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `config_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置键',
    `config_value` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置值',
    `config_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'STRING' COMMENT '配置类型(STRING/NUMBER/JSON/BOOLEAN)',
    `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '配置描述',
    `is_enabled` tinyint(4) DEFAULT '1' COMMENT '是否启用',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` tinyint(4) DEFAULT '0' COMMENT '逻辑删除标志',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_config_key` (`config_key`) USING BTREE,
    KEY `idx_config_enabled` (`is_enabled`, `deleted`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='系统配置表';

-- 11. 插入默认汇率配置
INSERT IGNORE INTO `t_system_config` (`config_key`, `config_value`, `config_type`, `description`) VALUES
('exchange_rate.usd_to_cny', '7.20', 'NUMBER', 'USD到CNY的汇率'),
('exchange_rate.cny_to_usd', '0.139', 'NUMBER', 'CNY到USD的汇率'),
('payment.frequency_limit', '5', 'NUMBER', '10分钟内最大支付次数'),
('payment.daily_amount_limit', '10000.00', 'NUMBER', '每日最大支付金额(CNY)'),
('payment.security_enabled', 'true', 'BOOLEAN', '是否启用支付安全验证'),
('payment.ip_whitelist_enabled', 'true', 'BOOLEAN', '是否启用IP白名单验证');

-- 12. 创建存储过程：清理过期的审计日志（保留最近3个月的数据）
DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS `CleanupPaymentAudit`()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE cleanup_date DATETIME;
    
    -- 计算3个月前的日期
    SET cleanup_date = DATE_SUB(NOW(), INTERVAL 3 MONTH);
    
    -- 删除3个月前的审计日志
    DELETE FROM t_payment_audit 
    WHERE create_time < cleanup_date 
    AND deleted = 0;
    
    -- 记录清理结果
    SELECT ROW_COUNT() as cleaned_records, cleanup_date as cleanup_before_date;
END$$
DELIMITER ;

-- 13. 创建存储过程：获取支付统计报告
DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS `GetPaymentReport`(
    IN start_date DATE,
    IN end_date DATE
)
BEGIN
    -- 基础统计
    SELECT 
        'overview' as report_type,
        COUNT(*) as total_orders,
        COUNT(CASE WHEN payment_status = 1 THEN 1 END) as successful_orders,
        COUNT(CASE WHEN payment_status = 2 THEN 1 END) as canceled_orders,
        COUNT(CASE WHEN payment_status = 3 THEN 1 END) as refunded_orders,
        SUM(CASE WHEN payment_status = 1 THEN amount ELSE 0 END) as total_revenue,
        AVG(CASE WHEN payment_status = 1 THEN amount END) as avg_order_amount,
        ROUND(COUNT(CASE WHEN payment_status = 1 THEN 1 END) * 100.0 / COUNT(*), 2) as success_rate
    FROM t_user_order 
    WHERE deleted = 0 
    AND DATE(create_time) BETWEEN start_date AND end_date;
    
    -- 支付方式统计
    SELECT 
        'payment_method' as report_type,
        payment_method,
        COUNT(*) as order_count,
        COUNT(CASE WHEN payment_status = 1 THEN 1 END) as successful_count,
        SUM(CASE WHEN payment_status = 1 THEN amount ELSE 0 END) as revenue
    FROM t_user_order 
    WHERE deleted = 0 
    AND DATE(create_time) BETWEEN start_date AND end_date
    GROUP BY payment_method;
    
    -- 每日统计
    SELECT 
        'daily' as report_type,
        DATE(create_time) as order_date,
        COUNT(*) as order_count,
        COUNT(CASE WHEN payment_status = 1 THEN 1 END) as successful_count,
        SUM(CASE WHEN payment_status = 1 THEN amount ELSE 0 END) as revenue
    FROM t_user_order 
    WHERE deleted = 0 
    AND DATE(create_time) BETWEEN start_date AND end_date
    GROUP BY DATE(create_time)
    ORDER BY order_date;
END$$
DELIMITER ;

-- 执行完成提示
SELECT 
    '支付模块完善SQL执行完成' as message,
    NOW() as execution_time,
    'v1.0' as version;
