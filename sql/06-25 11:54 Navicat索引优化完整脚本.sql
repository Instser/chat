-- =====================================================
-- Navicat 索引优化完整脚本
-- =====================================================
-- 生成时间: 2025年6月25日 11:54 (北京时间)
-- 版本: v1.0
-- 项目: jinyu-chat 聊天系统
-- 适用工具: Navicat Premium/MySQL
-- 执行流程: 性能检测 → 索引优化 → 性能对比
-- =====================================================

-- 设置执行环境
SET NAMES utf8mb4;
SET SESSION sql_mode = 'STRICT_TRANS_TABLES,NO_ZERO_DATE,NO_ZERO_IN_DATE,ERROR_FOR_DIVISION_BY_ZERO';

-- =====================================================
-- 第一阶段: 优化前性能基线检测
-- =====================================================

SELECT '🔍 ===== 开始优化前性能基线检测 =====' AS step_info, NOW() AS start_time;

-- 1. 检查当前数据库基本信息
SELECT '📊 数据库基本信息检查' AS check_type;
SELECT
    '数据库版本' AS info_type,
    VERSION() AS value,
    '确认MySQL版本兼容性' AS description
UNION ALL
SELECT
    '当前数据库' AS info_type,
    DATABASE() AS value,
    '确认操作正确的数据库' AS description
UNION ALL
SELECT
    '当前时间' AS info_type,
    CAST(NOW() AS CHAR) AS value,
    '记录优化开始时间' AS description;

-- 2. 检查表数据量和存储情况
SELECT '📈 表数据量统计' AS check_type;
SELECT 
    TABLE_NAME AS '表名',
    TABLE_ROWS AS '估计行数',
    ROUND(DATA_LENGTH/1024/1024, 2) AS '数据大小(MB)',
    ROUND(INDEX_LENGTH/1024/1024, 2) AS '索引大小(MB)',
    ROUND((DATA_LENGTH + INDEX_LENGTH)/1024/1024, 2) AS '总大小(MB)',
    UPDATE_TIME AS '最后更新时间'
FROM information_schema.TABLES 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_TYPE = 'BASE TABLE'
  AND TABLE_NAME IN ('t_chat_message', 't_chat_session', 't_user_order', 't_user_account', 't_package')
ORDER BY DATA_LENGTH DESC;

-- 3. 检查当前索引情况
SELECT '🔑 当前索引情况' AS check_type;
SELECT 
    TABLE_NAME AS '表名',
    INDEX_NAME AS '索引名',
    GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS '索引字段',
    INDEX_TYPE AS '索引类型',
    CASE WHEN NON_UNIQUE = 0 THEN '唯一' ELSE '非唯一' END AS '索引类型_详细'
FROM information_schema.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('t_chat_message', 't_chat_session', 't_user_order', 't_user_account', 't_package')
GROUP BY TABLE_NAME, INDEX_NAME
ORDER BY TABLE_NAME, INDEX_NAME;

-- 4. 获取测试数据样本
SELECT '🎯 获取测试数据样本' AS check_type;

-- 获取真实的测试数据ID
SET @test_session_id = (
    SELECT session_id 
    FROM t_chat_message 
    WHERE deleted = 0 
    ORDER BY create_time DESC 
    LIMIT 1
);

SET @test_user_id = (
    SELECT user_id 
    FROM t_chat_message 
    WHERE deleted = 0 
    ORDER BY create_time DESC 
    LIMIT 1
);

SELECT
    '测试会话ID' AS sample_type,
    CAST(@test_session_id AS CHAR) AS sample_value
UNION ALL
SELECT
    '测试用户ID' AS sample_type,
    CAST(@test_user_id AS CHAR) AS sample_value;

-- 5. 优化前性能测试
SELECT '⏱️ 优化前性能基线测试' AS check_type;

-- 开启性能分析
SET profiling = 1;

-- 测试1: 会话消息查询 (预期慢查询)
SELECT '测试1: 会话消息查询' AS test_name;
SELECT COUNT(*) AS message_count
FROM t_chat_message 
WHERE session_id = @test_session_id AND deleted = 0;

-- 测试2: 用户消息历史查询 (预期慢查询)
SELECT '测试2: 用户消息历史查询' AS test_name;
SELECT COUNT(*) AS user_message_count
FROM t_chat_message 
WHERE user_id = @test_user_id AND deleted = 0;

