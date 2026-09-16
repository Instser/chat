-- ========================================
-- 模型API密钥池：新建 t_ai_model_key
-- 创建时间: 2026-09-16 10:48
-- 版本: v1.0
-- 数据库: MySQL 8.0
-- 描述:
--   语义修正：同 model_code 的多行 t_ai_model 是不同模型级别（BEST/PERFORMANCE/STANDARD），
--   不允许跨行轮换。轮询维度从「model_code 的多行」改为「单个模型行（model_id）内的多个
--   API key」，即每行模型单独配多个密钥、在行内按有效权重加权随机轮询。
--
--   本表为「模型API密钥池」：一行 = 某模型（t_ai_model.id）下的一个可用 API key。
--   有效权重 = max(1, weight - failPenalty × consecutive_fails)，连续失败自动降权、成功清零。
--
--   不做数据迁移：t_ai_model.api_key 保留为「未配置密钥池时」的回退单 key，
--   服务在池为空/查询异常时自动回退该列，历史模型无需改动即可继续工作。
--
--   不建外键（保持与其它表一致的松散关联），不建 api_key 唯一索引（去重由应用层按
--   model_id + 未删除范围校验，避免逻辑删除后无法重新录入同一 key）。
--
-- ⚠️ 执行前请先备份数据库（至少备份 t_ai_model 表）：
--   mysqldump -u<user> -p<password> --single-transaction <database> t_ai_model > t_ai_model_backup.sql
-- ========================================

-- 1. 执行前检查：表若已存在请跳过（MySQL 8.0 不支持 CREATE TABLE IF NOT EXISTS 之外的条件创建）
SELECT TABLE_NAME
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 't_ai_model_key';

-- 2. 建表
CREATE TABLE IF NOT EXISTS `t_ai_model_key` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `model_id` bigint NOT NULL COMMENT '所属模型行ID（t_ai_model.id，轮询维度：行内多key）',
  `api_key` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'API密钥（明文，仅服务端使用，接口一律脱敏返回）',
  `remark` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注（便于区分渠道/供应商）',
  `weight` int NOT NULL DEFAULT '100' COMMENT '基础权重（1-100，越大被选中概率越高）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态（0-禁用，1-启用）',
  `success_count` bigint NOT NULL DEFAULT '0' COMMENT '累计成功次数',
  `fail_count` bigint NOT NULL DEFAULT '0' COMMENT '累计失败次数',
  `consecutive_fails` int NOT NULL DEFAULT '0' COMMENT '连续失败次数（成功清零，用于动态降权）',
  `last_error` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近一次失败错误摘要（脱敏后截断255）',
  `last_used_time` datetime DEFAULT NULL COMMENT '最近一次使用时间',
  `last_success_time` datetime DEFAULT NULL COMMENT '最近一次成功时间',
  `last_fail_time` datetime DEFAULT NULL COMMENT '最近一次失败时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标志（0-未删除，1-已删除）',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_model_id` (`model_id`, `deleted`, `status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='模型API密钥池';

-- 3. 验证
SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 't_ai_model_key'
ORDER BY ORDINAL_POSITION;

SELECT COUNT(*) AS `key_count` FROM `t_ai_model_key` WHERE `deleted` = 0;
