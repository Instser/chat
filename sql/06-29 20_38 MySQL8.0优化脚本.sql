/*
 MySQL 8.0 数据库优化脚本
 
 基于chat3.sql分析生成的优化建议
 创建时间: 2025-06-29 20:38 (北京时间)
 版本: v1.0
 
 执行前请备份数据库！
*/

-- ========================================
-- 第一阶段: 紧急修复和优化（高优先级）
-- ========================================

-- 1. 修复t_package表字段冗余问题
SELECT '🔧 修复t_package表字段冗余...' AS step;

-- 检查price字段是否存在
SELECT 
    CASE 
        WHEN COUNT(*) > 0 THEN 'price字段存在，需要删除'
        ELSE 'price字段不存在，无需处理'
    END as price_field_status
FROM information_schema.columns 
WHERE table_schema = 'chat' 
  AND table_name = 't_package' 
  AND column_name = 'price';

-- 安全删除price字段（如果存在）
SET @price_field_exists = (
    SELECT COUNT(*) 
    FROM information_schema.columns 
    WHERE table_schema = 'chat' 
    AND table_name = 't_package' 
    AND column_name = 'price'
);

SET @sql_drop_price = CASE 
    WHEN @price_field_exists > 0 THEN 
        'ALTER TABLE t_package DROP COLUMN price'
    ELSE 
        'SELECT "price字段不存在，无需删除" as message'
END;

PREPARE stmt FROM @sql_drop_price;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 添加t_chat_session缺失的message_count字段
SELECT '📊 添加t_chat_session.message_count字段...' AS step;

-- 检查message_count字段是否存在
SET @msg_count_exists = (
    SELECT COUNT(*) 
    FROM information_schema.columns 
    WHERE table_schema = 'chat' 
    AND table_name = 't_chat_session' 
    AND column_name = 'message_count'
);

SET @sql_add_msg_count = CASE 
    WHEN @msg_count_exists = 0 THEN 
        'ALTER TABLE t_chat_session ADD COLUMN message_count int DEFAULT 0 COMMENT "消息数量"'
    ELSE 
        'SELECT "message_count字段已存在" as message'
END;

PREPARE stmt FROM @sql_add_msg_count;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 创建缺失的t_user_package表
SELECT '📋 创建t_user_package表...' AS step;

-- 检查表是否存在
SET @user_package_exists = (
    SELECT COUNT(*) 
    FROM information_schema.tables 
    WHERE table_schema = 'chat' 
    AND table_name = 't_user_package'
);

