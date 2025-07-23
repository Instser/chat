-- ========================================
-- 数据现状检测脚本
-- 创建时间: 2025年7月20日 22:10 (北京时间)
-- 描述: 检测现有数据情况，分析ID迁移的影响范围
-- ========================================

USE chat;

SELECT '📊 数据库现状分析报告' AS report_title;
SELECT NOW() AS analysis_time;

-- ========================================
-- 第一部分：基础信息检查
-- ========================================

SELECT '🔍 数据库基础信息' AS section_title;

-- 数据库版本和字符集
SELECT 
    VERSION() as mysql_version,
    DATABASE() as current_database,
    DEFAULT_CHARACTER_SET_NAME as default_charset,
    DEFAULT_COLLATION_NAME as default_collation
FROM information_schema.SCHEMATA 
WHERE SCHEMA_NAME = 'chat';

-- ========================================
-- 第二部分：表结构和数据量分析
-- ========================================

SELECT '📈 表数据量统计' AS section_title;

-- 核心业务表数据量和AUTO_INCREMENT情况
SELECT
    TABLE_NAME as '表名',
    TABLE_ROWS as '估计行数',
    AUTO_INCREMENT as '当前自增值',
    ROUND(DATA_LENGTH/1024/1024, 2) as '数据大小MB',
    ROUND(INDEX_LENGTH/1024/1024, 2) as '索引大小MB',
    TABLE_COLLATION as '排序规则'
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'chat'
  AND TABLE_TYPE = 'BASE TABLE'
  AND TABLE_NAME IN (
    't_user', 't_ai_model', 't_chat_session', 't_chat_message',
    't_file_upload', 't_package', 't_user_account', 't_user_daily_usage',
    't_user_order', 't_payment_audit', 't_web_search_content'
  )
ORDER BY TABLE_ROWS DESC;

-- ========================================
-- 第三部分：主键ID范围分析
-- ========================================

SELECT '🔢 主键ID范围分析' AS section_title;

-- 检查各表的实际数据量和ID范围
SELECT 
    't_user' as table_name,
    COUNT(*) as actual_count,
    MIN(id) as min_id,
    MAX(id) as max_id,
    MAX(id) - MIN(id) + 1 as id_range,
    COUNT(*) / (MAX(id) - MIN(id) + 1) as density_ratio
FROM t_user 
WHERE deleted = 0
UNION ALL
SELECT 
    't_ai_model',
    COUNT(*),
    MIN(id),
    MAX(id),
    MAX(id) - MIN(id) + 1,
    COUNT(*) / (MAX(id) - MIN(id) + 1)
FROM t_ai_model 
WHERE deleted = 0
UNION ALL
SELECT 
    't_chat_session',
    COUNT(*),
    MIN(id),
    MAX(id),
    MAX(id) - MIN(id) + 1,
    COUNT(*) / (MAX(id) - MIN(id) + 1)
FROM t_chat_session 
WHERE deleted = 0
UNION ALL
SELECT 
    't_chat_message',
    COUNT(*),
    MIN(id),
    MAX(id),
    MAX(id) - MIN(id) + 1,
    COUNT(*) / (MAX(id) - MIN(id) + 1)
FROM t_chat_message 
WHERE deleted = 0
UNION ALL
SELECT 
    't_file_upload',
    COUNT(*),
    MIN(id),
    MAX(id),
    CASE WHEN COUNT(*) > 0 THEN MAX(id) - MIN(id) + 1 ELSE 0 END,
    CASE WHEN COUNT(*) > 0 THEN COUNT(*) / (MAX(id) - MIN(id) + 1) ELSE 0 END
FROM t_file_upload 
WHERE deleted = 0
UNION ALL
SELECT 
    't_package',
    COUNT(*),
    MIN(id),
    MAX(id),
    MAX(id) - MIN(id) + 1,
    COUNT(*) / (MAX(id) - MIN(id) + 1)
