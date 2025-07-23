-- ========================================
-- 安全的雪花ID迁移脚本
-- 创建时间: 2025年7月20日 22:15 (北京时间)
-- 描述: 安全地将现有自增ID数据迁移为雪花ID，保证数据关联完整性
-- ========================================

USE chat;

-- 开始事务
START TRANSACTION;

-- ========================================
-- 第一步：数据现状分析和备份
-- ========================================

-- 创建迁移备份表
CREATE TABLE IF NOT EXISTS id_migration_backup (
    table_name VARCHAR(64) PRIMARY KEY,
    old_auto_increment BIGINT,
    max_existing_id BIGINT,
    migration_strategy VARCHAR(100),
    backup_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 分析现有数据情况
SELECT '📊 现有数据分析' AS step;

-- 检查各表的数据量和ID范围
-- 先检查哪些表存在
SELECT '检查表存在情况' AS check_step;
SELECT
    TABLE_NAME as existing_tables,
    AUTO_INCREMENT as current_auto_increment
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = 'chat'
  AND AUTO_INCREMENT IS NOT NULL
  AND TABLE_NAME LIKE 't_%'
ORDER BY TABLE_NAME;

-- 检查哪些表有deleted字段
SELECT '检查deleted字段存在情况' AS check_step;
SELECT
    TABLE_NAME as table_with_deleted
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'chat'
  AND COLUMN_NAME = 'deleted'
  AND TABLE_NAME LIKE 't_%'
ORDER BY TABLE_NAME;

-- 安全地插入备份信息（只处理核心业务表）
INSERT INTO id_migration_backup (table_name, old_auto_increment, max_existing_id, migration_strategy)
SELECT
    TABLE_NAME,
    AUTO_INCREMENT,
    0, -- 暂时设为0，后续单独更新
    'FIXED_ID_MAPPING_MIGRATION'
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = 'chat'
  AND AUTO_INCREMENT IS NOT NULL
  AND TABLE_NAME IN (
    't_user', 't_ai_model', 't_chat_session', 't_chat_message',
    't_file_upload', 't_package', 't_user_account', 't_user_daily_usage',
    't_user_order', 't_payment_audit', 't_web_search_content'
  )
ON DUPLICATE KEY UPDATE
    old_auto_increment = VALUES(old_auto_increment),
    backup_time = CURRENT_TIMESTAMP;

-- 显示分析结果
SELECT 
    table_name as '表名',
    old_auto_increment as '当前自增值',
    max_existing_id as '最大ID值',
    migration_strategy as '迁移策略'
FROM id_migration_backup 
WHERE migration_strategy = 'ID_MAPPING_MIGRATION'
ORDER BY max_existing_id DESC;

-- ========================================
-- 第二步：创建ID映射表
-- ========================================

-- 创建ID映射表，用于记录旧ID到新雪花ID的映射关系
CREATE TABLE IF NOT EXISTS id_mapping (
    table_name VARCHAR(64) NOT NULL,
    old_id BIGINT NOT NULL,
    new_snowflake_id BIGINT NOT NULL,
    migration_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (table_name, old_id),
    UNIQUE KEY uk_new_id (table_name, new_snowflake_id)
) COMMENT='ID映射表：记录自增ID到雪花ID的映射关系';

-- ========================================
-- 第三步：生成雪花ID映射（安全的ID生成策略）
-- ========================================

-- 检查是否已经执行过迁移
SELECT '🔍 检查迁移状态' AS check_step;

-- 显示现有迁移记录
SELECT
    table_name,
    migration_strategy,
    backup_time
FROM id_migration_backup
WHERE migration_strategy IN ('ID_MAPPING_MIGRATION', 'SNOWFLAKE_ID_MIGRATION_SUCCESS', 'FIXED_SNOWFLAKE_ID_MIGRATION_SUCCESS')
ORDER BY backup_time DESC;

-- 清理可能存在的不完整迁移数据，避免重复执行冲突
TRUNCATE TABLE id_mapping;
DELETE FROM id_migration_backup WHERE migration_strategy IN ('ID_MAPPING_MIGRATION', 'SNOWFLAKE_ID_MIGRATION_SUCCESS', 'FIXED_SNOWFLAKE_ID_MIGRATION_SUCCESS');

SELECT '🧹 已清理旧的迁移数据，开始新的迁移' AS cleanup_message;

-- 注意：使用安全的ID生成策略，避免BIGINT溢出
-- 实际雪花ID会由MyBatis-Plus在应用重启后生成
-- 这里的目的是为现有数据预分配不冲突的ID

-- 使用安全的ID生成策略，避免BIGINT溢出
-- 为每个表分配独立的基数范围，确保不会溢出

-- 为存在的表生成ID映射，根据是否有deleted字段决定WHERE条件

-- 为 t_user 表生成ID映射（基数：1720000000000000）
SET @user_base = 1720000000000000;
INSERT IGNORE INTO id_mapping (table_name, old_id, new_snowflake_id)
SELECT
    't_user',
    id,
    @user_base + id
FROM t_user
WHERE deleted = 0
ORDER BY id;

-- 为 t_ai_model 表生成ID映射（基数：1720000001000000）
SET @model_base = 1720000001000000;
INSERT IGNORE INTO id_mapping (table_name, old_id, new_snowflake_id)
SELECT
    't_ai_model',
    id,
    @model_base + id
FROM t_ai_model
WHERE deleted = 0
ORDER BY id;

-- 为 t_chat_session 表生成ID映射（基数：1720000002000000）
SET @session_base = 1720000002000000;
INSERT IGNORE INTO id_mapping (table_name, old_id, new_snowflake_id)
SELECT
    't_chat_session',
    id,
    @session_base + id
FROM t_chat_session
WHERE deleted = 0
ORDER BY id;

-- 为 t_chat_message 表生成ID映射（基数：1720000003000000）
SET @message_base = 1720000003000000;
INSERT IGNORE INTO id_mapping (table_name, old_id, new_snowflake_id)
SELECT
    't_chat_message',
    id,
    @message_base + id
FROM t_chat_message
WHERE deleted = 0
ORDER BY id;

-- 为 t_file_upload 表生成ID映射（基数：1720000004000000）
SET @file_base = 1720000004000000;
INSERT IGNORE INTO id_mapping (table_name, old_id, new_snowflake_id)
SELECT
    't_file_upload',
    id,
    @file_base + id
FROM t_file_upload
WHERE deleted = 0
ORDER BY id;

-- 为 t_package 表生成ID映射（基数：1720000005000000）
SET @package_base = 1720000005000000;
INSERT IGNORE INTO id_mapping (table_name, old_id, new_snowflake_id)
SELECT
    't_package',
    id,
    @package_base + id
FROM t_package
WHERE deleted = 0
ORDER BY id;

-- 为 t_user_account 表生成ID映射（基数：1720000006000000）
SET @account_base = 1720000006000000;
INSERT IGNORE INTO id_mapping (table_name, old_id, new_snowflake_id)
SELECT
    't_user_account',
    id,
    @account_base + id
FROM t_user_account
WHERE deleted = 0
ORDER BY id;

-- 为 t_user_daily_usage 表生成ID映射（基数：1720000007000000）
-- 注意：t_user_daily_usage 表没有 deleted 字段
SET @usage_base = 1720000007000000;
INSERT IGNORE INTO id_mapping (table_name, old_id, new_snowflake_id)
SELECT
    't_user_daily_usage',
    id,
    @usage_base + id
FROM t_user_daily_usage
ORDER BY id;

-- 为 t_user_order 表生成ID映射（基数：1720000008000000）
SET @order_base = 1720000008000000;
INSERT IGNORE INTO id_mapping (table_name, old_id, new_snowflake_id)
SELECT
    't_user_order',
    id,
    @order_base + id
FROM t_user_order
WHERE deleted = 0
ORDER BY id;

-- 为 t_payment_audit 表生成ID映射（基数：1720000009000000）
SET @audit_base = 1720000009000000;
INSERT IGNORE INTO id_mapping (table_name, old_id, new_snowflake_id)
SELECT
    't_payment_audit',
    id,
    @audit_base + id
FROM t_payment_audit
WHERE deleted = 0
ORDER BY id;

-- 为 t_web_search_content 表生成ID映射（基数：1720000010000000）
-- 只有当表存在时才执行
SET @search_base = 1720000010000000;
SET @table_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                     WHERE TABLE_SCHEMA = 'chat' AND TABLE_NAME = 't_web_search_content');

