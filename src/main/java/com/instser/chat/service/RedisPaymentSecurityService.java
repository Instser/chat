package com.instser.chat.service;

import com.instser.chat.common.exception.PaymentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.Duration;

/**
 * Redis支付安全服务
 * 基于Redis实现分布式支付安全功能
 *
 * @author instser
 * @since 2025-06-30
 */
@Slf4j
@Service
public class RedisPaymentSecurityService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 支付安全配置
    @Value("${payment.security.frequency.max-count:5}")
    private int maxFrequencyCount;

    @Value("${payment.security.frequency.time-window:10}")
    private int frequencyTimeWindow;

    @Value("${payment.security.callback.expire-time:60}")
    private int callbackExpireTime;

    @Value("${payment.security.suspicious.threshold:3}")
    private int suspiciousThreshold;

    @Value("${payment.security.suspicious.expire-days:7}")
    private int suspiciousExpireDays;

    /**
     * Redis可用性检查（支付安全强依赖）
     */
    public void assertRedisAvailable() {
        try {
            redisTemplate.opsForValue().get("health_check");
        } catch (Exception e) {
            throw buildRedisUnavailableException("Redis健康检查", e);
        }
    }

    private PaymentException buildRedisUnavailableException(String action, Exception e) {
        log.error("Redis不可用，{}失败", action, e);
        return new PaymentException("支付系统暂时不可用，请稍后再试");
    }

    /**
     * 检查支付频率限制
     * 使用Redis滑动窗口计数器实现
     *
     * @param userId 用户ID
     * @return 是否通过频率检查
     */
    public boolean checkPaymentFrequency(Long userId) {
        if (userId == null) {
            log.warn("用户ID为空，拒绝支付请求");
            return false;
        }

        try {
            String key = "payment:freq:" + userId;
            String timeWindow = String.valueOf(System.currentTimeMillis() / (frequencyTimeWindow * 60 * 1000)); // 时间窗口
            String fullKey = key + ":" + timeWindow;

            // 使用Redis INCR原子操作
            Long count = redisTemplate.opsForValue().increment(fullKey);
            
            // 设置过期时间
            if (count == 1) {
                redisTemplate.expire(fullKey, Duration.ofMinutes(frequencyTimeWindow));
            }

            boolean allowed = count <= maxFrequencyCount;
            
            if (!allowed) {
                log.warn("用户{}支付频率过高，当前次数: {}, 最大允许: {}", userId, count, maxFrequencyCount);
            } else {
                log.debug("用户{}支付频率检查通过，当前次数: {}/{}", userId, count, maxFrequencyCount);
            }

            return allowed;

        } catch (Exception e) {
            throw buildRedisUnavailableException("支付频率检查", e);
        }
    }

    /**
     * 防重放攻击检查
     * 检查回调ID是否已经处理过
     *
     * @param callbackId 回调ID
     * @return true表示已处理过（重放攻击），false表示首次处理
     */
    public boolean isCallbackProcessed(String callbackId) {
        if (callbackId == null || callbackId.trim().isEmpty()) {
            log.warn("回调ID为空");
            return true; // 拒绝处理
        }

        try {
            String key = "payment:callback:" + callbackId;
            Boolean exists = redisTemplate.hasKey(key);

            if (exists != null && exists) {
                log.warn("检测到重放攻击，回调ID: {}", callbackId);
                return true; // 已处理过
            }

            // 标记为已处理
            redisTemplate.opsForValue().set(key, System.currentTimeMillis(), Duration.ofMinutes(callbackExpireTime));
            log.debug("回调ID: {} 标记为已处理", callbackId);
            return false; // 首次处理

        } catch (Exception e) {
            throw buildRedisUnavailableException("回调防重放校验", e);
        }
    }

    /**
     * 标记回调已处理（用于幂等性）
     *
     * @param callbackId 回调ID
     */
    public void markCallbackAsProcessed(String callbackId) {
        if (callbackId == null || callbackId.trim().isEmpty()) {
            return;
        }
        try {
            String key = "payment:callback:" + callbackId;
            redisTemplate.opsForValue().set(key, System.currentTimeMillis(), Duration.ofMinutes(callbackExpireTime));
            log.debug("回调ID: {} 标记为已处理", callbackId);
        } catch (Exception e) {
            throw buildRedisUnavailableException("回调幂等标记", e);
        }
    }

    /**
     * 记录可疑支付行为
     *
     * @param userId 用户ID
     * @param reason 可疑原因
     */
    public void recordSuspiciousBehavior(Long userId, String reason) {
        if (userId == null) {
            return;
        }

        try {
            String key = "payment:suspicious:" + userId;
            String value = reason + ":" + System.currentTimeMillis();

            // 使用List存储多次可疑行为
            redisTemplate.opsForList().leftPush(key, value);
            redisTemplate.expire(key, Duration.ofDays(suspiciousExpireDays));

            // 检查可疑行为次数
            Long count = redisTemplate.opsForList().size(key);
            log.warn("记录用户{}可疑行为: {}, 当前次数: {}", userId, reason, count);

            // 如果可疑行为超过阈值，可以进一步处理（如加入黑名单）
            if (count != null && count >= suspiciousThreshold) {
                log.error("用户{}可疑行为次数过多: {}, 建议加入黑名单", userId, count);
                // 这里可以调用其他服务进行进一步处理
            }

        } catch (Exception e) {
            log.error("记录可疑行为异常，用户ID: {}", userId, e);
        }
    }

    /**
     * 检查用户是否有可疑行为
     *
     * @param userId 用户ID
     * @return 是否通过可疑行为检查
     */
    public boolean checkSuspiciousBehavior(Long userId) {
        if (userId == null) {
            return true;
        }

        try {
            String key = "payment:suspicious:" + userId;
            Long count = redisTemplate.opsForList().size(key);

            if (count != null && count >= suspiciousThreshold) {
                log.warn("用户{}可疑行为次数过多: {}", userId, count);
                return false;
            }

            return true;

        } catch (Exception e) {
            throw buildRedisUnavailableException("可疑行为检查", e);
        }
    }

    /**
     * 获取用户支付频率信息
     *
     * @param userId 用户ID
     * @return 当前时间窗口内的支付次数
     */
    public int getUserPaymentFrequency(Long userId) {
        if (userId == null) {
            return 0;
        }

        try {
            String key = "payment:freq:" + userId;
            String timeWindow = String.valueOf(System.currentTimeMillis() / (frequencyTimeWindow * 60 * 1000));
            String fullKey = key + ":" + timeWindow;

            Object count = redisTemplate.opsForValue().get(fullKey);
            return count != null ? Integer.parseInt(count.toString()) : 0;

        } catch (Exception e) {
            log.error("获取用户支付频率异常，用户ID: {}", userId, e);
            return 0;
        }
    }

    /**
     * 获取用户可疑行为次数
     *
     * @param userId 用户ID
     * @return 可疑行为次数
     */
    public int getUserSuspiciousCount(Long userId) {
        if (userId == null) {
            return 0;
        }

        try {
            String key = "payment:suspicious:" + userId;
            Long count = redisTemplate.opsForList().size(key);
            return count != null ? count.intValue() : 0;

        } catch (Exception e) {
            log.error("获取用户可疑行为次数异常，用户ID: {}", userId, e);
            return 0;
        }
    }

    /**
     * 清除用户的可疑行为记录
     *
     * @param userId 用户ID
     */
    public void clearSuspiciousBehavior(Long userId) {
        if (userId == null) {
            return;
        }

        try {
            String key = "payment:suspicious:" + userId;
            redisTemplate.delete(key);
            log.info("清除用户{}的可疑行为记录", userId);

        } catch (Exception e) {
            log.error("清除可疑行为记录异常，用户ID: {}", userId, e);
        }
    }

    /**
     * 获取用户支付会话
     * 防止同一用户同时发起多个支付
     *
     * @param userId 用户ID
     * @return 是否成功获取会话锁
     */
    public boolean acquirePaymentSession(Long userId) {
        if (userId == null) {
            return false;
        }

        try {
            String key = "payment:session:" + userId;
            // ✅ 缩短过期时间到5分钟，减少死锁影响
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "locked", Duration.ofMinutes(5));

            if (acquired != null && acquired) {
                log.debug("用户{}成功获取支付会话锁，5分钟后自动过期", userId);
                return true;
            } else {
                log.warn("用户{}支付会话锁获取失败，可能存在并发支付", userId);
                return false;
            }

        } catch (Exception e) {
            throw buildRedisUnavailableException("获取支付会话锁", e);
        }
    }

    /**
     * 释放用户支付会话
     *
     * @param userId 用户ID
     */
    public void releasePaymentSession(Long userId) {
        if (userId == null) {
            return;
        }

        try {
            String key = "payment:session:" + userId;
            redisTemplate.delete(key);
            log.debug("用户{}释放支付会话锁", userId);

        } catch (Exception e) {
            log.error("释放支付会话锁异常，用户ID: {}", userId, e);
        }
    }

    /**
     * 检查用户是否有活跃的支付会话
     *
     * @param userId 用户ID
     * @return 是否有活跃会话
     */
    public boolean hasActivePaymentSession(Long userId) {
        if (userId == null) {
            return false;
        }

        try {
            String key = "payment:session:" + userId;
            Boolean exists = redisTemplate.hasKey(key);
            return exists != null && exists;

        } catch (Exception e) {
            throw buildRedisUnavailableException("检查支付会话", e);
        }
    }

    /**
     * 检查支付请求是否已处理（防重放攻击）
     * 用于防止用户端支付请求的重放攻击
     *
     * @param requestId 支付请求唯一标识
     * @param userId 用户ID（用于记录可疑行为）
     * @return true=已处理过(重放攻击), false=首次处理(允许)
     */
    public boolean isPaymentRequestProcessed(String requestId, Long userId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            log.warn("支付请求ID为空，拒绝处理");
            return true; // 拒绝处理
        }

        try {
            String key = "payment:request:" + requestId;
            // 例如: "payment:request:a1b2c3d4e5f6789012345678901234ab"

            // 检查Redis中是否已存在该键
            Boolean exists = redisTemplate.hasKey(key);

            if (exists != null && exists) {
                // 检测到重放攻击！相同的支付请求被重复发送
                log.warn("检测到支付请求重放攻击，请求ID: {}, 用户ID: {}", requestId, userId);

                // 记录可疑行为
                if (userId != null) {
                    recordSuspiciousBehavior(userId, "支付请求重放攻击");
                }

                return true; // 返回true表示已处理过，拒绝重复处理
            }

            // 首次处理该支付请求，将其标记为已处理
            long processTime = System.currentTimeMillis();
            redisTemplate.opsForValue().set(key, processTime, Duration.ofMinutes(30));

            // 设置30分钟过期时间，平衡防重放效果和存储效率
            log.debug("支付请求ID: {} 标记为已处理，过期时间: 30分钟", requestId);
            return false; // 返回false表示首次处理，允许继续处理

        } catch (Exception e) {
            throw buildRedisUnavailableException("支付请求防重放校验", e);
        }
    }

    /**
     * 生成支付请求唯一标识
     * 基于关键业务参数生成，确保相同业务请求有相同ID
     *
     * @param userId 用户ID
     * @param productId 商品ID
     * @param amount 支付金额
     * @param paymentType 支付类型
     * @return 支付请求唯一标识
     */
    public String generatePaymentRequestId(Long userId, String productId, String amount, String paymentType) {
        // 关键参数：用户ID + 商品ID + 金额 + 支付类型 + 时间戳（分钟级别）
        String source = userId +
                       (productId != null ? productId : "") +
                       (amount != null ? amount : "") +
                       (paymentType != null ? paymentType : "") +
                       // 使用分钟级时间戳，允许同一分钟内的重试
                       (System.currentTimeMillis() / (60 * 1000));

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(source.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("生成支付请求ID异常，使用备选方案", e);
            return source.hashCode() + "";
        }
    }

    /**
     * 清除支付请求记录（用于测试或特殊情况）
     *
     * @param requestId 支付请求ID
     */
    public void clearPaymentRequest(String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return;
        }

        try {
            String key = "payment:request:" + requestId;
            redisTemplate.delete(key);
            log.debug("清除支付请求记录: {}", requestId);

        } catch (Exception e) {
            log.error("清除支付请求记录异常，请求ID: {}", requestId, e);
        }
    }
}
