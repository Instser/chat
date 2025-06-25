-- =====================================================
-- Navicat 索引优化完整脚本 - MySQL 5.7 兼容版
-- =====================================================
-- 生成时间: 2025年6月25日 11:54 (北京时间)
-- 版本: v1.2 (MySQL 5.7 兼容版)
-- 项目: jinyu-chat 聊天系统
-- 适用工具: Navicat Premium/MySQL 5.7
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

-- 分别显示数据库信息，避免UNION字符集问题
SELECT 'MySQL版本' AS info_type, VERSION() AS value;
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

-- 分别显示测试数据
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
-- 第二阶段: 执行索引优化 (MySQL 5.7 兼容)
-- =====================================================

SELECT '🚀 ===== 开始执行索引优化 =====' AS step_info, NOW() AS optimization_start_time;

-- 1. t_chat_message 表索引优化
SELECT '🔧 优化 t_chat_message 表索引' AS optimization_type;

-- 索引1: 会话消息时间查询索引 (最高优先级)
-- MySQL 5.7 兼容写法：先检查索引是否存在，再删除
SET @index_exists = (
    SELECT COUNT(*) 
    FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() 
      AND TABLE_NAME = 't_chat_message' 
      AND INDEX_NAME = 'idx_chat_msg_session_time'
);

