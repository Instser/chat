/*
 MySQL 8.0 索引效果测试脚本 - 简化版
 
 兼容所有MySQL 8.0版本，验证索引效果
 创建时间: 2025-06-29 21:05 (北京时间)
 版本: v1.1 - 简化兼容版
*/

-- ========================================
-- 第一部分: 索引创建验证
-- ========================================

SELECT '📊 索引创建验证' AS test_section;

-- 验证新创建的索引
SELECT 
    table_name AS '表名',
    index_name AS '索引名',
    CASE 
        WHEN index_name LIKE '%_desc' THEN '✅ 降序索引'
        WHEN index_name LIKE '%_opt' THEN '✅ 查询优化索引'
        WHEN index_name LIKE '%_cover' THEN '✅ 覆盖索引'
        WHEN index_name LIKE '%_func' THEN '✅ 函数索引'
        WHEN index_name LIKE '%_stats' THEN '✅ 统计索引'
        WHEN index_name LIKE 'idx_test_%' THEN '✅ 测试索引'
        ELSE '✅ 关联查询索引'
    END AS '索引类型'
FROM information_schema.statistics 
WHERE table_schema = 'chat' 
  AND index_name IN (
    'idx_chat_msg_time_desc',
    'idx_session_active_desc', 
    'idx_user_order_time_desc',
    'idx_chat_msg_user_time_opt',
    'idx_session_user_active_opt',
    'idx_chat_msg_session_role',
    'idx_file_user_cover',
    'idx_order_user_cover',
    'idx_chat_msg_user_stats',
    'idx_chat_msg_date_func',
    'idx_order_date_func',
    'idx_usage_date_user',
    'idx_chat_msg_attachments_opt',
    'idx_file_session_time',
    'idx_chat_msg_model_stats',
    'idx_test_msg_status',
    'idx_test_file_type'
  )
GROUP BY table_name, index_name
ORDER BY table_name, index_name;

-- 统计创建的索引数量
SELECT 
    COUNT(*) AS '新索引总数',
    CASE 
        WHEN COUNT(*) = 17 THEN '✅ 所有索引创建成功'
        WHEN COUNT(*) > 0 THEN CONCAT('⚠️ 部分索引创建成功，共', COUNT(*), '个')
        ELSE '❌ 索引创建失败'
    END AS '创建状态'
FROM information_schema.statistics 
WHERE table_schema = 'chat' 
  AND index_name IN (
    'idx_chat_msg_time_desc', 'idx_session_active_desc', 'idx_user_order_time_desc',
    'idx_chat_msg_user_time_opt', 'idx_session_user_active_opt', 'idx_chat_msg_session_role',
    'idx_file_user_cover', 'idx_order_user_cover', 'idx_chat_msg_user_stats',
    'idx_chat_msg_date_func', 'idx_order_date_func', 'idx_usage_date_user',
    'idx_chat_msg_attachments_opt', 'idx_file_session_time', 'idx_chat_msg_model_stats',
    'idx_test_msg_status', 'idx_test_file_type'
  );

-- ========================================
-- 第二部分: 关键查询性能测试
-- ========================================

SELECT '🚀 关键查询性能测试' AS test_section;

-- 测试1: 最新消息查询 (应该使用 idx_chat_msg_time_desc)
SELECT '测试1: 最新消息查询 - 应该使用 idx_chat_msg_time_desc' AS test_info;
EXPLAIN 
SELECT * FROM t_chat_message 
WHERE deleted = 0 
ORDER BY create_time DESC 
LIMIT 20;

-- 测试2: 用户聊天记录查询 (应该使用 idx_chat_msg_user_time_opt)
SELECT '测试2: 用户聊天记录查询 - 应该使用 idx_chat_msg_user_time_opt' AS test_info;
EXPLAIN 
SELECT * FROM t_chat_message 
WHERE user_id = 1 AND deleted = 0 
ORDER BY create_time DESC 
LIMIT 20;

-- 测试3: 用户会话列表查询 (应该使用 idx_session_user_active_opt)
SELECT '测试3: 用户会话列表查询 - 应该使用 idx_session_user_active_opt' AS test_info;
EXPLAIN 
SELECT * FROM t_chat_session 
WHERE user_id = 1 AND deleted = 0 
ORDER BY last_active_time DESC 
LIMIT 10;

-- 测试4: 文件列表查询 (应该使用 idx_file_user_cover 覆盖索引)
SELECT '测试4: 文件列表查询 - 应该使用 idx_file_user_cover 覆盖索引' AS test_info;
EXPLAIN 
SELECT file_name, file_type, create_time 
FROM t_file_upload 
WHERE user_id = 1 AND deleted = 0;

-- 测试5: 每日消息统计 (应该使用 idx_chat_msg_date_func 函数索引)
SELECT '测试5: 每日消息统计 - 应该使用 idx_chat_msg_date_func 函数索引' AS test_info;
EXPLAIN 
SELECT DATE(create_time) as msg_date, COUNT(*) as msg_count 
FROM t_chat_message 
WHERE user_id = 1 
GROUP BY DATE(create_time) 
ORDER BY msg_date DESC;

-- ========================================
-- 第三部分: 实际数据测试
-- ========================================

SELECT '📊 实际数据测试' AS test_section;

