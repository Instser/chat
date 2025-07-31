-- 用户账户更新同步会员等级功能
-- 时间: 2025年07月30日 23:00
-- 版本: v1.0
-- 功能: 在更新user_account时自动同步membership_level字段

-- =====================================================
-- 功能说明
-- =====================================================
-- 1. 修改用户账户更新逻辑，在激活套餐、续费、升级时自动更新会员等级
-- 2. 根据套餐类型(package_type)自动映射对应的会员等级(membership_level)
-- 3. 确保数据一致性，避免套餐和会员等级不匹配的问题

-- =====================================================
-- 套餐类型与会员等级映射关系
-- =====================================================
-- package_type -> membership_level
-- basic        -> basic
-- medium       -> premium  
-- high         -> enterprise
-- premium      -> premium
-- enterprise   -> enterprise
-- vip          -> vip
-- 其他/null    -> free

-- =====================================================
-- 数据修复：同步现有数据的会员等级
-- =====================================================

-- 查看当前套餐类型分布
SELECT 
    p.package_type,
    COUNT(*) as user_count,
    GROUP_CONCAT(DISTINCT ua.membership_level) as current_membership_levels
FROM t_user_account ua
LEFT JOIN t_package p ON ua.package_id = p.id
WHERE ua.deleted = 0 AND p.deleted = 0
GROUP BY p.package_type
ORDER BY user_count DESC;

-- 修复会员等级数据
UPDATE t_user_account ua
LEFT JOIN t_package p ON ua.package_id = p.id
SET ua.membership_level = CASE 
    WHEN p.package_type = 'basic' THEN 'basic'
    WHEN p.package_type = 'medium' THEN 'premium'
    WHEN p.package_type = 'high' THEN 'enterprise'
    WHEN p.package_type = 'premium' THEN 'premium'
    WHEN p.package_type = 'enterprise' THEN 'enterprise'
    WHEN p.package_type = 'vip' THEN 'vip'
    ELSE 'free'
END,
ua.update_time = NOW()
WHERE ua.deleted = 0 
  AND (p.deleted = 0 OR p.id IS NULL)
  AND ua.membership_level != CASE 
    WHEN p.package_type = 'basic' THEN 'basic'
    WHEN p.package_type = 'medium' THEN 'premium'
    WHEN p.package_type = 'high' THEN 'enterprise'
    WHEN p.package_type = 'premium' THEN 'premium'
    WHEN p.package_type = 'enterprise' THEN 'enterprise'
    WHEN p.package_type = 'vip' THEN 'vip'
    ELSE 'free'
END;

-- 验证修复结果
SELECT 
    p.package_type,
    ua.membership_level,
    COUNT(*) as count
FROM t_user_account ua
LEFT JOIN t_package p ON ua.package_id = p.id
WHERE ua.deleted = 0
GROUP BY p.package_type, ua.membership_level
ORDER BY p.package_type, ua.membership_level;

-- =====================================================
-- 代码修改说明
-- =====================================================

/*
1. UserAccountServiceImpl.java 修改:
   - 添加 getMembershipLevelFromPackage() 方法
   - 在 activatePackage() 中同步更新会员等级
   - 在 renewPackage() 中同步更新会员等级
   - 在 upgradePackage() 中同步更新会员等级

2. UserAccountDao.java 修改:
   - insertOrUpdateAccount() 方法添加 membershipLevel 参数
   - updatePackageInfo() 方法添加 membershipLevel 参数

3. UserAccountMapper.xml 修改:
   - insertOrUpdateAccount SQL 更新 membership_level 字段
   - updatePackageInfo SQL 更新 membership_level 字段

4. 映射逻辑:
   private String getMembershipLevelFromPackage(Package packageInfo) {
       if (packageInfo == null || packageInfo.getPackageType() == null) {
           return "free";
       }
       
       switch (packageInfo.getPackageType().toLowerCase()) {
           case "basic": return "basic";
           case "medium": return "premium";
           case "high": return "enterprise";
           case "premium": return "premium";
           case "enterprise": return "enterprise";
           case "vip": return "vip";
           default: return "free";
       }
   }
*/

-- =====================================================
-- 测试验证
-- =====================================================

-- 测试1: 检查套餐激活后会员等级是否正确
-- 模拟激活basic套餐，检查membership_level是否为'basic'

-- 测试2: 检查套餐续费后会员等级是否保持一致
-- 模拟续费操作，检查membership_level是否正确更新

-- 测试3: 检查套餐升级后会员等级是否正确变更
-- 模拟从basic升级到premium，检查membership_level是否从'basic'变为'premium'

-- =====================================================
-- 监控查询
-- =====================================================

-- 查询套餐和会员等级不匹配的记录
SELECT 
    ua.user_id,
    ua.package_id,
    p.package_name,
    p.package_type,
    ua.membership_level,
    CASE 
        WHEN p.package_type = 'basic' THEN 'basic'
        WHEN p.package_type = 'medium' THEN 'premium'
        WHEN p.package_type = 'high' THEN 'enterprise'
        WHEN p.package_type = 'premium' THEN 'premium'
        WHEN p.package_type = 'enterprise' THEN 'enterprise'
        WHEN p.package_type = 'vip' THEN 'vip'
        ELSE 'free'
    END as expected_membership_level
FROM t_user_account ua
LEFT JOIN t_package p ON ua.package_id = p.id
WHERE ua.deleted = 0 
  AND (p.deleted = 0 OR p.id IS NULL)
  AND ua.membership_level != CASE 
    WHEN p.package_type = 'basic' THEN 'basic'
    WHEN p.package_type = 'medium' THEN 'premium'
    WHEN p.package_type = 'high' THEN 'enterprise'
    WHEN p.package_type = 'premium' THEN 'premium'
    WHEN p.package_type = 'enterprise' THEN 'enterprise'
    WHEN p.package_type = 'vip' THEN 'vip'
    ELSE 'free'
END;

-- 统计各会员等级用户数量
SELECT 
    membership_level,
    COUNT(*) as user_count,
    COUNT(CASE WHEN package_expire_time > NOW() THEN 1 END) as active_count,
    COUNT(CASE WHEN package_expire_time <= NOW() OR package_expire_time IS NULL THEN 1 END) as expired_count
FROM t_user_account 
WHERE deleted = 0
GROUP BY membership_level
ORDER BY user_count DESC;

-- =====================================================
-- 回滚方案（如需要）
-- =====================================================

-- 如果需要回滚，可以将所有membership_level重置为'free'
-- UPDATE t_user_account SET membership_level = 'free', update_time = NOW() WHERE deleted = 0;

-- =====================================================
-- 完成标记
-- =====================================================
-- 功能实现完成时间: 2025年07月30日 23:00
-- 影响范围: 用户账户管理、套餐激活、续费、升级功能
-- 测试状态: 待测试
-- 部署状态: 待部署
