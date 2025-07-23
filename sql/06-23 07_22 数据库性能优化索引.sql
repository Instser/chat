-- =====================================================
-- 数据库性能优化索引创建脚本
-- =====================================================
-- 生成时间: 2025年6月23日 07:22 (北京时间)
-- 版本: v1.0
-- 项目: jinyu-chat 聊天系统
-- 目标: 解决数据库性能瓶颈，提升查询效率
-- =====================================================

-- 设置字符集和校对规则
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================
-- 第一阶段: 紧急优化索引 (立即执行)
-- 解决严重性能问题，提升用户体验
-- =====================================================

-- 1. t_chat_message 表优化索引
-- =====================================================

-- 索引1: 会话消息时间查询索引 (最高优先级)
-- 用途: 加载会话中的消息列表，按时间正序排列
-- 查询场景: SELECT * FROM t_chat_message WHERE session_id = ? AND deleted = 0 ORDER BY create_time ASC
-- 预期提升: 响应时间从2-5秒降低到50-100ms (提升95%+)
DROP INDEX IF EXISTS idx_chat_msg_session_time ON t_chat_message;
CREATE INDEX idx_chat_msg_session_time ON t_chat_message(session_id, create_time ASC, deleted) 
COMMENT '会话消息时间查询索引-用于加载会话消息列表';

-- 索引2: 用户消息时间查询索引 (高优先级)  
-- 用途: 查询用户的消息历史，按时间倒序排列
-- 查询场景: SELECT * FROM t_chat_message WHERE user_id = ? AND deleted = 0 ORDER BY create_time DESC LIMIT 20
-- 预期提升: 响应时间从1-3秒降低到100-200ms (提升90%+)
DROP INDEX IF EXISTS idx_chat_msg_user_time ON t_chat_message;
CREATE INDEX idx_chat_msg_user_time ON t_chat_message(user_id, create_time DESC, deleted) 
COMMENT '用户消息时间查询索引-用于用户消息历史查询';

-- 2. t_chat_session 表优化索引
-- =====================================================

-- 索引3: 用户会话活跃时间查询索引 (高优先级)
-- 用途: 获取用户的会话列表，按最后活跃时间倒序排列
-- 查询场景: SELECT * FROM t_chat_session WHERE user_id = ? AND deleted = 0 ORDER BY last_active_time DESC LIMIT 10
-- 预期提升: 响应时间从800ms-2s降低到50-150ms (提升90%+)
DROP INDEX IF EXISTS idx_chat_session_user_active ON t_chat_session;
CREATE INDEX idx_chat_session_user_active ON t_chat_session(user_id, last_active_time DESC, deleted) 
COMMENT '用户会话活跃时间查询索引-用于会话列表查询';

-- 3. t_user_order 表优化索引
-- =====================================================

-- 索引4: 用户订单状态时间查询索引 (高优先级)
-- 用途: 查询用户的订单，支持状态过滤和时间排序
-- 查询场景: SELECT * FROM t_user_order WHERE user_id = ? AND payment_status = ? ORDER BY create_time DESC
-- 预期提升: 响应时间从800ms-2s降低到100-200ms (提升85%+)
DROP INDEX IF EXISTS idx_user_order_status_time ON t_user_order;
CREATE INDEX idx_user_order_status_time ON t_user_order(user_id, payment_status, create_time DESC) 
COMMENT '用户订单状态时间查询索引-用于订单管理查询';

-- =====================================================
-- 第二阶段: 性能优化索引 (1周内执行)
-- 进一步提升查询性能，优化复杂查询场景
-- =====================================================

-- 索引5: 用户消息统计查询索引 (中优先级)
-- 用途: 统计用户在特定时间范围内的消息数量
-- 查询场景: SELECT COUNT(*) FROM t_chat_message WHERE user_id = ? AND create_time >= ? AND deleted = 0
-- 预期提升: 响应时间从500ms-1s降低到100-200ms (提升80%+)
DROP INDEX IF EXISTS idx_chat_msg_user_date ON t_chat_message;
CREATE INDEX idx_chat_msg_user_date ON t_chat_message(user_id, deleted, create_time) 
COMMENT '用户消息统计查询索引-用于消息数量统计';

