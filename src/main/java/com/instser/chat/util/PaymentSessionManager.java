package com.instser.chat.util;

import com.instser.chat.common.exception.PaymentException;
import com.instser.chat.service.RedisPaymentSecurityService;
import lombok.extern.slf4j.Slf4j;

/**
 * 支付会话管理器
 * 实现AutoCloseable接口，提供自动资源管理功能
 * 确保支付会话锁在任何情况下都能正确释放
 * 
 * @author instser
 * @since 2025-07-09
 */
@Slf4j
public class PaymentSessionManager implements AutoCloseable {
    
    private final RedisPaymentSecurityService securityService;
    private final Long userId;
    private boolean acquired = false;
    
    /**
     * 构造函数
     * 
     * @param securityService Redis支付安全服务
     * @param userId 用户ID
     */
    public PaymentSessionManager(RedisPaymentSecurityService securityService, Long userId) {
        this.securityService = securityService;
        this.userId = userId;
        
        if (securityService == null) {
            throw new IllegalArgumentException("RedisPaymentSecurityService不能为空");
        }
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        
        log.debug("创建支付会话管理器，用户ID: {}", userId);
    }
    
    /**
     * 获取支付会话锁
     * 
     * @return 是否成功获取会话锁
     * @throws PaymentException 如果获取会话锁失败
     */
    public boolean acquire() throws PaymentException {
        if (acquired) {
            log.warn("支付会话锁已获取，不能重复获取，用户ID: {}", userId);
            return true;
        }
        
        try {
            if (securityService.acquirePaymentSession(userId)) {
                acquired = true;
                log.info("成功获取支付会话锁，用户ID: {}", userId);
                return true;
            } else {
                log.warn("获取支付会话锁失败，用户ID: {}", userId);
                throw new PaymentException("支付会话获取失败，请稍后再试");
            }
        } catch (PaymentException e) {
            // 重新抛出PaymentException
            throw e;
        } catch (Exception e) {
            log.error("获取支付会话锁时发生异常，用户ID: {}", userId, e);
            throw new PaymentException("支付会话获取异常，请稍后再试");
        }
    }
    
    /**
     * 检查是否已获取会话锁
     * 
     * @return 是否已获取会话锁
     */
    public boolean isAcquired() {
        return acquired;
    }
    
    /**
     * 手动释放支付会话锁
     * 通常不需要手动调用，close()方法会自动释放
     */
    public void release() {
        if (acquired) {
            try {
                securityService.releasePaymentSession(userId);
                acquired = false;
                log.info("手动释放支付会话锁成功，用户ID: {}", userId);
            } catch (Exception e) {
                log.error("手动释放支付会话锁异常，用户ID: {}", userId, e);
            }
        } else {
            log.debug("支付会话锁未获取，无需释放，用户ID: {}", userId);
        }
    }
    
    /**
     * 自动释放资源
     * 实现AutoCloseable接口，支持try-with-resources语法
     */
    @Override
    public void close() {
        if (acquired) {
            try {
                securityService.releasePaymentSession(userId);
                acquired = false;
                log.info("支付会话锁已自动释放，用户ID: {}", userId);
            } catch (Exception e) {
                log.error("自动释放支付会话锁异常，用户ID: {}", userId, e);
                // 即使释放失败，也要标记为未获取状态，避免重复释放
                acquired = false;
            }
        } else {
            log.debug("支付会话锁未获取，无需自动释放，用户ID: {}", userId);
        }
    }
    
    /**
     * 获取用户ID
     * 
     * @return 用户ID
     */
    public Long getUserId() {
        return userId;
    }
    
    /**
     * 重写toString方法，便于调试
     */
    @Override
    public String toString() {
        return String.format("PaymentSessionManager{userId=%d, acquired=%s}", userId, acquired);
    }
}
