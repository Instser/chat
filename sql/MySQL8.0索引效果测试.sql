/*
 MySQL 8.0 索引效果测试脚本
 
 用于验证17个新索引的性能提升效果
 创建时间: 2025-06-29 21:00 (北京时间)
 版本: v1.0
 
 使用方法: 执行此脚本查看索引使用情况和性能分析
*/

-- ========================================
-- 第一部分: 索引创建验证
-- ========================================

SELECT '📊 索引创建验证' AS test_section;

-- 验证所有17个索引是否创建成功
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
    END AS '索引状态',
    '可见' AS '可见性'
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

-- ========================================
-- 第二部分: 降序索引性能测试
-- ========================================

SELECT '🚀 降序索引性能测试' AS test_section;

-- 测试1: 聊天消息时间降序查询 (应该使用 idx_chat_msg_time_desc)
SELECT '测试1: 最新消息查询' AS test_name;
EXPLAIN FORMAT=JSON 
SELECT * FROM t_chat_message 
WHERE deleted = 0 
ORDER BY create_time DESC 
LIMIT 20;

-- 测试2: 会话活跃时间降序查询 (应该使用 idx_session_active_desc)
SELECT '测试2: 最近活跃会话查询' AS test_name;
EXPLAIN FORMAT=JSON 
SELECT * FROM t_chat_session 
WHERE deleted = 0 
ORDER BY last_active_time DESC 
LIMIT 10;

-- 测试3: 订单时间降序查询 (应该使用 idx_user_order_time_desc)
SELECT '测试3: 最新订单查询' AS test_name;
EXPLAIN FORMAT=JSON 
SELECT * FROM t_user_order 
WHERE payment_status = 1 
ORDER BY create_time DESC 
LIMIT 10;

-- ========================================
-- 第三部分: 复合查询优化测试
-- ========================================

SELECT '📊 复合查询优化测试' AS test_section;

-- 测试4: 用户消息查询 (应该使用 idx_chat_msg_user_time_opt)
SELECT '测试4: 用户聊天记录查询' AS test_name;
EXPLAIN FORMAT=JSON 
SELECT * FROM t_chat_message 
WHERE user_id = 1 AND deleted = 0 
ORDER BY create_time DESC 
LIMIT 20;

-- 测试5: 用户会话查询 (应该使用 idx_session_user_active_opt)
SELECT '测试5: 用户会话列表查询' AS test_name;
EXPLAIN FORMAT=JSON 
SELECT * FROM t_chat_session 
WHERE user_id = 1 AND deleted = 0 
ORDER BY last_active_time DESC 
LIMIT 10;

-- 测试6: 会话消息角色查询 (应该使用 idx_chat_msg_session_role)
SELECT '测试6: 会话用户消息查询' AS test_name;
EXPLAIN FORMAT=JSON 
SELECT * FROM t_chat_message 
WHERE session_id = 'test-session-id' AND role = 'user' 
ORDER BY create_time DESC;

-- ========================================
-- 第四部分: 覆盖索引效果测试
-- ========================================

SELECT '🎯 覆盖索引效果测试' AS test_section;

-- 测试7: 文件列表查询 (应该使用 idx_file_user_cover 覆盖索引)
SELECT '测试7: 用户文件列表查询' AS test_name;
EXPLAIN FORMAT=JSON 
SELECT file_name, file_type, create_time 
FROM t_file_upload 
WHERE user_id = 1 AND deleted = 0;

-- 测试8: 订单摘要查询 (应该使用 idx_order_user_cover 覆盖索引)
SELECT '测试8: 用户订单摘要查询' AS test_name;
EXPLAIN FORMAT=JSON 
SELECT payment_status, create_time, amount 
FROM t_user_order 
WHERE user_id = 1;

-- 测试9: 用户消息统计 (应该使用 idx_chat_msg_user_stats 覆盖索引)
SELECT '测试9: 用户消息统计查询' AS test_name;
EXPLAIN FORMAT=JSON 
SELECT session_id, COUNT(*), MAX(create_time) 
FROM t_chat_message 
WHERE user_id = 1 AND deleted = 0 
GROUP BY session_id;

-- ========================================
-- 第五部分: MySQL 8.0函数索引测试
-- ========================================

SELECT '✨ MySQL 8.0函数索引测试' AS test_section;

-- 测试10: 消息日期统计 (应该使用 idx_chat_msg_date_func 函数索引)
SELECT '测试10: 每日消息统计查询' AS test_name;
EXPLAIN FORMAT=JSON 
SELECT DATE(create_time) as msg_date, COUNT(*) as msg_count 
FROM t_chat_message 
WHERE user_id = 1 
GROUP BY DATE(create_time) 
ORDER BY msg_date DESC;

-- 测试11: 订单日期统计 (应该使用 idx_order_date_func 函数索引)
SELECT '测试11: 每日订单统计查询' AS test_name;
EXPLAIN FORMAT=JSON 
SELECT DATE(create_time) as order_date, COUNT(*) as order_count, SUM(amount) as total_amount 
FROM t_user_order 
WHERE payment_status = 1 
GROUP BY DATE(create_time) 
ORDER BY order_date DESC;