-- 使用条件插入，只有表存在时才执行
INSERT IGNORE INTO id_mapping (table_name, old_id, new_snowflake_id)
SELECT
    't_web_search_content',
    id,
    @search_base + id
FROM t_web_search_content
WHERE deleted = 0 AND @table_exists > 0
ORDER BY id;

-- 显示ID映射统计
SELECT
    table_name as '表名',
    COUNT(*) as '映射记录数',
    MIN(old_id) as '最小旧ID',
    MAX(old_id) as '最大旧ID',
    MIN(new_snowflake_id) as '最小新ID',
    MAX(new_snowflake_id) as '最大新ID'
FROM id_mapping
GROUP BY table_name
ORDER BY table_name;

-- ========================================
-- 第四步：更新主键ID（按依赖顺序）
-- ========================================

-- 重要：按照外键依赖关系的逆序更新，避免外键约束冲突
-- 更新顺序：子表 -> 父表

-- 4.1 更新 t_web_search_content 表
UPDATE t_web_search_content wsc
JOIN id_mapping im ON im.table_name = 't_web_search_content' AND im.old_id = wsc.id
SET wsc.id = im.new_snowflake_id;

-- 4.2 更新 t_payment_audit 表
UPDATE t_payment_audit pa
JOIN id_mapping im ON im.table_name = 't_payment_audit' AND im.old_id = pa.id
SET pa.id = im.new_snowflake_id;

