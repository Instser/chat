-- 套餐优惠价格测试数据设置脚本
-- 执行时间: 2025-07-16 02:15:00
-- 目的: 为现有套餐设置优惠价格，用于测试新功能

-- =====================================================
-- 1. 查看当前套餐数据
-- =====================================================

SELECT 
    id,
    package_name,
    package_type,
    price,
    discount_price,
    validity_days,
    is_enabled
FROM t_package 
WHERE deleted = 0
ORDER BY sort_order ASC, id ASC;

-- =====================================================
-- 2. 为套餐设置优惠价格
-- =====================================================

-- 基础套餐 - 设置优惠价格为8.88美元
UPDATE t_package 
SET discount_price = 8.88 
WHERE package_name = '基础套餐' AND deleted = 0;

-- 中级套餐 - 设置优惠价格为28.88美元
UPDATE t_package 
SET discount_price = 28.88 
WHERE package_name = '中级套餐' AND deleted = 0;

-- 企业套餐 - 不设置优惠价格（保持为NULL）
-- 这样可以测试有些套餐有优惠价格，有些没有的情况

-- =====================================================
-- 3. 验证设置结果
-- =====================================================

SELECT 
    id,
    package_name,
    package_type,
    price,
    discount_price,
    CASE 
        WHEN discount_price IS NOT NULL THEN CONCAT('有优惠: ', discount_price, ' USD')
        ELSE '无优惠'
    END as discount_status,
    validity_days,
    is_enabled
FROM t_package 
WHERE deleted = 0
ORDER BY sort_order ASC, id ASC;

-- =====================================================
-- 4. 计算优惠幅度
-- =====================================================

SELECT 
    id,
    package_name,
    price as original_price,
    discount_price,
    CASE 
        WHEN discount_price IS NOT NULL THEN 
            CONCAT(ROUND((price - discount_price) / price * 100, 1), '%')
        ELSE '无优惠'
    END as discount_percentage,
    CASE 
        WHEN discount_price IS NOT NULL THEN 
            CONCAT('节省 ', ROUND(price - discount_price, 2), ' USD')
        ELSE '无优惠'
    END as savings
FROM t_package 
WHERE deleted = 0 AND is_enabled = 1
ORDER BY sort_order ASC, id ASC;

-- =====================================================
-- 5. 测试查询 - 模拟前端请求
-- =====================================================

-- 查询所有启用的套餐（包含优惠价格信息）
SELECT 
    id,
    package_name as name,
    description,
    package_type,
    price,
    discount_price,
    'USD' as currency,
    validity_days as period,
    model_permissions as features,
    CASE 
        WHEN discount_price IS NOT NULL THEN 'true'
        ELSE 'false'
    END as has_discount
FROM t_package 
WHERE deleted = 0 AND is_enabled = 1
ORDER BY sort_order ASC, id ASC;

-- =====================================================
-- 6. 业务逻辑验证
-- =====================================================

-- 检查是否有优惠价格大于原价格的异常数据
SELECT 
    id,
    package_name,
    price,
    discount_price,
    '⚠️ 优惠价格不应大于原价格' as warning
FROM t_package 
WHERE deleted = 0 
    AND discount_price IS NOT NULL 
    AND discount_price >= price;

-- 如果上面的查询返回结果，说明有数据异常需要修正

-- =====================================================
-- 7. 回滚脚本（如果需要）
-- =====================================================

-- 如果需要清除所有优惠价格设置，可以执行以下语句：
-- UPDATE t_package SET discount_price = NULL WHERE deleted = 0;

-- =====================================================
-- 8. 功能测试建议
-- =====================================================

/*
测试场景：

1. 从未付费用户访问套餐列表
   - 应该看到有优惠价格的套餐显示discount_price字段
   - 没有优惠价格的套餐discount_price字段为null

2. 已付费用户访问套餐列表
   - 所有套餐的discount_price字段都应该为null

3. 未登录用户访问套餐列表
   - 应该看到有优惠价格的套餐显示discount_price字段
   - 没有优惠价格的套餐discount_price字段为null

测试API端点：
- GET /payment/packages?currency=USD
- POST /payment/package/info

测试用户：
- 从未付费用户：可以创建新用户测试
- 已付费用户：使用admin/admin123测试账号
- 未登录用户：不传递JWT token
*/
