package com.instser.chat.service.serviceimpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.instser.chat.dao.PackageDao;
import com.instser.chat.entity.Package;
import com.instser.chat.service.PackageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 套餐服务实现类
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@Service
public class PackageServiceImpl extends ServiceImpl<PackageDao, Package> implements PackageService {

    @Override
    public List<Package> getEnabledPackages() {
        return baseMapper.selectEnabledPackages();
    }

    @Override
    public List<Package> getPackagesByType(String packageType) {
        return baseMapper.selectByPackageType(packageType);
    }

    @Override
    public Package getPackageById(Long packageId) {
        return baseMapper.selectById(packageId);
    }

    @Override
    public Package getPackageByFrontendId(Integer frontendPackageId) {
        if (frontendPackageId == null) {
            log.warn("前端套餐ID不能为空");
            return null;
        }

        // 直接使用前端传递的ID查询数据库
        return baseMapper.selectById(frontendPackageId.longValue());
    }

    @Override
    public void initDefaultPackages() {
        // 检查是否已有数据
        if (this.count() > 0) {
            log.info("套餐数据已存在，跳过初始化");
            return;
        }

        log.info("开始初始化默认套餐数据");

        // 基础套餐
        Package basicPackage = new Package();
        basicPackage.setPackageName("基础套餐");
        basicPackage.setDescription("适合个人用户的基础AI对话套餐");
        basicPackage.setPackageType("basic");
        basicPackage.setPrice(new BigDecimal("69.99"));
        basicPackage.setValidityDays(30);
        basicPackage.setLimitType("total"); // 总次数限制模式
        // 使用新的模型权限配置格式
        basicPackage.setModelPermissions("{\"STANDARD\":{\"daily_limit\":33,\"description\":\"标准模型\"},\"PERFORMANCE\":{\"daily_limit\":10,\"description\":\"性能模型\"},\"BETA_MODEL\":{\"daily_limit\":5,\"description\":\"测试模型\"}}");
        basicPackage.setIsEnabled(1);
        basicPackage.setSortOrder(1);
        this.save(basicPackage);

        // 高级套餐
        Package premiumPackage = new Package();
        premiumPackage.setPackageName("高级套餐");
        premiumPackage.setDescription("适合专业用户的高级AI对话套餐");
        premiumPackage.setPackageType("premium");
        premiumPackage.setPrice(new BigDecimal("139.99"));
        premiumPackage.setValidityDays(30);
        premiumPackage.setLimitType("total"); // 总次数限制模式
        // 使用新的模型权限配置格式
        premiumPackage.setModelPermissions("{\"STANDARD\":{\"daily_limit\":167,\"description\":\"标准模型\"},\"PERFORMANCE\":{\"daily_limit\":50,\"description\":\"性能模型\"},\"BETA_MODEL\":{\"daily_limit\":20,\"description\":\"测试模型\"}}");
        premiumPackage.setIsEnabled(1);
        premiumPackage.setSortOrder(2);
        this.save(premiumPackage);

        // 企业套餐
        Package enterprisePackage = new Package();
        enterprisePackage.setPackageName("企业套餐");
        enterprisePackage.setDescription("适合企业用户的无限制AI对话套餐");
        enterprisePackage.setPackageType("enterprise");
        enterprisePackage.setPrice(new BigDecimal("699.99"));
        enterprisePackage.setValidityDays(30);
        enterprisePackage.setLimitType("unlimited"); // 无限制模式
        // 使用新的模型权限配置格式，-1表示无限制
        enterprisePackage.setModelPermissions("{\"STANDARD\":{\"daily_limit\":-1,\"description\":\"标准模型\"},\"PERFORMANCE\":{\"daily_limit\":-1,\"description\":\"性能模型\"},\"BETA_MODEL\":{\"daily_limit\":-1,\"description\":\"测试模型\"}}");
        enterprisePackage.setIsEnabled(1);
        enterprisePackage.setSortOrder(3);
        this.save(enterprisePackage);

        log.info("默认套餐数据初始化完成");
    }
}
