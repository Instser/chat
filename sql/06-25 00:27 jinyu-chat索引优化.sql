-- =====================================================
-- jinyu-chat 数据库索引优化脚本
-- =====================================================
-- 生成时间: 2025年6月25日 00:27 (北京时间)
-- 版本: v1.0
-- 项目: jinyu-chat 聊天系统
-- 目标: 基于现有数据库结构，创建关键性能优化索引
-- 数据库: MySQL 5.7.43 / InnoDB引擎
-- =====================================================

USE chat;

-- 设置安全模式
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================
-- 现有索引状况分析
-- =====================================================
/*
当前已存在的索引:
t_chat_message:
  - PRIMARY KEY (id)
  - UNIQUE KEY uk_message_id (message_id)  
  - KEY idx_session_id (session_id)
  - KEY idx_user_id (user_id)
  - KEY fk_message_model (model_id)

t_chat_session:
  - PRIMARY KEY (id)
  - UNIQUE KEY uk_session_id (session_id)
  - KEY idx_user_id (user_id)

t_user_daily_usage:
  - PRIMARY KEY (id)
  - UNIQUE KEY uk_user_package_date (user_id, package_id, usage_date)
  - KEY idx_user_date (user_id, usage_date)
  - KEY idx_usage_date (usage_date)
  - KEY idx_last_chat_time (last_chat_time)

t_user_order:
  - PRIMARY KEY (id)
  - UNIQUE KEY uk_order_no (order_no)
  - KEY idx_user_id (user_id)

缺失的关键索引: 复合索引用于优化常见查询场景
*/

-- =====================================================
-- 第一阶段: 紧急性能优化索引 (立即执行)
-- =====================================================

SELECT '=== 第一阶段: 创建紧急性能优化索引 ===' AS stage;

-- 1. t_chat_message 表关键索引
-- =====================================================

-- 索引1: 会话消息时间查询优化 (最高优先级)
-- 场景: 加载会话消息列表，按时间正序显示
-- SQL: SELECT * FROM t_chat_message WHERE session_id = ? AND deleted = 0 ORDER BY create_time ASC
DROP INDEX IF EXISTS idx_session_time_deleted ON t_chat_message;
CREATE INDEX idx_session_time_deleted ON t_chat_message(session_id, create_time ASC, deleted) 
COMMENT '会话消息时间查询索引-优化消息列表加载性能';

-- 索引2: 用户消息历史查询优化 (高优先级)
-- 场景: 查看用户历史消息，按时间倒序显示
-- SQL: SELECT * FROM t_chat_message WHERE user_id = ? AND deleted = 0 ORDER BY create_time DESC LIMIT 20
DROP INDEX IF EXISTS idx_user_time_deleted ON t_chat_message;
CREATE INDEX idx_user_time_deleted ON t_chat_message(user_id, create_time DESC, deleted) 
COMMENT '用户消息历史查询索引-优化用户消息浏览性能';

-- 2. t_chat_session 表关键索引
-- =====================================================

-- 索引3: 用户会话列表查询优化 (高优先级)
-- 场景: 获取用户会话列表，按最后活跃时间倒序
-- SQL: SELECT * FROM t_chat_session WHERE user_id = ? AND deleted = 0 ORDER BY last_active_time DESC LIMIT 10
DROP INDEX IF EXISTS idx_user_active_deleted ON t_chat_session;
CREATE INDEX idx_user_active_deleted ON t_chat_session(user_id, last_active_time DESC, deleted) 
COMMENT '用户会话列表查询索引-优化会话列表加载性能';

-- 3. t_user_order 表关键索引
-- =====================================================

-- 索引4: 用户订单状态查询优化 (高优先级)
-- 场景: 查询用户订单，支持状态过滤和时间排序
-- SQL: SELECT * FROM t_user_order WHERE user_id = ? AND payment_status = ? ORDER BY create_time DESC
DROP INDEX IF EXISTS idx_user_status_time ON t_user_order;
CREATE INDEX idx_user_status_time ON t_user_order(user_id, payment_status, create_time DESC) 
COMMENT '用户订单状态查询索引-优化订单管理性能';

-- =====================================================
-- 第二阶段: 性能提升索引 (1周内执行)
-- =====================================================

SELECT '=== 第二阶段: 创建性能提升索引 ===' AS stage;