FROM t_package 
WHERE deleted = 0
UNION ALL
SELECT 
    't_user_account',
    COUNT(*),
    MIN(id),
    MAX(id),
    CASE WHEN COUNT(*) > 0 THEN MAX(id) - MIN(id) + 1 ELSE 0 END,
    CASE WHEN COUNT(*) > 0 THEN COUNT(*) / (MAX(id) - MIN(id) + 1) ELSE 0 END
FROM t_user_account 
WHERE deleted = 0
UNION ALL
SELECT 
    't_user_order',
    COUNT(*),
    MIN(id),
    MAX(id),
    CASE WHEN COUNT(*) > 0 THEN MAX(id) - MIN(id) + 1 ELSE 0 END,
    CASE WHEN COUNT(*) > 0 THEN COUNT(*) / (MAX(id) - MIN(id) + 1) ELSE 0 END
FROM t_user_order 
WHERE deleted = 0;

-- ========================================
-- 第四部分：外键关联分析
-- ========================================

SELECT '🔗 外键关联分析' AS section_title;

-- 分析外键关联情况
SELECT 
    '用户相关关联' as relation_type,
    (SELECT COUNT(*) FROM t_user WHERE deleted = 0) as user_count,
    (SELECT COUNT(*) FROM t_chat_session WHERE deleted = 0) as session_count,
    (SELECT COUNT(*) FROM t_chat_message WHERE deleted = 0) as message_count,
    (SELECT COUNT(*) FROM t_user_account WHERE deleted = 0) as account_count,
    (SELECT COUNT(*) FROM t_user_order WHERE deleted = 0) as order_count;

-- 检查外键关联的完整性
SELECT '外键完整性检查' as check_type;

-- 检查孤立的会话（用户不存在）
SELECT 
    '孤立会话检查' as check_name,
    COUNT(*) as orphaned_count
FROM t_chat_session cs
LEFT JOIN t_user u ON cs.user_id = u.id
WHERE u.id IS NULL AND cs.deleted = 0;

-- 检查孤立的消息（用户不存在）
SELECT 
    '孤立消息检查' as check_name,
    COUNT(*) as orphaned_count
FROM t_chat_message cm
LEFT JOIN t_user u ON cm.user_id = u.id
WHERE u.id IS NULL AND cm.deleted = 0;

-- 检查孤立的消息（会话不存在）
SELECT 
    '孤立消息-会话检查' as check_name,
    COUNT(*) as orphaned_count
FROM t_chat_message cm
LEFT JOIN t_chat_session cs ON cm.session_id = cs.session_id
WHERE cs.session_id IS NULL AND cm.deleted = 0;

-- 检查孤立的文件（用户不存在）
SELECT 
    '孤立文件检查' as check_name,
    COUNT(*) as orphaned_count
FROM t_file_upload fu
LEFT JOIN t_user u ON fu.user_id = u.id
WHERE u.id IS NULL AND fu.deleted = 0;

-- ========================================
-- 第五部分：业务ID格式分析
-- ========================================

SELECT '📝 业务ID格式分析' AS section_title;

-- 检查sessionId格式
SELECT 
    'sessionId格式分析' as id_type,
    COUNT(*) as total_count,
    SUM(CASE WHEN session_id LIKE '%-%' THEN 1 ELSE 0 END) as with_dash_count,
    SUM(CASE WHEN LENGTH(session_id) = 32 THEN 1 ELSE 0 END) as format_32_count,
    SUM(CASE WHEN LENGTH(session_id) = 36 THEN 1 ELSE 0 END) as format_36_count,
    SUM(CASE WHEN LENGTH(session_id) NOT IN (32, 36) THEN 1 ELSE 0 END) as other_format_count
FROM t_chat_session 
WHERE deleted = 0;

