-- ============================================================
-- t_operation_audit 管理员审计字段增量
-- 时间: 2026-08-03 (北京时间)
-- 数据库: MySQL 8.0
-- 说明: 幂等增量，旧版本应用可忽略新列
-- ============================================================

-- 前置检查
SELECT COUNT(*) AS table_exists
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 't_operation_audit';

-- 增量列（若列已存在会报错，执行前请核对 information_schema）
ALTER TABLE `t_operation_audit`
    ADD COLUMN `operator_role` VARCHAR(32) DEFAULT NULL COMMENT '操作时角色快照' AFTER `operator_id`,
    ADD COLUMN `request_method` VARCHAR(16) DEFAULT NULL COMMENT 'HTTP 方法' AFTER `trace_id`,
    ADD COLUMN `request_path` VARCHAR(255) DEFAULT NULL COMMENT '请求路径' AFTER `request_method`,
    ADD COLUMN `user_agent` VARCHAR(512) DEFAULT NULL COMMENT 'User-Agent' AFTER `request_path`,
    ADD COLUMN `request_summary` VARCHAR(1000) DEFAULT NULL COMMENT '脱敏请求摘要' AFTER `user_agent`,
    ADD COLUMN `old_value` TEXT DEFAULT NULL COMMENT '变更前值(白名单字段)' AFTER `request_summary`,
    ADD COLUMN `new_value` TEXT DEFAULT NULL COMMENT '变更后值(白名单字段)' AFTER `old_value`,
    ADD COLUMN `failure_reason` VARCHAR(500) DEFAULT NULL COMMENT '失败摘要' AFTER `new_value`,
    ADD COLUMN `duration_ms` BIGINT DEFAULT NULL COMMENT '执行耗时毫秒' AFTER `failure_reason`,
    ADD COLUMN `risk_level` VARCHAR(16) DEFAULT NULL COMMENT 'LOW/MEDIUM/HIGH/CRITICAL' AFTER `duration_ms`;

-- 索引
CREATE INDEX `idx_op_audit_created_deleted` ON `t_operation_audit` (`created_time`, `deleted`);
CREATE INDEX `idx_op_audit_operator_time` ON `t_operation_audit` (`operator_id`, `created_time`);
CREATE INDEX `idx_op_audit_type_time` ON `t_operation_audit` (`operation_type`, `created_time`);
CREATE INDEX `idx_op_audit_target_time` ON `t_operation_audit` (`target`, `created_time`);

-- 验证
SELECT COLUMN_NAME, DATA_TYPE, COLUMN_COMMENT
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 't_operation_audit'
ORDER BY ORDINAL_POSITION;

-- 回滚脚本见: sql/2026-08-03_t_operation_audit_admin_fields_rollback.sql
