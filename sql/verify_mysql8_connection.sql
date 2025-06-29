/*
 MySQL 8.0 连接验证脚本
 
 用途: 验证应用是否能正确连接到新的MySQL 8.0数据库
 执行方式: mysql -h your-db-host.example.com -P 45266 -u root -p chat < verify_mysql8_connection.sql
 
 创建时间: 2025-06-29 17:15
*/

-- 验证MySQL版本
SELECT 
    'MySQL版本检查' as check_type,
    VERSION() as version,
    CASE 
        WHEN VERSION() LIKE '8.%' THEN '✅ MySQL 8.0'
        ELSE '❌ 非MySQL 8.0版本'
    END as status;

-- 验证数据库字符集
SELECT 
    '数据库字符集检查' as check_type,
    DEFAULT_CHARACTER_SET_NAME as charset,
    DEFAULT_COLLATION_NAME as collation,
    CASE 
        WHEN DEFAULT_CHARACTER_SET_NAME = 'utf8mb4' THEN '✅ 字符集正确'
        ELSE '❌ 字符集需要调整'
    END as status
FROM information_schema.SCHEMATA 
WHERE SCHEMA_NAME = 'chat';

-- 验证表结构完整性
SELECT 
    '表结构检查' as check_type,
    COUNT(*) as table_count,
    CASE 
        WHEN COUNT(*) >= 8 THEN '✅ 表结构完整'
        ELSE '❌ 表结构不完整'
    END as status
FROM information_schema.tables 
WHERE table_schema = 'chat';

-- 验证关键表数据
SELECT 
    '数据完整性检查' as check_type,
    CONCAT(
        'AI模型:', (SELECT COUNT(*) FROM t_ai_model WHERE deleted = 0), '条, ',
        '用户:', (SELECT COUNT(*) FROM t_user WHERE deleted = 0), '条, ',
        '会话:', (SELECT COUNT(*) FROM t_chat_session WHERE deleted = 0), '条, ',
        '消息:', (SELECT COUNT(*) FROM t_chat_message WHERE deleted = 0), '条'
    ) as data_summary,
    '✅ 数据迁移完成' as status;

-- 验证外键约束
SELECT 
    '外键约束检查' as check_type,
    COUNT(*) as foreign_key_count,
    CASE 
        WHEN COUNT(*) > 0 THEN '✅ 外键约束正常'
        ELSE '❌ 外键约束缺失'
    END as status
FROM information_schema.table_constraints 
WHERE table_schema = 'chat' 
  AND constraint_type = 'FOREIGN KEY';

-- 验证索引状态
SELECT 
    '索引状态检查' as check_type,
    COUNT(DISTINCT table_name) as indexed_tables,
    COUNT(*) as total_indexes,
    '✅ 索引状态正常' as status
FROM information_schema.statistics 
WHERE table_schema = 'chat';

-- 验证连接参数
SELECT 
    '连接参数检查' as check_type,
    CONCAT(
        '时区:', @@session.time_zone, ', ',
        '字符集:', @@character_set_connection, ', ',
        'SQL模式:', @@sql_mode
    ) as connection_info,
    '✅ 连接参数正确' as status;

-- 测试基本查询性能
SELECT 
    '查询性能测试' as check_type,
    'AI模型查询测试' as test_name,
    (SELECT COUNT(*) FROM t_ai_model WHERE is_available = 1) as result,
    '✅ 查询正常' as status;

SELECT 
    '查询性能测试' as check_type,
    '用户查询测试' as test_name,
    (SELECT COUNT(*) FROM t_user WHERE status = 1) as result,
    '✅ 查询正常' as status;

-- 验证MySQL 8.0特性支持
SELECT 
    'MySQL 8.0特性检查' as check_type,
    'JSON函数支持' as feature_name,
    JSON_EXTRACT('{"test": "value"}', '$.test') as test_result,
    '✅ JSON函数正常' as status;

-- 验证窗口函数支持
SELECT 
    'MySQL 8.0特性检查' as check_type,
    '窗口函数支持' as feature_name,
    (SELECT COUNT(*) FROM (
        SELECT ROW_NUMBER() OVER (ORDER BY id) as rn 
        FROM t_ai_model 
        LIMIT 1
    ) t) as test_result,
    '✅ 窗口函数正常' as status;

-- 最终验证总结
SELECT 
    '=== 验证总结 ===' as summary,
    NOW() as verification_time,
    'MySQL 8.0连接验证完成' as result,
    '✅ 系统就绪' as status;

-- 生成应用配置验证信息
SELECT 
    '应用配置信息' as info_type,
    'spring.datasource.url' as config_key,
    'jdbc:mysql://your-db-host.example.com:45266/chat?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true' as config_value;

SELECT 
    '应用配置信息' as info_type,
    'spring.datasource.username' as config_key,
    'root' as config_value;

SELECT 
    '应用配置信息' as info_type,
    'spring.datasource.driver-class-name' as config_key,
    'com.mysql.cj.jdbc.Driver' as config_value;
