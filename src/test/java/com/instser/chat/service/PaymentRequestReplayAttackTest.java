package com.instser.chat.service;

import com.instser.chat.common.exception.PaymentException;
import com.instser.chat.service.serviceimpl.PaymentServiceImpl;
import com.instser.chat.vo.PaymentRequestVO;
import com.instser.chat.vo.PaymentResponseVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 支付请求防重放攻击测试
 *
 * @author instser
 * @since 2025-06-30
 */
@SpringBootTest
@TestPropertySource(properties = {
    "payment.security.frequency.max-count=5",
    "payment.security.frequency.time-window=10"
})
class PaymentRequestReplayAttackTest {

    @Autowired
    private PaymentServiceImpl paymentService;

    @Autowired
    private RedisPaymentSecurityService redisPaymentSecurityService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void testNormalPaymentRequest() {
        // 创建测试支付请求
        PaymentRequestVO request = createTestPaymentRequest();
        
        try {
            // 第一次请求应该成功
            PaymentResponseVO response = paymentService.createPayment(request);
            assertNotNull(response, "支付响应不应该为空");
            
            // 验证Redis中已记录请求ID
            String requestId = request.getRequestId();
            if (requestId != null) {
                Boolean exists = redisTemplate.hasKey("payment:request:" + requestId);
                assertTrue(exists, "Redis中应该存在支付请求记录");
            }
            
        } catch (Exception e) {
            // 清理测试数据
            cleanupTestData(request);
            throw e;
        }
        
        // 清理测试数据
        cleanupTestData(request);
    }

    @Test
    void testPaymentRequestReplayAttack() {
        // 创建测试支付请求
        PaymentRequestVO request = createTestPaymentRequest();
        
        try {
            // 第一次请求成功
            PaymentResponseVO response1 = paymentService.createPayment(request);
            assertNotNull(response1, "第一次支付应该成功");
            
            // 第二次相同请求应该被拒绝
            PaymentException exception = assertThrows(PaymentException.class, () -> {
                paymentService.createPayment(request);
            });
            
            assertTrue(exception.getMessage().contains("重复的支付请求"), 
                "应该检测到重复的支付请求");
            
        } catch (Exception e) {
            // 清理测试数据
            cleanupTestData(request);
            throw e;
        }
        
        // 清理测试数据
        cleanupTestData(request);
    }

    @Test
    void testRequestIdGeneration() {
        // 测试请求ID生成功能
        PaymentRequestVO request1 = createTestPaymentRequest();
        PaymentRequestVO request2 = createTestPaymentRequest();
        
        // 相同参数应该生成相同的请求ID
        String requestId1 = redisPaymentSecurityService.generatePaymentRequestId(
            request1.getUserId(), "TestPackage", "99.00", request1.getPaymentMethod());
        String requestId2 = redisPaymentSecurityService.generatePaymentRequestId(
            request2.getUserId(), "TestPackage", "99.00", request2.getPaymentMethod());
        
        assertEquals(requestId1, requestId2, "相同参数应该生成相同的请求ID");
        
        // 不同参数应该生成不同的请求ID
        String requestId3 = redisPaymentSecurityService.generatePaymentRequestId(
            request1.getUserId(), "TestPackage", "199.00", request1.getPaymentMethod());
        
        assertNotEquals(requestId1, requestId3, "不同参数应该生成不同的请求ID");
    }

    @Test
    void testRequestIdExpiration() {
        // 测试请求ID过期功能
        String testRequestId = "test_request_" + System.currentTimeMillis();
        Long testUserId = 99999L;
        
        // 第一次检查应该返回false（未处理过）
        boolean firstCheck = redisPaymentSecurityService.isPaymentRequestProcessed(testRequestId, testUserId);
        assertFalse(firstCheck, "第一次检查应该返回false");
        
        // 第二次检查应该返回true（已处理过）
        boolean secondCheck = redisPaymentSecurityService.isPaymentRequestProcessed(testRequestId, testUserId);
        assertTrue(secondCheck, "第二次检查应该返回true");
        
        // 手动删除Redis键模拟过期
        redisTemplate.delete("payment:request:" + testRequestId);
        
        // 过期后应该可以重新处理
        boolean thirdCheck = redisPaymentSecurityService.isPaymentRequestProcessed(testRequestId, testUserId);
        assertFalse(thirdCheck, "过期后应该可以重新处理");
        
        // 清理测试数据
        redisTemplate.delete("payment:request:" + testRequestId);
        redisTemplate.delete("payment:suspicious:" + testUserId);
    }

    /**
     * 创建测试支付请求
     */
    private PaymentRequestVO createTestPaymentRequest() {
        PaymentRequestVO request = new PaymentRequestVO();
        request.setUserId(12345L);
        request.setPackageId(1L);
        request.setCurrency("CNY");
        request.setPaymentMethod("alipay");
        request.setReturnUrl("http://localhost:3000/payment/success");
        return request;
    }

    /**
     * 清理测试数据
     */
    private void cleanupTestData(PaymentRequestVO request) {
        if (request == null) return;
        
        Long userId = request.getUserId();
        String requestId = request.getRequestId();
        
        // 清理支付请求记录
        if (requestId != null) {
            redisTemplate.delete("payment:request:" + requestId);
        }
        
        // 清理支付频率记录
        if (userId != null) {
            String timeWindow = String.valueOf(System.currentTimeMillis() / (10 * 60 * 1000));
            redisTemplate.delete("payment:freq:" + userId + ":" + timeWindow);
            
            // 清理可疑行为记录
            redisTemplate.delete("payment:suspicious:" + userId);
            
            // 清理支付会话记录
            redisTemplate.delete("payment:session:" + userId);
        }
    }
}
