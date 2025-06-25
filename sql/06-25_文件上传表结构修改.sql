/*
 文件上传表结构修改SQL
 
 修改目的：
 1. 删除file_url字段（不再需要URL访问）
 2. 增加message_id字段（关联到聊天消息）
 3. 增加content字段（存储大量文件内容）
 
 执行时间：2025-06-25
 执行环境：MySQL 5.7+
 兼容性：Navicat执行环境
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ====================================================================
-- 第二步：修改表结构
-- ====================================================================

-- 2.1 删除file_url字段（如果存在）
-- 先检查字段是否存在，避免删除不存在的字段导致错误
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 't_file_upload'
     AND COLUMN_NAME = 'file_url') > 0,
    'ALTER TABLE `t_file_upload` DROP COLUMN `file_url`',
    'SELECT "file_url字段不存在，跳过删除操作" AS message'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2.2 增加message_id字段（关联到聊天消息）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 't_file_upload'
     AND COLUMN_NAME = 'message_id') = 0,
    'ALTER TABLE `t_file_upload` ADD COLUMN `message_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''关联的消息ID'' AFTER `session_id`',
    'SELECT "message_id字段已存在，跳过添加操作" AS message'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2.3 增加content字段（存储大量文件内容）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 't_file_upload'
     AND COLUMN_NAME = 'content') = 0,
    'ALTER TABLE `t_file_upload` ADD COLUMN `content` longtext COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''文件内容（用于存储文本文件或小文件的完整内容）'' AFTER `storage_path`',
    'SELECT "content字段已存在，跳过添加操作" AS message'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ====================================================================
-- 第三步：添加索引和外键约束
-- ====================================================================

-- 3.1 为message_id字段添加索引（如果不存在）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 't_file_upload'
     AND INDEX_NAME = 'idx_message_id') = 0,
    'ALTER TABLE `t_file_upload` ADD INDEX `idx_message_id` (`message_id`) USING BTREE COMMENT ''消息ID索引''',
    'SELECT "idx_message_id索引已存在，跳过创建操作" AS message'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3.2 添加message_id外键约束（关联到t_chat_message表）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
     WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 't_file_upload'
     AND CONSTRAINT_NAME = 'fk_file_message') = 0,
    'ALTER TABLE `t_file_upload` ADD CONSTRAINT `fk_file_message` FOREIGN KEY (`message_id`) REFERENCES `t_chat_message` (`message_id`) ON DELETE SET NULL ON UPDATE CASCADE',
    'SELECT "fk_file_message外键约束已存在，跳过创建操作" AS message'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3.3 为content字段添加全文索引（用于文件内容搜索，可选）
-- 注意：只有在需要搜索文件内容时才添加此索引
-- ALTER TABLE `t_file_upload` ADD FULLTEXT INDEX `ft_content` (`content`) WITH PARSER ngram;

-- ====================================================================
-- 第四步：更新表注释
-- ====================================================================

ALTER TABLE `t_file_upload` COMMENT = '文件上传表 - 支持文件内容存储和消息关联';

-- ====================================================================
-- 第五步：性能优化索引
-- ====================================================================

-- 5.1 添加复合索引：用户ID + 消息ID + 删除标志
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 't_file_upload'
     AND INDEX_NAME = 'idx_user_message_deleted') = 0,
    'ALTER TABLE `t_file_upload` ADD INDEX `idx_user_message_deleted` (`user_id`, `message_id`, `deleted`) USING BTREE COMMENT ''用户消息文件查询优化索引''',
    'SELECT "idx_user_message_deleted索引已存在，跳过创建操作" AS message'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5.2 添加复合索引：会话ID + 消息ID + 创建时间
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 't_file_upload'
     AND INDEX_NAME = 'idx_session_message_time') = 0,
    'ALTER TABLE `t_file_upload` ADD INDEX `idx_session_message_time` (`session_id`, `message_id`, `create_time`) USING BTREE COMMENT ''会话文件时间查询优化索引''',
    'SELECT "idx_session_message_time索引已存在，跳过创建操作" AS message'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5.3 添加复合索引：文件类型 + 状态 + 创建时间
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 't_file_upload'
     AND INDEX_NAME = 'idx_type_status_time') = 0,
    'ALTER TABLE `t_file_upload` ADD INDEX `idx_type_status_time` (`file_type`, `status`, `create_time`) USING BTREE COMMENT ''文件类型状态查询优化索引''',
    'SELECT "idx_type_status_time索引已存在，跳过创建操作" AS message'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ====================================================================
-- 第六步：数据迁移和清理（如果需要）
-- ====================================================================

-- 6.1 清理无效的session_id关联（如果存在）
-- UPDATE `t_file_upload` SET `session_id` = NULL 
-- WHERE `session_id` NOT IN (SELECT `session_id` FROM `t_chat_session`);

-- 6.2 为现有文件设置默认状态（如果需要）
-- UPDATE `t_file_upload` SET `status` = 1 WHERE `status` IS NULL;

-- ====================================================================
-- 第七步：验证修改结果
-- ====================================================================

-- 7.1 查看修改后的表结构
DESCRIBE `t_file_upload`;

-- 7.2 查看表的索引信息
SHOW INDEX FROM `t_file_upload`;

-- 7.3 查看表的约束信息
SELECT 
    CONSTRAINT_NAME,
    CONSTRAINT_TYPE,
    TABLE_NAME,
    COLUMN_NAME,
    REFERENCED_TABLE_NAME,
    REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE 
WHERE TABLE_SCHEMA = DATABASE() 
AND TABLE_NAME = 't_file_upload'
AND REFERENCED_TABLE_NAME IS NOT NULL;

-- ====================================================================
-- 第八步：性能测试查询（验证索引效果）
-- ====================================================================

-- 8.1 测试用户文件查询性能
-- EXPLAIN SELECT * FROM `t_file_upload` 
-- WHERE `user_id` = 1 AND `message_id` = 'test-message-id' AND `deleted` = 0;

-- 8.2 测试会话文件查询性能
-- EXPLAIN SELECT * FROM `t_file_upload` 
-- WHERE `session_id` = 'test-session-id' AND `message_id` IS NOT NULL 
-- ORDER BY `create_time` DESC LIMIT 10;

-- 8.3 测试文件类型查询性能
-- EXPLAIN SELECT COUNT(*) FROM `t_file_upload` 
-- WHERE `file_type` LIKE 'image/%' AND `status` = 1 
-- AND `create_time` >= DATE_SUB(NOW(), INTERVAL 30 DAY);

-- ====================================================================
-- 第九步：清理备份表（生产环境请谨慎执行）
-- ====================================================================

-- 注意：只有在确认修改成功且数据无误后才执行此步骤
-- DROP TABLE IF EXISTS `t_file_upload_backup_20250625`;

SET FOREIGN_KEY_CHECKS = 1;

-- ====================================================================
-- 修改完成说明
-- ====================================================================

/*
修改完成后的t_file_upload表结构：

字段列表：
- id: 主键ID
- file_id: 文件ID（UUID）
- user_id: 用户ID
- session_id: 关联的会话ID
- message_id: 关联的消息ID（新增）
- file_name: 文件名
- file_type: 文件类型（MIME类型）
- file_size: 文件大小（字节）
- storage_path: 存储路径
- content: 文件内容（新增，用于存储文本文件或小文件的完整内容）
- status: 状态（0-处理中，1-可用，2-失败）
- create_time: 创建时间
- update_time: 更新时间
- deleted: 逻辑删除标志

索引列表：
- PRIMARY KEY (id)
- UNIQUE KEY uk_file_id (file_id)
- KEY idx_user_id (user_id)
- KEY idx_session_id (session_id)
- KEY idx_message_id (message_id) -- 新增
- KEY idx_user_message_deleted (user_id, message_id, deleted) -- 新增
- KEY idx_session_message_time (session_id, message_id, create_time) -- 新增
- KEY idx_type_status_time (file_type, status, create_time) -- 新增

外键约束：
- fk_file_user: user_id -> t_user(id)
- fk_file_session: session_id -> t_chat_session(session_id)
- fk_file_message: message_id -> t_chat_message(message_id) -- 新增

主要改进：
1. 删除了file_url字段，不再依赖URL访问
2. 增加了message_id字段，支持文件与具体消息的关联
3. 增加了content字段，支持存储文件内容（特别适用于文本文件）
4. 优化了索引结构，提升查询性能
5. 完善了外键约束，保证数据一致性

使用建议：
1. content字段适用于存储文本文件、小图片的base64编码等
2. 大文件仍建议使用storage_path存储文件路径
3. 可以根据file_size判断是否将内容存储在content字段中
4. 建议设置content字段的大小限制（如16MB以下的文件）
*/