-- 索引6: 订单时间状态统计索引 (中优先级)
-- 用途: 按时间范围统计订单状态分布
-- 查询场景: SELECT COUNT(*), payment_status FROM t_user_order WHERE create_time >= ? GROUP BY payment_status
-- 预期提升: 响应时间从1-2s降低到200-400ms (提升75%+)
DROP INDEX IF EXISTS idx_user_order_time_status ON t_user_order;
CREATE INDEX idx_user_order_time_status ON t_user_order(create_time DESC, payment_status) 
COMMENT '订单时间状态统计索引-用于订单统计分析';

-- 索引7: 会话消息数量统计索引 (中优先级)
-- 用途: 统计会话中的消息数量，支持角色过滤
-- 查询场景: SELECT COUNT(*) FROM t_chat_message WHERE session_id = ? AND role = ? AND deleted = 0
DROP INDEX IF EXISTS idx_chat_msg_session_role ON t_chat_message;
CREATE INDEX idx_chat_msg_session_role ON t_chat_message(session_id, role, deleted) 
COMMENT '会话消息角色统计索引-用于消息统计';

-- =====================================================
-- 第三阶段: 辅助优化索引 (1个月内执行)
-- 完善查询覆盖，优化特殊场景
-- =====================================================

-- 索引8: 模型消息查询索引 (低优先级)
-- 用途: 按AI模型查询消息，用于模型使用统计
-- 查询场景: SELECT * FROM t_chat_message WHERE model_id = ? AND create_time >= ? AND deleted = 0
DROP INDEX IF EXISTS idx_chat_msg_model_time ON t_chat_message;
CREATE INDEX idx_chat_msg_model_time ON t_chat_message(model_id, create_time DESC, deleted) 
COMMENT '模型消息查询索引-用于模型使用统计';

-- 索引9: 用户账户余额查询优化 (低优先级)
-- 用途: 优化用户账户信息查询 (已有uk_user_id唯一索引，此处为覆盖索引优化)
-- 查询场景: SELECT balance, total_balance FROM t_user_account WHERE user_id = ? AND deleted = 0
DROP INDEX IF EXISTS idx_user_account_balance ON t_user_account;
CREATE INDEX idx_user_account_balance ON t_user_account(user_id, deleted, balance, total_balance) 
COMMENT '用户账户余额查询覆盖索引-减少回表查询';

-- 索引10: 套餐查询优化索引 (低优先级)
-- 用途: 优化可用套餐查询
-- 查询场景: SELECT * FROM t_package WHERE is_active = 1 AND deleted = 0 ORDER BY sort_order
DROP INDEX IF EXISTS idx_package_active_sort ON t_package;
CREATE INDEX idx_package_active_sort ON t_package(is_active, deleted, sort_order) 
COMMENT '套餐查询优化索引-用于套餐列表查询';

-- =====================================================
-- 索引创建验证和统计信息更新
-- =====================================================

-- 更新表统计信息，帮助优化器做出更好的执行计划选择
ANALYZE TABLE t_chat_message;
ANALYZE TABLE t_chat_session;  
ANALYZE TABLE t_user_order;
ANALYZE TABLE t_user_account;
ANALYZE TABLE t_package;

-- =====================================================
-- 索引使用情况查询脚本
-- =====================================================

-- 查询1: 检查新创建的索引
SELECT 
    TABLE_NAME,
    INDEX_NAME,
    COLUMN_NAME,
    SEQ_IN_INDEX,
    INDEX_COMMENT
FROM information_schema.STATISTICS 
WHERE TABLE_SCHEMA = 'chat' 
  AND INDEX_NAME LIKE 'idx_%'
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;

