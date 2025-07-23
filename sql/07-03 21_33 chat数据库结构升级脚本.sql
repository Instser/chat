/*
 chat数据库结构升级脚本
 
 升级目的：将chat.sql结构适配为chat1.sql结构
 执行时间：2025-07-03 21:33 (北京时间)
 版本：v1.0 - 结构升级版
 
 主要变更：
 1. t_ai_model表新增is_visible字段
 2. t_package表删除chat_count和daily_chat_limit字段
 3. 更新AUTO_INCREMENT值
*/

-- ========================================
-- 第一部分：t_ai_model表结构升级
-- ========================================

-- 1.1 添加is_visible字段
ALTER TABLE `t_ai_model` 
ADD COLUMN `is_visible` tinyint DEFAULT '1' COMMENT '是否可见（0-不可见，1-可见）' 
AFTER `is_available`;

-- 1.2 更新AUTO_INCREMENT值
ALTER TABLE `t_ai_model` AUTO_INCREMENT = 7;

-- ========================================
-- 第二部分：t_package表结构升级
-- ========================================

-- 2.1 删除废弃字段
ALTER TABLE `t_package` DROP COLUMN `chat_count`;
ALTER TABLE `t_package` DROP COLUMN `daily_chat_limit`;

-- 2.2 更新AUTO_INCREMENT值
ALTER TABLE `t_package` AUTO_INCREMENT = 7;

-- ========================================
-- 第三部分：t_file_upload表AUTO_INCREMENT更新
-- ========================================

-- 3.1 更新AUTO_INCREMENT值
ALTER TABLE `t_file_upload` AUTO_INCREMENT = 164;

-- ========================================
-- 第四部分：验证升级结果
-- ========================================

-- 4.1 验证t_ai_model表结构
SELECT 'AI模型表结构验证' as check_type;
DESCRIBE `t_ai_model`;

-- 4.2 验证t_package表结构
SELECT '套餐表结构验证' as check_type;
DESCRIBE `t_package`;

-- 4.3 验证字段是否正确删除
SELECT 
    '检查废弃字段删除状态' as description,
    CASE 
        WHEN COUNT(*) = 0 THEN '✅ 废弃字段已成功删除'
        ELSE CONCAT('❌ 仍有', COUNT(*), '个废弃字段未删除')
    END as status
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_package' 
  AND COLUMN_NAME IN ('chat_count', 'daily_chat_limit');

-- 4.4 验证新字段是否正确添加
SELECT 
    '检查新字段添加状态' as description,
    CASE 
        WHEN COUNT(*) = 1 THEN '✅ is_visible字段已成功添加'
        ELSE '❌ is_visible字段添加失败'
    END as status
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_ai_model' 
  AND COLUMN_NAME = 'is_visible';

-- 4.5 查看表的AUTO_INCREMENT状态
SELECT 
    TABLE_NAME as '表名',
    AUTO_INCREMENT as '当前AUTO_INCREMENT值'
FROM INFORMATION_SCHEMA.TABLES 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME IN ('t_ai_model', 't_package', 't_file_upload')
ORDER BY TABLE_NAME;

-- ========================================
-- 升级完成说明
-- ========================================

/*
升级完成后的主要变更：

1. t_ai_model表：
   - 新增字段：is_visible tinyint DEFAULT '1' COMMENT '是否可见（0-不可见，1-可见）'
   - AUTO_INCREMENT: 6 → 7

2. t_package表：
   - 删除字段：chat_count, daily_chat_limit
   - 保留字段：model_permissions (用于JSON权限管理)
   - AUTO_INCREMENT: 7 (保持不变)

3. t_file_upload表：
   - AUTO_INCREMENT: 144 → 164

4. 权限管理升级：
   - 从简单数值限制 → JSON权限配置
   - 支持模型级别的精细权限控制
   - 支持不同模型类型的差异化限制

5. 模型可见性控制：
   - 支持内部测试模型（不可见）
   - 支持公开API模型（可见）
   - 更精细的模型管理

注意事项：
- 执行前请备份数据库
- 确保应用代码已适配新的权限管理逻辑
- 验证所有套餐都有正确的model_permissions JSON配置
*/
