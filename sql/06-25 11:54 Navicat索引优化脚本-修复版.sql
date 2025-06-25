-- =====================================================
-- Navicat 索引优化完整脚本 - 修复版
-- =====================================================
-- 生成时间: 2025年6月25日 11:54 (北京时间)
-- 版本: v1.1 (修复字符集问题)
-- 项目: jinyu-chat 聊天系统
-- 适用工具: Navicat Premium/MySQL
-- 执行流程: 性能检测 → 索引优化 → 性能对比
-- =====================================================

-- 设置执行环境
SET NAMES utf8mb4;
SET SESSION sql_mode = 'STRICT_TRANS_TABLES,NO_ZERO_DATE,NO_ZERO_IN_DATE,ERROR_FOR_DIVISION_BY_ZERO';
SET collation_connection = 'utf8mb4_unicode_ci';

-- =====================================================
-- 第一阶段: 优化前性能基线检测
-- =====================================================

SELECT '🔍 ===== 开始优化前性能基线检测 =====' AS step_info, NOW() AS start_time;

-- 1. 检查当前数据库基本信息
SELECT '📊 数据库基本信息检查' AS check_type;

-- 分别显示数据库信息，避免UNION字符集问题
SELECT '数据库版本' AS info_type, VERSION() AS value;
SELECT '当前数据库' AS info_type, DATABASE() AS value;
SELECT '当前时间' AS info_type, NOW() AS value;

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

-- 分别显示测试数据，避免字符集问题
SELECT '测试会话ID' AS sample_type, @test_session_id AS sample_value;
SELECT '测试用户ID' AS sample_type, @test_user_id AS sample_value;

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

-- 关闭性能分析
SET profiling = 0;

-- =====================================================
-- 第二阶段: 执行索引优化
-- =====================================================

SELECT '🚀 ===== 开始执行索引优化 =====' AS step_info, NOW() AS optimization_start_time;

-- 1. t_chat_message 表索引优化
SELECT '🔧 优化 t_chat_message 表索引' AS optimization_type;

-- 索引1: 会话消息时间查询索引 (最高优先级)
-- 安全删除已存在的索引
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 't_chat_message'
       AND INDEX_NAME = 'idx_chat_msg_session_time') > 0,
    'DROP INDEX idx_chat_msg_session_time ON t_chat_message',
    'SELECT "索引 idx_chat_msg_session_time 不存在，跳过删除" AS info'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE INDEX idx_chat_msg_session_time ON t_chat_message(session_id, create_time ASC, deleted)
COMMENT '会话消息时间查询索引-用于加载会话消息列表';

-- 索引2: 用户消息时间查询索引 (高优先级)
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 't_chat_message'
       AND INDEX_NAME = 'idx_chat_msg_user_time') > 0,
    'DROP INDEX idx_chat_msg_user_time ON t_chat_message',
    'SELECT "索引 idx_chat_msg_user_time 不存在，跳过删除" AS info'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE INDEX idx_chat_msg_user_time ON t_chat_message(user_id, create_time DESC, deleted)
COMMENT '用户消息时间查询索引-用于用户消息历史查询';

-- 索引3: 用户消息统计查询索引 (中优先级)
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 't_chat_message'
       AND INDEX_NAME = 'idx_chat_msg_user_date') > 0,
    'DROP INDEX idx_chat_msg_user_date ON t_chat_message',
    'SELECT "索引 idx_chat_msg_user_date 不存在，跳过删除" AS info'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

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

-- 关闭性能分析
SET profiling = 0;

-- =====================================================
-- 第四阶段: 执行计划验证
-- =====================================================

SELECT '🔍 ===== 执行计划验证 =====' AS step_info, NOW() AS plan_check_time;

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

-- =====================================================
-- 第五阶段: 索引大小统计
-- =====================================================

SELECT '📈 ===== 索引大小统计 =====' AS step_info;

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
-- 第六阶段: 优化成果总结
-- =====================================================

SELECT '🎉 ===== 索引优化完成总结 =====' AS step_info, NOW() AS completion_time;

-- 优化成果统计
SELECT '新增索引数量' AS metric_name, COUNT(*) AS metric_value
FROM information_schema.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE()
  AND INDEX_NAME LIKE 'idx_%'
  AND INDEX_NAME NOT IN ('idx_session_id', 'idx_user_id', 'idx_user_date', 'idx_usage_date', 'idx_last_chat_time');

SELECT '优化表数量' AS metric_name, COUNT(DISTINCT TABLE_NAME) AS metric_value
FROM information_schema.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE()
  AND INDEX_NAME LIKE 'idx_%'
  AND INDEX_NAME NOT IN ('idx_session_id', 'idx_user_id', 'idx_user_date', 'idx_usage_date', 'idx_last_chat_time');

-- =====================================================
-- 后续监控建议
-- =====================================================

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

SELECT '✅ 索引优化脚本执行完成!' AS final_status;
SELECT '请对比优化前后的SHOW PROFILES结果，验证性能提升效果' AS next_action;
SELECT NOW() AS completion_timestamp;
