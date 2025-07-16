package com.instser.chat.service;

import com.instser.chat.entity.UserAccount;
import com.instser.chat.service.serviceimpl.UserPaymentStatusServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户支付状态服务测试
 *
 * @author instser
 * @since 2025-07-14
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
public class UserPaymentStatusServiceTest {

    @Autowired
    private UserPaymentStatusService userPaymentStatusService;

    @Autowired
    private UserAccountService userAccountService;

    /**
     * 测试用户充值状态判定
     */
    @Test
    public void testUserPaymentStatus() {
        // 测试用户ID（使用实际存在的用户）
        Long testUserId = 1L;
        
        log.info("=== 用户充值状态测试开始 ===");
        
        // 1. 测试是否从未充值
        boolean neverPaid = userPaymentStatusService.hasNeverPaid(testUserId);
        log.info("用户{}是否从未充值: {}", testUserId, neverPaid);
        
        // 2. 测试是否只使用免费套餐
        boolean onlyFree = userPaymentStatusService.isOnlyFreeUser(testUserId);
        log.info("用户{}是否只使用免费套餐: {}", testUserId, onlyFree);
        
        // 3. 测试是否为活跃付费用户
        boolean activePaid = userPaymentStatusService.isActivePaidUser(testUserId);
        log.info("用户{}是否为活跃付费用户: {}", testUserId, activePaid);
        
        // 4. 获取首次充值时间
        LocalDateTime firstPaymentTime = userPaymentStatusService.getFirstPaymentTime(testUserId);
        log.info("用户{}首次充值时间: {}", testUserId, firstPaymentTime);
        
        // 5. 获取总充值金额
        BigDecimal totalAmount = userPaymentStatusService.getTotalPaidAmount(testUserId);
        log.info("用户{}总充值金额: {}", testUserId, totalAmount);
        
        // 6. 获取用户账户信息
        UserAccount account = userAccountService.getUserAccount(testUserId);
        if (account != null) {
            log.info("用户{}当前套餐ID: {}", testUserId, account.getPackageId());
            log.info("用户{}套餐过期时间: {}", testUserId, account.getPackageExpireTime());
            log.info("用户{}剩余聊天次数: {}", testUserId, account.getChatCountRemaining());
        }
        
        log.info("=== 用户充值状态测试完成 ===");
    }

    /**
     * 测试多个用户的充值状态
     */
    @Test
    public void testMultipleUsersPaymentStatus() {
        log.info("=== 多用户充值状态测试开始 ===");
        
        // 测试多个用户ID
        Long[] testUserIds = {1L, 2L, 3L, 4L, 5L};
        
        for (Long userId : testUserIds) {
            try {
                boolean neverPaid = userPaymentStatusService.hasNeverPaid(userId);
                boolean onlyFree = userPaymentStatusService.isOnlyFreeUser(userId);
                boolean activePaid = userPaymentStatusService.isActivePaidUser(userId);
                BigDecimal totalAmount = userPaymentStatusService.getTotalPaidAmount(userId);
                
                String status = neverPaid ? "从未充值" : 
                               onlyFree ? "仅免费套餐" : 
                               activePaid ? "活跃付费用户" : "非活跃付费用户";
                
                log.info("用户{}: 状态={}, 总金额={}", userId, status, totalAmount);
                
            } catch (Exception e) {
                log.error("测试用户{}充值状态异常", userId, e);
            }
        }
        
        log.info("=== 多用户充值状态测试完成 ===");
    }

    /**
     * 性能测试
     */
    @Test
    public void testPerformance() {
        log.info("=== 性能测试开始 ===");
        
        Long testUserId = 1L;
        int testCount = 1000;
        
        // 测试hasNeverPaid方法性能
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < testCount; i++) {
            userPaymentStatusService.hasNeverPaid(testUserId);
        }
        long endTime = System.currentTimeMillis();
        log.info("hasNeverPaid方法执行{}次耗时: {}ms, 平均: {}ms", 
                testCount, (endTime - startTime), (endTime - startTime) / (double) testCount);
        
        // 测试isOnlyFreeUser方法性能
        startTime = System.currentTimeMillis();
        for (int i = 0; i < testCount; i++) {
            userPaymentStatusService.isOnlyFreeUser(testUserId);
        }
        endTime = System.currentTimeMillis();
        log.info("isOnlyFreeUser方法执行{}次耗时: {}ms, 平均: {}ms", 
                testCount, (endTime - startTime), (endTime - startTime) / (double) testCount);
        
        // 测试isActivePaidUser方法性能
        startTime = System.currentTimeMillis();
        for (int i = 0; i < testCount; i++) {
            userPaymentStatusService.isActivePaidUser(testUserId);
        }
        endTime = System.currentTimeMillis();
        log.info("isActivePaidUser方法执行{}次耗时: {}ms, 平均: {}ms", 
                testCount, (endTime - startTime), (endTime - startTime) / (double) testCount);
        
        log.info("=== 性能测试完成 ===");
    }

    /**
     * 边界条件测试
     */
    @Test
    public void testEdgeCases() {
        log.info("=== 边界条件测试开始 ===");
        
        // 测试null用户ID
        boolean neverPaidNull = userPaymentStatusService.hasNeverPaid(null);
        log.info("null用户ID是否从未充值: {}", neverPaidNull);
        
        // 测试不存在的用户ID
        Long nonExistentUserId = 999999L;
        boolean neverPaidNonExistent = userPaymentStatusService.hasNeverPaid(nonExistentUserId);
        log.info("不存在的用户{}是否从未充值: {}", nonExistentUserId, neverPaidNonExistent);
        
        // 测试负数用户ID
        Long negativeUserId = -1L;
        boolean neverPaidNegative = userPaymentStatusService.hasNeverPaid(negativeUserId);
        log.info("负数用户ID{}是否从未充值: {}", negativeUserId, neverPaidNegative);
        
        log.info("=== 边界条件测试完成 ===");
    }
}
