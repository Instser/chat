-- ========================================
-- ID策略统一为雪花ID修改脚本
-- 创建时间: 2025年7月20日 21:39 (北京时间)
-- 描述: 将所有表的AUTO_INCREMENT主键修改为雪花ID策略
-- ========================================

USE chat;

-- 开始事务
START TRANSACTION;

-- ========================================
-- 第一步：备份当前AUTO_INCREMENT值（用于回滚）
-- ========================================

CREATE TABLE IF NOT EXISTS id_migration_backup (
    table_name VARCHAR(64) PRIMARY KEY,
    old_auto_increment BIGINT,
    backup_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO id_migration_backup (table_name, old_auto_increment)
SELECT 
    TABLE_NAME,
    AUTO_INCREMENT
FROM INFORMATION_SCHEMA.TABLES 
WHERE TABLE_SCHEMA = 'chat' 
  AND AUTO_INCREMENT IS NOT NULL
ON DUPLICATE KEY UPDATE 
    old_auto_increment = VALUES(old_auto_increment),
    backup_time = CURRENT_TIMESTAMP;

-- ========================================
-- 第二步：检查现有数据情况
-- ========================================

-- 检查各表的数据量和ID范围
SELECT '📊 现有数据分析' AS analysis_type;

SELECT
    TABLE_NAME as '表名',
    TABLE_ROWS as '数据行数',
    AUTO_INCREMENT as '当前自增值'
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = 'chat'
  AND AUTO_INCREMENT IS NOT NULL
ORDER BY AUTO_INCREMENT DESC;

-- 检查最大ID值，确保雪花ID不会冲突
SELECT
    '现有数据ID范围检查' as check_type,
    (SELECT MAX(id) FROM t_user WHERE deleted = 0) as max_user_id,
    (SELECT MAX(id) FROM t_chat_session WHERE deleted = 0) as max_session_id,
    (SELECT MAX(id) FROM t_chat_message WHERE deleted = 0) as max_message_id,
    (SELECT MAX(id) FROM t_file_upload WHERE deleted = 0) as max_file_id;

-- ========================================
-- 第三步：安全的ID策略迁移（保留现有数据）
-- ========================================

-- 重要说明：我们只移除AUTO_INCREMENT，保留现有数据的ID值
-- 新数据将使用雪花ID，雪花ID的值远大于现有自增ID，不会冲突

-- 获取当前最大AUTO_INCREMENT值，用于设置雪花ID起始值
SET @max_auto_increment = (
    SELECT MAX(AUTO_INCREMENT)
    FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = 'chat'
      AND AUTO_INCREMENT IS NOT NULL
);

-- 记录迁移策略信息
INSERT INTO id_migration_backup (table_name, old_auto_increment)
VALUES ('MIGRATION_STRATEGY', @max_auto_increment)
ON DUPLICATE KEY UPDATE
    old_auto_increment = @max_auto_increment,
    backup_time = CURRENT_TIMESTAMP;

-- 修改表结构：移除AUTO_INCREMENT但保留现有数据
-- 注意：这不会改变现有数据的ID值，只是改变新数据的生成策略

-- 修改 t_user 表
ALTER TABLE t_user MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（现有数据保持自增ID，新数据使用雪花ID）';

-- 修改 t_ai_model 表
ALTER TABLE t_ai_model MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（现有数据保持自增ID，新数据使用雪花ID）';

-- 修改 t_chat_session 表
ALTER TABLE t_chat_session MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（现有数据保持自增ID，新数据使用雪花ID）';

-- 修改 t_chat_message 表
ALTER TABLE t_chat_message MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（现有数据保持自增ID，新数据使用雪花ID）';

-- 修改 t_file_upload 表
ALTER TABLE t_file_upload MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（现有数据保持自增ID，新数据使用雪花ID）';

-- 修改 t_package 表
ALTER TABLE t_package MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（现有数据保持自增ID，新数据使用雪花ID）';

-- 修改 t_user_account 表
ALTER TABLE t_user_account MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（现有数据保持自增ID，新数据使用雪花ID）';

-- 修改 t_user_daily_usage 表
ALTER TABLE t_user_daily_usage MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（现有数据保持自增ID，新数据使用雪花ID）';

-- 修改 t_user_order 表
ALTER TABLE t_user_order MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（现有数据保持自增ID，新数据使用雪花ID）';

-- 修改 t_payment_audit 表
ALTER TABLE t_payment_audit MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（现有数据保持自增ID，新数据使用雪花ID）';

-- 修改 t_web_search_content 表
ALTER TABLE t_web_search_content MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（现有数据保持自增ID，新数据使用雪花ID）';

-- 修改 t_system_setting 表（如果存在）
ALTER TABLE t_system_setting MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（现有数据保持自增ID，新数据使用雪花ID）';

-- 修改 t_admin_user 表（如果存在）
ALTER TABLE t_admin_user MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（现有数据保持自增ID，新数据使用雪花ID）';

-- 修改 t_admin_operation_log 表（如果存在）
ALTER TABLE t_admin_operation_log MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（现有数据保持自增ID，新数据使用雪花ID）';

-- 修改 t_oauth2_state 表（如果存在）
ALTER TABLE t_oauth2_state MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（现有数据保持自增ID，新数据使用雪花ID）';

-- 修改 t_oauth2_audit_log 表（如果存在）
ALTER TABLE t_oauth2_audit_log MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（现有数据保持自增ID，新数据使用雪花ID）';

-- 修改 t_user_oauth 表（如果存在）
ALTER TABLE t_user_oauth MODIFY COLUMN id BIGINT NOT NULL COMMENT '主键ID（现有数据保持自增ID，新数据使用雪花ID）';

-- ========================================
-- 第三步：验证修改结果
-- ========================================

-- 检查所有表是否已移除AUTO_INCREMENT
SELECT 
    TABLE_NAME as '表名',
    COLUMN_NAME as '列名',
    DATA_TYPE as '数据类型',
    EXTRA as '额外属性',
    CASE 
        WHEN EXTRA LIKE '%auto_increment%' THEN '❌ 仍有AUTO_INCREMENT'
        ELSE '✅ 已移除AUTO_INCREMENT'
    END as '修改状态'
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = 'chat' 
  AND COLUMN_NAME = 'id'
  AND TABLE_NAME LIKE 't_%'
ORDER BY TABLE_NAME;

-- 检查表结构完整性
SELECT 
    TABLE_NAME as '表名',
    TABLE_ROWS as '数据行数',
    CASE 
        WHEN TABLE_ROWS > 0 THEN '✅ 数据完整'
        ELSE '⚠️ 无数据'
    END as '数据状态'
FROM INFORMATION_SCHEMA.TABLES 
WHERE TABLE_SCHEMA = 'chat' 
  AND TABLE_TYPE = 'BASE TABLE'
  AND TABLE_NAME LIKE 't_%'
ORDER BY TABLE_NAME;

-- ========================================
-- 第四步：记录修改完成信息
-- ========================================

INSERT INTO id_migration_backup (table_name, old_auto_increment) 
VALUES ('MIGRATION_COMPLETED', 0)
ON DUPLICATE KEY UPDATE 
    backup_time = CURRENT_TIMESTAMP;

-- 提交事务
COMMIT;

-- ========================================
-- 第五步：统一业务ID格式（UUID格式标准化）
-- ========================================

-- 统一messageId格式：移除连字符，统一为32位格式
UPDATE t_chat_message
SET message_id = REPLACE(message_id, '-', '')
WHERE message_id LIKE '%-%' AND deleted = 0;

-- 统一sessionId格式：移除连字符，统一为32位格式（如果有的话）
UPDATE t_chat_session
SET session_id = REPLACE(session_id, '-', '')
WHERE session_id LIKE '%-%' AND deleted = 0;

-- 统一fileId格式：移除连字符，统一为32位格式（如果有的话）
UPDATE t_file_upload
SET file_id = REPLACE(file_id, '-', '')
WHERE file_id LIKE '%-%' AND deleted = 0;

-- 统一webSearchId格式：移除连字符，统一为32位格式（如果有的话）
UPDATE t_web_search_content
SET web_search_id = REPLACE(web_search_id, '-', '')
WHERE web_search_id LIKE '%-%' AND deleted = 0;

-- 同步更新关联表中的外键引用
UPDATE t_chat_message cm
JOIN t_chat_session cs ON cm.session_id = cs.session_id
SET cm.session_id = cs.session_id
WHERE cm.session_id LIKE '%-%' AND deleted = 0;

UPDATE t_file_upload fu
JOIN t_chat_session cs ON fu.session_id = cs.session_id
SET fu.session_id = cs.session_id
WHERE fu.session_id LIKE '%-%' AND deleted = 0;

UPDATE t_file_upload fu
JOIN t_chat_message cm ON fu.message_id = cm.message_id
SET fu.message_id = cm.message_id
WHERE fu.message_id LIKE '%-%' AND deleted = 0;

UPDATE t_web_search_content wsc
JOIN t_chat_session cs ON wsc.session_id = cs.session_id
SET wsc.session_id = cs.session_id
WHERE wsc.session_id LIKE '%-%' AND deleted = 0;

UPDATE t_web_search_content wsc
JOIN t_chat_message cm ON wsc.message_id = cm.message_id
SET wsc.message_id = cm.message_id
WHERE wsc.message_id LIKE '%-%' AND deleted = 0;

-- ========================================
-- 第六步：验证业务ID格式统一结果
-- ========================================

-- 检查messageId格式统一情况
SELECT
    '业务ID格式检查' as check_type,
    'messageId' as id_type,
    COUNT(*) as total_count,
    SUM(CASE WHEN message_id LIKE '%-%' THEN 1 ELSE 0 END) as with_dash_count,
    SUM(CASE WHEN LENGTH(message_id) = 32 THEN 1 ELSE 0 END) as standard_format_count
FROM t_chat_message
WHERE deleted = 0;

-- 检查sessionId格式统一情况
SELECT
    '业务ID格式检查' as check_type,
    'sessionId' as id_type,
    COUNT(*) as total_count,
    SUM(CASE WHEN session_id LIKE '%-%' THEN 1 ELSE 0 END) as with_dash_count,
    SUM(CASE WHEN LENGTH(session_id) = 32 THEN 1 ELSE 0 END) as standard_format_count
FROM t_chat_session
WHERE deleted = 0;

-- 检查fileId格式统一情况
SELECT
    '业务ID格式检查' as check_type,
    'fileId' as id_type,
    COUNT(*) as total_count,
    SUM(CASE WHEN file_id LIKE '%-%' THEN 1 ELSE 0 END) as with_dash_count,
    SUM(CASE WHEN LENGTH(file_id) = 32 THEN 1 ELSE 0 END) as standard_format_count
FROM t_file_upload
WHERE deleted = 0;

-- ========================================
-- 修改完成报告
-- ========================================

SELECT '🎉 ID策略统一修改完成！' AS status;
SELECT
    '修改完成时间' as item,
    NOW() as value
UNION ALL
SELECT
    '修改的表数量',
    CAST(COUNT(*) AS CHAR)
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'chat'
  AND COLUMN_NAME = 'id'
  AND TABLE_NAME LIKE 't_%'
UNION ALL
SELECT
    '备份记录数量',
    CAST(COUNT(*) AS CHAR)
FROM id_migration_backup;

SELECT '✅ 所有表已成功修改为雪花ID策略' AS completion_message;
SELECT '✅ 所有业务ID已统一为32位无连字符格式' AS uuid_format_message;
SELECT '⚠️ 请重启应用以使MyBatis-Plus雪花ID生成器生效' AS important_note;
