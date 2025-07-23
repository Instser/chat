-- ========================================
-- 清理迁移数据脚本
-- 创建时间: 2025年7月20日 22:30 (北京时间)
-- 描述: 清理失败或不完整的迁移数据，为重新迁移做准备
-- ========================================

USE chat;

-- 开始事务
START TRANSACTION;

-- ========================================
-- 第一步：检查当前迁移状态
-- ========================================

SELECT '🔍 当前迁移状态检查' AS step;

-- 检查迁移备份表
SELECT 
    table_name as '表名',
    migration_strategy as '迁移策略',
    backup_time as '备份时间'
FROM id_migration_backup 
ORDER BY backup_time DESC;

-- 检查ID映射表
SELECT 
    table_name as '表名',
    COUNT(*) as '映射记录数'
FROM id_mapping 
GROUP BY table_name
ORDER BY table_name;

-- ========================================
-- 第二步：清理迁移数据
-- ========================================

SELECT '🧹 开始清理迁移数据' AS step;

-- 清理ID映射表
TRUNCATE TABLE id_mapping;

-- 清理迁移备份记录
DELETE FROM id_migration_backup 
WHERE migration_strategy IN (
    'ID_MAPPING_MIGRATION', 
    'SNOWFLAKE_ID_MIGRATION_SUCCESS',
    'FIXED_ID_MAPPING_MIGRATION',
    'FIXED_SNOWFLAKE_ID_MIGRATION_SUCCESS'
);

-- ========================================
-- 第三步：验证清理结果
-- ========================================

SELECT '✅ 清理完成验证' AS step;

-- 验证ID映射表已清空
SELECT 
    CASE 
        WHEN COUNT(*) = 0 THEN '✅ ID映射表已清空'
        ELSE CONCAT('❌ ID映射表仍有 ', COUNT(*), ' 条记录')
    END as id_mapping_status
FROM id_mapping;

-- 验证备份表已清理
SELECT 
    CASE 
        WHEN COUNT(*) = 0 THEN '✅ 迁移备份记录已清理'
        ELSE CONCAT('❌ 仍有 ', COUNT(*), ' 条迁移备份记录')
    END as backup_status
FROM id_migration_backup 
WHERE migration_strategy IN (
    'ID_MAPPING_MIGRATION', 
    'SNOWFLAKE_ID_MIGRATION_SUCCESS',
    'FIXED_ID_MAPPING_MIGRATION',
    'FIXED_SNOWFLAKE_ID_MIGRATION_SUCCESS'
);

-- 检查核心业务表的AUTO_INCREMENT状态
SELECT 
    TABLE_NAME as '表名',
    AUTO_INCREMENT as '自增值',
    CASE 
        WHEN AUTO_INCREMENT IS NOT NULL THEN '✅ 仍为自增ID'
        ELSE '⚠️ 已移除AUTO_INCREMENT'
    END as '状态'
FROM INFORMATION_SCHEMA.TABLES 
WHERE TABLE_SCHEMA = 'chat' 
  AND TABLE_NAME IN (
    't_user', 't_ai_model', 't_chat_session', 't_chat_message', 
    't_file_upload', 't_package', 't_user_account', 't_user_daily_usage',
    't_user_order', 't_payment_audit', 't_web_search_content'
  )
ORDER BY TABLE_NAME;

-- 提交事务
COMMIT;

-- ========================================
-- 清理完成报告
-- ========================================

SELECT '🎉 迁移数据清理完成！' AS status;
SELECT 
    '清理完成时间' as item,
    NOW() as value;

SELECT '✅ 已清理所有迁移相关数据' AS completion_message;
SELECT '💡 现在可以重新执行迁移脚本' AS next_step;
SELECT '⚠️ 如果表结构已被修改，建议先执行回滚脚本恢复AUTO_INCREMENT' AS important_note;
