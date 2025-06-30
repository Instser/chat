package com.instser.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.dao.PaymentAuditDao;
import com.instser.chat.entity.PaymentAudit;
import com.instser.chat.service.serviceimpl.PaymentAuditServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 支付审计服务测试
 *
 * @author instser
 * @since 2025-06-30
 */
@ExtendWith(MockitoExtension.class)
class PaymentAuditServiceTest {

    @Mock
    private PaymentAuditDao paymentAuditDao;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PaymentAuditServiceImpl paymentAuditService;

    private String testOrderNo;
    private Long testUserId;
    private String testClientIp;
    private String testUserAgent;

    @BeforeEach
    void setUp() {
        testOrderNo = "ORDER_TEST_001";
        testUserId = 12345L;
        testClientIp = "192.168.1.100";
        testUserAgent = "Mozilla/5.0 Test Browser";
    }

    @Test
    void testRecordAudit() {
        // Given
        String operationType = "CREATE";
        Integer oldStatus = null;
        Integer newStatus = 0;
        String operatorType = "USER";
        String reason = "创建支付订单";
        String details = "{\"test\":\"data\"}";

        // When
        paymentAuditService.recordAudit(testOrderNo, operationType, oldStatus, newStatus,
                testUserId, operatorType, reason, details, testClientIp, testUserAgent);

        // Then
        ArgumentCaptor<PaymentAudit> auditCaptor = ArgumentCaptor.forClass(PaymentAudit.class);
        verify(paymentAuditDao).insert(auditCaptor.capture());

        PaymentAudit capturedAudit = auditCaptor.getValue();
        assertEquals(testOrderNo, capturedAudit.getOrderNo());
        assertEquals(operationType, capturedAudit.getOperationType());
        assertEquals(oldStatus, capturedAudit.getOldStatus());
        assertEquals(newStatus, capturedAudit.getNewStatus());
        assertEquals(testUserId, capturedAudit.getOperatorId());
        assertEquals(operatorType, capturedAudit.getOperatorType());
        assertEquals(reason, capturedAudit.getOperationReason());
        assertEquals(details, capturedAudit.getOperationDetails());
        assertEquals(testClientIp, capturedAudit.getClientIp());
        assertEquals(testUserAgent, capturedAudit.getUserAgent());
    }

    @Test
    void testRecordCreateAudit() {
        // Given
        String details = "{\"packageId\":1,\"amount\":\"10.00\"}";

        // When
        paymentAuditService.recordCreateAudit(testOrderNo, testUserId, details, testClientIp, testUserAgent);

        // Then
        ArgumentCaptor<PaymentAudit> auditCaptor = ArgumentCaptor.forClass(PaymentAudit.class);
        verify(paymentAuditDao).insert(auditCaptor.capture());

        PaymentAudit capturedAudit = auditCaptor.getValue();
        assertEquals(testOrderNo, capturedAudit.getOrderNo());
        assertEquals("CREATE", capturedAudit.getOperationType());
        assertNull(capturedAudit.getOldStatus());
        assertEquals(Integer.valueOf(0), capturedAudit.getNewStatus());
        assertEquals(testUserId, capturedAudit.getOperatorId());
        assertEquals("USER", capturedAudit.getOperatorType());
        assertEquals("创建支付订单", capturedAudit.getOperationReason());
        assertEquals(details, capturedAudit.getOperationDetails());
        assertEquals(testClientIp, capturedAudit.getClientIp());
        assertEquals(testUserAgent, capturedAudit.getUserAgent());
    }

    @Test
    void testRecordStatusChangeAudit_PaymentSuccess() {
        // Given
        Integer oldStatus = 0;
        Integer newStatus = 1; // 支付成功
        String reason = "支付宝回调确认";
        String details = "{\"transactionId\":\"TXN_123\"}";

        // When
        paymentAuditService.recordStatusChangeAudit(testOrderNo, oldStatus, newStatus, reason, details);

        // Then
        ArgumentCaptor<PaymentAudit> auditCaptor = ArgumentCaptor.forClass(PaymentAudit.class);
        verify(paymentAuditDao).insert(auditCaptor.capture());

        PaymentAudit capturedAudit = auditCaptor.getValue();
        assertEquals(testOrderNo, capturedAudit.getOrderNo());
        assertEquals("PAY", capturedAudit.getOperationType());
        assertEquals(oldStatus, capturedAudit.getOldStatus());
        assertEquals(newStatus, capturedAudit.getNewStatus());
        assertNull(capturedAudit.getOperatorId());
        assertEquals("CALLBACK", capturedAudit.getOperatorType());
        assertEquals(reason, capturedAudit.getOperationReason());
        assertEquals(details, capturedAudit.getOperationDetails());
    }

