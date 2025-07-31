-- 修复t_user_account表update_time字段时区问题
-- 创建时间: 2025-07-29 20:49:00 (北京时间)

USE chat;

-- 1. 修改update_time字段，移除ON UPDATE CURRENT_TIMESTAMP
ALTER TABLE t_user_account 
MODIFY COLUMN update_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间';

-- 2. 验证表结构修改
SHOW CREATE TABLE t_user_account;

-- 3. 检查现有数据的时区一致性
SELECT 
    id,
    user_id,
    create_time,
    update_time,
    TIMESTAMPDIFF(SECOND, create_time, update_time) as time_diff_seconds
FROM t_user_account 
WHERE deleted = 0 
ORDER BY id DESC 
LIMIT 5;

-- 4. 设置会话时区
SET time_zone = '+08:00';

-- 5. 验证时区设置
SELECT NOW() as current_time;
SELECT @@session.time_zone as session_timezone;
SELECT @@global.time_zone as global_timezone;
