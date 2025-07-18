package com.instser.chat.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * OAuth2用户绑定实体类
 *
 * @author instser
 * @since 2025-07-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_user_oauth")
public class UserOAuth implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * OAuth提供商
     */
    @TableField("provider")
    private String provider;

    /**
     * 第三方用户ID
     */
    @TableField("provider_id")
    private String providerId;

    /**
     * 第三方邮箱
     */
    @TableField("email")
    private String email;

    /**
     * 第三方用户名
     */
    @TableField("name")
    private String name;

    /**
     * 第三方头像URL
     */
    @TableField("avatar")
    private String avatar;

    /**
     * 访问令牌(AES加密)
     */
    @TableField("access_token")
    private String accessToken;

    /**
     * 刷新令牌(AES加密)
     */
    @TableField("refresh_token")
    private String refreshToken;

    /**
     * 令牌过期时间
     */
    @TableField("expires_at")
    private LocalDateTime expiresAt;

    /**
     * 最后登录时间
     */
    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;

    /**
     * 登录次数
     */
    @TableField("login_count")
    private Integer loginCount;

    /**
     * 状态:1=正常,0=禁用
     */
    @TableField("status")
    private Integer status;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标志（0-未删除，1-已删除）
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
