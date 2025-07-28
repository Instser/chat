package com.instser.chat.service;

import com.instser.chat.entity.Package;
import com.instser.chat.entity.UserOrder;
import com.instser.chat.service.serviceimpl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 订单优惠价格功能测试
 *
 * @author instser
 * @since 2025-07-28
 */
@ExtendWith(MockitoExtension.class)
class OrderDiscountPriceTest {

    @Mock
    private PackageService packageService;

    @Mock
    private UserPaymentStatusService userPaymentStatusService;

    @Mock
    private ExchangeRateService exchangeRateService;

    @Mock
    private RedisDistributedLockService distributedLockService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Package testPackage;
    private Long userId = 1001L;
    private Long packageId = 2L;

    @BeforeEach
    void setUp() {
        // 创建测试套餐
        testPackage = new Package();
        testPackage.setId(packageId);
        testPackage.setPackageName("测试套餐");
        testPackage.setPrice(new BigDecimal("9.99")); // 原价 9.99 USD
        testPackage.setDiscountPrice(new BigDecimal("6.99")); // 优惠价 6.99 USD
        testPackage.setIsEnabled(1);

        // Mock 基础服务
        when(packageService.getPackageById(packageId)).thenReturn(testPackage);
        when(exchangeRateService.convertPrice(any(BigDecimal.class), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    BigDecimal amount = invocation.getArgument(0);
                    return amount.multiply(new BigDecimal("7.2")); // 模拟汇率 1 USD = 7.2 CNY
                });
        when(exchangeRateService.getExchangeRate(anyString(), anyString()))
                .thenReturn(new BigDecimal("7.2"));
    }

    @Test
    void testNewUserWithDiscountPrice() {
        // 新用户且有优惠价格，应该使用优惠价格
        when(userPaymentStatusService.hasNeverPaid(userId)).thenReturn(true);

        UserOrder order = orderService.createOrder(userId, packageId, "USD", "alipay");

        assertNotNull(order);
        assertEquals(new BigDecimal("6.99"), order.getAmount()); // 应该使用优惠价格
        assertEquals("USD", order.getCurrency());
        assertEquals("alipay", order.getPaymentMethod());

        verify(userPaymentStatusService).hasNeverPaid(userId);
    }

    @Test
    void testOldUserWithDiscountPrice() {
        // 老用户即使有优惠价格，也应该使用原价
        when(userPaymentStatusService.hasNeverPaid(userId)).thenReturn(false);

        UserOrder order = orderService.createOrder(userId, packageId, "USD", "alipay");

        assertNotNull(order);
        assertEquals(new BigDecimal("9.99"), order.getAmount()); // 应该使用原价
        assertEquals("USD", order.getCurrency());

        verify(userPaymentStatusService).hasNeverPaid(userId);
    }

    @Test
    void testNewUserWithoutDiscountPrice() {
        // 新用户但没有优惠价格，应该使用原价
        testPackage.setDiscountPrice(null);
        when(userPaymentStatusService.hasNeverPaid(userId)).thenReturn(true);

        UserOrder order = orderService.createOrder(userId, packageId, "USD", "alipay");

        assertNotNull(order);
        assertEquals(new BigDecimal("9.99"), order.getAmount()); // 应该使用原价
        assertEquals("USD", order.getCurrency());

        verify(userPaymentStatusService).hasNeverPaid(userId);
    }

    @Test
    void testYiPayWithCurrencyConversion() {
        // 易支付应该转换为CNY
        when(userPaymentStatusService.hasNeverPaid(userId)).thenReturn(true);

        UserOrder order = orderService.createOrder(userId, packageId, "CNY", "alipay");

        assertNotNull(order);
        // 优惠价格 6.99 USD * 7.2 = 50.33 CNY
        assertEquals(new BigDecimal("50.33"), order.getAmount());
        assertEquals("CNY", order.getCurrency());
        assertEquals("alipay", order.getPaymentMethod());

        verify(exchangeRateService).convertPrice(new BigDecimal("6.99"), "USD", "CNY");
    }

    @Test
    void testOverseasAlipayKeepsUSD() {
        // 海外支付宝应该保持USD
        when(userPaymentStatusService.hasNeverPaid(userId)).thenReturn(true);

        UserOrder order = orderService.createOrder(userId, packageId, "USD", "overseas_alipay");

        assertNotNull(order);
        assertEquals(new BigDecimal("6.99"), order.getAmount()); // 优惠价格，保持USD
        assertEquals("USD", order.getCurrency());

        // 不应该调用汇率转换
        verify(exchangeRateService, never()).convertPrice(any(), any(), any());
    }

    @Test
    void testUserPaymentStatusServiceException() {
        // 用户支付状态服务异常时，应该使用原价
        when(userPaymentStatusService.hasNeverPaid(userId))
                .thenThrow(new RuntimeException("数据库连接失败"));

        UserOrder order = orderService.createOrder(userId, packageId, "USD", "alipay");

        assertNotNull(order);
        assertEquals(new BigDecimal("9.99"), order.getAmount()); // 异常时使用原价
        assertEquals("USD", order.getCurrency());
    }

    @Test
    void testExchangeRateServiceException() {
        // 汇率服务异常时，应该使用原始金额
        when(userPaymentStatusService.hasNeverPaid(userId)).thenReturn(true);
        when(exchangeRateService.convertPrice(any(BigDecimal.class), anyString(), anyString()))
                .thenThrow(new RuntimeException("汇率服务不可用"));

        UserOrder order = orderService.createOrder(userId, packageId, "CNY", "alipay", true);

        assertNotNull(order);
        assertEquals(new BigDecimal("6.99"), order.getAmount()); // 异常时使用原始金额
        assertEquals("CNY", order.getCurrency());
    }

    @Test
    void testZeroDiscountPrice() {
        // 优惠价格为0时，应该使用原价
        testPackage.setDiscountPrice(BigDecimal.ZERO);
        when(userPaymentStatusService.hasNeverPaid(userId)).thenReturn(true);

        UserOrder order = orderService.createOrder(userId, packageId, "USD", "alipay", false);

        assertNotNull(order);
        assertEquals(new BigDecimal("9.99"), order.getAmount()); // 应该使用原价
        assertEquals("USD", order.getCurrency());
    }

    @Test
    void testNegativeDiscountPrice() {
        // 负数优惠价格时，应该使用原价
        testPackage.setDiscountPrice(new BigDecimal("-1.00"));
        when(userPaymentStatusService.hasNeverPaid(userId)).thenReturn(true);

        UserOrder order = orderService.createOrder(userId, packageId, "USD", "alipay", false);

        assertNotNull(order);
        assertEquals(new BigDecimal("9.99"), order.getAmount()); // 应该使用原价
        assertEquals("USD", order.getCurrency());
    }
}