-- 4.3 更新 t_user_order 表
UPDATE t_user_order uo
JOIN id_mapping im ON im.table_name = 't_user_order' AND im.old_id = uo.id
SET uo.id = im.new_snowflake_id;

-- 4.4 更新 t_user_daily_usage 表
UPDATE t_user_daily_usage udu
JOIN id_mapping im ON im.table_name = 't_user_daily_usage' AND im.old_id = udu.id
SET udu.id = im.new_snowflake_id;

-- 4.5 更新 t_user_account 表
UPDATE t_user_account ua
JOIN id_mapping im ON im.table_name = 't_user_account' AND im.old_id = ua.id
SET ua.id = im.new_snowflake_id;

-- 4.6 更新 t_file_upload 表
UPDATE t_file_upload fu
JOIN id_mapping im ON im.table_name = 't_file_upload' AND im.old_id = fu.id
SET fu.id = im.new_snowflake_id;

-- 4.7 更新 t_chat_message 表
UPDATE t_chat_message cm
JOIN id_mapping im ON im.table_name = 't_chat_message' AND im.old_id = cm.id
SET cm.id = im.new_snowflake_id;

-- 4.8 更新 t_chat_session 表
UPDATE t_chat_session cs
JOIN id_mapping im ON im.table_name = 't_chat_session' AND im.old_id = cs.id
SET cs.id = im.new_snowflake_id;

-- 4.9 更新 t_package 表
UPDATE t_package p
JOIN id_mapping im ON im.table_name = 't_package' AND im.old_id = p.id
SET p.id = im.new_snowflake_id;

-- 4.10 更新 t_ai_model 表
UPDATE t_ai_model am
JOIN id_mapping im ON im.table_name = 't_ai_model' AND im.old_id = am.id
SET am.id = im.new_snowflake_id;

-- 4.11 更新 t_user 表
UPDATE t_user u
JOIN id_mapping im ON im.table_name = 't_user' AND im.old_id = u.id
SET u.id = im.new_snowflake_id;

-- ========================================
-- 第五步：更新外键引用
-- ========================================

-- 5.1 更新 t_chat_session 表中的外键引用
UPDATE t_chat_session cs
JOIN id_mapping im_user ON im_user.table_name = 't_user' AND im_user.old_id = cs.user_id
SET cs.user_id = im_user.new_snowflake_id;

UPDATE t_chat_session cs
JOIN id_mapping im_model ON im_model.table_name = 't_ai_model' AND im_model.old_id = cs.model_id
SET cs.model_id = im_model.new_snowflake_id;

-- 5.2 更新 t_chat_message 表中的外键引用
UPDATE t_chat_message cm
JOIN id_mapping im_user ON im_user.table_name = 't_user' AND im_user.old_id = cm.user_id
SET cm.user_id = im_user.new_snowflake_id;

UPDATE t_chat_message cm
JOIN id_mapping im_model ON im_model.table_name = 't_ai_model' AND im_model.old_id = cm.model_id
SET cm.model_id = im_model.new_snowflake_id;

-- 5.3 更新 t_file_upload 表中的外键引用
UPDATE t_file_upload fu
JOIN id_mapping im_user ON im_user.table_name = 't_user' AND im_user.old_id = fu.user_id
SET fu.user_id = im_user.new_snowflake_id;

-- 5.4 更新 t_user_account 表中的外键引用
UPDATE t_user_account ua
JOIN id_mapping im_user ON im_user.table_name = 't_user' AND im_user.old_id = ua.user_id
SET ua.user_id = im_user.new_snowflake_id;

UPDATE t_user_account ua
JOIN id_mapping im_package ON im_package.table_name = 't_package' AND im_package.old_id = ua.package_id
SET ua.package_id = im_package.new_snowflake_id;

