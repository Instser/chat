package com.instser.chat.controller;

import com.instser.chat.common.result.Result;
import com.instser.chat.service.PaymentSecurityEnhancer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 测试控制器
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final PaymentSecurityEnhancer paymentSecurityEnhancer;

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "UP");
        data.put("timestamp", LocalDateTime.now());
        data.put("application", "jinyu-chat");
        data.put("version", "1.0.0");
        
        log.info("健康检查接口被调用");
        return Result.success(data, "系统运行正常");
    }

    /**
     * 测试接口
     */
    @GetMapping("/hello")
    public Result<String> hello() {
        log.info("测试接口被调用");
        return Result.success("Hello, Jinyu Chat Backend!", "测试成功");
    }

    /**
     * 测试支付安全增强器
     */
    @GetMapping("/payment-security")
    public Result<Map<String, Object>> testPaymentSecurity() {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("securityLevel", "HIGH");
            data.put("timestamp", LocalDateTime.now());
            data.put("status", "Payment security system is active");

            log.info("支付安全测试接口被调用");
            return Result.success(data, "支付安全系统正常");
        } catch (Exception e) {
            log.error("支付安全测试失败", e);
            return Result.failed("支付安全测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试支付安全验证
     */
    @GetMapping("/payment-security-detailed")
    public Result<Map<String, Object>> testPaymentSecurityDetailed(
            @RequestParam(defaultValue = "1") Long userId,
            HttpServletRequest request) {
        try {
            Map<String, Object> data = new HashMap<>();

            // 测试支付频率检查
            boolean frequencyCheck = paymentSecurityEnhancer.checkPaymentFrequency(userId);
            data.put("frequencyCheck", frequencyCheck);

            // 测试IP验证
            boolean ipCheck = paymentSecurityEnhancer.validateCallbackIP(request, "alipay");
            data.put("ipCheck", ipCheck);

            data.put("userId", userId);
            data.put("clientIP", getClientIP(request));
            data.put("timestamp", LocalDateTime.now());

            log.info("支付安全验证测试，用户ID: {}, 频率检查: {}, IP检查: {}", userId, frequencyCheck, ipCheck);
            return Result.success(data, "支付安全验证测试完成");
        } catch (Exception e) {
            log.error("支付安全验证测试失败", e);
            return Result.failed("支付安全验证测试失败: " + e.getMessage());
        }
    }

    /**
     * 获取客户端IP
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
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
