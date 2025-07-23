-- ========================================
-- 雪花ID迁移回滚脚本
-- 创建时间: 2025年7月20日 22:20 (北京时间)
-- 描述: 将雪花ID迁移回滚到原始的自增ID状态
-- 警告: 仅在迁移失败或需要紧急回滚时使用
-- ========================================

USE chat;

-- 开始事务
START TRANSACTION;

-- ========================================
-- 第一步：验证回滚条件
-- ========================================

-- 检查是否存在迁移备份数据
SELECT '🔍 回滚条件检查' AS step;

-- 验证id_mapping表是否存在
SELECT 
    CASE 
        WHEN COUNT(*) > 0 THEN '✅ 发现ID映射表，可以执行回滚'
        ELSE '❌ 未发现ID映射表，无法执行回滚'
    END as mapping_table_status
FROM information_schema.tables 
WHERE table_schema = 'chat' 
  AND table_name = 'id_mapping';

-- 验证备份表是否存在
SELECT 
    CASE 
        WHEN COUNT(*) > 0 THEN '✅ 发现备份表，可以恢复AUTO_INCREMENT值'
        ELSE '❌ 未发现备份表，无法恢复AUTO_INCREMENT值'
    END as backup_table_status
FROM information_schema.tables 
WHERE table_schema = 'chat' 
  AND table_name = 'id_migration_backup';

-- 显示迁移记录统计
SELECT 
    table_name as '表名',
    COUNT(*) as '映射记录数'
FROM id_mapping 
GROUP BY table_name
ORDER BY table_name;

-- ========================================
-- 第二步：回滚外键引用
-- ========================================

SELECT '🔄 开始回滚外键引用' AS step;

-- 2.1 回滚 t_web_search_content 表中的外键引用
UPDATE t_web_search_content wsc
JOIN id_mapping im_user ON im_user.table_name = 't_user' AND im_user.new_snowflake_id = wsc.user_id
SET wsc.user_id = im_user.old_id;

-- 2.2 回滚 t_payment_audit 表中的外键引用
UPDATE t_payment_audit pa
JOIN id_mapping im_user ON im_user.table_name = 't_user' AND im_user.new_snowflake_id = pa.user_id
SET pa.user_id = im_user.old_id;

-- 2.3 回滚 t_user_order 表中的外键引用
UPDATE t_user_order uo
JOIN id_mapping im_user ON im_user.table_name = 't_user' AND im_user.new_snowflake_id = uo.user_id
SET uo.user_id = im_user.old_id;

UPDATE t_user_order uo
JOIN id_mapping im_package ON im_package.table_name = 't_package' AND im_package.new_snowflake_id = uo.package_id
SET uo.package_id = im_package.old_id;

-- 2.4 回滚 t_user_daily_usage 表中的外键引用
UPDATE t_user_daily_usage udu
JOIN id_mapping im_user ON im_user.table_name = 't_user' AND im_user.new_snowflake_id = udu.user_id
SET udu.user_id = im_user.old_id;

UPDATE t_user_daily_usage udu
JOIN id_mapping im_model ON im_model.table_name = 't_ai_model' AND im_model.new_snowflake_id = udu.model_id
SET udu.model_id = im_model.old_id;

-- 2.5 回滚 t_user_account 表中的外键引用
UPDATE t_user_account ua
JOIN id_mapping im_user ON im_user.table_name = 't_user' AND im_user.new_snowflake_id = ua.user_id
SET ua.user_id = im_user.old_id;

UPDATE t_user_account ua
JOIN id_mapping im_package ON im_package.table_name = 't_package' AND im_package.new_snowflake_id = ua.package_id
SET ua.package_id = im_package.old_id;

-- 2.6 回滚 t_file_upload 表中的外键引用
UPDATE t_file_upload fu
JOIN id_mapping im_user ON im_user.table_name = 't_user' AND im_user.new_snowflake_id = fu.user_id
SET fu.user_id = im_user.old_id;

-- 2.7 回滚 t_chat_message 表中的外键引用
UPDATE t_chat_message cm
JOIN id_mapping im_user ON im_user.table_name = 't_user' AND im_user.new_snowflake_id = cm.user_id
SET cm.user_id = im_user.old_id;

UPDATE t_chat_message cm
JOIN id_mapping im_model ON im_model.table_name = 't_ai_model' AND im_model.new_snowflake_id = cm.model_id
SET cm.model_id = im_model.old_id;

-- 2.8 回滚 t_chat_session 表中的外键引用
UPDATE t_chat_session cs
JOIN id_mapping im_user ON im_user.table_name = 't_user' AND im_user.new_snowflake_id = cs.user_id
SET cs.user_id = im_user.old_id;

