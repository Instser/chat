package com.instser.chat.service;

import com.instser.chat.dao.UserDailyUsageDao;
import com.instser.chat.entity.UserDailyUsage;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 原子性操作测试类
 * 用于验证ON DUPLICATE KEY UPDATE的并发安全性
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
public class AtomicOperationTest {

    @Autowired
    private UserDailyUsageDao userDailyUsageDao;

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Long TEST_USER_ID = 9999L;
    private static final Long TEST_PACKAGE_ID = 1L;
    private static final Integer DAILY_LIMIT = 10;

    /**
     * 获取业务日期
     */
    private LocalDate getBusinessDate() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    /**
     * 测试单线程原子性操作
     */
    @Test
    public void testSingleThreadAtomicOperation() {
        log.info("=== 开始单线程原子性操作测试 ===");
        
        // 清理测试数据
        cleanTestData();
        
        LocalDate businessDate = getBusinessDate();
        
        // 第一次调用 - 应该插入新记录
        int result1 = userDailyUsageDao.insertOrIncrementUsage(
            TEST_USER_ID, TEST_PACKAGE_ID, businessDate, DAILY_LIMIT);
        log.info("第一次调用结果: {}", result1);
        
        // 验证记录
        UserDailyUsage usage1 = userDailyUsageDao.selectByUserAndDate(
            TEST_USER_ID, TEST_PACKAGE_ID, businessDate);
        log.info("第一次调用后记录: {}", usage1);
        assert usage1 != null;
        assert usage1.getChatCountUsed() == 1;
        
        // 第二次调用 - 应该更新现有记录
        int result2 = userDailyUsageDao.insertOrIncrementUsage(
            TEST_USER_ID, TEST_PACKAGE_ID, businessDate, DAILY_LIMIT);
        log.info("第二次调用结果: {}", result2);
        
        // 验证记录
        UserDailyUsage usage2 = userDailyUsageDao.selectByUserAndDate(
            TEST_USER_ID, TEST_PACKAGE_ID, businessDate);
        log.info("第二次调用后记录: {}", usage2);
        assert usage2 != null;
        assert usage2.getChatCountUsed() == 2;
        
        // 清理测试数据
        cleanTestData();
        
        log.info("=== 单线程原子性操作测试完成 ===");
    }

    /**
     * 测试多线程并发原子性操作
     */
    @Test
    public void testConcurrentAtomicOperation() throws InterruptedException {
        log.info("=== 开始并发原子性操作测试 ===");
        
        // 清理测试数据
        cleanTestData();
        
        LocalDate businessDate = getBusinessDate();
        int threadCount = 50; // 50个并发线程
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        
        // 创建并发任务
        for (int i = 0; i < threadCount; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    // 等待统一开始信号
                    startLatch.await();
                    
                    // 执行原子性操作
                    int result = userDailyUsageDao.insertOrIncrementUsage(
                        TEST_USER_ID, TEST_PACKAGE_ID, businessDate, DAILY_LIMIT);
                    
                    if (result > 0) {
                        successCount.incrementAndGet();
                        log.debug("任务{}执行成功", taskId);
                    } else {
                        failCount.incrementAndGet();
                        log.warn("任务{}执行失败", taskId);
                    }
                    
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.error("任务{}执行异常: {}", taskId, e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        // 发出开始信号
        log.info("发出并发开始信号，{}个线程同时执行", threadCount);
        long startTime = System.currentTimeMillis();
        startLatch.countDown();
        
        // 等待所有任务完成
        endLatch.await();
        long endTime = System.currentTimeMillis();
        
        // 验证结果
        UserDailyUsage finalUsage = userDailyUsageDao.selectByUserAndDate(
            TEST_USER_ID, TEST_PACKAGE_ID, businessDate);
        
        log.info("=== 并发测试结果 ===");
        log.info("执行时间: {}ms", endTime - startTime);
        log.info("成功次数: {}", successCount.get());
        log.info("失败次数: {}", failCount.get());
        log.info("最终使用次数: {}", finalUsage != null ? finalUsage.getChatCountUsed() : 0);
        log.info("预期使用次数: {}", threadCount);
        
        // 断言验证
        assert finalUsage != null;
        assert finalUsage.getChatCountUsed() == threadCount;
        assert successCount.get() == threadCount;
        assert failCount.get() == 0;
        
        executor.shutdown();
        
        // 清理测试数据
        cleanTestData();
        
        log.info("=== 并发原子性操作测试完成 ===");
    }

    /**
     * 测试跨日期边界的并发操作
     */
    @Test
    public void testCrossDateBoundaryOperation() {
        log.info("=== 开始跨日期边界测试 ===");
        
        // 清理测试数据
        cleanTestData();
        
        LocalDate today = getBusinessDate();
        LocalDate yesterday = today.minusDays(1);
        
        // 模拟昨天的使用记录
        int result1 = userDailyUsageDao.insertOrIncrementUsage(
            TEST_USER_ID, TEST_PACKAGE_ID, yesterday, DAILY_LIMIT);
        log.info("昨天记录创建结果: {}", result1);
        
        // 模拟今天的使用记录
        int result2 = userDailyUsageDao.insertOrIncrementUsage(
            TEST_USER_ID, TEST_PACKAGE_ID, today, DAILY_LIMIT);
        log.info("今天记录创建结果: {}", result2);
        
        // 验证两天的记录都存在且独立
        UserDailyUsage yesterdayUsage = userDailyUsageDao.selectByUserAndDate(
            TEST_USER_ID, TEST_PACKAGE_ID, yesterday);
        UserDailyUsage todayUsage = userDailyUsageDao.selectByUserAndDate(
            TEST_USER_ID, TEST_PACKAGE_ID, today);
        
        log.info("昨天使用记录: {}", yesterdayUsage);
        log.info("今天使用记录: {}", todayUsage);
        
        assert yesterdayUsage != null;
        assert todayUsage != null;
        assert yesterdayUsage.getChatCountUsed() == 1;
        assert todayUsage.getChatCountUsed() == 1;
        assert !yesterdayUsage.getId().equals(todayUsage.getId());
        
        // 清理测试数据
        cleanTestData();
        
        log.info("=== 跨日期边界测试完成 ===");
    }

    /**
     * 清理测试数据
     */
    private void cleanTestData() {
        try {
            // 删除测试用户的所有记录
            userDailyUsageDao.delete(null); // 这里需要根据实际情况调整删除逻辑
            log.debug("测试数据清理完成");
        } catch (Exception e) {
            log.warn("清理测试数据失败: {}", e.getMessage());
        }
    }
}
