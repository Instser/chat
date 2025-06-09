package com.instser.chat.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 聊天会话VO
 *
 * @author instser
 * @since 2024-01-01
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ChatSessionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话ID（前端使用的主键）
     */
    private String id;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 用户ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /**
     * AI模型信息
     */
    private AiModelVO model;

    /**
     * 会话状态（0-进行中，1-已结束）
     */
    private Integer status;

    /**
     * 最后一条消息内容
     */
    private String lastMessage;

    /**
     * 最后活跃时间
     */
    private LocalDateTime lastActiveTime;

    /**
     * 累计消耗token数
     */
    private Integer tokenCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间（前端使用updated_at）
     */
    private LocalDateTime updatedAt;
}
