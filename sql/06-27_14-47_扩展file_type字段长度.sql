/*
 扩展file_type字段长度修复SQL
 
 修改目的：
 1. 将file_type字段长度从varchar(50)扩展到varchar(100)
 2. 解决DOCX等Office文档MIME类型过长导致的数据截断问题
 
 问题描述：
 - DOCX文件的MIME类型：application/vnd.openxmlformats-officedocument.wordprocessingml.document (71字符)
 - 当前字段长度：varchar(50)
 - 导致错误：Data too long for column 'file_type' at row 1
 
 执行时间：2025-06-27 14:47
 执行环境：MySQL 5.7+
 兼容性：Navicat执行环境
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ====================================================================
-- 第一步：备份当前数据（可选，生产环境建议执行）
-- ====================================================================

-- 创建备份表（如果需要）
-- CREATE TABLE `t_file_upload_backup_20250627` AS SELECT * FROM `t_file_upload`;

-- ====================================================================
-- 第二步：修改file_type字段长度
-- ====================================================================

-- 2.1 检查当前字段定义
SELECT 
    COLUMN_NAME,
    DATA_TYPE,
    CHARACTER_MAXIMUM_LENGTH,
    IS_NULLABLE,
    COLUMN_DEFAULT,
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
AND TABLE_NAME = 't_file_upload' 
AND COLUMN_NAME = 'file_type';

-- 2.2 修改字段长度
ALTER TABLE `t_file_upload` 
MODIFY COLUMN `file_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件类型（MIME类型）';

-- ====================================================================
-- 第三步：验证修改结果
-- ====================================================================

-- 3.1 检查修改后的字段定义
SELECT 
    COLUMN_NAME,
    DATA_TYPE,
    CHARACTER_MAXIMUM_LENGTH,
    IS_NULLABLE,
    COLUMN_DEFAULT,
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
AND TABLE_NAME = 't_file_upload' 
AND COLUMN_NAME = 'file_type';

-- 3.2 检查现有数据中file_type字段的长度分布
SELECT 
    file_type,
    LENGTH(file_type) as type_length,
    COUNT(*) as count
FROM `t_file_upload` 
GROUP BY file_type, LENGTH(file_type)
ORDER BY type_length DESC, count DESC;

-- 3.3 查找最长的file_type值
SELECT 
    file_type,
    LENGTH(file_type) as type_length,
    file_name,
    create_time
FROM `t_file_upload` 
ORDER BY LENGTH(file_type) DESC 
LIMIT 10;

-- ====================================================================
-- 第四步：测试常见的长MIME类型
-- ====================================================================

-- 4.1 测试插入长MIME类型（仅测试，不实际插入）
-- 以下是一些常见的长MIME类型及其长度：

/*
常见长MIME类型长度统计：
- application/vnd.openxmlformats-officedocument.wordprocessingml.document (71字符) - DOCX
- application/vnd.openxmlformats-officedocument.spreadsheetml.sheet (62字符) - XLSX  
- application/vnd.openxmlformats-officedocument.presentationml.presentation (71字符) - PPTX
- application/vnd.ms-excel.sheet.macroEnabled.12 (43字符) - XLSM
- application/vnd.ms-word.document.macroEnabled.12 (44字符) - DOCM
- application/vnd.ms-powerpoint.presentation.macroEnabled.12 (52字符) - PPTM

修改后的varchar(100)可以容纳所有这些类型。
*/

-- ====================================================================
-- 第五步：更新相关索引（如果需要）
-- ====================================================================

-- 5.1 检查file_type相关的索引
SELECT 
    INDEX_NAME,
    COLUMN_NAME,
    SEQ_IN_INDEX,
    NON_UNIQUE,
    INDEX_TYPE
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE() 
AND TABLE_NAME = 't_file_upload' 
AND COLUMN_NAME = 'file_type';

-- 5.2 如果有基于file_type的索引，MySQL会自动调整，无需手动重建

-- ====================================================================
-- 第六步：性能验证
-- ====================================================================

-- 6.1 测试文件类型查询性能
EXPLAIN SELECT * FROM `t_file_upload` 
WHERE `file_type` = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' 
AND `deleted` = 0;

-- 6.2 测试文件类型模糊查询性能
EXPLAIN SELECT * FROM `t_file_upload` 
WHERE `file_type` LIKE 'application/vnd.openxmlformats%' 
AND `deleted` = 0;

SET FOREIGN_KEY_CHECKS = 1;

-- ====================================================================
-- 修改完成说明
-- ====================================================================

/*
修改完成后的file_type字段：
- 原长度：varchar(50)
- 新长度：varchar(100)
- 字符集：utf8mb4_unicode_ci
- 约束：NOT NULL
- 注释：文件类型（MIME类型）

支持的长MIME类型示例：
✓ application/vnd.openxmlformats-officedocument.wordprocessingml.document (DOCX - 71字符)
✓ application/vnd.openxmlformats-officedocument.presentationml.presentation (PPTX - 71字符)  
✓ application/vnd.openxmlformats-officedocument.spreadsheetml.sheet (XLSX - 62字符)
✓ 其他所有常见MIME类型

注意事项：
1. 此修改向后兼容，不会影响现有数据
2. 索引会自动调整，无需手动重建
3. 应用程序无需修改，可以直接使用
4. 建议在生产环境执行前先备份数据
*/