-- 索引5: 用户消息统计查询优化 (中优先级)
-- 场景: 统计用户消息数量，支持时间范围过滤
-- SQL: SELECT COUNT(*) FROM t_chat_message WHERE user_id = ? AND create_time >= ? AND deleted = 0
DROP INDEX IF EXISTS idx_user_date_deleted ON t_chat_message;
CREATE INDEX idx_user_date_deleted ON t_chat_message(user_id, deleted, create_time) 
COMMENT '用户消息统计查询索引-优化消息统计性能';

-- 索引6: 订单时间状态统计优化 (中优先级)
-- 场景: 按时间统计订单状态分布
-- SQL: SELECT COUNT(*), payment_status FROM t_user_order WHERE create_time >= ? GROUP BY payment_status
DROP INDEX IF EXISTS idx_time_status ON t_user_order;
CREATE INDEX idx_time_status ON t_user_order(create_time DESC, payment_status) 
COMMENT '订单时间状态统计索引-优化订单统计分析';

-- 索引7: 会话消息角色统计优化 (中优先级)
-- 场景: 统计会话中不同角色的消息数量
-- SQL: SELECT COUNT(*) FROM t_chat_message WHERE session_id = ? AND role = ? AND deleted = 0
DROP INDEX IF EXISTS idx_session_role_deleted ON t_chat_message;
CREATE INDEX idx_session_role_deleted ON t_chat_message(session_id, role, deleted) 
COMMENT '会话消息角色统计索引-优化消息角色统计';

-- =====================================================
-- 第三阶段: 辅助优化索引 (1个月内执行)
-- =====================================================

SELECT '=== 第三阶段: 创建辅助优化索引 ===' AS stage;

-- 索引8: AI模型使用统计优化 (低优先级)
-- 场景: 统计AI模型的使用情况
-- SQL: SELECT COUNT(*) FROM t_chat_message WHERE model_id = ? AND create_time >= ? AND deleted = 0
DROP INDEX IF EXISTS idx_model_time_deleted ON t_chat_message;
CREATE INDEX idx_model_time_deleted ON t_chat_message(model_id, create_time DESC, deleted) 
COMMENT 'AI模型使用统计索引-优化模型使用分析';

-- 索引9: 会话状态查询优化 (低优先级)
-- 场景: 查询特定状态的会话
-- SQL: SELECT * FROM t_chat_session WHERE user_id = ? AND status = ? AND deleted = 0
DROP INDEX IF EXISTS idx_user_status_deleted ON t_chat_session;
CREATE INDEX idx_user_status_deleted ON t_chat_session(user_id, status, deleted) 
COMMENT '会话状态查询索引-优化会话状态过滤';

-- =====================================================
-- 索引创建完成验证
-- =====================================================

-- 更新表统计信息
ANALYZE TABLE t_chat_message;
ANALYZE TABLE t_chat_session;
ANALYZE TABLE t_user_order;

SELECT '=== 索引创建完成，开始验证 ===' AS verification;

-- 验证新创建的索引
SELECT 
    TABLE_NAME AS '表名',
    INDEX_NAME AS '索引名',
    GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ', ') AS '索引字段',
    INDEX_COMMENT AS '索引说明'
FROM information_schema.STATISTICS 
WHERE TABLE_SCHEMA = 'chat' 
  AND INDEX_NAME IN (
    'idx_session_time_deleted',
    'idx_user_time_deleted', 
    'idx_user_active_deleted',
    'idx_user_status_time',
    'idx_user_date_deleted',
    'idx_time_status',
    'idx_session_role_deleted',
    'idx_model_time_deleted',
    'idx_user_status_deleted'
  )
GROUP BY TABLE_NAME, INDEX_NAME, INDEX_COMMENT
ORDER BY TABLE_NAME, INDEX_NAME;

-- =====================================================
-- 性能测试查询示例
-- =====================================================

SELECT '=== 性能测试查询示例 ===' AS testing;

-- 以下查询可用于测试索引效果，使用EXPLAIN查看执行计划

-- 测试1: 会话消息加载 (应使用 idx_session_time_deleted)
/*
EXPLAIN SELECT * FROM t_chat_message 
WHERE session_id = '74cb1bd224d9448fa5ebe8665c589063' AND deleted = 0 
ORDER BY create_time ASC LIMIT 50;
*/

-- 测试2: 用户消息历史 (应使用 idx_user_time_deleted)
/*
EXPLAIN SELECT * FROM t_chat_message 
WHERE user_id = 1931350026773823490 AND deleted = 0 
ORDER BY create_time DESC LIMIT 20;
*/

