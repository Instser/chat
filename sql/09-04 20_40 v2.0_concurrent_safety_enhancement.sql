-- v2.0 并发安全优化 - 数据库结构更新
-- 创建时间: 2025-09-04
-- 说明: 为 t_user_account 表添加版本控制字段，支持乐观锁

-- 1. 添加版本控制字段
ALTER TABLE t_user_account ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL COMMENT '版本号，用于乐观锁控制';

-- 2. 创建版本字段索引
CREATE INDEX IF NOT EXISTS idx_user_account_version ON t_user_account(user_id, version);

-- 3. 更新现有记录的版本号
UPDATE t_user_account SET version = 0 WHERE version IS NULL;

-- 验证更新结果
SELECT 
    COUNT(*) as total_records,
    COUNT(CASE WHEN version = 0 THEN 1 END) as version_zero_records,
    MIN(version) as min_version,
    MAX(version) as max_version
FROM t_user_account;

-- 显示表结构变更
DESCRIBE t_user_account;