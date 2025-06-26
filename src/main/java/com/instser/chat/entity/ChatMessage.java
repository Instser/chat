package com.instser.chat.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 聊天消息实体类
 *
 * @author instser
 * @since 2024-01-01
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_chat_message")
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 消息ID（UUID）
     */
    @TableField("message_id")
    private String messageId;

    /**
     * 会话ID
     */
    @TableField("session_id")
    private String sessionId;

    /**
     * 消息角色（user/assistant）
     */
    @TableField("role")
    private String role;

    /**
     * 消息内容
     */
    @TableField("content")
    private String content;

    /**
     * 消息类型（text/image/file等）
     */
    @TableField("message_type")
    private String messageType;

    /**
     * 是否包含附件（0-无附件，1-有附件）
     */
    @TableField("has_attachments")
    private Integer hasAttachments;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * AI模型ID
     */
    @TableField("model_id")
    private Long modelId;

    /**
     * 消息token数
     */
    @TableField("token_count")
    private Integer tokenCount;

    /**
     * 消息状态（0-发送中，1-成功，2-失败）
     */
    @TableField("status")
    private Integer status;

    /**
     * 错误信息
     */
    @TableField("error_message")
    private String errorMessage;

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