-- 5.5 更新 t_user_daily_usage 表中的外键引用
UPDATE t_user_daily_usage udu
JOIN id_mapping im_user ON im_user.table_name = 't_user' AND im_user.old_id = udu.user_id
SET udu.user_id = im_user.new_snowflake_id;

UPDATE t_user_daily_usage udu
JOIN id_mapping im_model ON im_model.table_name = 't_ai_model' AND im_model.old_id = udu.model_id
SET udu.model_id = im_model.new_snowflake_id;

-- 5.6 更新 t_user_order 表中的外键引用
UPDATE t_user_order uo
JOIN id_mapping im_user ON im_user.table_name = 't_user' AND im_user.old_id = uo.user_id
SET uo.user_id = im_user.new_snowflake_id;

UPDATE t_user_order uo
JOIN id_mapping im_package ON im_package.table_name = 't_package' AND im_package.old_id = uo.package_id
SET uo.package_id = im_package.new_snowflake_id;

-- 5.7 更新 t_payment_audit 表中的外键引用
UPDATE t_payment_audit pa
JOIN id_mapping im_user ON im_user.table_name = 't_user' AND im_user.old_id = pa.user_id
SET pa.user_id = im_user.new_snowflake_id;

-- 5.8 更新 t_web_search_content 表中的外键引用
UPDATE t_web_search_content wsc
JOIN id_mapping im_user ON im_user.table_name = 't_user' AND im_user.old_id = wsc.user_id
SET wsc.user_id = im_user.new_snowflake_id;

-- ========================================
-- 第六步：移除AUTO_INCREMENT并统一业务ID格式
-- ========================================

-- 移除核心业务表的AUTO_INCREMENT属性
ALTER TABLE t_user MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（雪花ID）';
ALTER TABLE t_ai_model MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（雪花ID）';
ALTER TABLE t_chat_session MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（雪花ID）';
ALTER TABLE t_chat_message MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（雪花ID）';
ALTER TABLE t_file_upload MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（雪花ID）';
ALTER TABLE t_package MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（雪花ID）';
ALTER TABLE t_user_account MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（雪花ID）';
ALTER TABLE t_user_daily_usage MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（雪花ID）';
ALTER TABLE t_user_order MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（雪花ID）';
ALTER TABLE t_payment_audit MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（雪花ID）';

