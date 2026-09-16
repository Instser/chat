-- V3: 模型API密钥池——新建 t_ai_model_key（轮询维度：model_id 行内多 key）
-- 对应手动执行版：sql/09-16 10_48_模型密钥池表.sql
--
-- 说明：同 model_code 的多行 t_ai_model 是不同模型级别（BEST/PERFORMANCE/STANDARD），
-- 不允许跨行轮换；因此把轮询分组从「同 model_code 的多行」改为「单个模型行（model_id）
-- 内的多个 API key」。本迁移仅新建表，不建外键、不做数据迁移——t_ai_model.api_key
-- 保留为「未配置密钥池时」的回退单 key。
--
-- 幂等设计：生产环境 Flyway 当前为禁用状态（FLYWAY_ENABLED=false，chat 用户缺
-- user_variables_by_thread 权限），本表由人工/运维先行执行过；将来启用 Flyway 后
-- 本脚本会再执行一次，因此使用 CREATE TABLE IF NOT EXISTS，
-- 避免 "Table already exists" 导致启动失败。

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
