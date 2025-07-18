package com.instser.chat.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * OAuth2绑定信息VO
 *
 * @author instser
 * @since 2025-07-16
 */
@Data
public class OAuth2BindingInfo {

    /**
     * OAuth提供商
     */
    private String provider;

    /**
     * 提供商显示名称
     */
    private String providerName;

    /**
     * 第三方用户名
     */
    private String name;

    /**
     * 第三方邮箱
     */
    private String email;

    /**
     * 第三方头像URL
     */
    private String avatar;

    /**
     * 绑定时间
     */
    private LocalDateTime bindTime;

    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginTime;

    /**
     * 登录次数
     */
    private Integer loginCount;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 是否已绑定
     */
    private Boolean isBound;
}