-- 测试3: 用户会话列表查询 (预期慢查询)
SELECT '测试3: 用户会话列表查询' AS test_name;
SELECT COUNT(*) AS user_session_count
FROM t_chat_session 
WHERE user_id = @test_user_id AND deleted = 0;

-- 测试4: 用户订单查询 (预期慢查询)
SELECT '测试4: 用户订单查询' AS test_name;
SELECT COUNT(*) AS user_order_count
FROM t_user_order 
WHERE user_id = @test_user_id AND deleted = 0;

-- 记录优化前的性能数据
SELECT '📋 优化前性能基线记录' AS check_type;
SHOW PROFILES;

-- 创建临时表保存优化前的性能数据
CREATE TEMPORARY TABLE IF NOT EXISTS performance_before (
    test_name VARCHAR(100),
    duration DECIMAL(10,6),
    query_text TEXT,
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 插入优化前的性能数据
INSERT INTO performance_before (test_name, duration, query_text)
SELECT 
    CONCAT('优化前-', 
        CASE 
            WHEN QUERY_ID = (SELECT MAX(QUERY_ID) - 3 FROM information_schema.PROFILING) THEN '会话消息查询'
            WHEN QUERY_ID = (SELECT MAX(QUERY_ID) - 2 FROM information_schema.PROFILING) THEN '用户消息历史查询'
            WHEN QUERY_ID = (SELECT MAX(QUERY_ID) - 1 FROM information_schema.PROFILING) THEN '用户会话列表查询'
            WHEN QUERY_ID = (SELECT MAX(QUERY_ID) FROM information_schema.PROFILING) THEN '用户订单查询'
        END
    ) AS test_name,
    DURATION,
    QUERY
FROM information_schema.PROFILING 
WHERE QUERY LIKE '%COUNT(*)%' 
  AND QUERY NOT LIKE '%information_schema%'
ORDER BY QUERY_ID DESC 
LIMIT 4;

-- 关闭性能分析
SET profiling = 0;

-- =====================================================
-- 第二阶段: 执行索引优化
-- =====================================================

SELECT '🚀 ===== 开始执行索引优化 =====' AS step_info, NOW() AS optimization_start_time;

-- 1. t_chat_message 表索引优化
SELECT '🔧 优化 t_chat_message 表索引' AS optimization_type;

-- 索引1: 会话消息时间查询索引 (最高优先级)
DROP INDEX IF EXISTS idx_chat_msg_session_time ON t_chat_message;
CREATE INDEX idx_chat_msg_session_time ON t_chat_message(session_id, create_time ASC, deleted) 
COMMENT '会话消息时间查询索引-用于加载会话消息列表';

-- 索引2: 用户消息时间查询索引 (高优先级)
DROP INDEX IF EXISTS idx_chat_msg_user_time ON t_chat_message;
CREATE INDEX idx_chat_msg_user_time ON t_chat_message(user_id, create_time DESC, deleted) 
COMMENT '用户消息时间查询索引-用于用户消息历史查询';

-- 索引3: 用户消息统计查询索引 (中优先级)
DROP INDEX IF EXISTS idx_chat_msg_user_date ON t_chat_message;
CREATE INDEX idx_chat_msg_user_date ON t_chat_message(user_id, deleted, create_time) 
COMMENT '用户消息统计查询索引-用于消息数量统计';

-- 2. t_chat_session 表索引优化
SELECT '🔧 优化 t_chat_session 表索引' AS optimization_type;

-- 索引4: 用户会话活跃时间查询索引 (高优先级)
DROP INDEX IF EXISTS idx_chat_session_user_active ON t_chat_session;
CREATE INDEX idx_chat_session_user_active ON t_chat_session(user_id, last_active_time DESC, deleted) 
COMMENT '用户会话活跃时间查询索引-用于会话列表查询';

-- 3. t_user_order 表索引优化
SELECT '🔧 优化 t_user_order 表索引' AS optimization_type;

-- 索引5: 用户订单状态时间查询索引 (高优先级)
DROP INDEX IF EXISTS idx_user_order_status_time ON t_user_order;
CREATE INDEX idx_user_order_status_time ON t_user_order(user_id, payment_status, create_time DESC) 
COMMENT '用户订单状态时间查询索引-用于订单管理查询';

-- 索引6: 订单时间状态统计索引 (中优先级)
DROP INDEX IF EXISTS idx_user_order_time_status ON t_user_order;
CREATE INDEX idx_user_order_time_status ON t_user_order(create_time DESC, payment_status) 
COMMENT '订单时间状态统计索引-用于订单统计分析';

-- 4. 其他辅助索引
SELECT '🔧 创建辅助优化索引' AS optimization_type;

-- 索引7: 会话消息角色统计索引
DROP INDEX IF EXISTS idx_chat_msg_session_role ON t_chat_message;
CREATE INDEX idx_chat_msg_session_role ON t_chat_message(session_id, role, deleted) 
COMMENT '会话消息角色统计索引-用于消息统计';

-- 索引8: 模型消息查询索引
DROP INDEX IF EXISTS idx_chat_msg_model_time ON t_chat_message;
CREATE INDEX idx_chat_msg_model_time ON t_chat_message(model_id, create_time DESC, deleted) 
COMMENT '模型消息查询索引-用于模型使用统计';

-- 5. 更新表统计信息
SELECT '📊 更新表统计信息' AS optimization_type;
ANALYZE TABLE t_chat_message, t_chat_session, t_user_order, t_user_account, t_package;

-- =====================================================
-- 第三阶段: 优化后性能验证
-- =====================================================

SELECT '✅ ===== 开始优化后性能验证 =====' AS step_info, NOW() AS verification_start_time;

-- 1. 检查新创建的索引
SELECT '🔍 检查新创建的索引' AS check_type;
SELECT 
    TABLE_NAME AS '表名',
    INDEX_NAME AS '索引名',
    GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS '索引字段',
    INDEX_COMMENT AS '索引说明'
FROM information_schema.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE()
  AND INDEX_NAME LIKE 'idx_%'
  AND INDEX_NAME NOT IN ('idx_session_id', 'idx_user_id', 'idx_user_date', 'idx_usage_date', 'idx_last_chat_time')
GROUP BY TABLE_NAME, INDEX_NAME
ORDER BY TABLE_NAME, INDEX_NAME;

-- 2. 优化后性能测试
SELECT '⚡ 优化后性能测试' AS check_type;

-- 开启性能分析
SET profiling = 1;

-- 测试1: 会话消息查询 (应该使用新索引)
SELECT '测试1: 会话消息查询 (优化后)' AS test_name;
SELECT COUNT(*) AS message_count
FROM t_chat_message 
WHERE session_id = @test_session_id AND deleted = 0;

-- 测试2: 用户消息历史查询 (应该使用新索引)
SELECT '测试2: 用户消息历史查询 (优化后)' AS test_name;
SELECT COUNT(*) AS user_message_count
FROM t_chat_message 
WHERE user_id = @test_user_id AND deleted = 0;

-- 测试3: 用户会话列表查询 (应该使用新索引)
SELECT '测试3: 用户会话列表查询 (优化后)' AS test_name;
SELECT COUNT(*) AS user_session_count
FROM t_chat_session 
WHERE user_id = @test_user_id AND deleted = 0;

-- 测试4: 用户订单查询 (应该使用新索引)
SELECT '测试4: 用户订单查询 (优化后)' AS test_name;
SELECT COUNT(*) AS user_order_count
FROM t_user_order 
WHERE user_id = @test_user_id AND deleted = 0;

-- 记录优化后的性能数据
SELECT '📋 优化后性能数据记录' AS check_type;
SHOW PROFILES;

-- 创建临时表保存优化后的性能数据
CREATE TEMPORARY TABLE IF NOT EXISTS performance_after (
    test_name VARCHAR(100),
    duration DECIMAL(10,6),
    query_text TEXT,
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 插入优化后的性能数据
INSERT INTO performance_after (test_name, duration, query_text)
SELECT 
    CONCAT('优化后-', 
        CASE 
            WHEN QUERY_ID = (SELECT MAX(QUERY_ID) - 3 FROM information_schema.PROFILING) THEN '会话消息查询'
            WHEN QUERY_ID = (SELECT MAX(QUERY_ID) - 2 FROM information_schema.PROFILING) THEN '用户消息历史查询'
            WHEN QUERY_ID = (SELECT MAX(QUERY_ID) - 1 FROM information_schema.PROFILING) THEN '用户会话列表查询'
            WHEN QUERY_ID = (SELECT MAX(QUERY_ID) FROM information_schema.PROFILING) THEN '用户订单查询'
        END
    ) AS test_name,
    DURATION,
    QUERY
FROM information_schema.PROFILING 
WHERE QUERY LIKE '%COUNT(*)%' 
  AND QUERY NOT LIKE '%information_schema%'
ORDER BY QUERY_ID DESC 
LIMIT 4;

-- 关闭性能分析
SET profiling = 0;

-- =====================================================
-- 第四阶段: 性能对比分析
-- =====================================================

SELECT '📊 ===== 性能优化效果对比分析 =====' AS step_info, NOW() AS comparison_start_time;

-- 1. 性能对比报告
SELECT '🎯 性能优化效果对比报告' AS report_type;

SELECT 
    SUBSTRING_INDEX(b.test_name, '-', -1) AS '测试项目',
    ROUND(b.duration * 1000, 2) AS '优化前耗时(ms)',
    ROUND(a.duration * 1000, 2) AS '优化后耗时(ms)',
    ROUND((b.duration - a.duration) * 1000, 2) AS '性能提升(ms)',
    CONCAT(ROUND((1 - a.duration/b.duration) * 100, 1), '%') AS '提升百分比',
    CASE 
        WHEN a.duration < b.duration * 0.1 THEN '🚀 显著提升'
        WHEN a.duration < b.duration * 0.3 THEN '⚡ 大幅提升'
        WHEN a.duration < b.duration * 0.7 THEN '✅ 明显提升'
        ELSE '⚠️ 提升有限'
    END AS '优化效果'
FROM performance_before b
JOIN performance_after a ON SUBSTRING_INDEX(b.test_name, '-', -1) = SUBSTRING_INDEX(a.test_name, '-', -1)
ORDER BY (b.duration - a.duration) DESC;

-- 2. 执行计划对比
SELECT '🔍 执行计划验证' AS report_type;

-- 验证会话消息查询的执行计划
SELECT '会话消息查询执行计划:' AS plan_type;
EXPLAIN 
SELECT * FROM t_chat_message 
WHERE session_id = @test_session_id AND deleted = 0 
ORDER BY create_time ASC 
LIMIT 50;

-- 验证用户消息历史查询的执行计划
SELECT '用户消息历史查询执行计划:' AS plan_type;
EXPLAIN 
SELECT * FROM t_chat_message 
WHERE user_id = @test_user_id AND deleted = 0 
ORDER BY create_time DESC 
LIMIT 20;

-- 验证用户会话列表查询的执行计划
SELECT '用户会话列表查询执行计划:' AS plan_type;
EXPLAIN 
SELECT * FROM t_chat_session 
WHERE user_id = @test_user_id AND deleted = 0 
ORDER BY last_active_time DESC 
LIMIT 10;

-- 验证用户订单查询的执行计划
SELECT '用户订单查询执行计划:' AS plan_type;
EXPLAIN 
SELECT * FROM t_user_order 
WHERE user_id = @test_user_id AND deleted = 0 
ORDER BY create_time DESC;

-- 3. 索引使用情况检查
SELECT '📈 索引大小统计' AS report_type;
SELECT 
    TABLE_NAME AS '表名',
    INDEX_NAME AS '索引名',
    ROUND(stat_value * @@innodb_page_size / 1024 / 1024, 2) AS '索引大小(MB)'
FROM mysql.innodb_index_stats 
WHERE database_name = DATABASE() 
  AND stat_name = 'size'
  AND index_name LIKE 'idx_%'
ORDER BY stat_value DESC;

-- =====================================================
-- 第五阶段: 优化总结和建议
-- =====================================================

SELECT '🎉 ===== 索引优化完成总结 =====' AS step_info, NOW() AS completion_time;

-- 1. 优化成果总结
SELECT '📋 优化成果总结' AS summary_type;
SELECT
    '新增索引数量' AS metric_name,
    CAST(COUNT(*) AS CHAR) AS metric_value,
    '个' AS unit
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND INDEX_NAME LIKE 'idx_%'
  AND INDEX_NAME NOT IN ('idx_session_id', 'idx_user_id', 'idx_user_date', 'idx_usage_date', 'idx_last_chat_time')

UNION ALL

SELECT
    '优化表数量' AS metric_name,
    CAST(COUNT(DISTINCT TABLE_NAME) AS CHAR) AS metric_value,
    '个' AS unit
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND INDEX_NAME LIKE 'idx_%'
  AND INDEX_NAME NOT IN ('idx_session_id', 'idx_user_id', 'idx_user_date', 'idx_usage_date', 'idx_last_chat_time')

UNION ALL

SELECT
    '总索引大小' AS metric_name,
    CAST(ROUND(SUM(stat_value * @@innodb_page_size / 1024 / 1024), 2) AS CHAR) AS metric_value,
    'MB' AS unit
FROM mysql.innodb_index_stats
WHERE database_name = DATABASE()
  AND stat_name = 'size'
  AND index_name LIKE 'idx_%';

-- 2. 后续监控建议
SELECT '💡 后续监控和维护建议' AS advice_type;
SELECT '
🔍 监控建议:
1. 定期检查慢查询日志，确保查询性能持续优化
2. 监控索引使用情况，识别未使用的索引
3. 定期更新表统计信息: ANALYZE TABLE
4. 观察系统资源使用情况，特别是内存和磁盘I/O

⚡ 性能优化建议:
1. 如果数据量继续增长，考虑分区表策略
2. 对于超大表，考虑归档历史数据
3. 根据业务需求，可以考虑读写分离
4. 定期检查和优化应用层的SQL查询

🛠️ 维护建议:
1. 每周执行: ANALYZE TABLE 更新统计信息
2. 每月检查: 索引碎片和表空间使用情况
3. 季度优化: 根据查询模式调整索引策略
4. 年度评估: 整体数据库架构和性能规划
' AS recommendations;

-- 3. 清理临时表
DROP TEMPORARY TABLE IF EXISTS performance_before;
DROP TEMPORARY TABLE IF EXISTS performance_after;

-- 4. 详细的执行计划分析
SELECT '🔬 详细执行计划分析' AS analysis_type;

-- 创建执行计划分析表
CREATE TEMPORARY TABLE IF NOT EXISTS explain_analysis (
    query_name VARCHAR(100),
    table_name VARCHAR(100),
    type_info VARCHAR(20),
    possible_keys TEXT,
    key_used VARCHAR(100),
    key_len VARCHAR(20),
    ref_info VARCHAR(100),
    rows_examined INT,
    filtered DECIMAL(5,2),
    extra_info TEXT
);

-- 分析会话消息查询
INSERT INTO explain_analysis
SELECT
    '会话消息查询' AS query_name,
    table_name,
    type,
    possible_keys,
    key,
    key_len,
    ref,
    rows,
    filtered,
    Extra
FROM (
    EXPLAIN
    SELECT * FROM t_chat_message
    WHERE session_id = @test_session_id AND deleted = 0
    ORDER BY create_time ASC
    LIMIT 50
) AS explain_result;

-- 分析用户消息历史查询
INSERT INTO explain_analysis
SELECT
    '用户消息历史查询' AS query_name,
    table_name,
    type,
    possible_keys,
    key,
    key_len,
    ref,
    rows,
    filtered,
    Extra
FROM (
    EXPLAIN
    SELECT * FROM t_chat_message
    WHERE user_id = @test_user_id AND deleted = 0
    ORDER BY create_time DESC
    LIMIT 20
) AS explain_result;

-- 分析用户会话列表查询
INSERT INTO explain_analysis
SELECT
    '用户会话列表查询' AS query_name,
    table_name,
    type,
    possible_keys,
    key,
    key_len,
    ref,
    rows,
    filtered,
    Extra
FROM (
    EXPLAIN
    SELECT * FROM t_chat_session
    WHERE user_id = @test_user_id AND deleted = 0
    ORDER BY last_active_time DESC
    LIMIT 10
) AS explain_result;

-- 分析用户订单查询
INSERT INTO explain_analysis
SELECT
    '用户订单查询' AS query_name,
    table_name,
    type,
    possible_keys,
    key,
    key_len,
    ref,
    rows,
    filtered,
    Extra
FROM (
    EXPLAIN
    SELECT * FROM t_user_order
    WHERE user_id = @test_user_id AND deleted = 0
    ORDER BY create_time DESC
) AS explain_result;

-- 显示执行计划分析结果
SELECT
    query_name AS '查询类型',
    type_info AS '访问类型',
    key_used AS '使用的索引',
    rows_examined AS '扫描行数',
    CASE
        WHEN type_info = 'const' THEN '🟢 最优'
        WHEN type_info = 'eq_ref' THEN '🟢 优秀'
        WHEN type_info = 'ref' THEN '🟡 良好'
        WHEN type_info = 'range' THEN '🟡 可接受'
        WHEN type_info = 'index' THEN '🟠 需优化'
        WHEN type_info = 'ALL' THEN '🔴 全表扫描'
        ELSE '⚪ 其他'
    END AS '性能评级',
    CASE
        WHEN extra_info LIKE '%Using filesort%' THEN '⚠️ 需要文件排序'
        WHEN extra_info LIKE '%Using temporary%' THEN '⚠️ 使用临时表'
        WHEN extra_info LIKE '%Using index%' THEN '✅ 使用覆盖索引'
        WHEN extra_info LIKE '%Using where%' THEN '✅ 使用WHERE过滤'
        ELSE '✅ 正常'
    END AS '执行特征'
FROM explain_analysis
ORDER BY
    CASE type_info
        WHEN 'const' THEN 1
        WHEN 'eq_ref' THEN 2
        WHEN 'ref' THEN 3
        WHEN 'range' THEN 4
        WHEN 'index' THEN 5
        WHEN 'ALL' THEN 6
        ELSE 7
    END;

-- 5. 索引效果评估
SELECT '📊 索引效果综合评估' AS evaluation_type;

SELECT
    '索引优化效果评估' AS evaluation_item,
    CASE
        WHEN (
            SELECT COUNT(*)
            FROM explain_analysis
            WHERE type_info IN ('const', 'eq_ref', 'ref')
        ) >= 3 THEN '🎉 优化效果显著'
        WHEN (
            SELECT COUNT(*)
            FROM explain_analysis
            WHERE type_info IN ('const', 'eq_ref', 'ref', 'range')
        ) >= 2 THEN '✅ 优化效果良好'
        ELSE '⚠️ 需要进一步优化'
    END AS evaluation_result,
    CONCAT(
        '有 ',
        (SELECT COUNT(*) FROM explain_analysis WHERE type_info IN ('const', 'eq_ref', 'ref')),
        ' 个查询达到最优性能'
    ) AS detailed_info;

-- 6. 问题诊断和建议
SELECT '🔧 问题诊断和优化建议' AS diagnosis_type;

-- 检查是否还有全表扫描
SELECT
    CASE
        WHEN EXISTS (SELECT 1 FROM explain_analysis WHERE type_info = 'ALL') THEN
            CONCAT('⚠️ 发现全表扫描: ', GROUP_CONCAT(query_name SEPARATOR ', '))
        ELSE '✅ 无全表扫描问题'
    END AS scan_diagnosis
FROM explain_analysis
WHERE type_info = 'ALL';

-- 检查是否有文件排序
SELECT
    CASE
        WHEN EXISTS (SELECT 1 FROM explain_analysis WHERE extra_info LIKE '%Using filesort%') THEN
            CONCAT('⚠️ 发现文件排序: ', GROUP_CONCAT(query_name SEPARATOR ', '))
        ELSE '✅ 无文件排序问题'
    END AS sort_diagnosis
FROM explain_analysis
WHERE extra_info LIKE '%Using filesort%';

-- 检查扫描行数
SELECT
    CASE
        WHEN MAX(rows_examined) > 1000 THEN
            CONCAT('⚠️ 扫描行数过多: 最大 ', MAX(rows_examined), ' 行')
        WHEN MAX(rows_examined) > 100 THEN
            CONCAT('🟡 扫描行数较多: 最大 ', MAX(rows_examined), ' 行')
        ELSE '✅ 扫描行数合理'
    END AS rows_diagnosis
FROM explain_analysis;

-- 7. 性能监控SQL模板
SELECT '📋 后续性能监控SQL模板' AS template_type;

SELECT '
-- =====================================================
-- 性能监控SQL模板 (建议定期执行)
-- =====================================================

-- 1. 检查慢查询 (每日执行)
SELECT
    SCHEMA_NAME AS 数据库,
    DIGEST_TEXT AS 查询模式,
    COUNT_STAR AS 执行次数,
    ROUND(AVG_TIMER_WAIT/1000000000, 3) AS 平均耗时秒,
    ROUND(MAX_TIMER_WAIT/1000000000, 3) AS 最大耗时秒
FROM performance_schema.events_statements_summary_by_digest
WHERE SCHEMA_NAME = DATABASE()
  AND AVG_TIMER_WAIT > 1000000000  -- 大于1秒的查询
ORDER BY AVG_TIMER_WAIT DESC
LIMIT 10;

-- 2. 检查索引使用情况 (每周执行)
SELECT
    OBJECT_NAME AS 表名,
    INDEX_NAME AS 索引名,
    COUNT_FETCH AS 读取次数,
    COUNT_INSERT AS 插入次数,
    COUNT_UPDATE AS 更新次数,
    COUNT_DELETE AS 删除次数
FROM performance_schema.table_io_waits_summary_by_index_usage
WHERE OBJECT_SCHEMA = DATABASE()
  AND INDEX_NAME LIKE "idx_%"
ORDER BY COUNT_FETCH DESC;

-- 3. 检查未使用的索引 (每月执行)
SELECT
    t.TABLE_NAME AS 表名,
    t.INDEX_NAME AS 可能未使用的索引
FROM information_schema.STATISTICS t
LEFT JOIN performance_schema.table_io_waits_summary_by_index_usage p
  ON t.TABLE_SCHEMA = p.OBJECT_SCHEMA
  AND t.TABLE_NAME = p.OBJECT_NAME
  AND t.INDEX_NAME = p.INDEX_NAME
WHERE t.TABLE_SCHEMA = DATABASE()
  AND t.INDEX_NAME LIKE "idx_%"
  AND p.INDEX_NAME IS NULL;

-- 4. 检查表碎片情况 (每月执行)
SELECT
    TABLE_NAME AS 表名,
    ROUND(DATA_LENGTH/1024/1024, 2) AS 数据大小MB,
    ROUND(INDEX_LENGTH/1024/1024, 2) AS 索引大小MB,
    ROUND(DATA_FREE/1024/1024, 2) AS 碎片大小MB,
    ROUND(DATA_FREE/(DATA_LENGTH+INDEX_LENGTH)*100, 2) AS 碎片比例
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND DATA_FREE > 0
ORDER BY DATA_FREE DESC;
' AS monitoring_templates;

-- 清理临时表
DROP TEMPORARY TABLE IF EXISTS performance_before;
DROP TEMPORARY TABLE IF EXISTS performance_after;
DROP TEMPORARY TABLE IF EXISTS explain_analysis;

-- =====================================================
-- 脚本执行完成
-- =====================================================

SELECT '🎉 ===== 索引优化脚本执行完成 =====' AS final_status;

SELECT
    '执行完成时间' AS completion_info,
    CAST(NOW() AS CHAR) AS completion_time
UNION ALL
SELECT
    '优化建议' AS completion_info,
    '请查看上述性能对比报告和执行计划分析' AS completion_time
UNION ALL
SELECT
    '后续行动' AS completion_info,
    '建议保存监控SQL模板，定期检查数据库性能' AS completion_time
UNION ALL
SELECT
    '注意事项' AS completion_info,
    '如发现性能问题，请根据诊断建议进行进一步优化' AS completion_time;

-- =====================================================
-- 使用说明
-- =====================================================
/*
📖 使用说明:

1. 🎯 脚本功能:
   - 自动检测优化前的性能基线
   - 创建关键业务查询的优化索引
   - 验证优化后的性能提升效果
   - 提供详细的执行计划分析

2. 📊 性能指标说明:
   - 访问类型: const > eq_ref > ref > range > index > ALL
   - 扫描行数: 越少越好，理想情况下小于100行
   - 执行特征: 避免filesort和temporary，优选覆盖索引

3. ⚠️ 注意事项:
   - 请在测试环境先验证效果
   - 大表索引创建可能需要较长时间
   - 建议在业务低峰期执行
   - 执行前请备份重要数据

4. 🔍 结果解读:
   - 绿色指标: 性能优秀，无需调整
   - 黄色指标: 性能良好，可考虑优化
   - 红色指标: 需要重点关注和优化

5. 📈 持续监控:
   - 使用提供的监控SQL模板
   - 定期检查索引使用情况
   - 关注慢查询日志
   - 根据业务变化调整索引策略
*/
