package com.instser.chat.service.serviceimpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.instser.chat.common.exception.BusinessException;
import com.instser.chat.dao.UserOrderDao;
import com.instser.chat.entity.Package;
import com.instser.chat.entity.UserOrder;
import com.instser.chat.service.OrderService;
import com.instser.chat.service.PackageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单服务实现类
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<UserOrderDao, UserOrder> implements OrderService {

    private final PackageService packageService;

    @Override
    public UserOrder createOrder(Long userId, Long packageId, String currency, String paymentMethod) {
        // 获取套餐信息
        Package packageInfo = packageService.getPackageById(packageId);
        if (packageInfo == null) {
            throw new BusinessException("套餐不存在");
        }

        if (packageInfo.getIsEnabled() != 1) {
            throw new BusinessException("套餐已下架");
        }

        // 根据货币类型获取价格
        BigDecimal amount;
        if ("USD".equalsIgnoreCase(currency)) {
            amount = packageInfo.getPriceUsd();
        } else if ("CNY".equalsIgnoreCase(currency)) {
            amount = packageInfo.getPriceCny();
        } else {
            throw new BusinessException("不支持的货币类型: " + currency);
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("套餐价格配置错误");
        }

        // 创建订单
        UserOrder order = new UserOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setPackageId(packageId);
        order.setAmount(amount);
        order.setCurrency(currency.toUpperCase());
        order.setPaymentMethod(paymentMethod);
        order.setPaymentStatus(0); // 未支付
        order.setPaymentRequestId(generatePaymentRequestId());
        order.setExpireTime(LocalDateTime.now().plusHours(2)); // 2小时后过期
        order.setRemark("套餐购买: " + packageInfo.getPackageName());

        // 保存订单
        if (!this.save(order)) {
            throw new BusinessException("订单创建失败");
        }

        log.info("创建订单成功，订单号: {}, 用户ID: {}, 套餐ID: {}, 金额: {} {}", 
                order.getOrderNo(), userId, packageId, amount, currency);

        return order;
    }

    @Override
    public UserOrder getOrderByOrderNo(String orderNo) {
        return baseMapper.selectByOrderNo(orderNo);
    }

    @Override
    public UserOrder getOrderByPaymentRequestId(String paymentRequestId) {
        return baseMapper.selectByPaymentRequestId(paymentRequestId);
    }

    @Override
    public List<UserOrder> getOrdersByUserId(Long userId) {
        return baseMapper.selectByUserId(userId);
    }

    @Override
    public boolean updatePaymentStatus(String orderNo, Integer paymentStatus, String transactionId) {
        int result = baseMapper.updatePaymentStatus(orderNo, paymentStatus, transactionId);
        if (result > 0) {
            log.info("更新订单支付状态成功，订单号: {}, 状态: {}, 交易号: {}", orderNo, paymentStatus, transactionId);
            return true;
        }
        return false;
    }

    @Override
    public boolean cancelOrder(String orderNo) {
        UserOrder order = getOrderByOrderNo(orderNo);
        if (order == null) {
            return false;
        }

        if (order.getPaymentStatus() == 1) {
            throw new BusinessException("已支付的订单不能取消");
        }

        order.setPaymentStatus(2); // 已取消
        return this.updateById(order);
    }

    @Override
    public boolean isOrderExpired(String orderNo) {
        UserOrder order = getOrderByOrderNo(orderNo);
        if (order == null) {
            return true;
        }

        return order.getExpireTime() != null && LocalDateTime.now().isAfter(order.getExpireTime());
    }

    @Override
    public String generateOrderNo() {
        // 格式: JY + 年月日 + 时分秒 + 4位随机数
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "JY" + timestamp + random;
    }

    /**
     * 生成支付请求ID
     */
    private String generatePaymentRequestId() {
        // 格式: PAY + 年月日时分秒毫秒 + 3位随机数
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        int random = ThreadLocalRandom.current().nextInt(100, 999);
        return "PAY" + timestamp + random;
    }
}
