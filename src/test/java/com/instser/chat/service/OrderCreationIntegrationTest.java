package com.instser.chat.service;

import com.instser.chat.entity.UserOrder;
import com.instser.chat.service.serviceimpl.OrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 订单创建集成测试
 * 验证用户创建新订单时自动取消旧订单的完整流程
 *
 * @author instser
 * @since 2025-06-30
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional // 确保测试后回滚数据
public class OrderCreationIntegrationTest {

    @Autowired
    private OrderService orderService;

    /**
     * 测试用户创建新订单时自动取消旧订单的完整流程
     */
    @Test
    public void testCreateOrderWithAutoCancellation() {
        Long testUserId = 1L; // 使用已存在的admin用户ID
        Long packageId = 1L;
        String currency = "CNY";
        String paymentMethod = "alipay";

        try {
            // 1. 首先清理测试用户的所有订单（确保测试环境干净）
            int initialCancelledCount = orderService.cancelUserPendingOrders(testUserId);
            System.out.println("🧹 清理测试环境，取消了 " + initialCancelledCount + " 个旧订单");

            // 2. 创建第一个订单
            System.out.println("📝 创建第一个订单...");
            UserOrder firstOrder = orderService.createOrder(testUserId, packageId, currency, paymentMethod);
            
            assertNotNull(firstOrder, "第一个订单应该创建成功");
            assertNotNull(firstOrder.getOrderNo(), "订单号应该不为空");
            assertEquals(testUserId, firstOrder.getUserId(), "用户ID应该正确");
            assertEquals(Integer.valueOf(0), firstOrder.getPaymentStatus(), "订单状态应该为未支付");
            
            System.out.println("✅ 第一个订单创建成功，订单号: " + firstOrder.getOrderNo());

            // 3. 验证用户当前有一个待支付订单
            List<UserOrder> pendingOrders = orderService.getPendingOrdersByUserId(testUserId);
            assertEquals(1, pendingOrders.size(), "用户应该有1个待支付订单");
            assertEquals(firstOrder.getOrderNo(), pendingOrders.get(0).getOrderNo(), "待支付订单应该是刚创建的订单");
            
            System.out.println("✅ 验证通过：用户当前有1个待支付订单");

            // 4. 创建第二个订单（应该自动取消第一个订单）
            System.out.println("📝 创建第二个订单（应该自动取消第一个订单）...");
            UserOrder secondOrder = orderService.createOrder(testUserId, packageId, currency, paymentMethod);
            
            assertNotNull(secondOrder, "第二个订单应该创建成功");
            assertNotNull(secondOrder.getOrderNo(), "第二个订单号应该不为空");
            assertNotEquals(firstOrder.getOrderNo(), secondOrder.getOrderNo(), "第二个订单号应该与第一个不同");
            assertEquals(Integer.valueOf(0), secondOrder.getPaymentStatus(), "第二个订单状态应该为未支付");
            
            System.out.println("✅ 第二个订单创建成功，订单号: " + secondOrder.getOrderNo());

            // 5. 验证用户现在只有一个待支付订单（第二个订单）
            List<UserOrder> currentPendingOrders = orderService.getPendingOrdersByUserId(testUserId);
            assertEquals(1, currentPendingOrders.size(), "用户应该仍然只有1个待支付订单");
            assertEquals(secondOrder.getOrderNo(), currentPendingOrders.get(0).getOrderNo(), 
                "当前待支付订单应该是第二个订单");
            
            System.out.println("✅ 验证通过：用户现在只有1个待支付订单（第二个订单）");

            // 6. 验证第一个订单已被取消
            UserOrder cancelledFirstOrder = orderService.getOrderByOrderNo(firstOrder.getOrderNo());
            assertNotNull(cancelledFirstOrder, "第一个订单记录应该存在");
            assertEquals(Integer.valueOf(2), cancelledFirstOrder.getPaymentStatus(), 
                "第一个订单状态应该为已取消");
            
            System.out.println("✅ 验证通过：第一个订单已被自动取消");

            // 7. 测试创建第三个订单
            System.out.println("📝 创建第三个订单（应该自动取消第二个订单）...");
            UserOrder thirdOrder = orderService.createOrder(testUserId, packageId, currency, paymentMethod);
            
            assertNotNull(thirdOrder, "第三个订单应该创建成功");
            assertEquals(Integer.valueOf(0), thirdOrder.getPaymentStatus(), "第三个订单状态应该为未支付");
            
            System.out.println("✅ 第三个订单创建成功，订单号: " + thirdOrder.getOrderNo());

            // 8. 最终验证：用户只有一个待支付订单（第三个订单）
            List<UserOrder> finalPendingOrders = orderService.getPendingOrdersByUserId(testUserId);
            assertEquals(1, finalPendingOrders.size(), "用户最终应该只有1个待支付订单");
            assertEquals(thirdOrder.getOrderNo(), finalPendingOrders.get(0).getOrderNo(), 
                "最终待支付订单应该是第三个订单");
            
            System.out.println("✅ 最终验证通过：用户只有1个待支付订单（第三个订单）");

            // 9. 验证第二个订单也已被取消
            UserOrder cancelledSecondOrder = orderService.getOrderByOrderNo(secondOrder.getOrderNo());
            assertNotNull(cancelledSecondOrder, "第二个订单记录应该存在");
            assertEquals(Integer.valueOf(2), cancelledSecondOrder.getPaymentStatus(), 
                "第二个订单状态应该为已取消");
            
            System.out.println("✅ 验证通过：第二个订单也已被自动取消");

            System.out.println("\n🎉 订单自动取消功能集成测试完全通过！");
            System.out.println("📊 测试结果总结：");
            System.out.println("  - 创建了3个订单");
            System.out.println("  - 前2个订单被自动取消");
            System.out.println("  - 用户始终只有1个待支付订单");
            System.out.println("  - 自动取消机制工作正常");

        } catch (Exception e) {
            System.err.println("❌ 测试过程中发生异常: " + e.getMessage());
            e.printStackTrace();
            fail("集成测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试cancelUserPendingOrders方法的直接调用
     */
    @Test
    public void testCancelUserPendingOrdersDirectly() {
        Long testUserId = 1L; // 使用已存在的admin用户ID
        
        try {
            // 1. 先清理环境
            orderService.cancelUserPendingOrders(testUserId);
            
            // 2. 创建多个订单
            System.out.println("📝 创建多个测试订单...");
            UserOrder order1 = orderService.createOrder(testUserId, 1L, "CNY", "alipay");
            UserOrder order2 = orderService.createOrder(testUserId, 2L, "USD", "paypal");
            UserOrder order3 = orderService.createOrder(testUserId, 3L, "CNY", "wechat");
            
            // 注意：由于自动取消机制，实际上只有最后一个订单是待支付状态
            List<UserOrder> pendingOrders = orderService.getPendingOrdersByUserId(testUserId);
            assertEquals(1, pendingOrders.size(), "由于自动取消机制，用户应该只有1个待支付订单");
            
            System.out.println("✅ 当前待支付订单数量: " + pendingOrders.size());
            
            // 3. 直接调用取消方法
            int cancelledCount = orderService.cancelUserPendingOrders(testUserId);
            System.out.println("📋 直接取消操作结果: " + cancelledCount + " 个订单被取消");
            
            // 4. 验证所有订单都被取消
            List<UserOrder> remainingPendingOrders = orderService.getPendingOrdersByUserId(testUserId);
            assertEquals(0, remainingPendingOrders.size(), "所有待支付订单都应该被取消");
            
            System.out.println("✅ 验证通过：所有待支付订单都已被取消");
            
        } catch (Exception e) {
            System.err.println("❌ 直接取消测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("直接取消测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试边界情况：用户没有待支付订单时创建新订单
     */
    @Test
    public void testCreateOrderWithNoPendingOrders() {
        Long testUserId = 1L; // 使用已存在的admin用户ID
        
        try {
            // 1. 确保用户没有待支付订单
            orderService.cancelUserPendingOrders(testUserId);
            List<UserOrder> initialPendingOrders = orderService.getPendingOrdersByUserId(testUserId);
            assertEquals(0, initialPendingOrders.size(), "初始状态应该没有待支付订单");
            
            // 2. 创建新订单
            UserOrder newOrder = orderService.createOrder(testUserId, 1L, "CNY", "alipay");
            assertNotNull(newOrder, "新订单应该创建成功");
            assertEquals(Integer.valueOf(0), newOrder.getPaymentStatus(), "新订单状态应该为未支付");
            
            // 3. 验证订单创建成功
            List<UserOrder> finalPendingOrders = orderService.getPendingOrdersByUserId(testUserId);
            assertEquals(1, finalPendingOrders.size(), "应该有1个待支付订单");
            assertEquals(newOrder.getOrderNo(), finalPendingOrders.get(0).getOrderNo(), 
                "待支付订单应该是刚创建的订单");
            
            System.out.println("✅ 边界情况测试通过：无待支付订单时创建新订单正常");
            
        } catch (Exception e) {
            System.err.println("❌ 边界情况测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("边界情况测试失败: " + e.getMessage());
        }
    }
}
