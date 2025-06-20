package com.instser.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.instser.chat.entity.Package;

import java.util.List;

/**
 * 套餐服务接口
 *
 * @author instser
 * @since 2024-01-01
 */
public interface PackageService extends IService<Package> {

    /**
     * 获取所有启用的套餐
     *
     * @return 套餐列表
     */
    List<Package> getEnabledPackages();

    /**
     * 根据套餐类型获取套餐
     *
     * @param packageType 套餐类型
     * @return 套餐列表
     */
    List<Package> getPackagesByType(String packageType);

    /**
     * 根据ID获取套餐
     *
     * @param packageId 套餐ID
     * @return 套餐信息
     */
    Package getPackageById(Long packageId);

    /**
     * 初始化默认套餐数据
     */
    void initDefaultPackages();

    /**
     * 根据前端套餐ID获取套餐（支持1-6的映射）
     *
     * @param frontendPackageId 前端套餐ID (1-6)
     * @return 套餐信息
     */
    Package getPackageByFrontendId(Integer frontendPackageId);
}
