-- AI模型可见性字段添加脚本
-- 创建时间: 2025-06-30 18:18
-- 版本: v1.0
-- 描述: 为t_ai_model表添加is_visible字段，用于控制模型在API中的可见性

-- 添加is_visible字段
ALTER TABLE `t_ai_model` 
ADD COLUMN `is_visible` tinyint DEFAULT '1' COMMENT '是否可见（0-不可见，1-可见）' 
AFTER `is_available`;

-- 更新现有数据，默认所有模型都可见
UPDATE `t_ai_model` SET `is_visible` = 1 WHERE `deleted` = 0;

-- 验证字段添加成功
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

-- 查看表结构
DESCRIBE `t_ai_model`;
