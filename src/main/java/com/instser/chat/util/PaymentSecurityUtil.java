package com.instser.chat.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 支付安全工具类
 * 用于防止重放攻击和重复处理
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@Component
public class PaymentSecurityUtil {

    /**
     * 回调请求缓存，用于防重放攻击
     * Key: 请求唯一标识, Value: 处理时间戳
     */
    private final ConcurrentHashMap<String, Long> callbackCache = new ConcurrentHashMap<>();

    /**
     * 缓存过期时间（毫秒）- 1小时
     */
    private static final long CACHE_EXPIRE_TIME = TimeUnit.HOURS.toMillis(1);

    /**
     * 最大缓存大小
     */
    private static final int MAX_CACHE_SIZE = 10000;

    /**
     * 检查并记录回调请求，防止重放攻击
     *
     * @param requestId 请求唯一标识（可以是订单号+时间戳+签名的组合）
     * @return true-首次请求，false-重复请求
     */
    public boolean checkAndRecordCallback(String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            log.warn("回调请求ID为空，拒绝处理");
            return false;
        }

        long currentTime = System.currentTimeMillis();
        
        // 清理过期缓存
        cleanExpiredCache(currentTime);
        
        // 检查是否重复请求
        Long lastProcessTime = callbackCache.get(requestId);
        if (lastProcessTime != null) {
            log.warn("检测到重复的回调请求，requestId: {}, 上次处理时间: {}", 
                    requestId, lastProcessTime);
            return false;
        }
        
        // 检查缓存大小，防止内存溢出
        if (callbackCache.size() >= MAX_CACHE_SIZE) {
            log.warn("回调缓存已满，强制清理");
            callbackCache.clear();
        }
        
        // 记录本次请求
        callbackCache.put(requestId, currentTime);
        log.debug("记录回调请求，requestId: {}, 处理时间: {}", requestId, currentTime);
        
        return true;
    }

    /**
     * 生成回调请求唯一标识
     *
     * @param orderNo     订单号
     * @param timestamp   时间戳
     * @param signature   签名
     * @return 唯一标识
     */
    public String generateCallbackRequestId(String orderNo, String timestamp, String signature) {
        return String.format("%s_%s_%s", orderNo, timestamp, 
                signature != null ? signature.substring(0, Math.min(8, signature.length())) : "");
    }

    /**
     * 验证时间戳是否在有效范围内
     *
     * @param timestamp 时间戳（毫秒）
     * @param maxAgeMinutes 最大允许的时间差（分钟）
     * @return 是否有效
     */
    public boolean isTimestampValid(long timestamp, int maxAgeMinutes) {
        long currentTime = System.currentTimeMillis();
        long maxAge = TimeUnit.MINUTES.toMillis(maxAgeMinutes);
        
        // 检查时间戳是否过旧
        if (currentTime - timestamp > maxAge) {
            log.warn("回调时间戳过旧，当前时间: {}, 回调时间: {}, 最大允许差值: {}分钟", 
                    currentTime, timestamp, maxAgeMinutes);
            return false;
        }
        
        // 检查时间戳是否来自未来（允许5分钟的时钟偏差）
        if (timestamp - currentTime > TimeUnit.MINUTES.toMillis(5)) {
            log.warn("回调时间戳来自未来，当前时间: {}, 回调时间: {}", currentTime, timestamp);
            return false;
        }
        
        return true;
    }

    /**
     * 验证IP地址是否在白名单中
     *
     * @param clientIp IP地址
     * @param allowedIps 允许的IP列表（逗号分隔）
     * @return 是否允许
     */
    public boolean isIpAllowed(String clientIp, String allowedIps) {
        if (allowedIps == null || allowedIps.trim().isEmpty()) {
            // 如果没有配置白名单，则允许所有IP
            return true;
        }
        
        if (clientIp == null || clientIp.trim().isEmpty()) {
            log.warn("客户端IP为空");
            return false;
        }
        
        String[] ipList = allowedIps.split(",");
        for (String allowedIp : ipList) {
            if (clientIp.trim().equals(allowedIp.trim())) {
                return true;
            }
        }
        
        log.warn("客户端IP不在白名单中，IP: {}, 白名单: {}", clientIp, allowedIps);
        return false;
    }

    /**
     * 清理过期的缓存条目
     *
     * @param currentTime 当前时间戳
     */
    private void cleanExpiredCache(long currentTime) {
        callbackCache.entrySet().removeIf(entry -> {
            boolean expired = currentTime - entry.getValue() > CACHE_EXPIRE_TIME;
            if (expired) {
                log.debug("清理过期缓存条目，requestId: {}, 处理时间: {}", 
                        entry.getKey(), entry.getValue());
            }
            return expired;
        });
    }

    /**
     * 获取缓存统计信息
     *
     * @return 缓存大小
     */
    public int getCacheSize() {
        return callbackCache.size();
    }

    /**
     * 清空所有缓存（仅用于测试或紧急情况）
     */
    public void clearAllCache() {
        callbackCache.clear();
        log.info("已清空所有回调缓存");
    }
}
