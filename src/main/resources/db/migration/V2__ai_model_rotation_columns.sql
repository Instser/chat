-- V2: 模型密钥轮询（行级）——t_ai_model 追加权重与可用性统计列
-- 对应手动执行版：sql/09-15 20_17_模型密钥轮询列.sql
-- 设计文档：docs/26-09-15_20-36_模型API密钥轮询行级权重_v1.0.md
--
-- 说明：「密钥池」= 同 model_code 的多行 t_ai_model（系统已允许 model_code 重复）。
-- 本迁移仅追加列，不新建表、不做数据迁移、不建外键/唯一索引。
--
-- 幂等设计：生产环境 Flyway 当前为禁用状态（FLYWAY_ENABLED=false，chat 用户缺
-- user_variables_by_thread 权限），本列由人工/运维先行执行过；将来启用 Flyway 后
-- 本脚本会再执行一次，因此用 information_schema 预检 + PREPARE 跳过已存在的列，
-- 避免 "Duplicate column name" 导致启动失败。

SET @v2_col_count := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_ai_model'
    AND COLUMN_NAME IN ('weight', 'success_count', 'fail_count', 'consecutive_fails',
                        'last_error', 'last_used_time', 'last_success_time', 'last_fail_time')
);

SET @v2_sql := IF(@v2_col_count = 0,
  'ALTER TABLE `t_ai_model`
     ADD COLUMN `weight` int NOT NULL DEFAULT 100 COMMENT ''基础权重（1-100，越大被选中概率越高）'' AFTER `api_key`,
     ADD COLUMN `success_count` bigint NOT NULL DEFAULT 0 COMMENT ''累计成功次数'' AFTER `weight`,
     ADD COLUMN `fail_count` bigint NOT NULL DEFAULT 0 COMMENT ''累计失败次数'' AFTER `success_count`,
     ADD COLUMN `consecutive_fails` int NOT NULL DEFAULT 0 COMMENT ''连续失败次数（成功清零，用于动态降权）'' AFTER `fail_count`,
     ADD COLUMN `last_error` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''最近一次失败错误摘要（截断255）'' AFTER `consecutive_fails`,
     ADD COLUMN `last_used_time` datetime DEFAULT NULL COMMENT ''最近一次使用时间'' AFTER `last_error`,
     ADD COLUMN `last_success_time` datetime DEFAULT NULL COMMENT ''最近一次成功时间'' AFTER `last_used_time`,
     ADD COLUMN `last_fail_time` datetime DEFAULT NULL COMMENT ''最近一次失败时间'' AFTER `last_success_time`',
  'SELECT ''V2: t_ai_model rotation columns already exist, skipped'' AS info');

PREPARE stmt FROM @v2_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 存量行权重兜底（DEFAULT 100 已覆盖新增场景，此处防御历史 NULL/越界值）
UPDATE `t_ai_model` SET `weight` = 100 WHERE `weight` IS NULL OR `weight` < 1 OR `weight` > 100;
