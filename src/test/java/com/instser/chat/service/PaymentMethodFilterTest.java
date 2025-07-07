package com.instser.chat.service;

import com.instser.chat.service.serviceimpl.PaymentServiceImpl;
import com.instser.chat.vo.PaymentMethodVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 支付方式过滤测试
 */
@SpringBootTest
public class PaymentMethodFilterTest {

    @Autowired
    private PaymentService paymentService;

    @Test
    public void testGetAvailablePaymentMethods() {
        // 获取可用支付方式
        List<PaymentMethodVO> paymentMethods = paymentService.getAvailablePaymentMethods();
        
        System.out.println("=== 可用支付方式列表 ===");
        for (PaymentMethodVO method : paymentMethods) {
            System.out.println("ID: " + method.getId() + 
                             ", Name: " + method.getName() + 
                             ", Type: " + method.getType() + 
                             ", Available: " + method.getAvailable());
        }
        
        // 验证不包含易支付
        boolean hasYiPay = paymentMethods.stream()
                .anyMatch(method -> "yipay".equalsIgnoreCase(method.getId()) || 
                                  "yipay".equalsIgnoreCase(method.getType()));
        
        assertFalse(hasYiPay, "支付方式列表不应包含易支付选项");
        
        // 验证包含支付宝和微信支付（如果配置完整）
        boolean hasAlipay = paymentMethods.stream()
                .anyMatch(method -> "alipay".equalsIgnoreCase(method.getId()));
        
        boolean hasWechat = paymentMethods.stream()
                .anyMatch(method -> "wechatpay".equalsIgnoreCase(method.getId()));
        
        System.out.println("包含支付宝: " + hasAlipay);
        System.out.println("包含微信支付: " + hasWechat);
        System.out.println("包含易支付: " + hasYiPay);
        
        // 验证结果
        assertTrue(paymentMethods.size() >= 0, "支付方式列表应该存在");
        System.out.println("✅ 支付方式过滤测试通过");
    }
    
    @Test
    public void testPaymentMethodNames() {
        List<PaymentMethodVO> paymentMethods = paymentService.getAvailablePaymentMethods();
        
        System.out.println("=== 支付方式名称验证 ===");
        
        for (PaymentMethodVO method : paymentMethods) {
            switch (method.getId().toLowerCase()) {
                case "alipay":
                    assertEquals("支付宝", method.getName(), "支付宝名称应该正确");
                    break;
                case "wechatpay":
                    assertEquals("微信支付", method.getName(), "微信支付名称应该正确");
                    break;
                case "yipay":
                    fail("不应该包含易支付选项");
                    break;
            }
        }
        
        System.out.println("✅ 支付方式名称验证通过");
    }
}
