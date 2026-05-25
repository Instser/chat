-- ============================================================
-- 通用运维操作审计表
-- 用于记录缓存刷新、日志级别变更等运维操作
-- 创建时间: 2026-05-26 00:00 (北京时间)
-- ============================================================

CREATE TABLE IF NOT EXISTS `t_operation_audit` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `operator`        VARCHAR(64)   DEFAULT NULL COMMENT '操作人（用户名/系统）',
    `operator_id`     BIGINT        DEFAULT NULL COMMENT '操作人ID',
    `operation_type`  VARCHAR(32)   NOT NULL COMMENT '操作类型（CACHE_REFRESH/LOG_LEVEL_CHANGE/CONFIG_CHANGE等）',
    `target`          VARCHAR(128)  DEFAULT NULL COMMENT '操作对象（缓存key/接口路径/配置项等）',
    `target_detail`   TEXT          DEFAULT NULL COMMENT '操作对象详情（JSON）',
    `result`          VARCHAR(16)   DEFAULT NULL COMMENT '操作结果（SUCCESS/FAILED）',
    `client_ip`       VARCHAR(45)   DEFAULT NULL COMMENT '客户端IP',
    `trace_id`        VARCHAR(64)   DEFAULT NULL COMMENT '调用链ID',
    `created_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted`         TINYINT       DEFAULT 0 COMMENT '逻辑删除标记(0:未删除,1:已删除)',
    PRIMARY KEY (`id`),
    KEY `idx_operation_type` (`operation_type`),
    KEY `idx_operator` (`operator`),
    KEY `idx_created_time` (`created_time`),
    KEY `idx_trace_id` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通用运维操作审计表';
