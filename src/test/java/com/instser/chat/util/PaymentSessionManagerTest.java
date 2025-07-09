package com.instser.chat.util;

import com.instser.chat.common.exception.PaymentException;
import com.instser.chat.service.RedisPaymentSecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * PaymentSessionManager单元测试
 * 验证支付会话锁的自动管理功能
 * 
 * @author instser
 * @since 2025-07-09
 */
@ExtendWith(MockitoExtension.class)
class PaymentSessionManagerTest {

    @Mock
    private RedisPaymentSecurityService securityService;

    private Long userId = 12345L;

    @BeforeEach
    void setUp() {
        // 每个测试前重置mock
        reset(securityService);
    }

    @Test
    void testConstructorWithValidParameters() {
        // 测试正常构造
        PaymentSessionManager manager = new PaymentSessionManager(securityService, userId);
        
        assertNotNull(manager);
        assertEquals(userId, manager.getUserId());
        assertFalse(manager.isAcquired());
    }

    @Test
    void testConstructorWithNullService() {
        // 测试空服务参数
        assertThrows(IllegalArgumentException.class, () -> {
            new PaymentSessionManager(null, userId);
        });
    }

    @Test
    void testConstructorWithNullUserId() {
        // 测试空用户ID参数
        assertThrows(IllegalArgumentException.class, () -> {
            new PaymentSessionManager(securityService, null);
        });
    }

    @Test
    void testAcquireSuccess() throws PaymentException {
        // 模拟成功获取锁
        when(securityService.acquirePaymentSession(userId)).thenReturn(true);

        PaymentSessionManager manager = new PaymentSessionManager(securityService, userId);
        boolean result = manager.acquire();

        assertTrue(result);
        assertTrue(manager.isAcquired());
        verify(securityService, times(1)).acquirePaymentSession(userId);
    }

    @Test
    void testAcquireFailure() {
        // 模拟获取锁失败
        when(securityService.acquirePaymentSession(userId)).thenReturn(false);

        PaymentSessionManager manager = new PaymentSessionManager(securityService, userId);
        
        assertThrows(PaymentException.class, () -> {
            manager.acquire();
        });
        
        assertFalse(manager.isAcquired());
        verify(securityService, times(1)).acquirePaymentSession(userId);
    }

    @Test
    void testAcquireException() {
        // 模拟获取锁时发生异常
        when(securityService.acquirePaymentSession(userId)).thenThrow(new RuntimeException("Redis连接失败"));

        PaymentSessionManager manager = new PaymentSessionManager(securityService, userId);
        
        assertThrows(PaymentException.class, () -> {
            manager.acquire();
        });
        
        assertFalse(manager.isAcquired());
        verify(securityService, times(1)).acquirePaymentSession(userId);
    }

    @Test
    void testTryWithResourcesAutoClose() throws Exception {
        // 模拟成功获取锁
        when(securityService.acquirePaymentSession(userId)).thenReturn(true);

        // 使用try-with-resources测试自动释放
        try (PaymentSessionManager manager = new PaymentSessionManager(securityService, userId)) {
            manager.acquire();
            assertTrue(manager.isAcquired());
        }

        // 验证释放方法被调用
        verify(securityService, times(1)).acquirePaymentSession(userId);
        verify(securityService, times(1)).releasePaymentSession(userId);
    }

    @Test
    void testManualRelease() throws PaymentException {
        // 模拟成功获取锁
        when(securityService.acquirePaymentSession(userId)).thenReturn(true);

        PaymentSessionManager manager = new PaymentSessionManager(securityService, userId);
        manager.acquire();
        assertTrue(manager.isAcquired());

        // 手动释放
        manager.release();
        assertFalse(manager.isAcquired());

        verify(securityService, times(1)).acquirePaymentSession(userId);
        verify(securityService, times(1)).releasePaymentSession(userId);
    }

    @Test
    void testReleaseWithoutAcquire() {
        // 测试未获取锁时释放
        PaymentSessionManager manager = new PaymentSessionManager(securityService, userId);
        
        // 应该不会调用释放方法
        manager.release();
        
        verify(securityService, never()).releasePaymentSession(userId);
    }

    @Test
    void testDoubleAcquire() throws PaymentException {
        // 模拟成功获取锁
        when(securityService.acquirePaymentSession(userId)).thenReturn(true);

        PaymentSessionManager manager = new PaymentSessionManager(securityService, userId);
        manager.acquire();
        
        // 第二次获取应该直接返回true，不再调用服务
        boolean result = manager.acquire();
        assertTrue(result);
        
        // 验证只调用了一次获取方法
        verify(securityService, times(1)).acquirePaymentSession(userId);
    }

    @Test
    void testToString() {
        PaymentSessionManager manager = new PaymentSessionManager(securityService, userId);
        String result = manager.toString();
        
        assertTrue(result.contains("PaymentSessionManager"));
        assertTrue(result.contains(userId.toString()));
        assertTrue(result.contains("acquired=false"));
    }
}
