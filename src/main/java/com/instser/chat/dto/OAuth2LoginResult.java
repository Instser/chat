package com.instser.chat.dto;

import com.instser.chat.entity.User;
import lombok.Data;

/**
 * OAuth2登录结果DTO
 *
 * @author instser
 * @since 2025-07-16
 */
@Data
public class OAuth2LoginResult {

    /**
     * 用户信息
     */
    private User user;

    /**
     * 是否为新用户
     */
    private boolean isNewUser;

    /**
     * 是否为新绑定
     */
    private boolean isNewBinding;

    /**
     * 登录类型
     */
    private LoginType loginType;

    /**
     * 登录类型枚举
     */
    public enum LoginType {
        EXISTING_USER,    // 已存在用户登录
        NEW_USER,         // 新用户注册
        NEW_BINDING       // 已存在用户新绑定
    }

    /**
     * 创建已存在用户登录结果
     */
    public static OAuth2LoginResult existingUser(User user) {
        OAuth2LoginResult result = new OAuth2LoginResult();
        result.setUser(user);
        result.setNewUser(false);
        result.setNewBinding(false);
        result.setLoginType(LoginType.EXISTING_USER);
        return result;
    }

    /**
     * 创建新用户注册结果
     */
    public static OAuth2LoginResult newUser(User user) {
        OAuth2LoginResult result = new OAuth2LoginResult();
        result.setUser(user);
        result.setNewUser(true);
        result.setNewBinding(true);
        result.setLoginType(LoginType.NEW_USER);
        return result;
    }

    /**
     * 创建新绑定结果
     */
    public static OAuth2LoginResult newBinding(User user) {
        OAuth2LoginResult result = new OAuth2LoginResult();
        result.setUser(user);
        result.setNewUser(false);
        result.setNewBinding(true);
        result.setLoginType(LoginType.NEW_BINDING);
        return result;
    }
}
