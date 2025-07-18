package com.instser.chat.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * OAuth2审计日志实体类
 *
 * @author instser
 * @since 2025-07-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_oauth2_audit_log")
public class OAuth2AuditLog implements Serializable {

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
     * 操作类型
     */
    @TableField("action")
    private String action;

    /**
     * 操作状态
     */
    @TableField("status")
    private String status;

    /**
     * 客户端IP
     */
    @TableField("client_ip")
    private String clientIp;

    /**
     * 用户代理
     */
    @TableField("user_agent")
    private String userAgent;

    /**
     * 错误代码
     */
    @TableField("error_code")
    private String errorCode;

    /**
     * 错误信息
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * 请求数据
     */
    @TableField("request_data")
    private String requestData;

    /**
     * 响应数据
     */
    @TableField("response_data")
    private String responseData;

    /**
     * 执行时间(毫秒)
     */
    @TableField("execution_time")
    private Integer executionTime;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