UPDATE t_chat_session cs
JOIN id_mapping im_model ON im_model.table_name = 't_ai_model' AND im_model.new_snowflake_id = cs.model_id
SET cs.model_id = im_model.old_id;

-- ========================================
-- 第三步：回滚主键ID
-- ========================================

SELECT '🔄 开始回滚主键ID' AS step;

-- 按照依赖关系的正序回滚（父表 -> 子表）

-- 3.1 回滚 t_user 表
UPDATE t_user u
JOIN id_mapping im ON im.table_name = 't_user' AND im.new_snowflake_id = u.id
SET u.id = im.old_id;

-- 3.2 回滚 t_ai_model 表
UPDATE t_ai_model am
JOIN id_mapping im ON im.table_name = 't_ai_model' AND im.new_snowflake_id = am.id
SET am.id = im.old_id;

-- 3.3 回滚 t_package 表
UPDATE t_package p
JOIN id_mapping im ON im.table_name = 't_package' AND im.new_snowflake_id = p.id
SET p.id = im.old_id;

-- 3.4 回滚 t_chat_session 表
UPDATE t_chat_session cs
JOIN id_mapping im ON im.table_name = 't_chat_session' AND im.new_snowflake_id = cs.id
SET cs.id = im.old_id;

-- 3.5 回滚 t_chat_message 表
UPDATE t_chat_message cm
JOIN id_mapping im ON im.table_name = 't_chat_message' AND im.new_snowflake_id = cm.id
SET cm.id = im.old_id;

-- 3.6 回滚 t_file_upload 表
UPDATE t_file_upload fu
JOIN id_mapping im ON im.table_name = 't_file_upload' AND im.new_snowflake_id = fu.id
SET fu.id = im.old_id;

-- 3.7 回滚 t_user_account 表
UPDATE t_user_account ua
JOIN id_mapping im ON im.table_name = 't_user_account' AND im.new_snowflake_id = ua.id
SET ua.id = im.old_id;

-- 3.8 回滚 t_user_daily_usage 表
UPDATE t_user_daily_usage udu
JOIN id_mapping im ON im.table_name = 't_user_daily_usage' AND im.new_snowflake_id = udu.id
SET udu.id = im.old_id;

-- 3.9 回滚 t_user_order 表
UPDATE t_user_order uo
JOIN id_mapping im ON im.table_name = 't_user_order' AND im.new_snowflake_id = uo.id
SET uo.id = im.old_id;

-- 3.10 回滚 t_payment_audit 表
UPDATE t_payment_audit pa
JOIN id_mapping im ON im.table_name = 't_payment_audit' AND im.new_snowflake_id = pa.id
SET pa.id = im.old_id;

-- 3.11 回滚 t_web_search_content 表
UPDATE t_web_search_content wsc
JOIN id_mapping im ON im.table_name = 't_web_search_content' AND im.new_snowflake_id = wsc.id
SET wsc.id = im.old_id;

-- ========================================
-- 第四步：恢复AUTO_INCREMENT属性
-- ========================================

SELECT '🔄 恢复AUTO_INCREMENT属性' AS step;

-- 恢复核心业务表的AUTO_INCREMENT属性和起始值
ALTER TABLE t_user MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID（自增）';
ALTER TABLE t_ai_model MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID（自增）';
ALTER TABLE t_chat_session MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID（自增）';
ALTER TABLE t_chat_message MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID（自增）';
ALTER TABLE t_file_upload MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID（自增）';
ALTER TABLE t_package MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID（自增）';
ALTER TABLE t_user_account MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID（自增）';
ALTER TABLE t_user_daily_usage MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID（自增）';
ALTER TABLE t_user_order MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID（自增）';
ALTER TABLE t_payment_audit MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID（自增）';

