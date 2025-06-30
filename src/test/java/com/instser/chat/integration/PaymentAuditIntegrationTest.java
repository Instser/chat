package com.instser.chat.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.entity.PaymentAudit;
import com.instser.chat.service.PaymentAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 支付审计日志集成测试
 *
 * @author instser
 * @since 2025-06-30
 */
@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Transactional
@Rollback
class PaymentAuditIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private PaymentAuditService paymentAuditService;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void testPaymentAuditFullFlow() {
        // Given
        String testOrderNo = "ORDER_AUDIT_TEST_" + System.currentTimeMillis();
        Long testUserId = 99999L;
        String testClientIp = "192.168.1.200";
        String testUserAgent = "Test Browser/1.0";

        // 1. 测试记录支付创建审计日志
        String createDetails = "{\"packageId\":1,\"amount\":\"10.00\",\"currency\":\"USD\"}";
        paymentAuditService.recordCreateAudit(testOrderNo, testUserId, createDetails, testClientIp, testUserAgent);

        // 2. 测试记录支付状态变更审计日志（支付成功）
        String payDetails = "{\"transactionId\":\"TXN_TEST_123\",\"paymentMethod\":\"alipay\"}";
        paymentAuditService.recordStatusChangeAudit(testOrderNo, 0, 1, "支付宝回调确认", payDetails);

        // 3. 验证审计日志记录
        List<PaymentAudit> auditList = paymentAuditService.getAuditByOrderNo(testOrderNo);
        
        // 验证记录数量
        assertEquals(2, auditList.size(), "应该有2条审计记录");

        // 验证创建记录
        PaymentAudit createAudit = auditList.stream()
                .filter(audit -> "CREATE".equals(audit.getOperationType()))
                .findFirst()
                .orElse(null);
        
        assertNotNull(createAudit, "应该有创建审计记录");
        assertEquals(testOrderNo, createAudit.getOrderNo());
        assertEquals("CREATE", createAudit.getOperationType());
        assertNull(createAudit.getOldStatus());
        assertEquals(Integer.valueOf(0), createAudit.getNewStatus());
        assertEquals(testUserId, createAudit.getOperatorId());
        assertEquals("USER", createAudit.getOperatorType());
        assertEquals("创建支付订单", createAudit.getOperationReason());
        assertEquals(createDetails, createAudit.getOperationDetails());
        assertEquals(testClientIp, createAudit.getClientIp());
        assertEquals(testUserAgent, createAudit.getUserAgent());
        assertNotNull(createAudit.getCreateTime());

        // 验证支付记录
        PaymentAudit payAudit = auditList.stream()
                .filter(audit -> "PAY".equals(audit.getOperationType()))
                .findFirst()
                .orElse(null);
        
