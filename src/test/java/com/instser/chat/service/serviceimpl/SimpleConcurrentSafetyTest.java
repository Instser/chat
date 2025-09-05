package com.instser.chat.service.serviceimpl;

import com.instser.chat.entity.UserAccount;
import com.instser.chat.service.UserAccountService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 简化版并发安全测试用例
 * 验证乐观锁机制的正确性
 *
 * @author instser
 * @since 2025-09-04
 */
@SpringBootTest
@ActiveProfiles("test")
@Slf4j
class SimpleConcurrentSafetyTest {
    
    @Autowired
    private UserAccountService userAccountService;
    
    /**
     * 测试并发更新使用次数的安全性
     */
    @Test
    void testConcurrentUsageUpdate() throws InterruptedException {
        Long userId = 12345L;
        int threadCount = 10;
        int updatesPerThread = 5;
        int totalUpdates = threadCount * updatesPerThread;
        
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        // 初始化用户账户，设置足够的剩余次数
        initializeUserAccountForTest(userId, totalUpdates + 10);
        
        log.info("开始并发测试 - 线程数: {}, 每线程更新次数: {}, 总更新次数: {}", 
            threadCount, updatesPerThread, totalUpdates);
        
        // 启动多个线程并发更新
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    for (int j = 0; j < updatesPerThread; j++) {
                        boolean result = userAccountService.updateUsage(userId, 1);
                        if (result) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                        
                        // 模拟真实场景的随机延迟
                        Thread.sleep((long) (Math.random() * 10));
                    }
                } catch (Exception e) {
                    log.error("并发测试线程异常", e);
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        
        // 等待所有线程完成
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        assertTrue(completed, "并发测试在30秒内未完成");
        
        // 验证结果
        UserAccount finalAccount = userAccountService.getUserAccount(userId);
        assertNotNull(finalAccount, "用户账户不应为null");
        
        int actualUsed = (totalUpdates + 10) - finalAccount.getChatCountRemaining();
        int expectedUsed = successCount.get();
        
        log.info("并发测试结果 - 成功: {}, 失败: {}, 实际消费: {}, 预期消费: {}, 最终余额: {}", 
            successCount.get(), failureCount.get(), actualUsed, expectedUsed, finalAccount.getChatCountRemaining());
        
        // 验证数据一致性
        assertEquals(expectedUsed, actualUsed, "实际消费次数应该等于成功次数");
        assertEquals(totalUpdates, successCount.get() + failureCount.get(), "总操作次数应该匹配");
        
        // 验证没有出现负数
        assertTrue(finalAccount.getChatCountRemaining() >= 0, "剩余次数不应该为负数");
    }
    
    /**
     * 初始化测试用的用户账户
     */
    private void initializeUserAccountForTest(Long userId, int initialCount) {
        try {
            // 先初始化用户账户
            userAccountService.initUserAccount(userId);
            
            // 然后激活一个测试套餐，给足够的次数
            userAccountService.activatePackage(userId, 1L, 30, initialCount);
            
            log.debug("初始化测试账户完成 - 用户ID: {}, 初始次数: {}", userId, initialCount);
        } catch (Exception e) {
            log.error("初始化测试账户失败: userId={}", userId, e);
        }
    }
}