package com.instser.chat.dto;

import com.instser.chat.entity.User;
import lombok.Data;

/**
 * 一键试用登录结果DTO
 *
 * @author instser
 * @since 2026-08-22
 */
@Data
public class TrialLoginResult {

    /**
     * 体验用户信息
     */
    private User user;

    /**
     * 试用JWT Token
     */
    private String token;

    /**
     * 会话有效期（秒）
     */
    private long sessionSeconds;

    public static TrialLoginResult of(User user, String token, long sessionSeconds) {
        TrialLoginResult result = new TrialLoginResult();
        result.setUser(user);
        result.setToken(token);
        result.setSessionSeconds(sessionSeconds);
        return result;
    }
}
