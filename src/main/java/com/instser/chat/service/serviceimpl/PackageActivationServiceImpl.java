package com.instser.chat.service.serviceimpl;

import com.instser.chat.common.exception.BusinessException;
import com.instser.chat.entity.Package;
import com.instser.chat.entity.UserOrder;
import com.instser.chat.service.PackageActivationService;
import com.instser.chat.service.PackageService;
import com.instser.chat.service.OrderService;
import com.instser.chat.service.UserAccountService;
import com.instser.chat.vo.FreePackageRequestVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 套餐激活服务实现类
 *
 * @author instser
 * @since 2025-06-29
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PackageActivationServiceImpl implements PackageActivationService {

    private final PackageService packageService;
    private final OrderService orderService;
    private final UserAccountService userAccountService;

    @Override
    @Transactional
    public boolean activateFreePackage(FreePackageRequestVO request) {
        try {
            log.info("开始激活免费套餐，用户ID: {}, 套餐ID: {}", request.getUserId(), request.getPackageId());

            // 1. 验证套餐信息
            Package packageInfo = packageService.getPackageById(request.getPackageId());
            if (packageInfo == null) {
                throw new BusinessException("套餐不存在");
            }

            if (packageInfo.getIsEnabled() != 1) {
                throw new BusinessException("套餐已下架");
            }

            // 2. 验证是否为免费套餐
            if (!isFreePackage(packageInfo)) {
                throw new BusinessException("该套餐不是免费套餐，请使用支付方式购买");
            }

            // 3. 检查用户是否可以激活该套餐
            if (!canActivatePackage(request.getUserId(), request.getPackageId())) {
                throw new BusinessException("用户不能激活该套餐，可能已有激活的套餐或不满足激活条件");
            }

            // 4. 激活套餐
            boolean success = userAccountService.activatePackage(
                request.getUserId(),
                request.getPackageId(),
                packageInfo.getValidityDays(),
                packageInfo.getChatCount()
            );

            if (success) {
                log.info("免费套餐激活成功，用户ID: {}, 套餐ID: {}", request.getUserId(), request.getPackageId());
                return true;
            } else {
                log.error("免费套餐激活失败，用户ID: {}, 套餐ID: {}", request.getUserId(), request.getPackageId());
                throw new BusinessException("套餐激活失败");
            }

        } catch (BusinessException e) {
            log.error("免费套餐激活业务异常: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("免费套餐激活异常", e);
            throw new BusinessException("套餐激活失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public boolean activatePackageAfterPayment(String orderNo) {
        try {
            log.info("开始处理支付成功后的套餐激活，订单号: {}", orderNo);

            // 1. 查找订单
            UserOrder order = orderService.getOrderByOrderNo(orderNo);
            if (order == null) {
                throw new BusinessException("订单不存在: " + orderNo);
            }

            if (order.getPaymentStatus() != 1) {
                throw new BusinessException("订单状态异常，无法激活套餐，当前状态: " + order.getPaymentStatus());
            }

            // 2. 获取套餐信息
            Package packageInfo = packageService.getPackageById(order.getPackageId());
            if (packageInfo == null) {
                throw new BusinessException("套餐不存在: " + order.getPackageId());
            }

            // 3. 激活套餐
            boolean success = userAccountService.activatePackage(
                order.getUserId(),
                order.getPackageId(),
                packageInfo.getValidityDays(),
                packageInfo.getChatCount()
            );

            if (success) {
                log.info("支付后套餐激活成功，用户ID: {}, 订单号: {}, 套餐ID: {}", 
                        order.getUserId(), orderNo, order.getPackageId());
                return true;
            } else {
                log.error("支付后套餐激活失败，用户ID: {}, 订单号: {}", order.getUserId(), orderNo);
                throw new BusinessException("套餐激活失败");
            }

        } catch (BusinessException e) {
            log.error("支付后套餐激活业务异常: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("支付后套餐激活异常", e);
            throw new BusinessException("套餐激活失败: " + e.getMessage());
        }
    }

    @Override
    public boolean canActivatePackage(Long userId, Long packageId) {
        try {
            // 检查用户是否已有激活的套餐
            Package currentPackage = getCurrentActivePackage(userId);
            if (currentPackage != null) {
                log.debug("用户{}已有激活的套餐: {}", userId, currentPackage.getId());
                return false;
            }

            // 检查套餐是否存在且可用
            Package packageInfo = packageService.getPackageById(packageId);
            if (packageInfo == null || packageInfo.getIsEnabled() != 1) {
                log.debug("套餐{}不存在或已下架", packageId);
                return false;
            }

            return true;
        } catch (Exception e) {
            log.error("检查套餐激活权限异常", e);
            return false;
        }
    }

    @Override
    public Package getCurrentActivePackage(Long userId) {
        try {
            // 通过UserAccountService获取用户当前套餐
            // 这里需要根据实际的UserAccountService实现来调用
            // 暂时返回null，表示没有激活的套餐
            return null;
        } catch (Exception e) {
            log.error("获取用户当前套餐异常", e);
            return null;
        }
    }

    /**
     * 判断是否为免费套餐
     */
    private boolean isFreePackage(Package packageInfo) {
        // 检查价格是否为0
        BigDecimal price = packageInfo.getPrice();
        return (price == null || price.compareTo(BigDecimal.ZERO) == 0);
    }
}
