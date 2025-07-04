-- 简化版索引创建脚本
-- 创建时间: 2025-06-18 08:16:49 (北京时间)
-- 版本: v1.0
-- 目的: 安全地创建必要的索引，避免语法错误

-- 1. 检查当前表结构
SELECT 'Checking table structure...' as step;
DESCRIBE t_user_daily_usage;

-- 2. 检查现有索引
SELECT 'Current indexes:' as step;
SHOW INDEX FROM t_user_daily_usage;

-- 3. 检查唯一索引状态
SELECT 'Checking unique index status...' as step;
SELECT 
    CASE 
        WHEN COUNT(*) > 0 THEN 'Unique index uk_user_package_date exists'
        ELSE 'Unique index uk_user_package_date does not exist'
    END as unique_index_status
FROM information_schema.statistics 
WHERE table_schema = DATABASE() 
  AND table_name = 't_user_daily_usage' 
  AND index_name = 'uk_user_package_date';

-- 4. 安全创建唯一索引
SET @index_exists = (
    SELECT COUNT(*) 
    FROM information_schema.statistics 
    WHERE table_schema = DATABASE() 
    AND table_name = 't_user_daily_usage' 
    AND index_name = 'uk_user_package_date'
);

SET @sql = CASE 
    WHEN @index_exists = 0 THEN 
        'ALTER TABLE t_user_daily_usage ADD UNIQUE INDEX uk_user_package_date (user_id, package_id, usage_date)'
    ELSE 
        'SELECT "Unique index uk_user_package_date already exists" as message'
END;

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5. 验证唯一索引创建结果
SELECT 'Verifying unique index...' as step;
SELECT 
    CASE 
        WHEN COUNT(*) > 0 THEN 'Unique index verification SUCCESS'
        ELSE 'Unique index verification FAILED'
    END as verification_result
FROM information_schema.statistics 
WHERE table_schema = DATABASE() 
  AND table_name = 't_user_daily_usage' 
  AND index_name = 'uk_user_package_date';

-- 6. 创建性能优化索引 - usage_date
SET @usage_date_index_exists = (
    SELECT COUNT(*) 
    FROM information_schema.statistics 
    WHERE table_schema = DATABASE() 
    AND table_name = 't_user_daily_usage' 
    AND index_name = 'idx_usage_date'
);

SET @sql_usage_date = CASE 
    WHEN @usage_date_index_exists = 0 THEN 
        'CREATE INDEX idx_usage_date ON t_user_daily_usage(usage_date)'
    ELSE 
        'SELECT "Index idx_usage_date already exists" as message'
END;

PREPARE stmt FROM @sql_usage_date;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 7. 创建性能优化索引 - last_chat_time
SET @last_chat_index_exists = (
    SELECT COUNT(*) 
    FROM information_schema.statistics 
    WHERE table_schema = DATABASE() 
    AND table_name = 't_user_daily_usage' 
    AND index_name = 'idx_last_chat_time'
);

SET @sql_last_chat = CASE 
    WHEN @last_chat_index_exists = 0 THEN 
        'CREATE INDEX idx_last_chat_time ON t_user_daily_usage(last_chat_time)'
    ELSE 
        'SELECT "Index idx_last_chat_time already exists" as message'
END;

PREPARE stmt FROM @sql_last_chat;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 8. 最终索引状态检查
SELECT 'Final index status:' as step;
SHOW INDEX FROM t_user_daily_usage;

-- 9. 验证所有必要索引
SELECT 'Index verification summary:' as step;
SELECT 
    index_name,
    CASE 
        WHEN index_name = 'uk_user_package_date' THEN 'Unique index for atomic operations'
        WHEN index_name = 'idx_usage_date' THEN 'Date query optimization index'
        WHEN index_name = 'idx_last_chat_time' THEN 'Time query optimization index'
        ELSE 'Other index'
    END as description
FROM information_schema.statistics 
WHERE table_schema = DATABASE() 
  AND table_name = 't_user_daily_usage' 
  AND index_name IN ('uk_user_package_date', 'idx_usage_date', 'idx_last_chat_time')
ORDER BY index_name;

-- 10. 时区信息检查
SELECT 'Timezone information:' as step;
SELECT 
    @@global.time_zone as global_timezone,
    @@session.time_zone as session_timezone,
    NOW() as current_db_time,
    CONVERT_TZ(NOW(), @@session.time_zone, '+08:00') as beijing_time;

-- 11. 表统计信息
SELECT 'Table statistics:' as step;
SELECT 
    COUNT(*) as total_records,
    COUNT(DISTINCT user_id) as unique_users,
    COUNT(DISTINCT usage_date) as unique_dates,
    MIN(usage_date) as earliest_date,
    MAX(usage_date) as latest_date
FROM t_user_daily_usage;

-- 12. 数据一致性检查
SELECT 'Data consistency check:' as step;
SELECT 
    COUNT(*) as total_records,
    COUNT(CASE WHEN chat_count_used > daily_limit THEN 1 END) as over_limit_records,
    COUNT(CASE WHEN chat_count_used < 0 THEN 1 END) as negative_records,
    COUNT(CASE WHEN daily_limit <= 0 THEN 1 END) as invalid_limit_records
FROM t_user_daily_usage;

-- 13. 完成消息
SELECT 'Index creation completed successfully!' as completion_status;
SELECT 'Next steps:' as next_steps;
SELECT '1. Recompile Java project: mvn clean compile' as step1;
SELECT '2. Test atomic operation functionality' as step2;
SELECT '3. Monitor application logs' as step3;
