package com.instser.chat.integration;

import com.instser.chat.entity.PaymentAudit;
import com.instser.chat.service.PaymentAuditService;
import com.instser.chat.service.serviceimpl.PaymentAuditServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 支付审计日志端到端测试
 * 模拟完整的支付流程并验证审计日志记录
 *
 * @author instser
 * @since 2025-06-30
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Rollback
class PaymentAuditEndToEndTest {

    @Autowired
    private PaymentAuditService paymentAuditService;

    @Test
    void testCompletePaymentFlowAuditTrail() {
        // 模拟完整的支付流程审计日志记录
        String orderNo = "ORDER_E2E_" + System.currentTimeMillis();
        Long userId = 12345L;
        String clientIp = "192.168.1.100";
        String userAgent = "Mozilla/5.0 Test Browser";

        // 1. 用户创建支付订单
        Map<String, Object> createDetails = new HashMap<>();
        createDetails.put("packageId", 1L);
        createDetails.put("amount", "10.00");
        createDetails.put("currency", "USD");
        createDetails.put("paymentMethod", "alipay");
        
        String createDetailsJson = ((PaymentAuditServiceImpl) paymentAuditService).createOperationDetails(createDetails);
        paymentAuditService.recordCreateAudit(orderNo, userId, createDetailsJson, clientIp, userAgent);

        // 2. 支付宝回调 - 支付成功
        Map<String, Object> payDetails = new HashMap<>();
        payDetails.put("transactionId", "TXN_" + System.currentTimeMillis());
        payDetails.put("paymentMethod", "alipay");
        payDetails.put("callbackTime", LocalDateTime.now().toString());
        
        String payDetailsJson = ((PaymentAuditServiceImpl) paymentAuditService).createOperationDetails(payDetails);
        paymentAuditService.recordStatusChangeAudit(orderNo, 0, 1, "支付宝回调确认", payDetailsJson);

        // 3. 验证审计日志完整性
        List<PaymentAudit> auditTrail = paymentAuditService.getAuditByOrderNo(orderNo);
        
        assertEquals(2, auditTrail.size(), "应该有2条审计记录");

        // 验证创建记录
        PaymentAudit createAudit = auditTrail.stream()
                .filter(audit -> "CREATE".equals(audit.getOperationType()))
                .findFirst()
                .orElse(null);
        
        assertNotNull(createAudit, "应该有创建审计记录");
        assertEquals(orderNo, createAudit.getOrderNo());
        assertEquals("CREATE", createAudit.getOperationType());
        assertEquals(userId, createAudit.getOperatorId());
        assertEquals("USER", createAudit.getOperatorType());
        assertEquals(clientIp, createAudit.getClientIp());
        assertEquals(userAgent, createAudit.getUserAgent());
        assertTrue(createAudit.getOperationDetails().contains("packageId"));
        assertTrue(createAudit.getOperationDetails().contains("alipay"));

        // 验证支付记录
        PaymentAudit payAudit = auditTrail.stream()
                .filter(audit -> "PAY".equals(audit.getOperationType()))
                .findFirst()
                .orElse(null);
        
        assertNotNull(payAudit, "应该有支付审计记录");
        assertEquals(orderNo, payAudit.getOrderNo());
        assertEquals("PAY", payAudit.getOperationType());
        assertEquals(Integer.valueOf(0), payAudit.getOldStatus());
        assertEquals(Integer.valueOf(1), payAudit.getNewStatus());
        assertNull(payAudit.getOperatorId());
        assertEquals("CALLBACK", payAudit.getOperatorType());
        assertTrue(payAudit.getOperationDetails().contains("transactionId"));
        assertTrue(payAudit.getOperationDetails().contains("TXN_"));

        // 验证时间顺序
        assertTrue(createAudit.getCreateTime().isBefore(payAudit.getCreateTime()) ||
                   createAudit.getCreateTime().isEqual(payAudit.getCreateTime()),
                   "创建记录应该在支付记录之前或同时");
    }

    @Test
    void testPaymentCancellationAuditTrail() {
        // 模拟支付取消流程
        String orderNo = "ORDER_CANCEL_" + System.currentTimeMillis();
        Long userId = 54321L;

        // 1. 创建订单
        paymentAuditService.recordCreateAudit(orderNo, userId, "{\"test\":\"cancel\"}", null, null);

        // 2. 订单过期自动取消
        Map<String, Object> cancelDetails = new HashMap<>();
        cancelDetails.put("reason", "订单过期自动取消");
        cancelDetails.put("expireTime", LocalDateTime.now().minusMinutes(30).toString());
        cancelDetails.put("cancelTime", LocalDateTime.now().toString());
        
        String cancelDetailsJson = ((PaymentAuditServiceImpl) paymentAuditService).createOperationDetails(cancelDetails);
        paymentAuditService.recordStatusChangeAudit(orderNo, 0, 2, "订单过期自动取消", cancelDetailsJson);

        // 3. 验证审计日志
        List<PaymentAudit> auditTrail = paymentAuditService.getAuditByOrderNo(orderNo);
        
        assertEquals(2, auditTrail.size(), "应该有2条审计记录");

        PaymentAudit cancelAudit = auditTrail.stream()
                .filter(audit -> "CANCEL".equals(audit.getOperationType()))
                .findFirst()
                .orElse(null);
        
        assertNotNull(cancelAudit, "应该有取消审计记录");
        assertEquals("CANCEL", cancelAudit.getOperationType());
        assertEquals("SYSTEM", cancelAudit.getOperatorType());
        assertEquals(Integer.valueOf(0), cancelAudit.getOldStatus());
        assertEquals(Integer.valueOf(2), cancelAudit.getNewStatus());
        assertTrue(cancelAudit.getOperationDetails().contains("过期"));
    }

