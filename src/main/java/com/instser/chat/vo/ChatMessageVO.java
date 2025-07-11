package com.instser.chat.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 聊天消息VO
 *
 * @author instser
 * @since 2024-01-01
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ChatMessageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息ID（前端使用的主键）
     */
    private String id;

    /**
     * 聊天会话ID
     */
    private String chatId;

    /**
     * 消息角色（user/assistant）
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息类型（text/image/file等）
     */
    private String messageType;

    /**
     * 是否包含附件
     */
    private Boolean hasAttachments;

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
     * 消息token数
     */
    private Integer tokenCount;

    /**
     * 消息状态（0-发送中，1-成功，2-失败）
     */
    private Integer status;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 关联的文件名列表（逗号分隔）
     */
    private String filesName;

    /**
     * 网络搜索结果（JSON字符串）
     * 当has_web_search为1时，包含关联的网络信息记录中的content字段
     */
    private String networkResult;
}
