package com.instser.chat.service;

import com.instser.chat.dto.OAuth2LoginResult;
import com.instser.chat.dto.OAuth2UserInfo;
import com.instser.chat.vo.OAuth2BindingInfo;

import java.util.List;

/**
 * OAuth2服务接口
 *
 * @author instser
 * @since 2025-07-16
 */
public interface OAuth2Service {

    /**
     * 获取授权URL
     */
    String getAuthorizationUrl(String provider, String state);

    /**
     * 获取用户信息
     */
    OAuth2UserInfo getUserInfo(String provider, String code);

    /**
     * 处理登录
     */
    OAuth2LoginResult processLogin(OAuth2UserInfo userInfo, String clientIp, String userAgent);

    /**
     * 绑定OAuth账号
     */
    void bindOAuthAccount(Long userId, String provider, String code, String clientIp);

    /**
     * 解绑OAuth账号
     */
    void unbindOAuthAccount(Long userId, String provider);

    /**
     * 获取用户OAuth绑定列表
     */
    List<OAuth2BindingInfo> getUserOAuthBindings(Long userId);



    /**
     * 检查提供商是否支持
     */
    boolean isProviderSupported(String provider);

    /**
     * 获取支持的提供商列表
     */
    List<String> getSupportedProviders();
}
