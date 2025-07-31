-- =====================================================
-- 修复t_user_account表update_time字段时区问题
-- 创建时间: 2025-07-29 20:49:00 (北京时间)
-- 修复目标: 统一时区处理，移除数据库自动更新机制
-- =====================================================

USE chat;

-- 1. 备份当前表结构和数据（可选，生产环境建议执行）
-- CREATE TABLE t_user_account_backup_20250729 AS SELECT * FROM t_user_account;

-- 2. 修改update_time字段，移除ON UPDATE CURRENT_TIMESTAMP
-- 这样可以完全由应用层的MyBatis-Plus自动填充机制控制时间
ALTER TABLE t_user_account 
MODIFY COLUMN update_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间';

-- 3. 验证表结构修改
SHOW CREATE TABLE t_user_account;

-- 4. 检查现有数据的时区一致性（可选）
-- 查看最近的记录，检查时间戳是否合理
SELECT 
    id,
    user_id,
    create_time,
    update_time,
    TIMESTAMPDIFF(SECOND, create_time, update_time) as time_diff_seconds
FROM t_user_account 
WHERE deleted = 0 
ORDER BY id DESC 
LIMIT 10;

-- 5. 设置会话时区为Asia/Shanghai（确保当前会话时区正确）
SET time_zone = '+08:00';

-- 6. 验证时区设置
SELECT
    NOW() as current_time,
    @@session.time_zone as session_timezone,
    @@global.time_zone as global_timezone;

-- =====================================================
-- 说明：
-- 1. 移除ON UPDATE CURRENT_TIMESTAMP后，update_time字段将完全由应用层控制
-- 2. MyBatis-Plus的自动填充机制会在每次更新时自动设置update_time
-- 3. 自动填充使用的是Asia/Shanghai时区，确保时区一致性
-- 4. 原子性操作（如decrementChatCount）仍然需要在Mapper XML中处理
-- =====================================================
