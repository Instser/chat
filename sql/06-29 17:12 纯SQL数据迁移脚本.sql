/*
 纯SQL数据迁移脚本
 
 使用说明:
 1. 在MySQL 5.7上执行"数据导出"部分
 2. 在MySQL 8.0上执行"数据库创建"部分  
 3. 使用mysqldump命令导出导入数据
 4. 在MySQL 8.0上执行"数据验证"部分
 
 创建时间: 2025-06-29 17:12
*/

-- ========================================
-- 第一步: 在MySQL 5.7上检查数据
-- ========================================

-- 检查当前数据库状态
SELECT 
    VERSION() as mysql_version,
    DATABASE() as current_database,
    NOW() as check_time;

-- 统计各表数据量
SELECT 
    table_name as '表名',
    table_rows as '记录数',
    ROUND(((data_length + index_length) / 1024 / 1024), 2) AS '大小MB'
FROM information_schema.tables 
WHERE table_schema = 'chat'
ORDER BY table_rows DESC;

-- 检查字符集
SELECT 
    table_name,
    table_collation
FROM information_schema.tables 
WHERE table_schema = 'chat';

-- ========================================
-- 第二步: 导出命令（在命令行执行）
-- ========================================

/*
在命令行执行以下命令导出数据:

mysqldump -h [5.7服务器IP] -u root -p \
  --single-transaction \
  --routines \
  --triggers \
  --set-gtid-purged=OFF \
  --default-character-set=utf8mb4 \
  chat > chat_57_data.sql
*/

-- ========================================
-- 第三步: 在MySQL 8.0上创建数据库
-- ========================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS chat 
CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;

USE chat;

-- 检查MySQL 8.0版本
SELECT VERSION() as mysql_version;

-- ========================================
-- 第四步: 导入命令（在命令行执行）
-- ========================================

/*
在命令行执行以下命令导入数据:

mysql -h [8.0服务器IP] -u root -p chat < chat_57_data.sql
*/

-- ========================================
-- 第五步: 在MySQL 8.0上验证数据
-- ========================================

-- 验证表结构
SHOW TABLES;

-- 验证数据量
SELECT 
    table_name as '表名',
    table_rows as '记录数'
FROM information_schema.tables 
WHERE table_schema = 'chat'
ORDER BY table_rows DESC;

-- 验证关键数据
SELECT 'AI模型表' as table_name, COUNT(*) as record_count FROM t_ai_model WHERE deleted = 0
UNION ALL
SELECT '用户表', COUNT(*) FROM t_user WHERE deleted = 0
UNION ALL
SELECT '聊天会话表', COUNT(*) FROM t_chat_session WHERE deleted = 0
UNION ALL
SELECT '聊天消息表', COUNT(*) FROM t_chat_message WHERE deleted = 0
UNION ALL
SELECT '套餐表', COUNT(*) FROM t_package WHERE deleted = 0
UNION ALL
SELECT '用户订单表', COUNT(*) FROM t_user_order WHERE deleted = 0
UNION ALL
SELECT '用户套餐表', COUNT(*) FROM t_user_package WHERE deleted = 0
UNION ALL
SELECT '文件上传表', COUNT(*) FROM t_upload WHERE deleted = 0;

-- 验证外键关系
SELECT 
    TABLE_NAME as '表名',
    CONSTRAINT_NAME as '约束名',
    CONSTRAINT_TYPE as '约束类型'
FROM information_schema.table_constraints 
WHERE table_schema = 'chat' 
  AND constraint_type = 'FOREIGN KEY';

-- 验证外键数据完整性
SELECT 
    'chat_session外键检查' as check_name,
    COUNT(*) as invalid_count
FROM t_chat_session s 
LEFT JOIN t_user u ON s.user_id = u.id 
LEFT JOIN t_ai_model m ON s.model_id = m.id
WHERE u.id IS NULL OR m.id IS NULL;

SELECT 
    'chat_message外键检查' as check_name,
    COUNT(*) as invalid_count
FROM t_chat_message msg
LEFT JOIN t_user u ON msg.user_id = u.id 
LEFT JOIN t_ai_model m ON msg.model_id = m.id
LEFT JOIN t_chat_session s ON msg.session_id = s.session_id
WHERE u.id IS NULL OR m.id IS NULL OR s.session_id IS NULL;