-- 获取一个真实的用户ID进行测试
SET @test_user_id = (SELECT id FROM t_user WHERE deleted = 0 LIMIT 1);
SET @test_session_id = (SELECT session_id FROM t_chat_session WHERE deleted = 0 LIMIT 1);

SELECT CONCAT('使用测试用户ID: ', IFNULL(@test_user_id, '无数据')) AS test_user_info;
SELECT CONCAT('使用测试会话ID: ', IFNULL(@test_session_id, '无数据')) AS test_session_info;

-- 如果有数据，执行实际测试
-- 测试用户消息查询性能
SELECT '实际测试: 用户消息查询' AS actual_test;
SELECT 
    COUNT(*) as total_messages,
    MAX(create_time) as latest_message,
    MIN(create_time) as earliest_message
FROM t_chat_message 
WHERE user_id = IFNULL(@test_user_id, 1) AND deleted = 0;

-- 测试用户会话查询性能
SELECT '实际测试: 用户会话查询' AS actual_test;
SELECT 
    COUNT(*) as total_sessions,
    MAX(last_active_time) as latest_active,
    MIN(create_time) as earliest_session
FROM t_chat_session 
WHERE user_id = IFNULL(@test_user_id, 1) AND deleted = 0;

-- ========================================
-- 第四部分: 性能基准测试
-- ========================================

SELECT '⚡ 性能基准测试' AS test_section;

-- 启用性能分析
SET profiling = 1;

-- 执行关键查询
SELECT '执行性能基准测试...' AS status;

-- 查询1: 最新消息 (测试降序索引)
SELECT COUNT(*) FROM (
    SELECT * FROM t_chat_message 
    WHERE deleted = 0 
    ORDER BY create_time DESC 
    LIMIT 20
) t;

-- 查询2: 用户消息 (测试复合索引)
SELECT COUNT(*) FROM (
    SELECT * FROM t_chat_message 
    WHERE user_id = IFNULL(@test_user_id, 1) AND deleted = 0 
    ORDER BY create_time DESC 
    LIMIT 20
) t;

-- 查询3: 文件列表 (测试覆盖索引)
SELECT COUNT(*) FROM (
    SELECT file_name, file_type, create_time 
    FROM t_file_upload 
    WHERE user_id = IFNULL(@test_user_id, 1) AND deleted = 0
) t;

-- 显示性能分析结果
SELECT '性能测试结果 (Duration越小越好):' AS performance_title;
SHOW PROFILES;

-- 关闭性能分析
SET profiling = 0;

-- ========================================
-- 第五部分: 索引使用情况分析
-- ========================================

SELECT '📈 索引使用情况分析' AS analysis_section;

-- 显示表的索引信息
SELECT 
    table_name AS '表名',
    COUNT(*) AS '索引数量',
    GROUP_CONCAT(DISTINCT index_name ORDER BY index_name) AS '索引列表'
FROM information_schema.statistics 
WHERE table_schema = 'chat' 
  AND table_name IN ('t_chat_message', 't_chat_session', 't_file_upload', 't_user_order', 't_user_daily_usage')
GROUP BY table_name
ORDER BY table_name;

-- 显示新创建索引的详细信息
SELECT 
    table_name AS '表名',
    index_name AS '索引名',
    GROUP_CONCAT(column_name ORDER BY seq_in_index) AS '索引字段',
    index_type AS '索引类型'
FROM information_schema.statistics 
WHERE table_schema = 'chat' 
  AND index_name LIKE 'idx_%'
  AND index_name IN (
    'idx_chat_msg_time_desc', 'idx_session_active_desc', 'idx_user_order_time_desc',
    'idx_chat_msg_user_time_opt', 'idx_session_user_active_opt', 'idx_chat_msg_session_role',
    'idx_file_user_cover', 'idx_order_user_cover', 'idx_chat_msg_user_stats',
    'idx_chat_msg_date_func', 'idx_order_date_func', 'idx_usage_date_user',
    'idx_chat_msg_attachments_opt', 'idx_file_session_time', 'idx_chat_msg_model_stats'
  )
GROUP BY table_name, index_name, index_type
ORDER BY table_name, index_name;

-- ========================================
-- 第六部分: 测试总结和建议
-- ========================================

SELECT '🎉 索引测试完成!' AS completion_status;
SELECT CONCAT('测试完成时间: ', NOW()) AS completion_time;

SELECT '📋 结果分析指南:' AS analysis_guide;
SELECT '1. 查看EXPLAIN结果中的key字段，确认使用了预期的索引' AS guide1;
SELECT '2. type字段: const/eq_ref/ref > range > index > ALL' AS guide2;
SELECT '3. rows字段: 数值越小表示扫描行数越少，性能越好' AS guide3;
SELECT '4. Extra字段: "Using index"表示使用了覆盖索引(最优)' AS guide4;
SELECT '5. SHOW PROFILES中Duration越小表示查询越快' AS guide5;

SELECT '🚀 预期性能提升:' AS performance_expectations;
SELECT '• 用户聊天记录查询: 70-90%提升' AS perf1;
SELECT '• 会话列表查询: 60-80%提升' AS perf2;
SELECT '• 文件列表查询: 40-60%提升' AS perf3;
SELECT '• 日期统计查询: 80-95%提升' AS perf4;
SELECT '• 最新消息查询: 50-70%提升' AS perf5;

SELECT '✅ 建议重启应用以获得最佳性能表现!' AS final_recommendation;
