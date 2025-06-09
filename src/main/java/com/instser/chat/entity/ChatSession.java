package com.instser.chat.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 聊天会话实体类
 *
 * @author instser
 * @since 2024-01-01
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_chat_session")
public class ChatSession implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 会话ID（UUID）
     */
    @TableField("session_id")
    private String sessionId;

    /**
     * 会话标题
     */
    @TableField("title")
    private String title;

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
     * 会话状态（0-进行中，1-已结束）
     */
    @TableField("status")
    private Integer status;

    /**
     * 最后一条消息内容
     */
    @TableField("last_message")
    private String lastMessage;

    /**
     * 最后活跃时间
     */
    @TableField("last_active_time")
    private LocalDateTime lastActiveTime;

    /**
     * 累计消耗token数
     */
    @TableField("token_count")
    private Integer tokenCount;

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
