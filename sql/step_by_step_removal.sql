-- 分步执行的字段删除脚本
-- 请按步骤逐一执行，每步执行后检查结果

-- ========================================
-- 步骤1: 检查当前状态
-- ========================================

-- 查看表结构
DESCRIBE t_package;

-- 查看字段详情
SELECT 
    COLUMN_NAME,
    DATA_TYPE,
    IS_NULLABLE,
    COLUMN_DEFAULT,
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_package' 
  AND COLUMN_NAME IN ('chat_count', 'daily_chat_limit');

-- ========================================
-- 步骤2: 验证应用程序状态
-- ========================================

-- 确认应用程序已经部署新版本，不再使用这些字段
-- 可以通过应用程序日志或API测试来验证

-- ========================================
-- 步骤3: 删除chat_count字段
-- ========================================

-- 执行删除
ALTER TABLE t_package DROP COLUMN chat_count;

-- 验证删除结果
SELECT 
    COLUMN_NAME
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_package' 
  AND COLUMN_NAME = 'chat_count';
-- 应该返回空结果

-- ========================================
-- 步骤4: 删除daily_chat_limit字段
-- ========================================

-- 执行删除
ALTER TABLE t_package DROP COLUMN daily_chat_limit;

-- 验证删除结果
SELECT 
    COLUMN_NAME
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_package' 
  AND COLUMN_NAME = 'daily_chat_limit';
-- 应该返回空结果

-- ========================================
-- 步骤5: 验证最终状态
-- ========================================

-- 查看最终表结构
DESCRIBE t_package;

-- 验证套餐数据完整性
SELECT 
    id,
    package_name,
    limit_type,
    model_permissions,
    validity_days,
    price
FROM t_package 
WHERE deleted = 0
ORDER BY id;

-- 验证model_permissions配置
SELECT 
    id,
    package_name,
    CASE 
        WHEN model_permissions IS NULL OR model_permissions = '' THEN '缺少配置'
        WHEN JSON_VALID(model_permissions) = 0 THEN '配置格式错误'
        ELSE '配置正常'
    END as config_status,
    JSON_EXTRACT(model_permissions, '$.STANDARD.daily_limit') as standard_daily_limit
FROM t_package 
WHERE deleted = 0
ORDER BY id;

-- ========================================
-- 步骤6: 优化表结构
-- ========================================

OPTIMIZE TABLE t_package;

-- ========================================
-- 步骤7: 应用程序验证
-- ========================================

-- 删除完成后，请执行以下验证：
-- 1. 重启应用程序
-- 2. 测试套餐列表API
-- 3. 测试套餐详情API
-- 4. 测试用户套餐激活功能
-- 5. 测试聊天限制检查功能
-- 6. 测试聊天使用记录功能

-- ========================================
-- 回滚脚本（如果需要）
-- ========================================

-- 如果删除后发现问题，可以执行以下回滚操作：

-- 重新添加chat_count字段
-- ALTER TABLE t_package ADD COLUMN chat_count INT DEFAULT 1000 COMMENT '包含的AI对话次数';

-- 重新添加daily_chat_limit字段
-- ALTER TABLE t_package ADD COLUMN daily_chat_limit INT DEFAULT 30 COMMENT '每日聊天次数限制';

-- 根据套餐类型设置默认值
-- UPDATE t_package SET chat_count = 1000, daily_chat_limit = 30 WHERE package_type = 'basic';
-- UPDATE t_package SET chat_count = 3000, daily_chat_limit = 100 WHERE package_type = 'medium';
-- UPDATE t_package SET chat_count = -1, daily_chat_limit = -1 WHERE package_type = 'high';

-- ========================================
-- 执行建议
-- ========================================

-- 1. 在维护窗口期间执行
-- 2. 确保应用程序已部署新版本
-- 3. 逐步执行，每步验证结果
-- 4. 保留回滚方案
-- 5. 执行后全面测试应用功能
