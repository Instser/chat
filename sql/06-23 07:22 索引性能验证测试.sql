-- =====================================================
-- 索引性能验证测试脚本
-- =====================================================
-- 生成时间: 2025年6月23日 07:22 (北京时间)
-- 版本: v1.0
-- 项目: jinyu-chat 聊天系统
-- 目标: 验证索引优化效果，测试查询性能
-- =====================================================

-- 设置测试环境
SET NAMES utf8mb4;
SET profiling = 1;
SET profiling_history_size = 100;

-- =====================================================
-- 第一部分: 索引创建验证
-- =====================================================

-- 1. 检查所有新创建的索引
SELECT 
    '=== 新创建的索引列表 ===' AS info;

SELECT 
    TABLE_NAME AS '表名',
    INDEX_NAME AS '索引名',
    GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS '索引字段',
    INDEX_TYPE AS '索引类型',
    INDEX_COMMENT AS '索引说明'
FROM information_schema.STATISTICS 
WHERE TABLE_SCHEMA = 'chat' 
  AND INDEX_NAME LIKE 'idx_%'
  AND INDEX_NAME NOT IN ('idx_session_id', 'idx_user_id', 'idx_user_date', 'idx_usage_date', 'idx_last_chat_time')
GROUP BY TABLE_NAME, INDEX_NAME
ORDER BY TABLE_NAME, INDEX_NAME;

-- 2. 检查索引大小和存储占用
SELECT 
    '=== 索引存储占用分析 ===' AS info;

SELECT 
    TABLE_NAME AS '表名',
    INDEX_NAME AS '索引名',
    ROUND(stat_value * @@innodb_page_size / 1024 / 1024, 2) AS '大小(MB)'
FROM mysql.innodb_index_stats 
WHERE database_name = 'chat' 
  AND stat_name = 'size'
  AND index_name LIKE 'idx_%'
ORDER BY stat_value DESC;

-- =====================================================
-- 第二部分: 查询性能测试
-- =====================================================

-- 测试前清理性能分析历史
SET profiling = 0;
SET profiling = 1;

SELECT 
    '=== 开始性能测试 ===' AS info;

-- 测试1: 会话消息加载性能测试
SELECT 
    '测试1: 会话消息加载 (应使用 idx_chat_msg_session_time)' AS test_name;

-- 获取一个真实的session_id进行测试
SET @test_session_id = (
    SELECT session_id 
    FROM t_chat_message 
    WHERE deleted = 0 
    LIMIT 1
);

-- 执行查询并分析执行计划
EXPLAIN FORMAT=JSON
SELECT * FROM t_chat_message 
WHERE session_id = @test_session_id AND deleted = 0 
ORDER BY create_time ASC 
LIMIT 50;

-- 执行实际查询测试性能
SELECT COUNT(*) AS message_count
FROM t_chat_message 
WHERE session_id = @test_session_id AND deleted = 0;

-- 测试2: 用户消息历史性能测试  
SELECT 
    '测试2: 用户消息历史 (应使用 idx_chat_msg_user_time)' AS test_name;

-- 获取一个真实的user_id进行测试
SET @test_user_id = (
    SELECT user_id 
    FROM t_chat_message 
    WHERE deleted = 0 
    LIMIT 1
);

-- 执行查询并分析执行计划
EXPLAIN FORMAT=JSON
SELECT * FROM t_chat_message 
WHERE user_id = @test_user_id AND deleted = 0 
ORDER BY create_time DESC 
LIMIT 20;

-- 执行实际查询测试性能
SELECT COUNT(*) AS user_message_count
FROM t_chat_message 
WHERE user_id = @test_user_id AND deleted = 0;

-- 测试3: 用户会话列表性能测试
SELECT 
    '测试3: 用户会话列表 (应使用 idx_chat_session_user_active)' AS test_name;

-- 执行查询并分析执行计划
EXPLAIN FORMAT=JSON
SELECT * FROM t_chat_session 
WHERE user_id = @test_user_id AND deleted = 0 
ORDER BY last_active_time DESC 
LIMIT 10;

