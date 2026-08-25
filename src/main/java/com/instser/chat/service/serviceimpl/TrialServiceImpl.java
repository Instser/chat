package com.instser.chat.service.serviceimpl;

import com.instser.chat.common.exception.BusinessException;
import com.instser.chat.common.result.ResultCode;
import com.instser.chat.common.util.LogUtils;
import com.instser.chat.config.TrialProperties;
import com.instser.chat.dto.TrialLoginResult;
import com.instser.chat.entity.User;
import com.instser.chat.service.TrialService;
import com.instser.chat.service.UserService;
import com.instser.chat.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * 一键试用服务实现
 * 任何人点击试用即发放 2 小时临时会话，体验内容与普通用户完全一致，仅过期时间不同
 *
 * @author instser
 * @since 2026-08-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrialServiceImpl implements TrialService {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final TrialProperties trialProperties;
    private final RedisTemplate<String, Object> redisTemplate;

    private final SecureRandom secureRandom = new SecureRandom();

    /** 体验用户创建冲突时最大重试次数 */
    private static final int MAX_CREATE_ATTEMPTS = 3;

    @Override
    public TrialLoginResult createTrialSession(Long guestId, String clientIp) {
        if (!trialProperties.isEnabled()) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "一键试用暂未开放");
        }

        // IP 维度限流，防止脚本刷体验账号
        checkIpRateLimit(clientIp);

        // 查找或创建体验用户（携带 guestId 则复用，聊天记录保留）
        User user = findOrCreateGuest(guestId);

        // 与普通登录行为一致：刷新最后登录时间
        userService.updateLastLoginTime(user.getId());

        long sessionSeconds = trialProperties.getSessionHours() * 3600;
        String token = jwtUtil.generateTrialToken(user, sessionSeconds);

        LogUtils.logUserOperation(log, user.getId(), "一键试用", "试用会话创建成功，IP：{}，有效期：{}秒", clientIp, sessionSeconds);
        return TrialLoginResult.of(user, token, sessionSeconds);
    }

    /**
     * IP 维度限流检查（Redis 不可用时放行，保证体验可用性）
     */
    private void checkIpRateLimit(String clientIp) {
        if (clientIp == null || clientIp.isEmpty()) {
            return;
        }

        String key = "trial:ip:limit:" + clientIp + ":" + LocalDate.now();

        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, 2, TimeUnit.DAYS);
            }
            if (count != null && count > trialProperties.getPerIpDailyLimit()) {
                LogUtils.logUserWarning(log, null, "一键试用", "IP限流触发，IP：{}，今日次数：{}", clientIp, count);
                throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "今日试用次数已达上限，请明天再来");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // Redis 异常时放行，避免影响体验入口可用性
            log.warn("试用IP限流Redis异常，放行本次请求，IP：{}，原因：{}", clientIp, e.getMessage());
        }
    }

    /**
     * 查找或创建体验用户
     * 携带 guestId 且用户仍有效（未删除、未禁用、guest 前缀）则复用
     */
    private User findOrCreateGuest(Long guestId) {
        if (guestId != null) {
            User existing = userService.getById(guestId);
            if (isValidGuest(existing)) {
                LogUtils.logUserDebug(log, guestId, "一键试用", "复用体验用户：{}", existing.getUsername());
                return existing;
            }
            LogUtils.logUserDebug(log, guestId, "一键试用", "guestId 无效，创建新体验用户");
        }
        return createGuestWithRetry();
    }

    /**
     * 校验是否为有效的体验用户
     */
    private boolean isValidGuest(User user) {
        return user != null
                && Integer.valueOf(1).equals(user.getStatus())
                && user.getUsername() != null
                && user.getUsername().startsWith(trialProperties.getUsernamePrefix());
    }

    /**
     * 创建体验用户（随机用户名冲突时重试）
     */
    private User createGuestWithRetry() {
        for (int attempt = 1; ; attempt++) {
            String username = trialProperties.getUsernamePrefix() + randomHex(12);
            String email = username + "@" + trialProperties.getEmailDomain();
            String password = randomPassword();
            try {
                User user = userService.registerGuestUser(username, email, password);
                LogUtils.logUserDebug(log, user.getId(), "一键试用", "体验用户创建成功：{}", username);
                return user;
            } catch (BusinessException e) {
                if (attempt >= MAX_CREATE_ATTEMPTS) {
                    throw e;
                }
                log.warn("体验用户创建冲突，重试第{}次，原因：{}", attempt, e.getMessage());
            }
        }
    }

    /**
     * 清理长时间未使用的体验用户（软删除，保留历史聊天数据）
     */
    @Scheduled(cron = "0 10 3 * * ?")
    public void cleanupStaleGuests() {
        if (!trialProperties.isEnabled()) {
            return;
        }

        LocalDateTime threshold = LocalDateTime.now().minusHours(trialProperties.getRetentionHours());
        try {
            boolean updated = userService.lambdaUpdate()
                    .eq(User::getDeleted, 0)
                    .likeRight(User::getUsername, trialProperties.getUsernamePrefix())
                    .lt(User::getLastLoginTime, threshold)
                    .set(User::getDeleted, 1)
                    .set(User::getUpdateTime, LocalDateTime.now())
                    .update();
            log.info("体验用户清理完成，软删除用户数：{}，阈值：{}", updated, threshold);
        } catch (Exception e) {
            log.error("体验用户清理异常：{}", e.getMessage(), e);
        }
    }

    /**
     * 生成随机 hex 字符串
     */
    private String randomHex(int length) {
        byte[] bytes = new byte[(length + 1) / 2];
        secureRandom.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.substring(0, length);
    }

    /**
     * 生成随机密码（体验用户无法使用密码登录）
     */
    private String randomPassword() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
