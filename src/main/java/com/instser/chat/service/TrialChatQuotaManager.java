package com.instser.chat.service;

import com.instser.chat.common.util.LogUtils;
import com.instser.chat.config.TrialProperties;
import com.instser.chat.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 游客试用对话额度管理器
 *
 * 每个游客账号（guest_ 前缀用户）拥有固定对话额度（trial.chat-limit，默认 10 次），
 * AI 成功回复计 1 次，续登不重置，用完引导注册。计数存 Redis，与套餐额度体系独立：
 * 游客不再消耗套餐次数，只受本额度约束。
 *
 * 与试用 IP 限流一致的策略：Redis 异常时放行（按未使用处理），保证聊天链路可用性。
 *
 * @author instser
 * @since 2026-09-15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrialChatQuotaManager {

    /**
     * 游客已用额度计数 key 前缀（trial:chat:used:{userId}）
     */
    private static final String USED_KEY_PREFIX = "trial:chat:used:";

    /**
     * 游客身份标记缓存 key 前缀（trial:guest:flag:{userId}，"1"/"0"）
     */
    private static final String GUEST_FLAG_PREFIX = "trial:guest:flag:";

    /**
     * 已用计数过期时间（游客账号 48h 未活跃即软删，计数多保留一段时间即可）
     */
    private static final Duration USED_KEY_TTL = Duration.ofDays(7);

    /**
     * 游客身份标记缓存时间（避免每次聊天都查库）
     */
    private static final Duration GUEST_FLAG_TTL = Duration.ofMinutes(10);

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserService userService;
    private final TrialProperties trialProperties;

    /**
     * 判断用户是否为游客（guest_ 前缀用户名），结果短时效缓存
     */
    public boolean isGuest(Long userId) {
        if (userId == null) {
            return false;
        }
        String key = GUEST_FLAG_PREFIX + userId;
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return "1".equals(String.valueOf(cached));
            }
        } catch (Exception e) {
            // 缓存不可用时直接查库，不影响主流程
            log.warn("游客身份缓存读取异常，降级查库，用户ID：{}，原因：{}", userId, e.getMessage());
        }

        User user = userService.getById(userId);
        boolean guest = user != null
                && user.getUsername() != null
                && user.getUsername().startsWith(trialProperties.getUsernamePrefix());

        try {
            redisTemplate.opsForValue().set(key, guest ? "1" : "0", GUEST_FLAG_TTL);
        } catch (Exception e) {
            log.warn("游客身份缓存写入异常，用户ID：{}，原因：{}", userId, e.getMessage());
        }
        return guest;
    }

    /**
     * 获取配置的每个游客对话额度上限
     */
    public int getLimit() {
        return trialProperties.getChatLimit();
    }

    /**
     * 获取游客已使用额度（Redis 异常按 0 处理，放行）
     */
    public int getUsedCount(Long userId) {
        try {
            Object value = redisTemplate.opsForValue().get(USED_KEY_PREFIX + userId);
            if (value == null) {
                return 0;
            }
            return Math.max(0, Integer.parseInt(String.valueOf(value)));
        } catch (Exception e) {
            log.warn("游客试用额度读取异常，按0已用放行，用户ID：{}，原因：{}", userId, e.getMessage());
            return 0;
        }
    }

    /**
     * 获取游客剩余额度
     */
    public int getRemaining(Long userId) {
        return Math.max(0, trialProperties.getChatLimit() - getUsedCount(userId));
    }

    /**
     * 记录一次游客对话额度使用（AI 成功回复后调用）
     * Redis 异常时只记日志不放行失败，避免影响聊天主流程
     */
    public void recordUsage(Long userId) {
        String key = USED_KEY_PREFIX + userId;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, USED_KEY_TTL);
            }
            LogUtils.logUserDebug(log, userId, "游客试用额度", "游客额度扣减成功，已用：{}/{}",
                    count, trialProperties.getChatLimit());
        } catch (Exception e) {
            log.warn("游客试用额度计数异常，本次不计数（放行），用户ID：{}，原因：{}", userId, e.getMessage());
        }
    }
}
