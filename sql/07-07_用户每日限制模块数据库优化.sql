-- =====================================================
-- 用户每日限制模块数据库优化脚本
-- 创建时间: 2025-07-07 22:20:00
-- 版本: v1.0
-- 说明: 优化t_user_daily_usage表结构，移除冗余字段，提升性能
-- =====================================================

-- 设置SQL模式和字符集
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;


-- =====================================================
-- 第二步：创建优化后的新表结构
-- =====================================================

-- 创建新的用户每日使用记录表
DROP TABLE IF EXISTS `t_user_daily_usage_new`;
CREATE TABLE `t_user_daily_usage_new` (
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

-- =====================================================
-- 第三步：数据分析和清理
-- =====================================================

-- 分析原表中的重复数据情况
SELECT
    '原表总记录数' as description,
    COUNT(*) as count_value
FROM `t_user_daily_usage`
UNION ALL
SELECT
    '原表唯一组合数' as description,
    COUNT(DISTINCT CONCAT(`user_id`, '-', COALESCE(`model_name`, 'STANDARD'), '-', `usage_date`)) as count_value
FROM `t_user_daily_usage`
UNION ALL
SELECT
    '重复记录数' as description,
    COUNT(*) - COUNT(DISTINCT CONCAT(`user_id`, '-', COALESCE(`model_name`, 'STANDARD'), '-', `usage_date`)) as count_value
FROM `t_user_daily_usage`;

-- 查看重复数据的详细情况（如果有的话）
SELECT
    `user_id`,
    COALESCE(`model_name`, 'STANDARD') as `model_name`,
    `usage_date`,
    COUNT(*) as duplicate_count,
    SUM(`chat_count_used`) as total_chat_count,
    GROUP_CONCAT(`id` ORDER BY `create_time`) as duplicate_ids
FROM `t_user_daily_usage`
WHERE `user_id` IS NOT NULL
  AND `usage_date` IS NOT NULL
GROUP BY `user_id`, COALESCE(`model_name`, 'STANDARD'), `usage_date`
HAVING COUNT(*) > 1
ORDER BY duplicate_count DESC
LIMIT 10;

-- =====================================================
-- 第四步：数据迁移
-- =====================================================

-- 迁移数据到新表（移除package_id和daily_limit冗余字段，处理重复数据）
INSERT INTO `t_user_daily_usage_new`
(`user_id`, `model_name`, `usage_date`, `chat_count_used`, `last_chat_time`, `create_time`, `update_time`)
SELECT
    `user_id`,
    COALESCE(`model_name`, 'STANDARD') as `model_name`,
    `usage_date`,
    SUM(`chat_count_used`) as `chat_count_used`,  -- 合并重复记录的使用次数
    MAX(`last_chat_time`) as `last_chat_time`,    -- 取最新的聊天时间
    MIN(`create_time`) as `create_time`,          -- 取最早的创建时间
    MAX(`update_time`) as `update_time`           -- 取最新的更新时间
FROM `t_user_daily_usage`
WHERE `user_id` IS NOT NULL
  AND `usage_date` IS NOT NULL
GROUP BY `user_id`, COALESCE(`model_name`, 'STANDARD'), `usage_date`  -- 按用户、模型、日期分组去重
ORDER BY `user_id`, `usage_date`;

-- 验证数据迁移结果
SELECT
    '原表记录数' as table_name, COUNT(*) as record_count
FROM `t_user_daily_usage`
UNION ALL
SELECT
    '新表记录数' as table_name, COUNT(*) as record_count
FROM `t_user_daily_usage_new`
UNION ALL
SELECT
    '原表唯一组合数' as table_name,
    COUNT(DISTINCT CONCAT(`user_id`, '-', COALESCE(`model_name`, 'STANDARD'), '-', `usage_date`)) as record_count
FROM `t_user_daily_usage`
UNION ALL
SELECT
    '新表唯一组合数' as table_name,
    COUNT(DISTINCT CONCAT(`user_id`, '-', `model_name`, '-', `usage_date`)) as record_count
FROM `t_user_daily_usage_new`;

-- 检查是否还有重复数据
SELECT
    `user_id`,
    `model_name`,
    `usage_date`,
    COUNT(*) as duplicate_count
FROM `t_user_daily_usage_new`
GROUP BY `user_id`, `model_name`, `usage_date`
HAVING COUNT(*) > 1;

-- 如果上面的查询返回结果，说明还有重复数据，需要进一步处理

-- =====================================================
-- 第五步：表结构切换
-- =====================================================

-- 重命名表（原子操作）
RENAME TABLE 
    `t_user_daily_usage` TO `t_user_daily_usage_old`,
    `t_user_daily_usage_new` TO `t_user_daily_usage`;

-- =====================================================
-- 第六步：添加表分区（可选，用于大数据量优化）
-- =====================================================

-- 为新表添加按月分区（如果数据量大的话）
-- 注意：分区表不能有外键约束，如需分区请先删除外键
/*
ALTER TABLE `t_user_daily_usage` DROP FOREIGN KEY `fk_daily_usage_user_new`;

ALTER TABLE `t_user_daily_usage` 
PARTITION BY RANGE (TO_DAYS(`usage_date`)) (
    PARTITION p202507 VALUES LESS THAN (TO_DAYS('2025-08-01')) COMMENT '2025年7月',
    PARTITION p202508 VALUES LESS THAN (TO_DAYS('2025-09-01')) COMMENT '2025年8月',
    PARTITION p202509 VALUES LESS THAN (TO_DAYS('2025-10-01')) COMMENT '2025年9月',
    PARTITION p202510 VALUES LESS THAN (TO_DAYS('2025-11-01')) COMMENT '2025年10月',
    PARTITION p202511 VALUES LESS THAN (TO_DAYS('2025-12-01')) COMMENT '2025年11月',
    PARTITION p202512 VALUES LESS THAN (TO_DAYS('2026-01-01')) COMMENT '2025年12月',
    PARTITION p_future VALUES LESS THAN MAXVALUE COMMENT '未来分区'
);
*/

-- =====================================================
-- 第七步：创建优化的存储过程和函数
-- =====================================================

-- 删除旧的存储过程
DROP PROCEDURE IF EXISTS `sp_get_user_daily_usage`;
DROP PROCEDURE IF EXISTS `sp_record_user_chat_usage`;

-- 创建获取用户每日使用情况的存储过程
DELIMITER $$
CREATE PROCEDURE `sp_get_user_daily_usage`(
    IN p_user_id BIGINT,
    IN p_model_name VARCHAR(50),
    IN p_usage_date DATE
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    
    SELECT 
        `id`,
        `user_id`,
        `model_name`,
        `usage_date`,
        `chat_count_used`,
        `last_chat_time`,
        `create_time`,
        `update_time`
    FROM `t_user_daily_usage`
    WHERE `user_id` = p_user_id
      AND `model_name` = p_model_name
      AND `usage_date` = p_usage_date
    LIMIT 1;
END$$

-- 创建记录用户聊天使用的存储过程（原子操作）
CREATE PROCEDURE `sp_record_user_chat_usage`(
    IN p_user_id BIGINT,
    IN p_model_name VARCHAR(50),
    IN p_usage_date DATE,
    OUT p_result INT,
    OUT p_current_count INT
)
BEGIN
    DECLARE v_count INT DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET p_result = 0;
        ROLLBACK;
        RESIGNAL;
    END;
    
    START TRANSACTION;
    
    -- 原子性插入或更新
    INSERT INTO `t_user_daily_usage` 
    (`user_id`, `model_name`, `usage_date`, `chat_count_used`, `last_chat_time`, `create_time`, `update_time`)
    VALUES (p_user_id, p_model_name, p_usage_date, 1, NOW(), NOW(), NOW())
    ON DUPLICATE KEY UPDATE
        `chat_count_used` = `chat_count_used` + 1,
        `last_chat_time` = NOW(),
        `update_time` = NOW();
    
    -- 获取当前使用次数
    SELECT `chat_count_used` INTO v_count
    FROM `t_user_daily_usage`
    WHERE `user_id` = p_user_id
      AND `model_name` = p_model_name
      AND `usage_date` = p_usage_date;
    
    SET p_current_count = v_count;
    SET p_result = 1;
    
    COMMIT;
END$$

DELIMITER ;

-- =====================================================
-- 第八步：创建数据清理任务
-- =====================================================

-- 创建清理过期数据的存储过程
DELIMITER $$
CREATE PROCEDURE `sp_cleanup_expired_usage_records`(
    IN p_retention_days INT DEFAULT 90
)
BEGIN
    DECLARE v_deleted_count INT DEFAULT 0;
    DECLARE v_cutoff_date DATE;
    
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    
    SET v_cutoff_date = DATE_SUB(CURDATE(), INTERVAL p_retention_days DAY);
    
    START TRANSACTION;
    
    -- 删除过期记录
    DELETE FROM `t_user_daily_usage`
    WHERE `usage_date` < v_cutoff_date;
    
    SET v_deleted_count = ROW_COUNT();
    
    -- 记录清理日志
    INSERT INTO `t_system_log` (`log_type`, `log_content`, `create_time`)
    VALUES ('DATA_CLEANUP', 
            CONCAT('清理过期使用记录完成，删除记录数: ', v_deleted_count, 
                   ', 保留天数: ', p_retention_days), 
            NOW());
    
    COMMIT;
    
    SELECT v_deleted_count as deleted_count, v_cutoff_date as cutoff_date;
END$$

DELIMITER ;

-- =====================================================
-- 第九步：创建索引优化建议
-- =====================================================

-- 分析表统计信息
ANALYZE TABLE `t_user_daily_usage`;

-- 检查索引使用情况的查询
-- （运行一段时间后可以执行以下查询来检查索引效果）
/*
-- 检查索引基数
SELECT 
    TABLE_NAME,
    INDEX_NAME,
    CARDINALITY,
    SUB_PART,
    NULLABLE
FROM information_schema.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_user_daily_usage'
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;

-- 检查表大小和行数
SELECT 
    TABLE_NAME,
    TABLE_ROWS,
    DATA_LENGTH,
    INDEX_LENGTH,
    (DATA_LENGTH + INDEX_LENGTH) as TOTAL_SIZE
FROM information_schema.TABLES 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME LIKE '%user_daily_usage%';
*/

-- =====================================================
-- 第十步：验证和清理
-- =====================================================

-- 验证新表结构
SHOW CREATE TABLE `t_user_daily_usage`;

-- 验证数据完整性
SELECT 
    COUNT(*) as total_records,
    COUNT(DISTINCT user_id) as unique_users,
    COUNT(DISTINCT model_name) as unique_models,
    MIN(usage_date) as earliest_date,
    MAX(usage_date) as latest_date
FROM `t_user_daily_usage`;

-- 记录优化完成日志
INSERT INTO `t_system_log` (`log_type`, `log_content`, `create_time`)
VALUES ('DATABASE_OPTIMIZATION', 
        '用户每日限制模块数据库优化完成：移除冗余字段，优化索引结构，创建存储过程和视图', 
        NOW());

-- 恢复外键检查
SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================
-- 优化完成提示
-- =====================================================
SELECT 
    '数据库优化完成' as status,
    '请检查应用程序兼容性' as next_step,
    '建议运行性能测试验证优化效果' as recommendation;