    @Test
    void testMultipleOperationTypesQuery() {
        // 创建多种类型的审计记录
        String baseOrderNo = "ORDER_MULTI_" + System.currentTimeMillis();
        Long userId = 99999L;
        
        LocalDateTime startTime = LocalDateTime.now().minusMinutes(1);

        // 创建不同类型的操作
        paymentAuditService.recordCreateAudit(baseOrderNo + "_1", userId, "{\"type\":\"create\"}", null, null);
        paymentAuditService.recordStatusChangeAudit(baseOrderNo + "_2", 0, 1, "支付成功", "{\"type\":\"pay\"}");
        paymentAuditService.recordStatusChangeAudit(baseOrderNo + "_3", 0, 2, "支付取消", "{\"type\":\"cancel\"}");
        paymentAuditService.recordStatusChangeAudit(baseOrderNo + "_4", 1, 3, "支付退款", "{\"type\":\"refund\"}");
        
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(1);

        // 验证按操作类型查询
        List<PaymentAudit> createAudits = paymentAuditService.getAuditByOperationType("CREATE", startTime, endTime);
        List<PaymentAudit> payAudits = paymentAuditService.getAuditByOperationType("PAY", startTime, endTime);
        List<PaymentAudit> cancelAudits = paymentAuditService.getAuditByOperationType("CANCEL", startTime, endTime);
        List<PaymentAudit> refundAudits = paymentAuditService.getAuditByOperationType("REFUND", startTime, endTime);

        assertTrue(createAudits.size() >= 1, "应该有CREATE记录");
        assertTrue(payAudits.size() >= 1, "应该有PAY记录");
        assertTrue(cancelAudits.size() >= 1, "应该有CANCEL记录");
        assertTrue(refundAudits.size() >= 1, "应该有REFUND记录");

        // 验证按操作人查询
        List<PaymentAudit> userAudits = paymentAuditService.getAuditByOperator(userId, startTime, endTime);
        assertTrue(userAudits.size() >= 1, "应该有该用户的记录");
        assertTrue(userAudits.stream().allMatch(audit -> userId.equals(audit.getOperatorId())));
    }

    @Test
    void testAuditDetailsJsonStructure() {
        // 测试审计详情的JSON结构
        String orderNo = "ORDER_JSON_" + System.currentTimeMillis();
        
        Map<String, Object> complexDetails = new HashMap<>();
        complexDetails.put("packageId", 1L);
        complexDetails.put("amount", "25.99");
        complexDetails.put("currency", "USD");
        complexDetails.put("paymentMethod", "alipay");
        complexDetails.put("userAgent", "Mozilla/5.0");
        complexDetails.put("metadata", Map.of("source", "web", "version", "1.0"));
        
        String detailsJson = ((PaymentAuditServiceImpl) paymentAuditService).createOperationDetails(complexDetails);
        paymentAuditService.recordCreateAudit(orderNo, 12345L, detailsJson, "192.168.1.1", "Test Browser");

        List<PaymentAudit> audits = paymentAuditService.getAuditByOrderNo(orderNo);
        assertEquals(1, audits.size());
        
        PaymentAudit audit = audits.get(0);
        String details = audit.getOperationDetails();
        
        // 验证JSON包含所有预期字段
        assertTrue(details.contains("packageId"), "应该包含packageId");
        assertTrue(details.contains("25.99"), "应该包含金额");
        assertTrue(details.contains("alipay"), "应该包含支付方式");
        assertTrue(details.contains("timestamp"), "应该包含时间戳");
        assertTrue(details.contains("metadata"), "应该包含元数据");
    }

    @Test
    void testAuditTimeRangeFiltering() {
        // 测试时间范围过滤
        String orderNo = "ORDER_TIME_" + System.currentTimeMillis();
        
        // 记录审计日志
        paymentAuditService.recordCreateAudit(orderNo, 12345L, "{\"test\":\"time\"}", null, null);
        
        LocalDateTime now = LocalDateTime.now();
        
        // 测试不同的时间范围
        List<PaymentAudit> auditsInRange = paymentAuditService.getAuditByOperationType(
            "CREATE", now.minusHours(1), now.plusHours(1));
        List<PaymentAudit> auditsOutOfRange = paymentAuditService.getAuditByOperationType(
            "CREATE", now.plusHours(1), now.plusHours(2));
        
        // 验证时间过滤效果
        assertTrue(auditsInRange.stream().anyMatch(audit -> orderNo.equals(audit.getOrderNo())),
                   "在时间范围内应该能找到记录");
        assertFalse(auditsOutOfRange.stream().anyMatch(audit -> orderNo.equals(audit.getOrderNo())),
                    "在时间范围外不应该找到记录");
    }
}