-- 验证字符集
SELECT 
    table_name as '表名',
    table_collation as '排序规则'
FROM information_schema.tables 
WHERE table_schema = 'chat';

-- ========================================
-- 第六步: 性能优化
-- ========================================

-- 分析表统计信息
ANALYZE TABLE t_ai_model;
ANALYZE TABLE t_user;
ANALYZE TABLE t_chat_session;
ANALYZE TABLE t_chat_message;
ANALYZE TABLE t_package;
ANALYZE TABLE t_user_order;
ANALYZE TABLE t_user_package;
ANALYZE TABLE t_upload;

-- 优化表
OPTIMIZE TABLE t_ai_model;
OPTIMIZE TABLE t_user;
OPTIMIZE TABLE t_chat_session;
OPTIMIZE TABLE t_chat_message;
OPTIMIZE TABLE t_package;
OPTIMIZE TABLE t_user_order;
OPTIMIZE TABLE t_user_package;
OPTIMIZE TABLE t_upload;

-- 创建MySQL 8.0特有的优化索引
CREATE INDEX IF NOT EXISTS idx_chat_msg_time_desc ON t_chat_message (create_time DESC, deleted);
CREATE INDEX IF NOT EXISTS idx_session_active_desc ON t_chat_session (last_active_time DESC, deleted);

-- 检查索引使用情况
SHOW INDEX FROM t_chat_message;
SHOW INDEX FROM t_chat_session;

-- ========================================
-- 第七步: 最终验证
-- ========================================

-- 验证MySQL版本
SELECT 
    'MySQL版本' as info_type, 
    VERSION() as info_value
UNION ALL
SELECT 
    '数据库字符集', 
    DEFAULT_CHARACTER_SET_NAME 
FROM information_schema.SCHEMATA 
WHERE SCHEMA_NAME = 'chat'
UNION ALL
SELECT 
    '迁移完成时间', 
    NOW();

-- 性能测试查询
EXPLAIN SELECT * FROM t_chat_message WHERE user_id = 1 ORDER BY create_time DESC LIMIT 20;
EXPLAIN SELECT * FROM t_chat_session WHERE user_id = 1 ORDER BY last_active_time DESC LIMIT 10;

-- 数据完整性最终检查
SELECT 
    '总用户数' as metric,
    COUNT(*) as value
FROM t_user WHERE deleted = 0
UNION ALL
SELECT 
    '总会话数',
    COUNT(*)
FROM t_chat_session WHERE deleted = 0
UNION ALL
SELECT 
    '总消息数',
    COUNT(*)
FROM t_chat_message WHERE deleted = 0
UNION ALL
SELECT 
    '活跃套餐数',
    COUNT(*)
FROM t_package WHERE deleted = 0 AND is_active = 1;

-- ========================================
-- 应用配置更新提示
-- ========================================

/*
更新应用配置文件 application.yml:

spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://[8.0服务器IP]:3306/chat?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: [密码]
*/

-- 迁移完成标记
SELECT 'MySQL 5.7 到 8.0 数据迁移完成' as status, NOW() as completion_time;

-- ========================================
-- 故障排除SQL
-- ========================================

-- 如果遇到外键约束问题，临时禁用
-- SET FOREIGN_KEY_CHECKS = 0;
-- 执行导入操作后重新启用
-- SET FOREIGN_KEY_CHECKS = 1;

-- 如果遇到字符集问题
-- ALTER DATABASE chat CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 如果需要修复表
-- REPAIR TABLE t_chat_message;

-- 检查表状态
-- SHOW TABLE STATUS LIKE 't_chat_message';

-- ========================================
-- 回滚准备
-- ========================================

-- 如果需要回滚，记录当前状态
CREATE TABLE IF NOT EXISTS migration_log (
    id INT AUTO_INCREMENT PRIMARY KEY,
    operation VARCHAR(100),
    status VARCHAR(50),
    message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO migration_log (operation, status, message) 
VALUES ('MySQL 8.0 Migration', 'COMPLETED', 'Data migration from MySQL 5.7 to 8.0 completed successfully');

-- 查看迁移日志
SELECT * FROM migration_log ORDER BY created_at DESC;
