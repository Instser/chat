-- =====================================================
-- 代码适配数据库优化修复脚本
-- 创建时间: 2025-07-07 23:30:00
-- 版本: v1.0
-- 说明: 修复代码与优化后数据库结构的兼容性问题
-- =====================================================

-- 设置SQL模式和字符集
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================
-- 问题说明
-- =====================================================
/*
错误信息：
Unknown column 'package_id' in 'field list'

原因：
1. 数据库表结构已优化，移除了package_id和daily_limit字段
2. 但代码中的Mapper XML和Service层仍在使用这些字段
3. 需要更新代码以适配新的表结构

已修复的文件：
1. src/main/resources/mapper/UserDailyUsageMapper.xml
2. src/main/java/com/instser/chat/entity/UserDailyUsage.java
3. src/main/java/com/instser/chat/dao/UserDailyUsageDao.java
4. src/main/java/com/instser/chat/service/serviceimpl/UserDailyUsageServiceImpl.java
*/

-- =====================================================
-- 验证当前表结构
-- =====================================================

-- 检查表结构是否正确
DESCRIBE t_user_daily_usage;

-- 验证字段是否存在
SELECT 
    COLUMN_NAME,
    DATA_TYPE,
    IS_NULLABLE,
    COLUMN_DEFAULT,
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_user_daily_usage'
ORDER BY ORDINAL_POSITION;

-- =====================================================
-- 数据验证查询
-- =====================================================

-- 检查数据完整性
SELECT 
    COUNT(*) as total_records,
    COUNT(DISTINCT user_id) as unique_users,
    COUNT(DISTINCT model_name) as unique_models,
    MIN(usage_date) as earliest_date,
    MAX(usage_date) as latest_date,
    SUM(chat_count_used) as total_usage
FROM t_user_daily_usage;

-- 检查是否有空值
SELECT 
    'user_id空值' as check_type, COUNT(*) as count_value
FROM t_user_daily_usage WHERE user_id IS NULL
UNION ALL
SELECT 
    'model_name空值' as check_type, COUNT(*) as count_value
FROM t_user_daily_usage WHERE model_name IS NULL OR model_name = ''
UNION ALL
SELECT 
    'usage_date空值' as check_type, COUNT(*) as count_value
FROM t_user_daily_usage WHERE usage_date IS NULL;

-- 检查重复数据
SELECT 
    user_id, 
    model_name, 
    usage_date, 
    COUNT(*) as duplicate_count
FROM t_user_daily_usage
GROUP BY user_id, model_name, usage_date
HAVING COUNT(*) > 1;

-- =====================================================
-- 测试新的查询方式
-- =====================================================

-- 测试按用户和模型查询（新方式）
SELECT 
    id,
    user_id,
    model_name,
    usage_date,
    chat_count_used,
    last_chat_time,
    create_time,
    update_time
FROM t_user_daily_usage
WHERE user_id = 1931350026773823490
  AND model_name = 'STANDARD'
  AND usage_date = CURDATE()
LIMIT 1;

-- 测试插入或更新操作（新方式）
INSERT INTO t_user_daily_usage
(user_id, model_name, usage_date, chat_count_used, last_chat_time, create_time, update_time)
VALUES (999999999, 'TEST_MODEL', CURDATE(), 1, NOW(), NOW(), NOW())
ON DUPLICATE KEY UPDATE
    chat_count_used = chat_count_used + 1,
    last_chat_time = NOW(),
    update_time = NOW();

-- 清理测试数据
DELETE FROM t_user_daily_usage 
WHERE user_id = 999999999 AND model_name = 'TEST_MODEL';

-- =====================================================
-- 性能测试查询
-- =====================================================

-- 测试新索引性能
EXPLAIN SELECT * FROM t_user_daily_usage 
WHERE user_id = 1931350026773823490 
  AND model_name = 'STANDARD' 
  AND usage_date = CURDATE();

-- 测试日期范围查询性能
EXPLAIN SELECT * FROM t_user_daily_usage 
WHERE usage_date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
ORDER BY usage_date DESC, user_id;

-- =====================================================
-- 创建测试数据（可选）
-- =====================================================

-- 如果需要测试数据，可以执行以下语句
/*
INSERT INTO t_user_daily_usage 
(user_id, model_name, usage_date, chat_count_used, last_chat_time, create_time, update_time)
VALUES 
(1001, 'STANDARD', CURDATE(), 5, NOW(), NOW(), NOW()),
(1001, 'PERFORMANCE', CURDATE(), 2, NOW(), NOW(), NOW()),
(1001, 'BETA_MODEL', CURDATE(), 1, NOW(), NOW(), NOW()),
(1002, 'STANDARD', CURDATE(), 10, NOW(), NOW(), NOW()),
(1002, 'PERFORMANCE', CURDATE(), 3, NOW(), NOW(), NOW());
*/

-- =====================================================
-- 监控查询（用于观察系统运行状态）
-- =====================================================

-- 今日活跃用户统计
SELECT 
    model_name,
    COUNT(DISTINCT user_id) as active_users,
    SUM(chat_count_used) as total_usage,
    AVG(chat_count_used) as avg_usage_per_user
FROM t_user_daily_usage
WHERE usage_date = CURDATE()
GROUP BY model_name
ORDER BY total_usage DESC;

-- 最近7天使用趋势
SELECT 
    usage_date,
    model_name,
    COUNT(DISTINCT user_id) as active_users,
    SUM(chat_count_used) as total_usage
FROM t_user_daily_usage
WHERE usage_date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
GROUP BY usage_date, model_name
ORDER BY usage_date DESC, model_name;

-- 用户使用排行榜（今日）
SELECT 
    user_id,
    model_name,
    chat_count_used,
    last_chat_time
FROM t_user_daily_usage
WHERE usage_date = CURDATE()
ORDER BY chat_count_used DESC
LIMIT 10;

-- =====================================================
-- 清理和维护
-- =====================================================

-- 分析表统计信息
ANALYZE TABLE t_user_daily_usage;

-- 检查表状态
SHOW TABLE STATUS LIKE 't_user_daily_usage';

-- 检查索引使用情况
SHOW INDEX FROM t_user_daily_usage;

-- =====================================================
-- 完成验证
-- =====================================================

SELECT 
    '数据库结构优化完成' as status,
    '代码适配修复完成' as code_status,
    '请重启应用程序测试' as next_step,
    NOW() as completion_time;

-- 恢复外键检查
SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================
-- 注意事项
-- =====================================================
/*
1. 重启应用程序后测试以下功能：
   - 用户每日限制检查
   - 聊天使用记录
   - 剩余次数查询
   - 使用统计查询

2. 观察日志中是否还有相关错误

3. 如果仍有问题，检查以下文件是否正确更新：
   - UserDailyUsageMapper.xml
   - UserDailyUsage.java
   - UserDailyUsageDao.java
   - UserDailyUsageServiceImpl.java

4. 性能监控：
   - 查询响应时间
   - 数据库连接数
   - 缓存命中率

5. 数据一致性检查：
   - 定期检查重复数据
   - 验证使用统计准确性
*/
