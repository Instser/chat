/*
 删除t_file_upload表的storage_path字段
 
 修改原因：
 1. 新的文件上传架构不再需要存储文件路径
 2. 所有文件都解析为文本内容存储在content字段中
 3. 临时文件在解析完成后立即删除
 4. 简化数据库结构，提升性能
 
 执行时间：2025-06-27 14:28
 执行环境：MySQL 5.7+
 兼容性：Navicat执行环境
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ====================================================================
-- 备份当前表结构（可选）
-- ====================================================================

-- 创建备份表（如果需要回滚）
-- CREATE TABLE `t_file_upload_backup_20250627` AS SELECT * FROM `t_file_upload`;

-- ====================================================================
-- 删除storage_path字段
-- ====================================================================

-- 检查字段是否存在，如果存在则删除
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 't_file_upload'
     AND COLUMN_NAME = 'storage_path') > 0,
    'ALTER TABLE `t_file_upload` DROP COLUMN `storage_path`',
    'SELECT "storage_path字段不存在，跳过删除操作" AS message'
));

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ====================================================================
-- 验证修改结果
-- ====================================================================

-- 显示修改后的表结构
DESCRIBE `t_file_upload`;

-- 显示表的创建语句
SHOW CREATE TABLE `t_file_upload`;

-- ====================================================================
-- 性能优化建议
-- ====================================================================

-- 由于删除了storage_path字段，可以考虑优化相关索引
-- 如果有包含storage_path的复合索引，需要重新创建

-- 检查是否有包含storage_path的索引需要处理
SELECT 
    INDEX_NAME,
    COLUMN_NAME,
    SEQ_IN_INDEX
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE() 
    AND TABLE_NAME = 't_file_upload'
    AND COLUMN_NAME = 'storage_path';

-- ====================================================================
-- 修改说明
-- ====================================================================

/*
修改前的字段：
- storage_path: varchar(255) NOT NULL COMMENT '存储路径'

修改后：
- 字段已删除

影响分析：
1. 数据库存储空间减少
2. 插入操作不再需要提供storage_path值
3. 查询操作不能再使用storage_path字段
4. 应用代码需要同步更新FileUpload实体类

注意事项：
1. 执行前请确保应用代码已经更新
2. 建议在维护窗口期间执行
3. 执行后无法通过storage_path查询历史文件
4. 如需回滚，请使用备份表恢复数据

兼容性：
- MySQL 5.7+
- 支持Navicat等图形化工具执行
- 支持命令行执行

执行步骤：
1. 停止应用服务（可选，建议）
2. 执行此SQL脚本
3. 更新应用代码中的FileUpload实体类
4. 重启应用服务
5. 验证功能正常

回滚方案：
如需回滚，可执行：
ALTER TABLE `t_file_upload` ADD COLUMN `storage_path` varchar(255) DEFAULT '' COMMENT '存储路径';
UPDATE `t_file_upload` SET `storage_path` = CONCAT('temp/', file_id) WHERE `storage_path` = '';
*/

SET FOREIGN_KEY_CHECKS = 1;
