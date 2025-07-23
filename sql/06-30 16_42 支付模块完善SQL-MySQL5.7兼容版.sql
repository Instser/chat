-- 支付模块完善SQL脚本 - MySQL 5.7兼容版
-- 创建时间: 2025-06-30 16:42 (北京时间)
-- 版本: v1.1 (MySQL 5.7兼容版)
-- 目的: 创建支付审计表和相关索引，完善支付模块功能
-- 修复: 移除MySQL 8.0特有的IF NOT EXISTS语法，改为动态检查

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

-- 2. 动态检查并创建订单表索引
-- 检查idx_order_expire_status索引是否存在
SET @index_exists = (
    SELECT COUNT(1) 
    FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 't_user_order' 
    AND INDEX_NAME = 'idx_order_expire_status'
);

SET @sql = IF(@index_exists = 0, 
    'CREATE INDEX `idx_order_expire_status` ON `t_user_order` (`expire_time`, `payment_status`, `deleted`)',
    'SELECT "索引 idx_order_expire_status 已存在，跳过创建" as message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查idx_order_archive索引是否存在
SET @index_exists = (
    SELECT COUNT(1) 
    FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 't_user_order' 
    AND INDEX_NAME = 'idx_order_archive'
);

SET @sql = IF(@index_exists = 0, 
    'CREATE INDEX `idx_order_archive` ON `t_user_order` (`create_time`, `payment_status`, `deleted`)',
    'SELECT "索引 idx_order_archive 已存在，跳过创建" as message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查idx_order_payment_time索引是否存在
SET @index_exists = (
    SELECT COUNT(1) 
    FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 't_user_order' 
    AND INDEX_NAME = 'idx_order_payment_time'
);

SET @sql = IF(@index_exists = 0, 
    'CREATE INDEX `idx_order_payment_time` ON `t_user_order` (`payment_time`, `payment_status`, `deleted`)',
    'SELECT "索引 idx_order_payment_time 已存在，跳过创建" as message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查idx_order_method_time索引是否存在
SET @index_exists = (
    SELECT COUNT(1) 
    FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 't_user_order' 
    AND INDEX_NAME = 'idx_order_method_time'
);

SET @sql = IF(@index_exists = 0, 
    'CREATE INDEX `idx_order_method_time` ON `t_user_order` (`payment_method`, `payment_time`, `deleted`)',
    'SELECT "索引 idx_order_method_time 已存在，跳过创建" as message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查idx_order_package_time索引是否存在
SET @index_exists = (
    SELECT COUNT(1) 
    FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 't_user_order' 
    AND INDEX_NAME = 'idx_order_package_time'
);

SET @sql = IF(@index_exists = 0, 
    'CREATE INDEX `idx_order_package_time` ON `t_user_order` (`package_id`, `payment_time`, `deleted`)',
    'SELECT "索引 idx_order_package_time 已存在，跳过创建" as message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 创建支付数据分析视图
DROP VIEW IF EXISTS `v_payment_analytics`;
CREATE VIEW `v_payment_analytics` AS
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

-- 4. 创建每日支付统计视图
DROP VIEW IF EXISTS `v_daily_payment_stats`;
CREATE VIEW `v_daily_payment_stats` AS
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

-- 5. 创建套餐销售统计视图
DROP VIEW IF EXISTS `v_package_sales_stats`;
CREATE VIEW `v_package_sales_stats` AS
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

-- 6. 创建用户支付行为分析视图
DROP VIEW IF EXISTS `v_user_payment_behavior`;
CREATE VIEW `v_user_payment_behavior` AS
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

-- 7. 创建系统配置表
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

-- 8. 插入默认汇率配置
INSERT IGNORE INTO `t_system_config` (`config_key`, `config_value`, `config_type`, `description`) VALUES
('exchange_rate.usd_to_cny', '7.20', 'NUMBER', 'USD到CNY的汇率'),
('exchange_rate.cny_to_usd', '0.139', 'NUMBER', 'CNY到USD的汇率'),
('payment.frequency_limit', '5', 'NUMBER', '10分钟内最大支付次数'),
('payment.daily_amount_limit', '10000.00', 'NUMBER', '每日最大支付金额(CNY)'),
('payment.security_enabled', 'true', 'BOOLEAN', '是否启用支付安全验证'),
('payment.ip_whitelist_enabled', 'true', 'BOOLEAN', '是否启用IP白名单验证');

-- 9. 创建支付审计清理存储过程
DROP PROCEDURE IF EXISTS `CleanupPaymentAudit`;

DELIMITER $$
CREATE PROCEDURE `CleanupPaymentAudit`()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE cleanup_date DATETIME;
    DECLARE cleaned_count INT DEFAULT 0;

    -- 计算3个月前的日期
    SET cleanup_date = DATE_SUB(NOW(), INTERVAL 3 MONTH);

    -- 删除3个月前的审计日志
    DELETE FROM t_payment_audit
    WHERE create_time < cleanup_date
    AND deleted = 0;

    -- 获取清理的记录数
    SET cleaned_count = ROW_COUNT();

    -- 记录清理结果
    SELECT cleaned_count as cleaned_records, cleanup_date as cleanup_before_date;
END$$
DELIMITER ;

-- 10. 创建支付报表存储过程
DROP PROCEDURE IF EXISTS `GetPaymentReport`;

DELIMITER $$
CREATE PROCEDURE `GetPaymentReport`(
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

-- 11. 创建订单状态统计存储过程
DROP PROCEDURE IF EXISTS `GetOrderStatusStats`;

DELIMITER $$
CREATE PROCEDURE `GetOrderStatusStats`()
BEGIN
    -- 订单状态分布统计
    SELECT
        'status_distribution' as report_type,
        CASE payment_status
            WHEN 0 THEN '待支付'
            WHEN 1 THEN '支付成功'
            WHEN 2 THEN '支付取消'
            WHEN 3 THEN '已退款'
            ELSE '未知状态'
        END as status_name,
        payment_status,
        COUNT(*) as order_count,
        ROUND(COUNT(*) * 100.0 / (SELECT COUNT(*) FROM t_user_order WHERE deleted = 0), 2) as percentage,
        SUM(amount) as total_amount
    FROM t_user_order
    WHERE deleted = 0
    GROUP BY payment_status
    ORDER BY payment_status;

    -- 最近7天订单趋势
    SELECT
        'recent_trend' as report_type,
        DATE(create_time) as order_date,
        COUNT(*) as total_orders,
        COUNT(CASE WHEN payment_status = 1 THEN 1 END) as successful_orders,
        SUM(CASE WHEN payment_status = 1 THEN amount ELSE 0 END) as daily_revenue
    FROM t_user_order
    WHERE deleted = 0
    AND create_time >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
    GROUP BY DATE(create_time)
    ORDER BY order_date DESC;
END$$
DELIMITER ;

-- 执行完成提示
SELECT
    '支付模块完善SQL执行完成 (MySQL 5.7兼容版)' as message,
    NOW() as execution_time,
    'v1.1' as version,
    'MySQL 5.7兼容' as compatibility;