SET @sql = IF(@index_exists > 0, 
    'ALTER TABLE t_chat_message DROP INDEX idx_chat_msg_session_time', 
    'SELECT "索引不存在，跳过删除" AS info'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE INDEX idx_chat_msg_session_time ON t_chat_message(session_id, create_time ASC, deleted);
SELECT '✅ 创建索引: idx_chat_msg_session_time' AS result;

-- 索引2: 用户消息时间查询索引 (高优先级)
SET @index_exists = (
    SELECT COUNT(*) 
    FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() 
      AND TABLE_NAME = 't_chat_message' 
      AND INDEX_NAME = 'idx_chat_msg_user_time'
);

SET @sql = IF(@index_exists > 0, 
    'ALTER TABLE t_chat_message DROP INDEX idx_chat_msg_user_time', 
    'SELECT "索引不存在，跳过删除" AS info'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE INDEX idx_chat_msg_user_time ON t_chat_message(user_id, create_time DESC, deleted);
SELECT '✅ 创建索引: idx_chat_msg_user_time' AS result;

-- 索引3: 用户消息统计查询索引 (中优先级)
SET @index_exists = (
    SELECT COUNT(*) 
    FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() 
      AND TABLE_NAME = 't_chat_message' 
      AND INDEX_NAME = 'idx_chat_msg_user_date'
);

SET @sql = IF(@index_exists > 0, 
    'ALTER TABLE t_chat_message DROP INDEX idx_chat_msg_user_date', 
    'SELECT "索引不存在，跳过删除" AS info'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE INDEX idx_chat_msg_user_date ON t_chat_message(user_id, deleted, create_time);
SELECT '✅ 创建索引: idx_chat_msg_user_date' AS result;

-- 2. t_chat_session 表索引优化
SELECT '🔧 优化 t_chat_session 表索引' AS optimization_type;

-- 索引4: 用户会话活跃时间查询索引 (高优先级)
SET @index_exists = (
    SELECT COUNT(*) 
    FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() 
      AND TABLE_NAME = 't_chat_session' 
      AND INDEX_NAME = 'idx_chat_session_user_active'
);

SET @sql = IF(@index_exists > 0, 
    'ALTER TABLE t_chat_session DROP INDEX idx_chat_session_user_active', 
    'SELECT "索引不存在，跳过删除" AS info'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE INDEX idx_chat_session_user_active ON t_chat_session(user_id, last_active_time DESC, deleted);
SELECT '✅ 创建索引: idx_chat_session_user_active' AS result;

-- 3. t_user_order 表索引优化
SELECT '🔧 优化 t_user_order 表索引' AS optimization_type;

-- 索引5: 用户订单状态时间查询索引 (高优先级)
SET @index_exists = (
    SELECT COUNT(*) 
    FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() 
      AND TABLE_NAME = 't_user_order' 
      AND INDEX_NAME = 'idx_user_order_status_time'
);

SET @sql = IF(@index_exists > 0, 
    'ALTER TABLE t_user_order DROP INDEX idx_user_order_status_time', 
    'SELECT "索引不存在，跳过删除" AS info'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE INDEX idx_user_order_status_time ON t_user_order(user_id, payment_status, create_time DESC);
SELECT '✅ 创建索引: idx_user_order_status_time' AS result;

-- 索引6: 订单时间状态统计索引 (中优先级)
SET @index_exists = (
    SELECT COUNT(*) 
    FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() 
      AND TABLE_NAME = 't_user_order' 
      AND INDEX_NAME = 'idx_user_order_time_status'
);

SET @sql = IF(@index_exists > 0, 
    'ALTER TABLE t_user_order DROP INDEX idx_user_order_time_status', 
    'SELECT "索引不存在，跳过删除" AS info'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE INDEX idx_user_order_time_status ON t_user_order(create_time DESC, payment_status);
SELECT '✅ 创建索引: idx_user_order_time_status' AS result;

-- 4. 其他辅助索引
SELECT '🔧 创建辅助优化索引' AS optimization_type;

-- 索引7: 会话消息角色统计索引
SET @index_exists = (
    SELECT COUNT(*) 
    FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() 
      AND TABLE_NAME = 't_chat_message' 
      AND INDEX_NAME = 'idx_chat_msg_session_role'
);

SET @sql = IF(@index_exists > 0, 
    'ALTER TABLE t_chat_message DROP INDEX idx_chat_msg_session_role', 
    'SELECT "索引不存在，跳过删除" AS info'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE INDEX idx_chat_msg_session_role ON t_chat_message(session_id, role, deleted);
SELECT '✅ 创建索引: idx_chat_msg_session_role' AS result;

-- 索引8: 模型消息查询索引
SET @index_exists = (
    SELECT COUNT(*) 
    FROM information_schema.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() 
      AND TABLE_NAME = 't_chat_message' 
      AND INDEX_NAME = 'idx_chat_msg_model_time'
);

SET @sql = IF(@index_exists > 0, 
    'ALTER TABLE t_chat_message DROP INDEX idx_chat_msg_model_time', 
    'SELECT "索引不存在，跳过删除" AS info'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE INDEX idx_chat_msg_model_time ON t_chat_message(model_id, create_time DESC, deleted);
SELECT '✅ 创建索引: idx_chat_msg_model_time' AS result;

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
    GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS '索引字段'
FROM information_schema.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE()
  AND INDEX_NAME LIKE 'idx_chat_%' OR INDEX_NAME LIKE 'idx_user_%'
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
-- 第五阶段: 索引大小统计 (MySQL 5.7 兼容)
-- =====================================================

SELECT '📈 ===== 索引大小统计 =====' AS step_info;

-- MySQL 5.7 使用 information_schema 查询索引大小
SELECT
    s.TABLE_NAME AS '表名',
    s.INDEX_NAME AS '索引名',
    ROUND(SUM(s.CARDINALITY * t.AVG_ROW_LENGTH) / 1024 / 1024, 2) AS '估计大小(MB)'
FROM information_schema.STATISTICS s
JOIN information_schema.TABLES t ON s.TABLE_SCHEMA = t.TABLE_SCHEMA AND s.TABLE_NAME = t.TABLE_NAME
WHERE s.TABLE_SCHEMA = DATABASE()
  AND (s.INDEX_NAME LIKE 'idx_chat_%' OR s.INDEX_NAME LIKE 'idx_user_%')
GROUP BY s.TABLE_NAME, s.INDEX_NAME
ORDER BY SUM(s.CARDINALITY * t.AVG_ROW_LENGTH) DESC;

-- =====================================================
-- 第六阶段: 优化成果总结
-- =====================================================

SELECT '🎉 ===== 索引优化完成总结 =====' AS step_info, NOW() AS completion_time;

-- 优化成果统计
SELECT '新增索引统计' AS summary_type;
SELECT
    TABLE_NAME AS '表名',
    COUNT(*) AS '新增索引数量'
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND (INDEX_NAME LIKE 'idx_chat_%' OR INDEX_NAME LIKE 'idx_user_%')
GROUP BY TABLE_NAME
ORDER BY TABLE_NAME;

-- 总体统计
SELECT '总体优化成果' AS summary_type;
SELECT COUNT(*) AS '总新增索引数量'
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND (INDEX_NAME LIKE 'idx_chat_%' OR INDEX_NAME LIKE 'idx_user_%');

-- =====================================================
-- 性能对比建议
-- =====================================================

SELECT '📊 ===== 性能对比建议 =====' AS step_info;

SELECT '
📋 性能对比方法:

1. 🔍 查看SHOW PROFILES结果对比:
   - 找到优化前的4个测试查询的Duration
   - 找到优化后的4个测试查询的Duration
   - 计算性能提升百分比

2. 📈 关键指标对比:
   - 会话消息查询: 预期提升90%+
   - 用户消息历史: 预期提升85%+
   - 用户会话列表: 预期提升90%+
   - 用户订单查询: 预期提升80%+

3. 🔍 执行计划验证:
   - type字段应该显示ref或range (不是ALL)
   - key字段应该显示使用了新创建的索引
   - rows字段应该显著减少

4. ⚡ 性能等级评估:
   - 🟢 优秀: type=const/eq_ref, rows<100
   - 🟡 良好: type=ref/range, rows<1000
   - 🔴 需优化: type=ALL, rows>1000
' AS performance_comparison_guide;

-- =====================================================
-- 后续监控建议
-- =====================================================

SELECT '💡 后续监控和维护建议' AS advice_type;

SELECT '
🔍 日常监控建议:

1. 📊 每日检查 (建议加入定时任务):
   -- 检查慢查询 (需要开启慢查询日志)
   SHOW VARIABLES LIKE "slow_query_log";
   SHOW VARIABLES LIKE "long_query_time";

2. 📈 每周检查:
   -- 检查索引使用情况
   SHOW INDEX FROM t_chat_message;
   SHOW INDEX FROM t_chat_session;
   SHOW INDEX FROM t_user_order;

3. 🛠️ 每月维护:
   -- 更新表统计信息
   ANALYZE TABLE t_chat_message, t_chat_session, t_user_order;

   -- 检查表状态
   SHOW TABLE STATUS LIKE "t_chat_%";

4. 📋 性能基准记录:
   -- 保存当前的SHOW PROFILES结果作为基准
   -- 定期重复相同的测试查询进行对比

⚠️ 注意事项:
- MySQL 5.7 不支持某些新特性，建议考虑升级到8.0
- 大表索引创建可能需要较长时间，建议在低峰期执行
- 定期备份数据库，特别是在进行结构变更前
' AS monitoring_recommendations;

-- =====================================================
-- 脚本执行完成
-- =====================================================

SELECT '✅ ===== MySQL 5.7 兼容版索引优化脚本执行完成 =====' AS final_status;

SELECT '执行完成时间' AS completion_info, NOW() AS completion_time;
SELECT '优化建议' AS completion_info, '请对比优化前后的SHOW PROFILES结果验证性能提升' AS completion_time;
SELECT '后续行动' AS completion_info, '建议定期监控数据库性能，根据业务变化调整索引策略' AS completion_time;
