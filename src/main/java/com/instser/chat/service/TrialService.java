package com.instser.chat.service;

import com.instser.chat.dto.TrialLoginResult;

/**
 * 一键试用服务
 *
 * @author instser
 * @since 2026-08-22
 */
public interface TrialService {

    /**
     * 创建试用会话：IP限流检查 → 查找/创建体验用户 → 签发短时效JWT
     *
     * @param guestId  体验用户ID（可选，复用同一个体验用户以保留聊天记录）
     * @param clientIp 客户端IP（限流维度）
     * @return 试用登录结果（用户 + 短时效Token）
     */
    TrialLoginResult createTrialSession(Long guestId, String clientIp);
}
