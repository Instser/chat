package com.instser.chat.service;

/**
 * 用户支付状态服务接口
 * 用于高效判定用户充值状态
 *
 * @author instser
 * @since 2025-07-14
 */
public interface UserPaymentStatusService {

    /**
     * 判定用户是否从未充值过套餐
     * 高效方案：基于订单表的支付状态判定
     *
     * @param userId 用户ID
     * @return true-从未充值，false-已充值过
     */
    boolean hasNeverPaid(Long userId);

    /**
     * 判定用户是否只使用免费套餐
     * 检查用户是否只绑定过ID=1的默认套餐
     *
     * @param userId 用户ID
     * @return true-只使用免费套餐，false-使用过付费套餐
     */
    boolean isOnlyFreeUser(Long userId);

    /**
     * 获取用户首次充值时间
     *
     * @param userId 用户ID
     * @return 首次充值时间，如果从未充值返回null
     */
    java.time.LocalDateTime getFirstPaymentTime(Long userId);

    /**
     * 获取用户总充值金额
     *
     * @param userId 用户ID
     * @return 总充值金额
     */
    java.math.BigDecimal getTotalPaidAmount(Long userId);

    /**
     * 判定用户是否为活跃付费用户
     * 检查用户是否有有效的付费套餐
     *
     * @param userId 用户ID
     * @return true-活跃付费用户，false-非活跃付费用户
     */
    boolean isActivePaidUser(Long userId);
}
