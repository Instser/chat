package com.instser.chat.service;

import com.instser.chat.entity.Package;
import com.instser.chat.entity.UserOrder;
import com.instser.chat.vo.PaymentRequestVO;
import com.instser.chat.vo.PaymentResponseVO;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 支付服务测试类
 *
 * @author instser
 * @since 2024-01-01
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class PaymentServiceTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PackageService packageService;

    @Autowired
    private OrderService orderService;

    @Test
    public void testGetPackages() {
        // 测试获取套餐列表
        List<Package> packages = packageService.getEnabledPackages();
        assertNotNull(packages);
        assertTrue(packages.size() >= 3); // 应该有至少3个默认套餐
        
        // 验证基础套餐
        Package basicPackage = packages.stream()
                .filter(p -> "basic".equals(p.getPackageType()))
                .findFirst()
                .orElse(null);
        
        assertNotNull(basicPackage);
        assertEquals("基础套餐", basicPackage.getPackageName());
        assertEquals(new BigDecimal("9.99"), basicPackage.getPriceUsd());
        assertEquals(new BigDecimal("69.99"), basicPackage.getPriceCny());
    }

    @Test
    public void testCreateOrder() {
        // 获取一个套餐
        List<Package> packages = packageService.getEnabledPackages();
        assertFalse(packages.isEmpty());
        
        Package testPackage = packages.get(0);
        
        // 创建订单
        UserOrder order = orderService.createOrder(1L, testPackage.getId(), "USD", "alipay");
        
        assertNotNull(order);
        assertNotNull(order.getOrderNo());
        assertNotNull(order.getPaymentRequestId());
        assertEquals(testPackage.getId(), order.getPackageId());
        assertEquals("USD", order.getCurrency());
        assertEquals("alipay", order.getPaymentMethod());
        assertEquals(Integer.valueOf(0), order.getPaymentStatus()); // 未支付
        assertEquals(testPackage.getPriceUsd(), order.getAmount());
    }

    @Test
    public void testCreatePaymentWithoutConfig() {
        // 测试在没有支付宝配置的情况下创建支付
        List<Package> packages = packageService.getEnabledPackages();
        assertFalse(packages.isEmpty());
        
        Package testPackage = packages.get(0);
        
        PaymentRequestVO request = new PaymentRequestVO();
        request.setUserId(1L);
        request.setPackageId(testPackage.getId());
        request.setCurrency("USD");
        request.setPaymentMethod("alipay");
        
        PaymentResponseVO response = paymentService.createPayment(request);
        
        assertNotNull(response);
        assertFalse(response.getSuccess()); // 应该失败，因为没有配置
        assertNotNull(response.getErrorMessage());
        assertTrue(response.getErrorMessage().contains("支付配置未完成"));
    }

    @Test
    public void testOrderStatusUpdate() {
        // 创建订单
        List<Package> packages = packageService.getEnabledPackages();
        Package testPackage = packages.get(0);
        
        UserOrder order = orderService.createOrder(1L, testPackage.getId(), "USD", "alipay");
        String orderNo = order.getOrderNo();
        
        // 更新支付状态
        boolean updated = orderService.updatePaymentStatus(orderNo, 1, "TEST_TRANSACTION_ID");
        assertTrue(updated);
        
        // 查询订单状态
        UserOrder updatedOrder = orderService.getOrderByOrderNo(orderNo);
        assertNotNull(updatedOrder);
        assertEquals(Integer.valueOf(1), updatedOrder.getPaymentStatus());
        assertEquals("TEST_TRANSACTION_ID", updatedOrder.getTransactionId());
        assertNotNull(updatedOrder.getPaymentTime());
    }

    @Test
    public void testCancelOrder() {
        // 创建订单
        List<Package> packages = packageService.getEnabledPackages();
        Package testPackage = packages.get(0);
        
        UserOrder order = orderService.createOrder(1L, testPackage.getId(), "USD", "alipay");
        String orderNo = order.getOrderNo();
        
        // 取消订单
        boolean cancelled = orderService.cancelOrder(orderNo);
        assertTrue(cancelled);
        
        // 验证订单状态
        UserOrder cancelledOrder = orderService.getOrderByOrderNo(orderNo);
        assertNotNull(cancelledOrder);
        assertEquals(Integer.valueOf(2), cancelledOrder.getPaymentStatus()); // 已取消
    }

    @Test
    public void testGenerateOrderNo() {
        String orderNo1 = orderService.generateOrderNo();
        String orderNo2 = orderService.generateOrderNo();
        
        assertNotNull(orderNo1);
        assertNotNull(orderNo2);
        assertNotEquals(orderNo1, orderNo2); // 订单号应该不同
        assertTrue(orderNo1.startsWith("JY")); // 应该以JY开头
        assertTrue(orderNo2.startsWith("JY"));
    }

    @Test
    public void testMultiCurrencySupport() {
        List<Package> packages = packageService.getEnabledPackages();
        Package testPackage = packages.get(0);
        
        // 测试USD订单
        UserOrder usdOrder = orderService.createOrder(1L, testPackage.getId(), "USD", "alipay");
        assertEquals("USD", usdOrder.getCurrency());
        assertEquals(testPackage.getPriceUsd(), usdOrder.getAmount());
        
        // 测试CNY订单
        UserOrder cnyOrder = orderService.createOrder(1L, testPackage.getId(), "CNY", "alipay");
        assertEquals("CNY", cnyOrder.getCurrency());
        assertEquals(testPackage.getPriceCny(), cnyOrder.getAmount());
    }

    @Test
    public void testInvalidCurrency() {
        List<Package> packages = packageService.getEnabledPackages();
        Package testPackage = packages.get(0);
        
        // 测试不支持的货币类型
        assertThrows(Exception.class, () -> {
            orderService.createOrder(1L, testPackage.getId(), "EUR", "alipay");
        });
    }

    @Test
    public void testInvalidPackage() {
        // 测试不存在的套餐ID
        assertThrows(Exception.class, () -> {
            orderService.createOrder(1L, 999999L, "USD", "alipay");
        });
    }
}