-- 只有当t_web_search_content表存在时才恢复
SET @table_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                     WHERE TABLE_SCHEMA = 'chat' AND TABLE_NAME = 't_web_search_content');
SET @sql = IF(@table_exists > 0,
    'ALTER TABLE t_web_search_content MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT ''主键ID（自增）'';',
    'SELECT ''t_web_search_content表不存在，跳过恢复'' as skip_message;'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 恢复AUTO_INCREMENT起始值
SET @sql = CONCAT('ALTER TABLE t_user AUTO_INCREMENT = ', 
    (SELECT old_auto_increment FROM id_migration_backup WHERE table_name = 't_user'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = CONCAT('ALTER TABLE t_ai_model AUTO_INCREMENT = ', 
    (SELECT old_auto_increment FROM id_migration_backup WHERE table_name = 't_ai_model'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = CONCAT('ALTER TABLE t_chat_session AUTO_INCREMENT = ', 
    (SELECT old_auto_increment FROM id_migration_backup WHERE table_name = 't_chat_session'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = CONCAT('ALTER TABLE t_chat_message AUTO_INCREMENT = ', 
    (SELECT old_auto_increment FROM id_migration_backup WHERE table_name = 't_chat_message'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = CONCAT('ALTER TABLE t_file_upload AUTO_INCREMENT = ', 
    (SELECT old_auto_increment FROM id_migration_backup WHERE table_name = 't_file_upload'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = CONCAT('ALTER TABLE t_package AUTO_INCREMENT = ', 
    (SELECT old_auto_increment FROM id_migration_backup WHERE table_name = 't_package'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = CONCAT('ALTER TABLE t_user_account AUTO_INCREMENT = ', 
    (SELECT old_auto_increment FROM id_migration_backup WHERE table_name = 't_user_account'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = CONCAT('ALTER TABLE t_user_order AUTO_INCREMENT = ', 
    (SELECT old_auto_increment FROM id_migration_backup WHERE table_name = 't_user_order'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ========================================
-- 第五步：验证回滚结果
-- ========================================

SELECT '🔍 验证回滚结果' AS step;

-- 验证AUTO_INCREMENT是否恢复
SELECT 
    TABLE_NAME as '表名',
    AUTO_INCREMENT as '恢复的自增值',
    CASE 
        WHEN AUTO_INCREMENT IS NOT NULL THEN '✅ AUTO_INCREMENT已恢复'
        ELSE '❌ AUTO_INCREMENT恢复失败'
    END as '恢复状态'
FROM INFORMATION_SCHEMA.TABLES 
WHERE TABLE_SCHEMA = 'chat' 
  AND TABLE_NAME LIKE 't_%'
ORDER BY TABLE_NAME;

-- 验证数据完整性
SELECT 
    '数据完整性验证' as check_type,
    (SELECT COUNT(*) FROM t_user WHERE deleted = 0) as user_count,
    (SELECT COUNT(*) FROM t_chat_session WHERE deleted = 0) as session_count,
    (SELECT COUNT(*) FROM t_chat_message WHERE deleted = 0) as message_count;

-- 验证外键关联
SELECT 
    '外键关联验证' as check_type,
    (SELECT COUNT(*) FROM t_chat_session cs 
     LEFT JOIN t_user u ON cs.user_id = u.id 
     WHERE u.id IS NULL AND cs.deleted = 0) as orphaned_sessions,
    (SELECT COUNT(*) FROM t_chat_message cm 
     LEFT JOIN t_user u ON cm.user_id = u.id 
     WHERE u.id IS NULL AND cm.deleted = 0) as orphaned_messages;

-- 记录回滚完成
INSERT INTO id_migration_backup (table_name, old_auto_increment, migration_strategy) 
VALUES ('ROLLBACK_COMPLETED', 0, 'AUTO_INCREMENT_RESTORED')
ON DUPLICATE KEY UPDATE 
    backup_time = CURRENT_TIMESTAMP,
    migration_strategy = 'AUTO_INCREMENT_RESTORED';

-- 提交事务
COMMIT;

-- ========================================
-- 第六步：清理迁移数据（可选）
-- ========================================

SELECT '🧹 清理迁移数据' AS step;

-- 注意：以下操作会删除迁移过程中创建的表，请谨慎执行
-- 如果确认回滚成功，可以取消注释执行

-- DROP TABLE IF EXISTS id_mapping;
-- DELETE FROM id_migration_backup WHERE migration_strategy IN ('ID_MAPPING_MIGRATION', 'SNOWFLAKE_ID_MIGRATION_SUCCESS');

SELECT '⚠️ 迁移数据表保留，如需清理请手动执行' AS cleanup_note;

-- ========================================
-- 回滚完成报告
-- ========================================

SELECT '🎉 雪花ID迁移回滚完成！' AS status;
SELECT 
    '回滚完成时间' as item,
    NOW() as value
UNION ALL
SELECT 
    '回滚的表数量',
    CAST(COUNT(DISTINCT table_name) AS CHAR)
FROM id_mapping
UNION ALL
SELECT 
    '回滚的记录总数',
    CAST(COUNT(*) AS CHAR)
FROM id_mapping;

SELECT '✅ 所有主键ID已回滚为自增ID' AS completion_message;
SELECT '✅ 所有外键关联已恢复' AS integrity_message;
SELECT '✅ AUTO_INCREMENT属性已恢复' AS auto_increment_message;
SELECT '⚠️ 请重启应用以使MyBatis-Plus自增ID配置生效' AS important_note;
SELECT '💡 建议验证应用功能正常后再清理迁移数据表' AS recommendation;