-- 测试3: 用户会话列表 (应使用 idx_user_active_deleted)
/*
EXPLAIN SELECT * FROM t_chat_session 
WHERE user_id = 1931350026773823490 AND deleted = 0 
ORDER BY last_active_time DESC LIMIT 10;
*/

-- 测试4: 用户订单查询 (应使用 idx_user_status_time)
/*
EXPLAIN SELECT * FROM t_user_order 
WHERE user_id = 1931350026773823490 AND payment_status = 'SUCCESS' 
ORDER BY create_time DESC;
*/

-- =====================================================
-- 性能监控查询
-- =====================================================

SELECT '=== 性能监控查询 ===' AS monitoring;

-- 查询1: 检查索引大小
SELECT 
    TABLE_NAME AS '表名',
    INDEX_NAME AS '索引名',
    ROUND(stat_value * @@innodb_page_size / 1024 / 1024, 2) AS '大小MB'
FROM mysql.innodb_index_stats 
WHERE database_name = 'chat' 
  AND stat_name = 'size'
  AND index_name LIKE 'idx_%'
ORDER BY stat_value DESC;

-- 查询2: 检查表统计信息
SELECT 
    TABLE_NAME AS '表名',
    TABLE_ROWS AS '估计行数',
    ROUND(DATA_LENGTH/1024/1024, 2) AS '数据大小MB',
    ROUND(INDEX_LENGTH/1024/1024, 2) AS '索引大小MB',
    ROUND(INDEX_LENGTH/DATA_LENGTH*100, 2) AS '索引比例%'
FROM information_schema.TABLES 
WHERE TABLE_SCHEMA = 'chat' 
  AND TABLE_NAME IN ('t_chat_message', 't_chat_session', 't_user_order')
ORDER BY DATA_LENGTH DESC;

-- =====================================================
-- 维护建议
-- =====================================================

SELECT '=== 维护建议 ===' AS maintenance;

SELECT '
定期维护任务建议:

1. 每周执行 (建议周日凌晨):
   ANALYZE TABLE t_chat_message, t_chat_session, t_user_order;

2. 每月检查索引使用情况:
   SELECT * FROM sys.schema_unused_indexes WHERE object_schema = ''chat'';

3. 监控慢查询:
   SET GLOBAL slow_query_log = ''ON'';
   SET GLOBAL long_query_time = 1;

4. 检查索引碎片 (每季度):
   SELECT TABLE_NAME, DATA_FREE/1024/1024 AS free_mb 
   FROM information_schema.TABLES 
   WHERE TABLE_SCHEMA = ''chat'' AND DATA_FREE > 0;

预期性能提升:
- 会话消息加载: 2-5秒 → 50-100ms (提升95%+)
- 用户消息历史: 1-3秒 → 100-200ms (提升90%+)  
- 用户会话列表: 800ms-2s → 50-150ms (提升90%+)
- 订单状态查询: 800ms-2s → 100-200ms (提升85%+)
' AS maintenance_info;

-- =====================================================
-- 回滚脚本 (如需要)
-- =====================================================

/*
如果需要回滚索引，执行以下语句:

DROP INDEX idx_session_time_deleted ON t_chat_message;
DROP INDEX idx_user_time_deleted ON t_chat_message;
DROP INDEX idx_user_active_deleted ON t_chat_session;
DROP INDEX idx_user_status_time ON t_user_order;
DROP INDEX idx_user_date_deleted ON t_chat_message;
DROP INDEX idx_time_status ON t_user_order;
DROP INDEX idx_session_role_deleted ON t_chat_message;
DROP INDEX idx_model_time_deleted ON t_chat_message;
DROP INDEX idx_user_status_deleted ON t_chat_session;
*/

-- 恢复设置
SET FOREIGN_KEY_CHECKS = 1;

SELECT '=== jinyu-chat 索引优化完成 ===' AS completion, NOW() AS completion_time;

-- =====================================================
-- 执行说明
-- =====================================================
/*
执行步骤:
1. 备份数据库 (生产环境必须)
2. 在低峰期执行此脚本
3. 执行后运行性能测试查询验证效果
4. 监控系统性能变化
5. 如有问题可使用回滚脚本

注意事项:
- 大表创建索引可能需要较长时间
- 建议分阶段执行，先执行第一阶段
- 监控磁盘空间，索引会占用额外存储
- 定期维护索引以保持最佳性能
*/