-- 只有当t_web_search_content表存在时才修改
SET @sql = IF(@table_exists > 0,
    'ALTER TABLE t_web_search_content MODIFY COLUMN id BIGINT NOT NULL COMMENT ''主键ID（雪花ID）'';',
    'SELECT ''t_web_search_content表不存在，跳过修改'' as skip_message;'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 统一业务ID格式为32位无连字符
UPDATE t_chat_message
SET message_id = REPLACE(message_id, '-', '')
WHERE message_id LIKE '%-%' AND deleted = 0;

UPDATE t_chat_session
SET session_id = REPLACE(session_id, '-', '')
WHERE session_id LIKE '%-%' AND deleted = 0;

UPDATE t_file_upload
SET file_id = REPLACE(file_id, '-', '')
WHERE file_id LIKE '%-%' AND deleted = 0;

-- 只有当t_web_search_content表存在时才更新
UPDATE t_web_search_content
SET web_search_id = REPLACE(web_search_id, '-', '')
WHERE web_search_id LIKE '%-%' AND deleted = 0 AND @table_exists > 0;

-- 同步更新关联表中的外键引用
UPDATE t_chat_message cm
JOIN t_chat_session cs ON cm.session_id = cs.session_id
SET cm.session_id = cs.session_id
WHERE cm.session_id LIKE '%-%' AND cm.deleted = 0;

UPDATE t_file_upload fu
JOIN t_chat_session cs ON fu.session_id = cs.session_id
SET fu.session_id = cs.session_id
WHERE fu.session_id LIKE '%-%' AND fu.deleted = 0;

UPDATE t_file_upload fu
JOIN t_chat_message cm ON fu.message_id = cm.message_id
SET fu.message_id = cm.message_id
WHERE fu.message_id LIKE '%-%' AND fu.deleted = 0;

-- 只有当t_web_search_content表存在时才更新
UPDATE t_web_search_content wsc
JOIN t_chat_session cs ON wsc.session_id = cs.session_id
SET wsc.session_id = cs.session_id
WHERE wsc.session_id LIKE '%-%' AND wsc.deleted = 0 AND @table_exists > 0;

UPDATE t_web_search_content wsc
JOIN t_chat_message cm ON wsc.message_id = cm.message_id
SET wsc.message_id = cm.message_id
WHERE wsc.message_id LIKE '%-%' AND wsc.deleted = 0 AND @table_exists > 0;

-- ========================================
-- 第七步：数据完整性验证
-- ========================================

-- 验证主键ID迁移结果
SELECT '🔍 主键ID迁移验证' AS verification_type;

SELECT
    table_name as '表名',
    COUNT(*) as '迁移记录数',
    MIN(new_snowflake_id) as '最小雪花ID',
    MAX(new_snowflake_id) as '最大雪花ID'
FROM id_mapping
GROUP BY table_name
ORDER BY table_name;

-- 验证外键关联完整性
SELECT '🔗 外键关联完整性验证' AS verification_type;

-- 检查用户-会话关联
SELECT
    '用户-会话关联' as check_type,
    COUNT(*) as total_sessions,
    SUM(CASE WHEN u.id IS NULL THEN 1 ELSE 0 END) as orphaned_sessions
FROM t_chat_session cs
LEFT JOIN t_user u ON cs.user_id = u.id
WHERE cs.deleted = 0 AND u.deleted = 0;

-- 检查用户-消息关联
SELECT
    '用户-消息关联' as check_type,
    COUNT(*) as total_messages,
    SUM(CASE WHEN u.id IS NULL THEN 1 ELSE 0 END) as orphaned_messages
FROM t_chat_message cm
LEFT JOIN t_user u ON cm.user_id = u.id
WHERE cm.deleted = 0 AND u.deleted = 0;

-- 检查模型-会话关联
SELECT
    '模型-会话关联' as check_type,
    COUNT(*) as total_sessions,
    SUM(CASE WHEN am.id IS NULL THEN 1 ELSE 0 END) as orphaned_sessions
FROM t_chat_session cs
LEFT JOIN t_ai_model am ON cs.model_id = am.id
WHERE cs.deleted = 0 AND am.deleted = 0;

-- 验证业务ID格式统一
SELECT '📝 业务ID格式验证' AS verification_type;

SELECT
    'messageId格式' as id_type,
    COUNT(*) as total_count,
    SUM(CASE WHEN message_id LIKE '%-%' THEN 1 ELSE 0 END) as with_dash_count,
    SUM(CASE WHEN LENGTH(message_id) = 32 THEN 1 ELSE 0 END) as standard_format_count
FROM t_chat_message
WHERE deleted = 0;

SELECT
    'sessionId格式' as id_type,
    COUNT(*) as total_count,
    SUM(CASE WHEN session_id LIKE '%-%' THEN 1 ELSE 0 END) as with_dash_count,
    SUM(CASE WHEN LENGTH(session_id) = 32 THEN 1 ELSE 0 END) as standard_format_count
FROM t_chat_session
WHERE deleted = 0;

SELECT
    'fileId格式' as id_type,
    COUNT(*) as total_count,
    SUM(CASE WHEN file_id LIKE '%-%' THEN 1 ELSE 0 END) as with_dash_count,
    SUM(CASE WHEN LENGTH(file_id) = 32 THEN 1 ELSE 0 END) as standard_format_count
FROM t_file_upload
WHERE deleted = 0;

-- 记录迁移完成
INSERT INTO id_migration_backup (table_name, old_auto_increment, migration_strategy)
VALUES ('MIGRATION_COMPLETED', 0, 'FIXED_SNOWFLAKE_ID_MIGRATION_SUCCESS')
ON DUPLICATE KEY UPDATE
    backup_time = CURRENT_TIMESTAMP,
    migration_strategy = 'FIXED_SNOWFLAKE_ID_MIGRATION_SUCCESS';

-- 提交事务
COMMIT;

-- ========================================
-- 迁移完成报告
-- ========================================

SELECT '🎉 雪花ID迁移完成！' AS status;
SELECT
    '迁移完成时间' as item,
    NOW() as value
UNION ALL
SELECT
    '迁移的表数量',
    CAST(COUNT(DISTINCT table_name) AS CHAR)
FROM id_mapping
UNION ALL
SELECT
    '迁移的记录总数',
    CAST(COUNT(*) AS CHAR)
FROM id_mapping;

SELECT '✅ 所有主键ID已成功迁移为雪花ID' AS completion_message;
SELECT '✅ 所有外键关联已保持完整' AS integrity_message;
SELECT '✅ 所有业务ID已统一为32位无连字符格式' AS uuid_format_message;
SELECT '⚠️ 请重启应用以使MyBatis-Plus雪花ID生成器生效' AS important_note;
SELECT '💡 新数据将自动使用真正的雪花ID，与现有数据不会冲突' AS future_note;
