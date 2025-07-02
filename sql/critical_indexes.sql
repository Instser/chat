-- 数据库索引优化脚本 - 一键执行版本
-- 针对每日限制逻辑和核心业务查询优化

-- 1. t_package表索引
CREATE INDEX idx_package_limit_type ON t_package (limit_type, deleted);
CREATE INDEX idx_package_list ON t_package (deleted, is_enabled, is_available, sort_order);

-- 2. t_user_account表索引
CREATE INDEX idx_account_package ON t_user_account (package_id, deleted);
CREATE INDEX idx_account_expire ON t_user_account (package_expire_time, deleted);

-- 3. t_ai_model表索引
CREATE INDEX idx_model_availability ON t_ai_model (is_available, is_visible, deleted);

-- 验证索引创建结果
SELECT
    CONCAT('✅ 索引 ', INDEX_NAME, ' 已创建') as result
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND INDEX_NAME IN ('idx_package_limit_type', 'idx_package_list', 'idx_account_package', 'idx_account_expire', 'idx_model_availability')
GROUP BY INDEX_NAME;

-- 完成提示
SELECT '🎉 索引优化完成！建议重启应用程序以获得最佳性能。' as completion_message;