-- 执行实际查询测试性能
SELECT COUNT(*) AS user_session_count
FROM t_chat_session 
WHERE user_id = @test_user_id AND deleted = 0;

-- 测试4: 用户订单查询性能测试
SELECT 
    '测试4: 用户订单查询 (应使用 idx_user_order_status_time)' AS test_name;

-- 执行查询并分析执行计划
EXPLAIN FORMAT=JSON
SELECT * FROM t_user_order 
WHERE user_id = @test_user_id AND payment_status = 'SUCCESS' 
ORDER BY create_time DESC;

-- 执行实际查询测试性能
SELECT COUNT(*) AS user_order_count
FROM t_user_order 
WHERE user_id = @test_user_id AND deleted = 0;

-- 测试5: 复合查询性能测试
SELECT 
    '测试5: 用户消息统计 (应使用 idx_chat_msg_user_date)' AS test_name;

-- 执行查询并分析执行计划
EXPLAIN FORMAT=JSON
SELECT COUNT(*) 
FROM t_chat_message 
WHERE user_id = @test_user_id 
  AND create_time >= DATE_SUB(NOW(), INTERVAL 30 DAY)
  AND deleted = 0;

-- 执行实际查询
SELECT 
    COUNT(*) AS recent_message_count,
    MIN(create_time) AS earliest_message,
    MAX(create_time) AS latest_message
FROM t_chat_message 
WHERE user_id = @test_user_id 
  AND create_time >= DATE_SUB(NOW(), INTERVAL 30 DAY)
  AND deleted = 0;

-- =====================================================
-- 第三部分: 性能分析结果
-- =====================================================

SELECT 
    '=== 查询性能分析结果 ===' AS info;

-- 显示所有查询的性能分析
SHOW PROFILES;

-- 获取最近几个查询的详细性能信息
SELECT 
    QUERY_ID,
    DURATION,
    QUERY
FROM information_schema.PROFILING 
WHERE QUERY NOT LIKE '%EXPLAIN%'
  AND QUERY NOT LIKE '%SELECT ''%'
ORDER BY QUERY_ID DESC 
LIMIT 10;

-- =====================================================
-- 第四部分: 索引使用情况分析
-- =====================================================

SELECT 
    '=== 索引使用情况分析 ===' AS info;

-- 检查索引的实际使用情况 (需要在有查询负载后执行)
SELECT 
    OBJECT_SCHEMA AS '数据库',
    OBJECT_NAME AS '表名',
    INDEX_NAME AS '索引名',
    COUNT_FETCH AS '读取次数',
    COUNT_INSERT AS '插入次数',
    COUNT_UPDATE AS '更新次数',
    COUNT_DELETE AS '删除次数'
FROM performance_schema.table_io_waits_summary_by_index_usage
WHERE OBJECT_SCHEMA = 'chat'
  AND INDEX_NAME LIKE 'idx_%'
ORDER BY COUNT_FETCH DESC;

-- =====================================================
-- 第五部分: 慢查询分析
-- =====================================================

SELECT 
    '=== 慢查询分析 ===' AS info;

-- 检查是否有慢查询 (需要开启慢查询日志)
SELECT 
    SCHEMA_NAME AS '数据库',
    DIGEST_TEXT AS '查询模式',
    COUNT_STAR AS '执行次数',
    ROUND(AVG_TIMER_WAIT/1000000000, 3) AS '平均耗时(秒)',
    ROUND(MAX_TIMER_WAIT/1000000000, 3) AS '最大耗时(秒)',
    ROUND(SUM_TIMER_WAIT/1000000000, 3) AS '总耗时(秒)',
    SUM_ROWS_EXAMINED AS '扫描行数',
    SUM_ROWS_SENT AS '返回行数'
FROM performance_schema.events_statements_summary_by_digest 
WHERE SCHEMA_NAME = 'chat'
  AND DIGEST_TEXT IS NOT NULL
ORDER BY AVG_TIMER_WAIT DESC
LIMIT 10;

