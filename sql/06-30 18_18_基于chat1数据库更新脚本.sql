-- 基于chat1.sql的数据库更新脚本
-- 创建时间: 2025-06-30 18:18
-- 版本: v1.0
-- 描述: 为现有数据库添加AI模型可见性字段
-- 基础结构: chat1.sql

-- ============================================
-- AI模型可见性功能更新
-- ============================================

-- 检查数据库连接
SELECT 'Database update script started' AS status;

-- 1. 检查t_ai_model表是否存在
SELECT 
    TABLE_NAME,
    TABLE_COMMENT
FROM INFORMATION_SCHEMA.TABLES 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_ai_model';

-- 2. 检查is_visible字段是否已存在
SELECT 
    COLUMN_NAME,
    DATA_TYPE,
    IS_NULLABLE,
    COLUMN_DEFAULT,
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_ai_model' 
  AND COLUMN_NAME = 'is_visible';

-- 3. 添加is_visible字段（如果不存在）
-- 注意：MySQL会忽略已存在的字段，但为了安全起见，建议先检查
SET @sql = (
    SELECT CASE 
        WHEN COUNT(*) = 0 THEN 
            'ALTER TABLE `t_ai_model` ADD COLUMN `is_visible` tinyint DEFAULT ''1'' COMMENT ''是否可见（0-不可见，1-可见）'' AFTER `is_available`;'
        ELSE 
            'SELECT ''Field is_visible already exists'' AS message;'
    END
    FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() 
      AND TABLE_NAME = 't_ai_model' 
      AND COLUMN_NAME = 'is_visible'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4. 更新现有数据，确保所有模型默认可见
UPDATE `t_ai_model` 
SET `is_visible` = 1 
WHERE `deleted` = 0 
  AND (`is_visible` IS NULL OR `is_visible` = 0);

-- 5. 验证字段添加成功
SELECT 
    COLUMN_NAME,
    DATA_TYPE,
    IS_NULLABLE,
    COLUMN_DEFAULT,
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_ai_model' 
  AND COLUMN_NAME = 'is_visible';

-- 6. 查看更新后的表结构
DESCRIBE `t_ai_model`;

-- 7. 验证数据更新结果
SELECT 
    id,
    model_code,
    model_name,
    is_available,
    is_visible,
    deleted
FROM `t_ai_model` 
WHERE `deleted` = 0
ORDER BY `create_time` ASC;

-- 8. 统计信息
SELECT 
    COUNT(*) AS total_models,
    SUM(CASE WHEN is_available = 1 THEN 1 ELSE 0 END) AS available_models,
    SUM(CASE WHEN is_visible = 1 THEN 1 ELSE 0 END) AS visible_models,
    SUM(CASE WHEN is_available = 1 AND is_visible = 1 THEN 1 ELSE 0 END) AS available_and_visible_models
FROM `t_ai_model` 
WHERE `deleted` = 0;

-- ============================================
-- 更新完成提示
-- ============================================
SELECT 'Database update completed successfully' AS status;

-- ============================================
-- 使用说明
-- ============================================
/*
使用说明：

1. 字段含义：
   - is_visible = 1: 模型在API中可见，前端可以获取
   - is_visible = 0: 模型在API中不可见，仅供内部使用

2. 管理模型可见性：
   
   -- 隐藏模型（设为内部使用）
   UPDATE t_ai_model SET is_visible = 0 WHERE model_code = 'internal-model';
   
   -- 显示模型（设为公开可用）
   UPDATE t_ai_model SET is_visible = 1 WHERE model_code = 'public-model';
   
   -- 批量隐藏测试模型
   UPDATE t_ai_model SET is_visible = 0 WHERE model_code LIKE '%test%';

3. 查询不同类型的模型：
   
   -- 查询所有可见模型（API返回）
   SELECT * FROM t_ai_model 
   WHERE is_available = 1 AND is_visible = 1 AND deleted = 0;
   
   -- 查询所有可用模型（内部使用）
   SELECT * FROM t_ai_model 
   WHERE is_available = 1 AND deleted = 0;
   
   -- 查询隐藏的模型
   SELECT * FROM t_ai_model 
   WHERE is_available = 1 AND is_visible = 0 AND deleted = 0;

4. 注意事项：
   - 修改可见性不影响模型的可用性
   - 内部功能（如自动标题生成）仍可使用隐藏的模型
   - 建议在修改前备份数据
   - 重要变更应记录操作日志

5. API影响：
   - GET /api/models - 只返回可见模型
   - GET /api/chat/models - 只返回可见模型
   - 内部服务仍可访问所有可用模型
*/
