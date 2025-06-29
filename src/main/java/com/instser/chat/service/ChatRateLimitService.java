package com.instser.chat.service;

/**
 * 聊天频率限制服务接口
 * 
 * @author instser
 * @since 2025-06-29
 */
public interface ChatRateLimitService {

    /**
     * 检查用户聊天频率限制
     * 
     * @param userId 用户ID
     * @return 是否允许聊天
     */
    boolean checkUserChatRateLimit(Long userId);

    /**
     * 检查IP聊天频率限制
     * 
     * @param clientIp 客户端IP
     * @return 是否允许聊天
     */
    boolean checkIpChatRateLimit(String clientIp);

    /**
     * 检查用户套餐聊天限制（每日限制或总次数限制）
     *
     * @param userId 用户ID
     * @return 是否允许聊天
     */
    boolean checkDailyChatLimit(Long userId);

    /**
     * 获取客户端IP地址
     * 
     * @return 客户端IP地址
     */
    String getClientIpAddress();
}