-- 检查messageId格式
SELECT 
    'messageId格式分析' as id_type,
    COUNT(*) as total_count,
    SUM(CASE WHEN message_id LIKE '%-%' THEN 1 ELSE 0 END) as with_dash_count,
    SUM(CASE WHEN LENGTH(message_id) = 32 THEN 1 ELSE 0 END) as format_32_count,
    SUM(CASE WHEN LENGTH(message_id) = 36 THEN 1 ELSE 0 END) as format_36_count,
    SUM(CASE WHEN LENGTH(message_id) NOT IN (32, 36) THEN 1 ELSE 0 END) as other_format_count
FROM t_chat_message 
WHERE deleted = 0;

-- 检查fileId格式
SELECT 
    'fileId格式分析' as id_type,
    COUNT(*) as total_count,
    SUM(CASE WHEN file_id LIKE '%-%' THEN 1 ELSE 0 END) as with_dash_count,
    SUM(CASE WHEN LENGTH(file_id) = 32 THEN 1 ELSE 0 END) as format_32_count,
    SUM(CASE WHEN LENGTH(file_id) = 36 THEN 1 ELSE 0 END) as format_36_count,
    SUM(CASE WHEN LENGTH(file_id) NOT IN (32, 36) THEN 1 ELSE 0 END) as other_format_count
FROM t_file_upload 
WHERE deleted = 0;

-- ========================================
-- 第六部分：迁移风险评估
-- ========================================

SELECT '⚠️ 迁移风险评估' AS section_title;

-- 计算需要迁移的记录总数
SELECT 
    '迁移规模评估' as assessment_type,
    (SELECT SUM(TABLE_ROWS) FROM information_schema.TABLES 
     WHERE TABLE_SCHEMA = 'chat' AND TABLE_NAME LIKE 't_%') as total_estimated_rows,
    (SELECT COUNT(*) FROM t_user WHERE deleted = 0) +
    (SELECT COUNT(*) FROM t_ai_model WHERE deleted = 0) +
    (SELECT COUNT(*) FROM t_chat_session WHERE deleted = 0) +
    (SELECT COUNT(*) FROM t_chat_message WHERE deleted = 0) +
    (SELECT COUNT(*) FROM t_file_upload WHERE deleted = 0) +
    (SELECT COUNT(*) FROM t_package WHERE deleted = 0) +
    (SELECT COUNT(*) FROM t_user_account WHERE deleted = 0) +
    (SELECT COUNT(*) FROM t_user_order WHERE deleted = 0) as actual_migration_count;

-- 评估外键更新的复杂度
SELECT 
    '外键更新复杂度' as assessment_type,
    'HIGH' as complexity_level,
    '需要更新多个表的外键引用，建议分步执行' as recommendation;

-- 评估业务ID格式统一的影响
SELECT 
    '业务ID格式统一影响' as assessment_type,
    (SELECT COUNT(*) FROM t_chat_session WHERE session_id LIKE '%-%' AND deleted = 0) +
    (SELECT COUNT(*) FROM t_chat_message WHERE message_id LIKE '%-%' AND deleted = 0) +
    (SELECT COUNT(*) FROM t_file_upload WHERE file_id LIKE '%-%' AND deleted = 0) as records_to_update,
    'MEDIUM' as impact_level;

-- ========================================
-- 第七部分：建议和注意事项
-- ========================================

SELECT '💡 迁移建议' AS section_title;

SELECT 
    '建议1' as recommendation,
    '在非生产环境先进行完整测试' as description
UNION ALL
SELECT 
    '建议2',
    '迁移前进行完整数据备份'
UNION ALL
SELECT 
    '建议3',
    '分步执行，先测试小表再处理大表'
UNION ALL
SELECT 
    '建议4',
    '迁移过程中暂停应用服务'
UNION ALL
SELECT 
    '建议5',
    '准备回滚方案以防迁移失败';

SELECT '✅ 数据现状检测完成' AS completion_status;
SELECT '📋 请根据以上分析结果决定是否执行迁移' AS next_step;
