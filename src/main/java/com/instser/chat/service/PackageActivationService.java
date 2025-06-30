package com.instser.chat.service;

import com.instser.chat.entity.Package;
import com.instser.chat.vo.FreePackageRequestVO;

/**
 * 套餐激活服务接口
 *
 * @author instser
 * @since 2025-06-29
 */
public interface PackageActivationService {

    /**
     * 激活免费套餐
     *
     * @param request 免费套餐激活请求
     * @return 是否激活成功
     */
    boolean activateFreePackage(FreePackageRequestVO request);

    /**
     * 支付成功后激活套餐
     *
     * @param orderNo 订单号
     * @return 是否激活成功
     */
    boolean activatePackageAfterPayment(String orderNo);

    /**
     * 检查用户是否可以激活指定套餐
     *
     * @param userId 用户ID
     * @param packageId 套餐ID
     * @return 是否可以激活
     */
    boolean canActivatePackage(Long userId, Long packageId);

    /**
     * 获取用户当前激活的套餐
     *
     * @param userId 用户ID
     * @return 当前激活的套餐，如果没有则返回null
     */
    Package getCurrentActivePackage(Long userId);
}