-- 查询2: 检查索引大小
SELECT 
    TABLE_NAME,
    INDEX_NAME,
    ROUND(stat_value * @@innodb_page_size / 1024 / 1024, 2) AS size_mb
FROM mysql.innodb_index_stats 
WHERE database_name = 'chat' 
  AND stat_name = 'size'
  AND index_name LIKE 'idx_%'
ORDER BY stat_value DESC;

-- =====================================================
-- 性能测试查询示例
-- =====================================================

-- 测试查询1: 会话消息加载 (应该使用 idx_chat_msg_session_time)
-- EXPLAIN SELECT * FROM t_chat_message 
-- WHERE session_id = '74cb1bd224d9448fa5ebe8665c589063' AND deleted = 0 
-- ORDER BY create_time ASC LIMIT 50;

-- 测试查询2: 用户消息历史 (应该使用 idx_chat_msg_user_time)  
-- EXPLAIN SELECT * FROM t_chat_message 
-- WHERE user_id = 1931350026773823490 AND deleted = 0 
-- ORDER BY create_time DESC LIMIT 20;

-- 测试查询3: 用户会话列表 (应该使用 idx_chat_session_user_active)
-- EXPLAIN SELECT * FROM t_chat_session 
-- WHERE user_id = 1931350026773823490 AND deleted = 0 
-- ORDER BY last_active_time DESC LIMIT 10;

-- 测试查询4: 用户订单查询 (应该使用 idx_user_order_status_time)
-- EXPLAIN SELECT * FROM t_user_order 
-- WHERE user_id = 1931350026773823490 AND payment_status = 'SUCCESS' 
-- ORDER BY create_time DESC;

-- =====================================================
-- 监控和维护建议
-- =====================================================

-- 1. 定期检查索引使用情况 (建议每周执行)
-- SELECT * FROM sys.schema_unused_indexes WHERE object_schema = 'chat';

-- 2. 定期更新统计信息 (建议每周执行)
-- ANALYZE TABLE t_chat_message, t_chat_session, t_user_order;

-- 3. 监控慢查询日志
-- SET GLOBAL slow_query_log = 'ON';
-- SET GLOBAL long_query_time = 1;

-- 4. 定期检查索引碎片 (建议每月执行)
-- SELECT TABLE_NAME, ENGINE, TABLE_ROWS, DATA_LENGTH, INDEX_LENGTH,
--        ROUND(INDEX_LENGTH/DATA_LENGTH,2) AS index_ratio
-- FROM information_schema.TABLES 
-- WHERE TABLE_SCHEMA = 'chat' AND ENGINE = 'InnoDB'
-- ORDER BY DATA_LENGTH DESC;

-- =====================================================
-- 回滚脚本 (如果需要删除索引)
-- =====================================================

-- 如果需要回滚，可以执行以下语句删除创建的索引:
/*
DROP INDEX idx_chat_msg_session_time ON t_chat_message;
DROP INDEX idx_chat_msg_user_time ON t_chat_message;
DROP INDEX idx_chat_session_user_active ON t_chat_session;
DROP INDEX idx_user_order_status_time ON t_user_order;
DROP INDEX idx_chat_msg_user_date ON t_chat_message;
DROP INDEX idx_user_order_time_status ON t_user_order;
DROP INDEX idx_chat_msg_session_role ON t_chat_message;
DROP INDEX idx_chat_msg_model_time ON t_chat_message;
DROP INDEX idx_user_account_balance ON t_user_account;
DROP INDEX idx_package_active_sort ON t_package;
*/

-- 恢复外键检查
SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================
-- 脚本执行完成
-- =====================================================
-- 执行完成后，建议：
-- 1. 运行性能测试查询验证优化效果
-- 2. 监控系统性能指标变化
-- 3. 观察慢查询日志的改善情况
-- 4. 定期维护和监控索引使用情况
-- =====================================================
