-- ========================================
-- 模型密钥轮询（行级）：t_ai_model 追加权重与可用性统计列
-- 创建时间: 2025-09-15 20:17
-- 版本: v1.0
-- 数据库: MySQL 8.0
-- 描述:
--   系统已允许 model_code 重复（同一模型代码可对应多套凭据/端点），
--   因此"密钥池"即同 model_code 的多行 t_ai_model。本次不新建表、不做数据迁移，
--   仅在 t_ai_model 上追加权重与统计列，支持：
--     有效权重 = max(1, weight - failPenalty × consecutive_fails)
--     连续失败自动降权、成功清零恢复。
--   不建外键、不建唯一索引（model_code 本就可重复）。
--
-- ⚠️ 执行前请先备份数据库（至少备份 t_ai_model 表）：
--   mysqldump -u<user> -p<password> --single-transaction <database> t_ai_model > t_ai_model_backup.sql
-- ========================================

-- 1. 执行前检查：以下 8 列若已存在，请跳过对应 ALTER 项（MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS）
SELECT COLUMN_NAME
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 't_ai_model'
  AND COLUMN_NAME IN ('weight', 'success_count', 'fail_count', 'consecutive_fails',
                      'last_error', 'last_used_time', 'last_success_time', 'last_fail_time');

-- 2. 追加列
ALTER TABLE `t_ai_model`
  ADD COLUMN `weight` int NOT NULL DEFAULT 100 COMMENT '基础权重（1-100，越大被选中概率越高）' AFTER `api_key`,
  ADD COLUMN `success_count` bigint NOT NULL DEFAULT 0 COMMENT '累计成功次数' AFTER `weight`,
  ADD COLUMN `fail_count` bigint NOT NULL DEFAULT 0 COMMENT '累计失败次数' AFTER `success_count`,
  ADD COLUMN `consecutive_fails` int NOT NULL DEFAULT 0 COMMENT '连续失败次数（成功清零，用于动态降权）' AFTER `fail_count`,
  ADD COLUMN `last_error` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近一次失败错误摘要（截断255）' AFTER `consecutive_fails`,
  ADD COLUMN `last_used_time` datetime DEFAULT NULL COMMENT '最近一次使用时间' AFTER `last_error`,
  ADD COLUMN `last_success_time` datetime DEFAULT NULL COMMENT '最近一次成功时间' AFTER `last_used_time`,
  ADD COLUMN `last_fail_time` datetime DEFAULT NULL COMMENT '最近一次失败时间' AFTER `last_success_time`;

-- 3. 存量行权重初始化（DEFAULT 100 已覆盖，此处显式兜底 NULL 场景）
UPDATE `t_ai_model` SET `weight` = 100 WHERE `weight` IS NULL OR `weight` < 1 OR `weight` > 100;

-- 4. 验证
SELECT `id`, `model_code`, `weight`, `success_count`, `fail_count`, `consecutive_fails`, `deleted`
FROM `t_ai_model`
WHERE `deleted` = 0
ORDER BY `model_code`, `id`;

-- 按 model_code 分组查看"可轮询"的行（同代码多行即为密钥池）
SELECT `model_code`, COUNT(*) AS row_count
FROM `t_ai_model`
WHERE `deleted` = 0 AND `is_available` = 1 AND `api_key` IS NOT NULL AND `api_key` != ''
GROUP BY `model_code`
HAVING COUNT(*) > 1;

-- 5. 表结构确认
DESCRIBE `t_ai_model`;

COMMIT;
