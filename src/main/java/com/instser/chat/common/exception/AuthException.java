package com.instser.chat.common.exception;

import com.instser.chat.common.result.ResultCode;

/**
 * 认证授权相关异常
 *
 * @author instser
 * @since 2024-01-01
 */
public class AuthException extends BusinessException {

    public AuthException(String message) {
        super(ResultCode.UNAUTHORIZED.getCode(), message);
    }

    public AuthException(String message, Throwable cause) {
        super(message, cause);
    }

    public AuthException(String message, String detail) {
        super(ResultCode.UNAUTHORIZED.getCode(), message, detail);
    }

    /**
     * 用户未登录异常
     */
    public static AuthException notLogin() {
        return new AuthException("用户未登录");
    }

    /**
     * Token无效异常
     */
    public static AuthException invalidToken(String token) {
        return new AuthException("Token无效", "Token: " + token);
    }

    /**
     * Token过期异常
     */
    public static AuthException tokenExpired() {
        return new AuthException("Token已过期");
    }

    /**
     * 用户名或密码错误
     */
    public static AuthException invalidCredentials() {
        return new AuthException("用户名或密码错误");
    }

    /**
     * 账户被禁用
     */
    public static AuthException accountDisabled(String username) {
        return new AuthException("账户已被禁用", "用户名: " + username);
    }

    /**
     * 权限不足异常
     */
    public static AuthException insufficientPermissions(String resource) {
        return new AuthException("权限不足", "资源: " + resource);
    }
}
