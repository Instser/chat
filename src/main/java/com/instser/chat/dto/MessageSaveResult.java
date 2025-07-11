package com.instser.chat.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * 消息保存结果DTO
 * 包含消息ID和计算的Token数量
 *
 * @author instser
 * @since 2025-07-02
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MessageSaveResult {

    /**
     * 消息数据库ID
     */
    private Long messageId;

    /**
     * 消息UUID
     */
    private String messageUuid;

    /**
     * 计算的Token数量
     */
    private Integer tokenCount;

    /**
     * 创建成功结果
     *
     * @param messageId  消息数据库ID
     * @param tokenCount Token数量
     * @return 保存结果
     */
    public static MessageSaveResult success(Long messageId, Integer tokenCount) {
        return new MessageSaveResult(messageId, null, tokenCount);
    }

    /**
     * 创建成功结果（包含UUID）
     *
     * @param messageId  消息数据库ID
     * @param messageUuid 消息UUID
     * @param tokenCount Token数量
     * @return 保存结果
     */
    public static MessageSaveResult success(Long messageId, String messageUuid, Integer tokenCount) {
        return new MessageSaveResult(messageId, messageUuid, tokenCount);
    }

    /**
     * 创建失败结果
     *
     * @return 保存结果
     */
    public static MessageSaveResult failure() {
        return new MessageSaveResult(null, null, 0);
    }

    /**
     * 判断是否保存成功
     *
     * @return true-成功，false-失败
     */
    public boolean isSuccess() {
        return messageId != null;
    }
}
