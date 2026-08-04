-- ============================================================
-- t_operation_audit 管理员审计字段回滚
-- 时间: 2026-08-03 (北京时间)
-- 数据库: MySQL 8.0
-- ============================================================

DROP INDEX `idx_op_audit_created_deleted` ON `t_operation_audit`;
DROP INDEX `idx_op_audit_operator_time` ON `t_operation_audit`;
DROP INDEX `idx_op_audit_type_time` ON `t_operation_audit`;
DROP INDEX `idx_op_audit_target_time` ON `t_operation_audit`;

ALTER TABLE `t_operation_audit`
    DROP COLUMN `operator_role`,
    DROP COLUMN `request_method`,
    DROP COLUMN `request_path`,
    DROP COLUMN `user_agent`,
    DROP COLUMN `request_summary`,
    DROP COLUMN `old_value`,
    DROP COLUMN `new_value`,
    DROP COLUMN `failure_reason`,
    DROP COLUMN `duration_ms`,
    DROP COLUMN `risk_level`;