SET @sql_create_user_package = CASE 
    WHEN @user_package_exists = 0 THEN 
        'CREATE TABLE t_user_package (
            id bigint NOT NULL AUTO_INCREMENT COMMENT "主键ID",
            user_id bigint NOT NULL COMMENT "用户ID",
            package_id bigint NOT NULL COMMENT "套餐ID",
            chat_used int DEFAULT 0 COMMENT "已使用聊天次数",
            chat_limit int NOT NULL COMMENT "聊天次数限制",
            expire_time datetime NOT NULL COMMENT "过期时间",
            is_active tinyint DEFAULT 1 COMMENT "是否激活",
            create_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT "创建时间",
            update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT "更新时间",
            deleted tinyint DEFAULT 0 COMMENT "逻辑删除标志",
            PRIMARY KEY (id),
            KEY idx_user_id (user_id),
            KEY fk_user_package_package (package_id),
            KEY idx_user_package_active_expire (user_id,is_active,expire_time,deleted),
            CONSTRAINT fk_user_package_package FOREIGN KEY (package_id) REFERENCES t_package (id),
            CONSTRAINT fk_user_package_user FOREIGN KEY (user_id) REFERENCES t_user (id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT="用户套餐表"'
    ELSE 
        'SELECT "t_user_package表已存在" as message'
END;

PREPARE stmt FROM @sql_create_user_package;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ========================================
-- 第二阶段: MySQL 8.0特有优化索引
-- ========================================

SELECT '🚀 创建MySQL 8.0优化索引...' AS step;

-- 4. 创建降序索引（MySQL 8.0真正支持降序）
-- 聊天消息时间降序索引
CREATE INDEX IF NOT EXISTS idx_chat_msg_time_desc 
ON t_chat_message (create_time DESC, deleted);

-- 会话活跃时间降序索引
CREATE INDEX IF NOT EXISTS idx_session_active_desc 
ON t_chat_session (last_active_time DESC, deleted);

-- 用户订单时间降序索引
CREATE INDEX IF NOT EXISTS idx_user_order_time_desc 
ON t_user_order (create_time DESC, payment_status);

-- 5. 创建用户查询优化索引
-- 用户消息时间优化索引
CREATE INDEX IF NOT EXISTS idx_chat_msg_user_time_opt 
ON t_chat_message (user_id, create_time DESC, deleted);

-- 用户会话活跃时间优化索引
CREATE INDEX IF NOT EXISTS idx_session_user_active_opt 
ON t_chat_session (user_id, last_active_time DESC, deleted);

-- 6. 创建覆盖索引
-- 文件查询覆盖索引
CREATE INDEX IF NOT EXISTS idx_file_user_cover 
ON t_file_upload (user_id, deleted, file_name, file_type, create_time);

-- 订单查询覆盖索引
CREATE INDEX IF NOT EXISTS idx_order_user_cover 
ON t_user_order (user_id, payment_status, create_time, amount);

-- 用户消息统计覆盖索引
CREATE INDEX IF NOT EXISTS idx_chat_msg_user_stats 
ON t_chat_message (user_id, deleted, session_id, create_time, role);

-- ========================================
-- 第三阶段: MySQL 8.0新特性应用
-- ========================================

SELECT '✨ 应用MySQL 8.0新特性...' AS step;

-- 7. 优化JSON字段（如果model_permissions不是JSON类型）
-- 检查model_permissions字段类型
SELECT 
    DATA_TYPE,
    CASE 
        WHEN DATA_TYPE = 'json' THEN 'JSON类型正确'
        ELSE '需要转换为JSON类型'
    END as json_status
FROM information_schema.columns 
WHERE table_schema = 'chat' 
  AND table_name = 't_package' 
  AND column_name = 'model_permissions';

-- 8. 创建函数索引
-- 日期函数索引，优化按日期统计查询
CREATE INDEX IF NOT EXISTS idx_chat_msg_date_func 
ON t_chat_message ((DATE(create_time)), user_id);

CREATE INDEX IF NOT EXISTS idx_order_date_func 
ON t_user_order ((DATE(create_time)), payment_status);

-- 9. 创建不可见索引用于测试
-- 消息角色测试索引
CREATE INDEX IF NOT EXISTS idx_test_msg_role 
ON t_chat_message (session_id, role) INVISIBLE;

-- 文件类型测试索引
CREATE INDEX IF NOT EXISTS idx_test_file_type 
ON t_file_upload (file_type, status) INVISIBLE;

-- ========================================
-- 第四阶段: 统计信息更新和验证
-- ========================================

SELECT '📊 更新统计信息...' AS step;

-- 10. 更新所有表的统计信息
ANALYZE TABLE t_ai_model;
ANALYZE TABLE t_chat_message;
ANALYZE TABLE t_chat_session;
ANALYZE TABLE t_file_upload;
ANALYZE TABLE t_package;
ANALYZE TABLE t_user;
ANALYZE TABLE t_user_account;
ANALYZE TABLE t_user_daily_usage;
ANALYZE TABLE t_user_order;

-- 如果t_user_package表创建成功，也更新其统计信息
SET @analyze_user_package = (
    SELECT CASE 
        WHEN COUNT(*) > 0 THEN 'ANALYZE TABLE t_user_package'
        ELSE 'SELECT "t_user_package表不存在，跳过统计信息更新" as message'
    END
    FROM information_schema.tables 
    WHERE table_schema = 'chat' 
    AND table_name = 't_user_package'
);

PREPARE stmt FROM @analyze_user_package;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ========================================
-- 第五阶段: 验证和报告
-- ========================================

SELECT '✅ 优化完成验证...' AS step;

-- 11. 验证新创建的索引
SELECT 
    '新创建的优化索引' as category,
    table_name,
    index_name,
    CASE 
        WHEN index_name LIKE '%_desc' THEN '降序索引'
        WHEN index_name LIKE '%_opt' THEN '查询优化索引'
        WHEN index_name LIKE '%_cover' THEN '覆盖索引'
        WHEN index_name LIKE '%_func' THEN '函数索引'
        WHEN index_name LIKE 'idx_test_%' THEN '测试索引(不可见)'
        ELSE '其他优化索引'
    END as index_type
FROM information_schema.statistics 
WHERE table_schema = 'chat' 
  AND index_name IN (
    'idx_chat_msg_time_desc',
    'idx_session_active_desc', 
    'idx_user_order_time_desc',
    'idx_chat_msg_user_time_opt',
    'idx_session_user_active_opt',
    'idx_file_user_cover',
    'idx_order_user_cover',
    'idx_chat_msg_user_stats',
    'idx_chat_msg_date_func',
    'idx_order_date_func',
    'idx_test_msg_role',
    'idx_test_file_type'
  )
GROUP BY table_name, index_name
ORDER BY table_name, index_name;

-- 12. 检查表结构修复情况
SELECT '表结构修复验证' as category;

-- 检查t_package表price字段是否已删除
SELECT 
    CASE 
        WHEN COUNT(*) = 0 THEN '✅ t_package.price字段已成功删除'
        ELSE '❌ t_package.price字段仍然存在'
    END as price_field_check
FROM information_schema.columns 
WHERE table_schema = 'chat' 
  AND table_name = 't_package' 
  AND column_name = 'price';

-- 检查t_chat_session表message_count字段是否已添加
SELECT 
    CASE 
        WHEN COUNT(*) > 0 THEN '✅ t_chat_session.message_count字段已成功添加'
        ELSE '❌ t_chat_session.message_count字段添加失败'
    END as msg_count_field_check
FROM information_schema.columns 
WHERE table_schema = 'chat' 
  AND table_name = 't_chat_session' 
  AND column_name = 'message_count';

-- 检查t_user_package表是否已创建
SELECT 
    CASE 
        WHEN COUNT(*) > 0 THEN '✅ t_user_package表已成功创建'
        ELSE '❌ t_user_package表创建失败'
    END as user_package_table_check
FROM information_schema.tables 
WHERE table_schema = 'chat' 
  AND table_name = 't_user_package';

-- 13. 生成优化报告
SELECT '📈 MySQL 8.0优化完成报告' as report_title;
SELECT 
    '优化完成时间' as item,
    NOW() as value
UNION ALL
SELECT 
    '总索引数量',
    CAST(COUNT(*) AS CHAR)
FROM information_schema.statistics 
WHERE table_schema = 'chat'
UNION ALL
SELECT 
    '新增优化索引数量',
    CAST(COUNT(*) AS CHAR)
FROM information_schema.statistics 
WHERE table_schema = 'chat' 
  AND index_name LIKE 'idx_%'
  AND index_name IN (
    'idx_chat_msg_time_desc',
    'idx_session_active_desc', 
    'idx_user_order_time_desc',
    'idx_chat_msg_user_time_opt',
    'idx_session_user_active_opt',
    'idx_file_user_cover',
    'idx_order_user_cover',
    'idx_chat_msg_user_stats',
    'idx_chat_msg_date_func',
    'idx_order_date_func'
  );

SELECT '🎉 MySQL 8.0优化脚本执行完成！' AS completion_message;
SELECT '建议重启应用以获得最佳性能' AS recommendation;