        assertNotNull(payAudit, "应该有支付审计记录");
        assertEquals(testOrderNo, payAudit.getOrderNo());
        assertEquals("PAY", payAudit.getOperationType());
        assertEquals(Integer.valueOf(0), payAudit.getOldStatus());
        assertEquals(Integer.valueOf(1), payAudit.getNewStatus());
        assertNull(payAudit.getOperatorId());
        assertEquals("CALLBACK", payAudit.getOperatorType());
        assertEquals("支付宝回调确认", payAudit.getOperationReason());
        assertEquals(payDetails, payAudit.getOperationDetails());
        assertNotNull(payAudit.getCreateTime());
    }

    @Test
    void testPaymentAuditQueryByOperationType() {
        // Given
        String testOrderNo = "ORDER_OP_TEST_" + System.currentTimeMillis();
        Long testUserId = 88888L;
        
        LocalDateTime startTime = LocalDateTime.now().minusMinutes(1);
        
        // 记录不同类型的审计日志
        paymentAuditService.recordCreateAudit(testOrderNo + "_1", testUserId, "{\"test\":1}", null, null);
        paymentAuditService.recordStatusChangeAudit(testOrderNo + "_2", 0, 1, "支付成功", "{\"test\":2}");
        paymentAuditService.recordStatusChangeAudit(testOrderNo + "_3", 0, 2, "支付取消", "{\"test\":3}");
        
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(1);

        // When & Then
        // 查询CREATE类型
        List<PaymentAudit> createAudits = paymentAuditService.getAuditByOperationType("CREATE", startTime, endTime);
        assertTrue(createAudits.size() >= 1, "应该至少有1条CREATE记录");
        assertTrue(createAudits.stream().allMatch(audit -> "CREATE".equals(audit.getOperationType())));

        // 查询PAY类型
        List<PaymentAudit> payAudits = paymentAuditService.getAuditByOperationType("PAY", startTime, endTime);
        assertTrue(payAudits.size() >= 1, "应该至少有1条PAY记录");
        assertTrue(payAudits.stream().allMatch(audit -> "PAY".equals(audit.getOperationType())));

        // 查询CANCEL类型
        List<PaymentAudit> cancelAudits = paymentAuditService.getAuditByOperationType("CANCEL", startTime, endTime);
        assertTrue(cancelAudits.size() >= 1, "应该至少有1条CANCEL记录");
        assertTrue(cancelAudits.stream().allMatch(audit -> "CANCEL".equals(audit.getOperationType())));
    }

    @Test
    void testPaymentAuditQueryByOperator() {
        // Given
        Long testUserId = 77777L;
        String testOrderNo = "ORDER_USER_TEST_" + System.currentTimeMillis();
        
        LocalDateTime startTime = LocalDateTime.now().minusMinutes(1);
        
        // 记录该用户的审计日志
        paymentAuditService.recordCreateAudit(testOrderNo + "_1", testUserId, "{\"test\":1}", null, null);
        paymentAuditService.recordCreateAudit(testOrderNo + "_2", testUserId, "{\"test\":2}", null, null);
        
        // 记录其他用户的审计日志
        paymentAuditService.recordCreateAudit(testOrderNo + "_3", 66666L, "{\"test\":3}", null, null);
        
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(1);

        // When
        List<PaymentAudit> userAudits = paymentAuditService.getAuditByOperator(testUserId, startTime, endTime);

        // Then
        assertTrue(userAudits.size() >= 2, "应该至少有2条该用户的记录");
        assertTrue(userAudits.stream().allMatch(audit -> testUserId.equals(audit.getOperatorId())));
    }

    @Test
    void testPaymentAuditTimeRange() {
        // Given
        String testOrderNo = "ORDER_TIME_TEST_" + System.currentTimeMillis();
        Long testUserId = 55555L;
        
        // 记录审计日志
        paymentAuditService.recordCreateAudit(testOrderNo, testUserId, "{\"test\":\"time\"}", null, null);
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.minusHours(1);
        LocalDateTime endTime = now.plusHours(1);
        LocalDateTime futureStart = now.plusHours(2);
        LocalDateTime futureEnd = now.plusHours(3);

        // When & Then
        // 在时间范围内查询
        List<PaymentAudit> auditsInRange = paymentAuditService.getAuditByOperationType("CREATE", startTime, endTime);
        assertTrue(auditsInRange.stream().anyMatch(audit -> testOrderNo.equals(audit.getOrderNo())));

        // 在时间范围外查询
        List<PaymentAudit> auditsOutOfRange = paymentAuditService.getAuditByOperationType("CREATE", futureStart, futureEnd);
        assertFalse(auditsOutOfRange.stream().anyMatch(audit -> testOrderNo.equals(audit.getOrderNo())));
    }

    @Test
    void testPaymentAuditOperationTypes() {
        // Given
        String testOrderNo = "ORDER_TYPES_TEST_" + System.currentTimeMillis();
        
        // When & Then - 测试不同状态变更对应的操作类型
        
        // 支付成功 -> PAY
        paymentAuditService.recordStatusChangeAudit(testOrderNo, 0, 1, "支付成功", "{}");
        List<PaymentAudit> payAudits = paymentAuditService.getAuditByOrderNo(testOrderNo);
        assertEquals("PAY", payAudits.get(0).getOperationType());
        assertEquals("CALLBACK", payAudits.get(0).getOperatorType());
        
        // 支付取消 -> CANCEL
        paymentAuditService.recordStatusChangeAudit(testOrderNo + "_2", 0, 2, "支付取消", "{}");
        List<PaymentAudit> cancelAudits = paymentAuditService.getAuditByOrderNo(testOrderNo + "_2");
        assertEquals("CANCEL", cancelAudits.get(0).getOperationType());
        assertEquals("SYSTEM", cancelAudits.get(0).getOperatorType());
        
        // 支付退款 -> REFUND
        paymentAuditService.recordStatusChangeAudit(testOrderNo + "_3", 1, 3, "支付退款", "{}");
        List<PaymentAudit> refundAudits = paymentAuditService.getAuditByOrderNo(testOrderNo + "_3");
        assertEquals("REFUND", refundAudits.get(0).getOperationType());
        assertEquals("SYSTEM", refundAudits.get(0).getOperatorType());
        
        // 其他状态 -> SYNC
        paymentAuditService.recordStatusChangeAudit(testOrderNo + "_4", 0, 4, "状态同步", "{}");
        List<PaymentAudit> syncAudits = paymentAuditService.getAuditByOrderNo(testOrderNo + "_4");
        assertEquals("SYNC", syncAudits.get(0).getOperationType());
        assertEquals("SYSTEM", syncAudits.get(0).getOperatorType());
    }
}