-- =====================================================
-- 第六部分: 表统计信息
-- =====================================================

SELECT 
    '=== 表统计信息 ===' AS info;

-- 显示主要表的统计信息
SELECT 
    TABLE_NAME AS '表名',
    TABLE_ROWS AS '估计行数',
    ROUND(DATA_LENGTH/1024/1024, 2) AS '数据大小(MB)',
    ROUND(INDEX_LENGTH/1024/1024, 2) AS '索引大小(MB)',
    ROUND(INDEX_LENGTH/DATA_LENGTH*100, 2) AS '索引比例(%)',
    UPDATE_TIME AS '最后更新时间'
FROM information_schema.TABLES 
WHERE TABLE_SCHEMA = 'chat' 
  AND TABLE_TYPE = 'BASE TABLE'
ORDER BY DATA_LENGTH DESC;

-- =====================================================
-- 第七部分: 优化建议
-- =====================================================

SELECT 
    '=== 优化建议检查 ===' AS info;

-- 检查是否有未使用的索引
SELECT 
    t.TABLE_SCHEMA AS '数据库',
    t.TABLE_NAME AS '表名',
    t.INDEX_NAME AS '索引名',
    '可能未使用' AS '状态'
FROM information_schema.STATISTICS t
LEFT JOIN performance_schema.table_io_waits_summary_by_index_usage p
  ON t.TABLE_SCHEMA = p.OBJECT_SCHEMA 
  AND t.TABLE_NAME = p.OBJECT_NAME 
  AND t.INDEX_NAME = p.INDEX_NAME
WHERE t.TABLE_SCHEMA = 'chat'
  AND t.INDEX_NAME LIKE 'idx_%'
  AND p.INDEX_NAME IS NULL
ORDER BY t.TABLE_NAME, t.INDEX_NAME;

-- 检查重复或冗余的索引
SELECT 
    '检查重复索引' AS info;

SELECT 
    TABLE_NAME AS '表名',
    GROUP_CONCAT(INDEX_NAME) AS '可能重复的索引',
    GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS '索引字段'
FROM information_schema.STATISTICS 
WHERE TABLE_SCHEMA = 'chat'
GROUP BY TABLE_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)
HAVING COUNT(*) > 1;

-- =====================================================
-- 第八部分: 性能监控设置
-- =====================================================

SELECT 
    '=== 性能监控设置建议 ===' AS info;

-- 显示当前性能监控相关设置
SELECT 
    '当前慢查询设置:' AS setting_type,
    CONCAT('slow_query_log = ', @@slow_query_log) AS setting_value
UNION ALL
SELECT 
    '慢查询时间阈值:',
    CONCAT('long_query_time = ', @@long_query_time, ' 秒')
UNION ALL
SELECT 
    '查询缓存状态:',
    CONCAT('query_cache_type = ', @@query_cache_type)
UNION ALL
SELECT 
    'InnoDB缓冲池大小:',
    CONCAT('innodb_buffer_pool_size = ', ROUND(@@innodb_buffer_pool_size/1024/1024/1024, 2), ' GB');

-- =====================================================
-- 测试完成
-- =====================================================

SELECT 
    '=== 性能测试完成 ===' AS info,
    NOW() AS completion_time;

-- 关闭性能分析
SET profiling = 0;

-- =====================================================
-- 使用说明
-- =====================================================
/*
使用说明:
1. 在执行索引创建脚本后运行此验证脚本
2. 观察EXPLAIN输出，确认查询使用了正确的索引
3. 检查SHOW PROFILES的结果，对比优化前后的性能
4. 定期运行此脚本监控索引使用情况
5. 根据分析结果调整索引策略

关键指标:
- type应该是ref或range，避免ALL(全表扫描)
- rows应该显著减少
- Extra中应该避免Using filesort
- 查询时间应该在毫秒级别

如果发现问题:
1. 检查索引是否正确创建
2. 确认查询条件符合最左前缀原则
3. 考虑是否需要调整索引字段顺序
4. 检查统计信息是否需要更新
*/
