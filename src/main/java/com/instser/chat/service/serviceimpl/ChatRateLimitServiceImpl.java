package com.instser.chat.service.serviceimpl;

import com.instser.chat.entity.Package;
import com.instser.chat.entity.UserAccount;
import com.instser.chat.service.ChatRateLimitService;
import com.instser.chat.service.PackageService;
import com.instser.chat.service.UserAccountService;
import com.instser.chat.service.UserDailyUsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 聊天频率限制服务实现
 *
 * @author instser
 * @since 2025-06-29
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRateLimitServiceImpl implements ChatRateLimitService {

    // 注入现有的聊天限制相关服务
    private final UserAccountService userAccountService;
    private final PackageService packageService;
    private final UserDailyUsageService userDailyUsageService;

    // 频率限制缓存：key -> 时间戳列表
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, Boolean>> rateLimitCache = new ConcurrentHashMap<>();

    // 频率限制配置
    private static final int USER_RATE_LIMIT_PER_MINUTE = 20; // 用户每分钟最多20次聊天
    private static final int IP_RATE_LIMIT_PER_MINUTE = 30;   // IP每分钟最多30次聊天

    @Override
    public boolean checkUserChatRateLimit(Long userId) {
        if (userId == null) {
            log.warn("用户ID为空，拒绝聊天请求");
            return false;
        }

        String key = "chat:rate_limit:user:" + userId;
        boolean allowed = checkRateLimit(key, USER_RATE_LIMIT_PER_MINUTE, 60);
        
        if (!allowed) {
            log.warn("用户聊天频率超限，用户ID: {}, 限制: {}次/分钟", userId, USER_RATE_LIMIT_PER_MINUTE);
        }
        
        return allowed;
    }

    @Override
    public boolean checkIpChatRateLimit(String clientIp) {
        if (clientIp == null || clientIp.trim().isEmpty()) {
            log.warn("客户端IP为空，允许聊天请求");
            return true; // IP为空时不限制
        }

        String key = "chat:rate_limit:ip:" + clientIp;
        boolean allowed = checkRateLimit(key, IP_RATE_LIMIT_PER_MINUTE, 60);
        
        if (!allowed) {
            log.warn("IP聊天频率超限，IP: {}, 限制: {}次/分钟", clientIp, IP_RATE_LIMIT_PER_MINUTE);
        }
        
        return allowed;
    }

    @Override
    public boolean checkDailyChatLimit(Long userId) {
        if (userId == null) {
            log.warn("用户ID为空，拒绝聊天请求");
            return false;
        }

        try {
            // 获取用户账户信息
            UserAccount userAccount = userAccountService.getUserAccount(userId);
            if (userAccount == null || userAccount.getPackageId() == null) {
                log.warn("用户{}没有有效套餐，拒绝聊天请求", userId);
                return false;
            }

            // 获取套餐信息
            Package packageInfo = packageService.getPackageById(userAccount.getPackageId());
            if (packageInfo == null) {
                log.warn("用户{}的套餐{}不存在，拒绝聊天请求", userId, userAccount.getPackageId());
                return false;
            }

            // 根据套餐的限制类型进行检查
            if ("daily".equals(packageInfo.getLimitType())) {
                // 每日限制模式：使用现有的UserDailyUsageService，默认使用模型ID 1
                String modelId = "1"; // 默认使用模型ID 1 (STANDARD模型)
                boolean canChat = userDailyUsageService.canChatWithModel(userId, userAccount.getPackageId(), modelId);
                if (!canChat) {
                    int remaining = userDailyUsageService.getRemainingModelChats(userId, userAccount.getPackageId(), modelId);
                    log.warn("用户{}每日聊天次数已达到套餐限制，模型ID: {}, 剩余次数: {}", userId, modelId, remaining);
                }
                return canChat;
            } else if ("total".equals(packageInfo.getLimitType())) {
                // 总次数限制模式：检查剩余次数
                int remaining = userAccount.getChatCountRemaining() != null ? userAccount.getChatCountRemaining() : 0;
                if (remaining <= 0) {
                    log.warn("用户{}套餐聊天次数已用完，剩余次数: {}", userId, remaining);
                    return false;
                }
                return true;
            } else {
                // 无限制模式
                log.debug("用户{}使用无限制套餐，允许聊天", userId);
                return true;
            }
        } catch (Exception e) {
            log.error("检查用户{}每日聊天限制时发生异常", userId, e);
            return false; // 异常时拒绝请求，保证系统安全
        }
    }

    @Override
    public String getClientIpAddress() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            
            HttpServletRequest request = attributes.getRequest();
            
            // 尝试从各种头部获取真实IP
            String ip = request.getHeader("X-Forwarded-For");
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For可能包含多个IP，取第一个
                return ip.split(",")[0].trim();
            }
            
            ip = request.getHeader("X-Real-IP");
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }
            
            ip = request.getHeader("Proxy-Client-IP");
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }
            
            ip = request.getHeader("WL-Proxy-Client-IP");
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }
            
            // 最后使用RemoteAddr
            return request.getRemoteAddr();
            
        } catch (Exception e) {
            log.warn("获取客户端IP地址失败", e);
            return null;
        }
    }

    /**
     * 检查频率限制
     *
     * @param key 限制键
     * @param maxRequests 最大请求数
     * @param timeWindowSeconds 时间窗口（秒）
     * @return 是否允许请求
     */
    private boolean checkRateLimit(String key, int maxRequests, int timeWindowSeconds) {
        if (key == null || key.trim().isEmpty()) {
            log.warn("频率限制键为空");
            return false;
        }

        long currentTime = System.currentTimeMillis();
        long timeWindow = TimeUnit.SECONDS.toMillis(timeWindowSeconds);

        // 获取或创建该键的请求记录
        ConcurrentHashMap<Long, Boolean> requests = rateLimitCache.computeIfAbsent(key, k -> new ConcurrentHashMap<>());

        // 清理过期的请求记录
        requests.entrySet().removeIf(entry -> currentTime - entry.getKey() > timeWindow);

        // 记录本次请求
        requests.put(currentTime, true);

        // 检查当前请求数是否超限
        if (requests.size() > maxRequests) {
            log.debug("聊天频率限制触发，键: {}, 当前请求数: {}, 最大允许: {}, 时间窗口: {}秒",
                    key, requests.size(), maxRequests, timeWindowSeconds);
            return false;
        }

        log.debug("聊天频率限制检查通过，键: {}, 当前请求数: {}/{}, 时间窗口: {}秒",
                key, requests.size(), maxRequests, timeWindowSeconds);

        return true;
    }



    /**
     * 清理过期的缓存数据（可以通过定时任务调用）
     */
    public void cleanExpiredCache() {
        try {
            // 清理频率限制缓存中的过期数据
            long currentTime = System.currentTimeMillis();
            long oneHourAgo = currentTime - TimeUnit.HOURS.toMillis(1);
            
            rateLimitCache.entrySet().removeIf(entry -> {
                ConcurrentHashMap<Long, Boolean> requests = entry.getValue();
                requests.entrySet().removeIf(requestEntry -> requestEntry.getKey() < oneHourAgo);
                return requests.isEmpty();
            });

            log.debug("聊天频率限制缓存清理完成");
        } catch (Exception e) {
            log.error("清理聊天频率限制缓存失败", e);
        }
    }

    /**
     * 获取缓存统计信息
     */
    public String getCacheStats() {
        return String.format("RateLimit缓存: %d个键", rateLimitCache.size());
    }
}
