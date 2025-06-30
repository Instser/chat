package com.instser.chat.service;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redis分布式锁服务
 * 基于Redisson实现分布式锁功能
 *
 * @author instser
 * @since 2025-06-30
 */
@Slf4j
@Service
public class RedisDistributedLockService {

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 执行带锁的操作
     *
     * @param lockKey 锁的键
     * @param waitTime 等待时间（秒）
     * @param leaseTime 锁持有时间（秒）
     * @param supplier 要执行的操作
     * @param <T> 返回类型
     * @return 操作结果
     */
    public <T> T executeWithLock(String lockKey, long waitTime, long leaseTime, Supplier<T> supplier) {
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 尝试获取锁
            boolean acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);
            
            if (!acquired) {
                log.warn("获取分布式锁失败，锁键: {}", lockKey);
                return null;
            }
            
            log.debug("成功获取分布式锁，锁键: {}", lockKey);
            
            // 执行业务操作
            return supplier.get();
            
        } catch (InterruptedException e) {
            log.error("获取分布式锁被中断，锁键: {}", lockKey, e);
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.error("执行分布式锁操作异常，锁键: {}", lockKey, e);
            return null;
        } finally {
            // 释放锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("释放分布式锁，锁键: {}", lockKey);
            }
        }
    }

    /**
     * 执行带锁的操作（无返回值）
     *
     * @param lockKey 锁的键
     * @param waitTime 等待时间（秒）
     * @param leaseTime 锁持有时间（秒）
     * @param runnable 要执行的操作
     * @return 是否执行成功
     */
    public boolean executeWithLock(String lockKey, long waitTime, long leaseTime, Runnable runnable) {
        return executeWithLock(lockKey, waitTime, leaseTime, () -> {
            runnable.run();
            return true;
        }) != null;
    }

    /**
     * 订单状态更新分布式锁
     *
     * @param orderNo 订单号
     * @param supplier 更新操作
     * @param <T> 返回类型
     * @return 操作结果
     */
    public <T> T executeOrderLock(String orderNo, Supplier<T> supplier) {
        String lockKey = "order:lock:" + orderNo;
        return executeWithLock(lockKey, 3, 10, supplier);
    }

    /**
     * 订单状态更新分布式锁（无返回值）
     *
     * @param orderNo 订单号
     * @param runnable 更新操作
     * @return 是否执行成功
     */
    public boolean executeOrderLock(String orderNo, Runnable runnable) {
        String lockKey = "order:lock:" + orderNo;
        return executeWithLock(lockKey, 3, 10, runnable);
    }

    /**
     * 用户支付锁
     *
     * @param userId 用户ID
     * @param supplier 支付操作
     * @param <T> 返回类型
     * @return 操作结果
     */
    public <T> T executeUserPaymentLock(Long userId, Supplier<T> supplier) {
        String lockKey = "user:payment:lock:" + userId;
        return executeWithLock(lockKey, 5, 30, supplier);
    }

    /**
     * 用户支付锁（无返回值）
     *
     * @param userId 用户ID
     * @param runnable 支付操作
     * @return 是否执行成功
     */
    public boolean executeUserPaymentLock(Long userId, Runnable runnable) {
        String lockKey = "user:payment:lock:" + userId;
        return executeWithLock(lockKey, 5, 30, runnable);
    }

    /**
     * 检查锁是否存在
     *
     * @param lockKey 锁的键
     * @return 是否存在
     */
    public boolean isLocked(String lockKey) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            return lock.isLocked();
        } catch (Exception e) {
            log.error("检查锁状态异常，锁键: {}", lockKey, e);
            return false;
        }
    }

    /**
     * 强制释放锁（谨慎使用）
     *
     * @param lockKey 锁的键
     * @return 是否释放成功
     */
    public boolean forceUnlock(String lockKey) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            if (lock.isLocked()) {
                lock.forceUnlock();
                log.warn("强制释放锁，锁键: {}", lockKey);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("强制释放锁异常，锁键: {}", lockKey, e);
            return false;
        }
    }
}
