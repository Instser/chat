package com.instser.chat.service.serviceimpl;

import com.instser.chat.entity.UserAccount;
import com.instser.chat.dao.UserOrderDao;
import com.instser.chat.service.UserAccountService;
import com.instser.chat.service.UserPaymentStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户支付状态服务实现
 * 高效判定用户充值状态
 *
 * @author instser
 * @since 2025-07-14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPaymentStatusServiceImpl implements UserPaymentStatusService {

    private final UserOrderDao userOrderDao;
    private final UserAccountService userAccountService;

    @Override
    public boolean hasNeverPaid(Long userId) {
        if (userId == null) {
            return true;
        }

        try {
            // 方案1：直接查询是否存在支付成功的订单（最高效）
            // 使用索引：idx_user_order_status_time (user_id, payment_status, create_time)
            Integer paidOrderCount = userOrderDao.countPaidOrdersByUserId(userId);
            
            boolean neverPaid = (paidOrderCount == null || paidOrderCount == 0);
            
            log.debug("用户{}充值状态检查：已支付订单数={}, 从未充值={}", 
                    userId, paidOrderCount, neverPaid);
            
            return neverPaid;
            
        } catch (Exception e) {
            log.error("检查用户{}充值状态异常", userId, e);
            // 异常情况下保守返回false，避免误判
            return false;
        }
    }

    @Override
    public boolean isOnlyFreeUser(Long userId) {
        if (userId == null) {
            return true;
        }

        try {
            // 检查用户是否从未充值
            if (hasNeverPaid(userId)) {
                return true;
            }

            // 检查用户账户中是否只绑定过免费套餐(ID=1)
            UserAccount account = userAccountService.getUserAccount(userId);
            if (account == null) {
                return true;
            }

            // 如果当前套餐不是免费套餐，说明使用过付费套餐
            Long currentPackageId = account.getPackageId();
            if (currentPackageId != null && !currentPackageId.equals(1L)) {
                log.debug("用户{}当前使用付费套餐，套餐ID={}", userId, currentPackageId);
                return false;
            }

            // 检查历史是否购买过非免费套餐
            Integer paidNonFreeOrderCount = userOrderDao.countPaidNonFreeOrdersByUserId(userId);
            boolean onlyFree = (paidNonFreeOrderCount == null || paidNonFreeOrderCount == 0);
            
            log.debug("用户{}套餐使用状态：付费套餐订单数={}, 仅使用免费套餐={}", 
                    userId, paidNonFreeOrderCount, onlyFree);
            
            return onlyFree;
            
        } catch (Exception e) {
            log.error("检查用户{}套餐使用状态异常", userId, e);
            return false;
        }
    }

    @Override
    public LocalDateTime getFirstPaymentTime(Long userId) {
        if (userId == null) {
            return null;
        }

        try {
            return userOrderDao.getFirstPaymentTimeByUserId(userId);
        } catch (Exception e) {
            log.error("获取用户{}首次充值时间异常", userId, e);
            return null;
        }
    }

    @Override
    public BigDecimal getTotalPaidAmount(Long userId) {
        if (userId == null) {
            return BigDecimal.ZERO;
        }

        try {
            BigDecimal totalAmount = userOrderDao.getTotalPaidAmountByUserId(userId);
            return totalAmount != null ? totalAmount : BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("获取用户{}总充值金额异常", userId, e);
            return BigDecimal.ZERO;
        }
    }

    @Override
    public boolean isActivePaidUser(Long userId) {
        if (userId == null) {
            return false;
        }

        try {
            // 检查用户是否从未充值
            if (hasNeverPaid(userId)) {
                return false;
            }

            // 检查用户是否有有效的付费套餐
            UserAccount account = userAccountService.getUserAccount(userId);
            if (account == null || !account.hasValidPackage()) {
                return false;
            }

            // 检查当前套餐是否为付费套餐（非ID=1的免费套餐）
            Long currentPackageId = account.getPackageId();
            boolean isActivePaid = (currentPackageId != null && !currentPackageId.equals(1L));
            
            log.debug("用户{}活跃付费状态：当前套餐ID={}, 是否活跃付费用户={}", 
                    userId, currentPackageId, isActivePaid);
            
            return isActivePaid;
            
        } catch (Exception e) {
            log.error("检查用户{}活跃付费状态异常", userId, e);
            return false;
        }
    }
}
