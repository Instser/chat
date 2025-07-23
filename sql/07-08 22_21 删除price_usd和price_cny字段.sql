/*
删除price_usd和price_cny字段脚本
执行时间: 2025-07-08 22:21 (北京时间)
版本: v1.1 (已清理汇率转换功能)
目的: 删除t_package表中的price_usd和price_cny字段，统一使用CNY价格
注意: 执行前请确保已备份数据，并且代码已更新为不再使用这些字段
*/


-- =====================================================
-- 2. 验证当前表结构和数据
-- =====================================================

-- 检查当前表结构
DESCRIBE t_package;

-- 检查price相关字段的数据分布
SELECT 
    '删除前数据检查' as description,
    COUNT(*) as total_packages,
    COUNT(CASE WHEN price_usd IS NOT NULL AND price_usd > 0 THEN 1 END) as valid_price_usd,
    COUNT(CASE WHEN price_cny IS NOT NULL AND price_cny > 0 THEN 1 END) as valid_price_cny,
    COUNT(CASE WHEN price IS NOT NULL AND price > 0 THEN 1 END) as valid_price,
    AVG(price_usd) as avg_price_usd,
    AVG(price_cny) as avg_price_cny,
    AVG(price) as avg_price
FROM t_package 
WHERE deleted = 0;

-- 显示具体的价格数据
SELECT 
    id,
    package_name,
    package_type,
    price_usd,
    price_cny,
    price,
    is_enabled
FROM t_package 
WHERE deleted = 0
ORDER BY id;

-- =====================================================
-- 3. 检查是否有外键约束
-- =====================================================

SELECT 
    CONSTRAINT_NAME,
    TABLE_NAME,
    COLUMN_NAME,
    REFERENCED_TABLE_NAME,
    REFERENCED_COLUMN_NAME
FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_package'
  AND COLUMN_NAME IN ('price_usd', 'price_cny');

-- =====================================================
-- 4. 删除字段（谨慎执行）
-- =====================================================

-- 注意：执行前请确保：
-- 1. 已经备份数据
-- 2. 应用程序代码已更新，不再使用price_usd和price_cny字段
-- 3. 已经测试过新版本代码的功能正常

-- 删除price_usd字段
ALTER TABLE t_package DROP COLUMN price_usd;

-- 删除price_cny字段  
ALTER TABLE t_package DROP COLUMN price_cny;

-- =====================================================
-- 5. 验证删除结果
-- =====================================================

-- 检查删除后的表结构
DESCRIBE t_package;

-- 验证字段是否已删除
SELECT 
    COLUMN_NAME,
    DATA_TYPE,
    IS_NULLABLE,
    COLUMN_DEFAULT,
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 't_package'
  AND COLUMN_NAME IN ('price_usd', 'price_cny');

-- 如果上面查询返回空结果，说明字段删除成功

-- =====================================================
-- 6. 优化表结构
-- =====================================================

-- 优化表
OPTIMIZE TABLE t_package;

-- =====================================================
-- 7. 最终验证
-- =====================================================

-- 验证当前表结构
SELECT 
    '删除后验证' as description,
    COUNT(*) as total_packages,
    COUNT(CASE WHEN price IS NOT NULL AND price > 0 THEN 1 END) as valid_price_records,
    AVG(price) as avg_price,
    MIN(price) as min_price,
    MAX(price) as max_price
FROM t_package 
WHERE deleted = 0;

-- 显示最终的套餐数据结构
SELECT 
    id,
    package_name,
    package_type,
    price,
    validity_days,
    limit_type,
    is_enabled,
    create_time
FROM t_package 
WHERE deleted = 0
ORDER BY sort_order ASC, id ASC;

-- =====================================================
-- 8. 清理说明
-- =====================================================

/*
删除完成后的清理工作：

1. 代码修改：
   - 更新Package.java实体类，移除priceUsd和priceCny字段
   - 更新PackageMapper.xml，移除相关字段映射
   - 更新所有使用这些字段的Service、Controller代码
   - 更新VO转换器中的价格计算逻辑

2. 测试验证：
   - 测试套餐查询功能
   - 测试支付流程
   - 测试价格显示功能

3. 监控：
   - 监控应用启动是否正常
   - 监控相关功能是否工作正常
   - 检查日志是否有字段相关错误

4. 备份保留：
   - 保留备份表t_package_backup_20250708至少30天
   - 确认系统稳定后可删除备份表

注意：如果需要回滚，可以使用备份表恢复数据
*/
