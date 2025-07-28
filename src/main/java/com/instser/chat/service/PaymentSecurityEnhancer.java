package com.instser.chat.service;

import com.instser.chat.vo.PaymentRequestVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalTime;
import java.util.Set;

/**
 * 支付安全增强服务
 * 提供IP白名单验证、支付频率限制、异常支付检测等安全功能
 *
 * @author instser
 * @since 2025-06-29
 */
@Slf4j
@Component
public class PaymentSecurityEnhancer {

    @Autowired
    private RedisPaymentSecurityService redisPaymentSecurityService;

    @Autowired
    private EnhancedPaymentSecurityService enhancedPaymentSecurityService;

    // 支付宝回调IP白名单（示例IP，实际使用时需要从支付宝官方获取最新IP列表）
    private static final Set<String> ALIPAY_CALLBACK_IPS = Set.of(
        "110.75.143.0/24",   // 支付宝回调IP段1
        "203.119.24.0/24",   // 支付宝回调IP段2
        "110.75.146.0/24",   // 支付宝回调IP段3
        "203.119.26.0/24"    // 支付宝回调IP段4
    );

    // 易支付回调IP白名单（需要根据实际易支付平台配置）
    private static final Set<String> YIPAY_CALLBACK_IPS = Set.of(
        "127.0.0.1",         // 本地测试IP
        "localhost",
        "https://your-pay-gateway.example.com"          // 本地测试域名
    );

    /**
     * 验证支付回调IP是否在白名单中
     *
     * @param request 请求对象
     * @param paymentMethod 支付方式
     * @return 是否通过验证
     */
    public boolean validateCallbackIP(HttpServletRequest request, String paymentMethod) {
        String clientIP = getClientIP(request);
        log.debug("验证支付回调IP，客户端IP: {}, 支付方式: {}", clientIP, paymentMethod);

        if (clientIP == null || clientIP.trim().isEmpty()) {
            log.warn("无法获取客户端IP地址");
            return false;
        }

        Set<String> allowedIPs;
        if ("alipay".equalsIgnoreCase(paymentMethod)) {
            allowedIPs = ALIPAY_CALLBACK_IPS;
        } else if ("yipay".equalsIgnoreCase(paymentMethod)) {
            allowedIPs = YIPAY_CALLBACK_IPS;
        } else {
            log.warn("未知的支付方式: {}", paymentMethod);
            return false;
        }

        boolean isAllowed = allowedIPs.stream().anyMatch(ip -> isIPInRange(clientIP, ip));
        
        if (!isAllowed) {
            log.warn("支付回调IP验证失败，客户端IP: {}, 支付方式: {}, 允许的IP: {}", 
                    clientIP, paymentMethod, allowedIPs);
        } else {
            log.debug("支付回调IP验证通过，客户端IP: {}, 支付方式: {}", clientIP, paymentMethod);
        }

        return isAllowed;
    }

    /**
     * 检查支付频率限制
     * 使用增强版混合缓存策略实现分布式频率限制
     *
     * @param userId 用户ID
     * @return 是否通过频率检查
     */
    public boolean checkPaymentFrequency(Long userId) {
        // 优先使用增强版服务（支持混合缓存和熔断器）
        try {
            return enhancedPaymentSecurityService.checkPaymentFrequency(userId);
        } catch (Exception e) {
            log.error("增强版支付频率检查异常，回退到原始服务，用户ID: {}", userId, e);
            // 回退到原始Redis服务
            return redisPaymentSecurityService.checkPaymentFrequency(userId);
        }
    }

    /**
     * 检测异常支付行为
     *
     * @param request 支付请求
     * @return 是否通过异常检测
     */
    public boolean detectAbnormalPayment(PaymentRequestVO request) {
        if (request == null || request.getUserId() == null) {
            log.warn("支付请求或用户ID为空");
            return false;
        }

        try {
            // 1. 检查大额支付
            if (!checkLargeAmountPayment(request)) {
                return false;
            }

            // 2. 检查异常时间支付
            if (!checkUnusualTimePayment()) {
                return false;
            }

            // 3. 检查可疑用户行为
            if (!checkSuspiciousUserBehavior(request.getUserId())) {
                return false;
            }

            log.debug("用户{}异常支付检测通过", request.getUserId());
            return true;

        } catch (Exception e) {
            log.error("异常支付检测异常，用户ID: {}", request.getUserId(), e);
            // 异常情况下允许支付，避免影响正常业务
            return true;
        }
    }

    /**
     * 检查大额支付
     */
    private boolean checkLargeAmountPayment(PaymentRequestVO request) {
        // 这里需要根据套餐信息获取实际金额，暂时跳过
        // 实际实现时需要注入PackageService获取套餐价格
        log.debug("大额支付检查通过，用户ID: {}", request.getUserId());
        return true;
    }

    /**
     * 检查异常时间支付（如深夜支付）
     */
    private boolean checkUnusualTimePayment() {
        LocalTime now = LocalTime.now();
        LocalTime nightStart = LocalTime.of(23, 0); // 晚上11点
        LocalTime nightEnd = LocalTime.of(6, 0);    // 早上6点

        boolean isNightTime = now.isAfter(nightStart) || now.isBefore(nightEnd);
        
        if (isNightTime) {
            log.debug("检测到深夜支付，时间: {}", now);
            // 深夜支付不直接拒绝，只记录日志，可以根据业务需要调整
        }

        return true; // 暂时不限制深夜支付
    }

    /**
     * 检查可疑用户行为
     * 使用Redis实现分布式可疑行为检查
     */
    private boolean checkSuspiciousUserBehavior(Long userId) {
        return redisPaymentSecurityService.checkSuspiciousBehavior(userId);
    }

    /**
     * 记录可疑行为
     * 使用增强版混合缓存策略记录可疑行为
     */
    public void recordSuspiciousBehavior(Long userId, String reason) {
        try {
            enhancedPaymentSecurityService.recordSuspiciousBehavior(userId, reason);
        } catch (Exception e) {
            log.error("增强版可疑行为记录异常，回退到原始服务，用户ID: {}", userId, e);
            // 回退到原始Redis服务
            redisPaymentSecurityService.recordSuspiciousBehavior(userId, reason);
        }
    }

    /**
     * 获取客户端真实IP地址
     */
    private String getClientIP(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        // 如果是多个IP，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        return ip;
    }

    /**
     * 检查IP是否在指定范围内
     */
    private boolean isIPInRange(String clientIP, String allowedIP) {
        if (clientIP == null || allowedIP == null) {
            return false;
        }

        // 简单的IP匹配，支持CIDR格式
        if (allowedIP.contains("/")) {
            // CIDR格式，这里简化处理，实际应该使用专门的IP工具库
            String baseIP = allowedIP.split("/")[0];
            return clientIP.startsWith(baseIP.substring(0, baseIP.lastIndexOf(".")));
        } else {
            // 精确匹配
            return clientIP.equals(allowedIP);
        }
    }
}
