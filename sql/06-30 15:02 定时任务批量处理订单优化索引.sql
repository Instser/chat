-- 定时任务批量处理订单优化索引
-- 创建时间: 2025-06-30 15:02
-- 版本: v1.0
-- 说明: 为OptimizedScheduledTaskService的批量订单处理功能添加数据库索引优化

-- 1. 为过期订单查询优化添加复合索引
-- 用于查询: payment_status = 0 AND expire_time < NOW() AND deleted = 0
CREATE INDEX idx_order_expired_processing 
ON t_user_order (payment_status, expire_time, deleted, update_time);

-- 2. 为状态同步订单查询优化添加复合索引  
-- 用于查询: payment_status = 0 AND expire_time > NOW() AND update_time < DATE_SUB(NOW(), INTERVAL 1 HOUR) AND deleted = 0
CREATE INDEX idx_order_status_sync 
ON t_user_order (payment_status, expire_time, update_time, deleted);

-- 3. 为批量处理查询优化添加覆盖索引
-- 包含查询中需要的所有字段，避免回表查询
CREATE INDEX idx_order_batch_processing_covering 
ON t_user_order (payment_status, expire_time, deleted, update_time, order_no, user_id, package_id);

-- 4. 为支付同步任务优化添加索引
-- 用于查询: payment_status = 0 AND expire_time > NOW() AND create_time > #{afterTime} AND deleted = 0
CREATE INDEX idx_order_payment_sync 
ON t_user_order (payment_status, expire_time, create_time, deleted);

-- 5. 为订单清理任务优化添加索引
-- 用于更新: UPDATE t_user_order SET payment_status = 2 WHERE payment_status = 0 AND expire_time < NOW() AND deleted = 0
CREATE INDEX idx_order_cleanup 
ON t_user_order (payment_status, expire_time, deleted);

-- 6. 分析表统计信息，确保查询优化器使用最新的统计信息
ANALYZE TABLE t_user_order;

-- 7. 查看索引使用情况的查询语句（用于性能监控）
-- 可以定期执行以下查询来监控索引效果：

/*
-- 查看索引使用统计
SELECT 
    TABLE_NAME,
    INDEX_NAME,
    SEQ_IN_INDEX,
    COLUMN_NAME,
    CARDINALITY
FROM information_schema.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_user_order'
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;

-- 查看慢查询日志中相关的订单查询
SELECT 
    sql_text,
    exec_count,
    avg_timer_wait/1000000000 as avg_time_seconds,
    sum_timer_wait/1000000000 as total_time_seconds
FROM performance_schema.events_statements_summary_by_digest 
WHERE sql_text LIKE '%t_user_order%'
  AND sql_text LIKE '%payment_status%'
ORDER BY sum_timer_wait DESC
LIMIT 10;
*/

-- 注意事项:
-- 1. 这些索引主要针对定时任务的批量查询进行优化
-- 2. 索引会占用额外的存储空间，但能显著提升查询性能
-- 3. 在生产环境中建议在业务低峰期执行索引创建
-- 4. 建议定期监控索引的使用情况和查询性能
-- 5. 如果发现某些索引使用率很低，可以考虑删除以节省空间

-- 预期效果:
-- 1. 过期订单查询性能提升 60-80%
-- 2. 状态同步查询性能提升 50-70% 
-- 3. 批量处理查询避免全表扫描
-- 4. 支付同步任务查询性能提升 40-60%
-- 5. 订单清理任务执行时间减少 50%以上
