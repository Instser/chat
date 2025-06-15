package com.instser.chat.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户实体类
 *
 * @author instser
 * @since 2024-01-01
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_user")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户名
     */
    @TableField("username")
    private String username;

    /**
     * 用户的谷歌邮箱
     */
    @TableField("email")
    private String email;

    /**
     * 密码（加密存储）
     */
    @TableField("password")
    private String password;

    /**
     * 头像URL
     */
    @TableField("avatar")
    private String avatar;

    /**
     * 角色（user/admin）
     */
    @TableField("role")
    private String role;

    /**
     * 状态（0-禁用，1-启用）
     */
    @TableField("status")
    private Integer status;

    /**
     * 支付宝ID，可为空，创建账号后续填充
     */
    @TableField("alipay_id")
    private String alipayId;

    /**
     * 最后登录时间
     */
    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

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
