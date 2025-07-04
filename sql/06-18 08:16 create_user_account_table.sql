-- 创建用户账户表的SQL脚本
-- 创建时间: 2025-06-18 08:16:49 (北京时间)
-- 版本: v1.0
-- 目的: 创建t_user_account表，支持套餐管理功能

-- 1. 检查表是否存在
SELECT 'Checking if t_user_account table exists...' as info;
SELECT COUNT(*) as table_exists 
FROM information_schema.tables 
WHERE table_schema = DATABASE() AND table_name = 't_user_account';

-- 2. 创建用户账户表（如果不存在）
CREATE TABLE IF NOT EXISTS t_user_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    package_id BIGINT NULL COMMENT '当前套餐ID',
    package_expire_time DATETIME NULL COMMENT '套餐过期时间',
    chat_count_remaining INT DEFAULT 0 COMMENT '剩余对话次数（用于总次数限制模式）',
    total_chat_count INT DEFAULT 0 COMMENT '总对话次数（用于统计）',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    -- 索引
    UNIQUE KEY uk_user_id (user_id) COMMENT '用户ID唯一索引',
    KEY idx_package_id (package_id) COMMENT '套餐ID索引',
    KEY idx_package_expire_time (package_expire_time) COMMENT '过期时间索引',
    
    -- 外键约束
    CONSTRAINT fk_user_account_user FOREIGN KEY (user_id) REFERENCES t_user (id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_user_account_package FOREIGN KEY (package_id) REFERENCES t_package (id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户账户表';

-- 3. 验证表创建结果
SELECT 'Verifying table creation...' as info;
DESCRIBE t_user_account;

-- 4. 检查索引创建情况
SELECT 'Checking indexes...' as info;
SHOW INDEX FROM t_user_account;

-- 5. 检查外键约束
SELECT 'Checking foreign key constraints...' as info;
SELECT 
    CONSTRAINT_NAME,
    COLUMN_NAME,
    REFERENCED_TABLE_NAME,
    REFERENCED_COLUMN_NAME,
    UPDATE_RULE,
    DELETE_RULE
FROM information_schema.KEY_COLUMN_USAGE 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_user_account' 
  AND REFERENCED_TABLE_NAME IS NOT NULL;

-- 6. 插入测试数据（可选）
-- 注意：这里使用存在的用户ID进行测试
SELECT 'Inserting test data...' as info;

-- 获取一个存在的用户ID用于测试
SET @test_user_id = (SELECT id FROM t_user LIMIT 1);
SET @test_package_id = (SELECT id FROM t_package LIMIT 1);

-- 如果找到了测试用户和套餐，插入测试数据
INSERT IGNORE INTO t_user_account 
(user_id, package_id, package_expire_time, chat_count_remaining, total_chat_count)
SELECT 
    @test_user_id,
    @test_package_id,
    DATE_ADD(NOW(), INTERVAL 30 DAY),
    100,
    0
WHERE @test_user_id IS NOT NULL AND @test_package_id IS NOT NULL;

-- 7. 验证测试数据
SELECT 'Verifying test data...' as info;
SELECT 
    id,
    user_id,
    package_id,
    package_expire_time,
    chat_count_remaining,
    total_chat_count,
    created_time
FROM t_user_account 
WHERE user_id = @test_user_id;

-- 8. 测试原子性操作
SELECT 'Testing atomic operations...' as info;

-- 测试insertOrUpdateAccount操作
INSERT INTO t_user_account 
(user_id, package_id, package_expire_time, chat_count_remaining, total_chat_count, created_time, update_time)
VALUES (@test_user_id, @test_package_id, DATE_ADD(NOW(), INTERVAL 60 DAY), 200, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE 
    package_id = @test_package_id,
    package_expire_time = DATE_ADD(NOW(), INTERVAL 60 DAY),
    chat_count_remaining = 200,
    update_time = NOW();

-- 验证原子性操作结果
SELECT 'Atomic operation result:' as info;
SELECT 
    user_id,
    package_id,
    package_expire_time,
    chat_count_remaining,
    update_time
FROM t_user_account 
WHERE user_id = @test_user_id;

-- 9. 清理测试数据
SELECT 'Cleaning up test data...' as info;
DELETE FROM t_user_account WHERE user_id = @test_user_id;

-- 10. 性能优化建议
SELECT 'Performance optimization suggestions:' as info;
SELECT '1. Consider adding composite index on (package_id, package_expire_time) for expired package queries' as suggestion
UNION ALL
SELECT '2. Consider partitioning by created_time if table grows large' as suggestion
UNION ALL
SELECT '3. Regular cleanup of expired records to maintain performance' as suggestion;

-- 11. 最终状态检查
SELECT 'Final status check:' as info;
SELECT 
    COUNT(*) as total_records,
    COUNT(CASE WHEN package_id IS NOT NULL THEN 1 END) as accounts_with_package,
    COUNT(CASE WHEN package_expire_time IS NOT NULL AND package_expire_time > NOW() THEN 1 END) as active_packages,
    COUNT(CASE WHEN package_expire_time IS NOT NULL AND package_expire_time <= NOW() THEN 1 END) as expired_packages
FROM t_user_account;

SELECT 'User account table creation completed successfully!' as completion_message;