    @Test
    void testRecordStatusChangeAudit_PaymentCancel() {
        // Given
        Integer oldStatus = 0;
        Integer newStatus = 2; // 支付取消
        String reason = "订单过期自动取消";
        String details = "{\"expireTime\":\"2025-06-30T10:00:00\"}";

        // When
        paymentAuditService.recordStatusChangeAudit(testOrderNo, oldStatus, newStatus, reason, details);

        // Then
        ArgumentCaptor<PaymentAudit> auditCaptor = ArgumentCaptor.forClass(PaymentAudit.class);
        verify(paymentAuditDao).insert(auditCaptor.capture());

        PaymentAudit capturedAudit = auditCaptor.getValue();
        assertEquals("CANCEL", capturedAudit.getOperationType());
        assertEquals("SYSTEM", capturedAudit.getOperatorType());
    }

    @Test
    void testGetAuditByOrderNo() {
        // Given
        List<PaymentAudit> expectedAudits = Arrays.asList(
                createTestAudit("CREATE", 0),
                createTestAudit("PAY", 1)
        );
        when(paymentAuditDao.selectByOrderNo(testOrderNo)).thenReturn(expectedAudits);

        // When
        List<PaymentAudit> result = paymentAuditService.getAuditByOrderNo(testOrderNo);

        // Then
        assertEquals(expectedAudits, result);
        verify(paymentAuditDao).selectByOrderNo(testOrderNo);
    }

    @Test
    void testGetAuditByOperationType() {
        // Given
        String operationType = "CREATE";
        LocalDateTime startTime = LocalDateTime.now().minusDays(1);
        LocalDateTime endTime = LocalDateTime.now();
        List<PaymentAudit> expectedAudits = Arrays.asList(createTestAudit(operationType, 0));
        
        when(paymentAuditDao.selectByOperationType(operationType, startTime, endTime))
                .thenReturn(expectedAudits);

        // When
        List<PaymentAudit> result = paymentAuditService.getAuditByOperationType(operationType, startTime, endTime);

        // Then
        assertEquals(expectedAudits, result);
        verify(paymentAuditDao).selectByOperationType(operationType, startTime, endTime);
    }

    @Test
    void testGetAuditByOperator() {
        // Given
        LocalDateTime startTime = LocalDateTime.now().minusDays(1);
        LocalDateTime endTime = LocalDateTime.now();
        List<PaymentAudit> expectedAudits = Arrays.asList(createTestAudit("CREATE", 0));
        
        when(paymentAuditDao.selectByOperator(testUserId, startTime, endTime))
                .thenReturn(expectedAudits);

        // When
        List<PaymentAudit> result = paymentAuditService.getAuditByOperator(testUserId, startTime, endTime);

        // Then
        assertEquals(expectedAudits, result);
        verify(paymentAuditDao).selectByOperator(testUserId, startTime, endTime);
    }

    @Test
    void testRecordAuditWithException() {
        // Given
        when(paymentAuditDao.insert(any(PaymentAudit.class)))
                .thenThrow(new RuntimeException("Database error"));

        // When & Then - 不应该抛出异常，应该被捕获并记录日志
        assertDoesNotThrow(() -> {
            paymentAuditService.recordAudit(testOrderNo, "CREATE", null, 0,
                    testUserId, "USER", "test", "details", testClientIp, testUserAgent);
        });

        verify(paymentAuditDao).insert(any(PaymentAudit.class));
    }

    @Test
    void testGetAuditByOrderNoWithException() {
        // Given
        when(paymentAuditDao.selectByOrderNo(testOrderNo))
                .thenThrow(new RuntimeException("Database error"));

        // When
        List<PaymentAudit> result = paymentAuditService.getAuditByOrderNo(testOrderNo);

        // Then
        assertTrue(result.isEmpty());
        verify(paymentAuditDao).selectByOrderNo(testOrderNo);
    }

    private PaymentAudit createTestAudit(String operationType, Integer newStatus) {
        PaymentAudit audit = new PaymentAudit();
        audit.setId(1L);
        audit.setOrderNo(testOrderNo);
        audit.setOperationType(operationType);
        audit.setOldStatus(null);
        audit.setNewStatus(newStatus);
        audit.setOperatorId(testUserId);
        audit.setOperatorType("USER");
        audit.setOperationReason("测试审计");
        audit.setOperationDetails("{\"test\":\"data\"}");
        audit.setClientIp(testClientIp);
        audit.setUserAgent(testUserAgent);
        audit.setCreateTime(LocalDateTime.now());
        audit.setUpdateTime(LocalDateTime.now());
        return audit;
    }
}