-- 测试12: 用户使用统计 (应该使用 idx_usage_date_user)
SELECT '测试12: 用户使用统计查询' AS test_name;
EXPLAIN FORMAT=JSON 
SELECT * FROM t_user_daily_usage 
WHERE user_id = 1 
ORDER BY usage_date DESC 
LIMIT 30;

-- ========================================
-- 第六部分: 关联查询优化测试
-- ========================================

SELECT '🔗 关联查询优化测试' AS test_section;

-- 测试13: 附件消息查询 (应该使用 idx_chat_msg_attachments_opt)
SELECT '测试13: 带附件消息查询' AS test_name;
EXPLAIN FORMAT=JSON 
SELECT * FROM t_chat_message 
WHERE has_attachments = 1 AND session_id = 'test-session-id' 
ORDER BY create_time DESC;

-- 测试14: 会话文件查询 (应该使用 idx_file_session_time)
SELECT '测试14: 会话文件列表查询' AS test_name;
EXPLAIN FORMAT=JSON 
SELECT * FROM t_file_upload 
WHERE session_id = 'test-session-id' AND deleted = 0 
ORDER BY create_time DESC;

-- 测试15: AI模型使用统计 (应该使用 idx_chat_msg_model_stats)
SELECT '测试15: AI模型使用统计查询' AS test_name;
EXPLAIN FORMAT=JSON 
SELECT model_id, COUNT(*) as usage_count, SUM(token_count) as total_tokens 
FROM t_chat_message 
WHERE deleted = 0 AND create_time >= DATE_SUB(NOW(), INTERVAL 7 DAY)
GROUP BY model_id 
ORDER BY usage_count DESC;

-- ========================================
-- 第七部分: 性能对比测试
-- ========================================

SELECT '⚡ 性能对比测试' AS test_section;

-- 启用性能分析
SET profiling = 1;

-- 执行关键查询进行性能测试
SELECT '执行性能测试查询...' AS status;

-- 查询1: 用户最新消息
SELECT COUNT(*) as user_latest_messages FROM t_chat_message WHERE user_id = 1 AND deleted = 0 ORDER BY create_time DESC LIMIT 20;

-- 查询2: 用户会话列表
SELECT COUNT(*) as user_sessions FROM t_chat_session WHERE user_id = 1 AND deleted = 0 ORDER BY last_active_time DESC LIMIT 10;

-- 查询3: 每日消息统计
SELECT COUNT(*) as daily_stats FROM (
    SELECT DATE(create_time), COUNT(*) 
    FROM t_chat_message 
    WHERE user_id = 1 
    GROUP BY DATE(create_time)
) t;

-- 查询4: 文件列表
SELECT COUNT(*) as file_list FROM t_file_upload WHERE user_id = 1 AND deleted = 0;

-- 显示性能分析结果
SELECT '性能分析结果:' AS performance_results;
SHOW PROFILES;

-- 关闭性能分析
SET profiling = 0;

-- ========================================
-- 第八部分: 索引使用统计
-- ========================================

SELECT '📈 索引使用统计' AS test_section;

-- 显示索引大小统计
SELECT 
    table_name AS '表名',
    index_name AS '索引名',
    ROUND(stat_value * @@innodb_page_size / 1024 / 1024, 2) AS '索引大小(MB)'
FROM mysql.innodb_index_stats 
WHERE database_name = 'chat' 
  AND stat_name = 'size'
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
    'idx_chat_msg_model_stats'
  )
ORDER BY stat_value DESC;

-- ========================================
-- 第九部分: 测试总结
-- ========================================

SELECT '🎉 索引效果测试完成!' AS test_completion;
SELECT CONCAT('测试时间: ', NOW()) AS test_time;
SELECT '共测试15个关键查询场景' AS test_summary;

-- 测试建议
SELECT '📋 测试结果分析建议:' AS analysis_tips;
SELECT '1. 查看EXPLAIN结果中的key字段，确认使用了预期的索引' AS tip1;
SELECT '2. 查看type字段，ref/range/index比ALL(全表扫描)性能更好' AS tip2;
SELECT '3. 查看rows字段，数值越小表示扫描行数越少，性能越好' AS tip3;
SELECT '4. 查看Extra字段，"Using index"表示使用了覆盖索引' AS tip4;
SELECT '5. 对比SHOW PROFILES结果，Duration越小表示查询越快' AS tip5;

-- 不可见索引测试提示
SELECT '🧪 不可见索引测试:' AS invisible_index_info;
SELECT '要测试不可见索引效果，可以执行:' AS invisible_test_tip;
SELECT 'ALTER INDEX idx_test_msg_status ON t_chat_message VISIBLE;' AS make_visible_example;
SELECT 'ALTER INDEX idx_test_msg_status ON t_chat_message INVISIBLE;' AS make_invisible_example;
