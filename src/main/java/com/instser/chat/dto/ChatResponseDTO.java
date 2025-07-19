package com.instser.chat.dto;

import lombok.Data;

/**
 * 聊天响应DTO
 *
 * @author instser
 * @since 2025-06-25
 */
@Data
public class ChatResponseDTO {

    /**
     * 是否有内容发送
     */
    private boolean hasContentSent;

    /**
     * 是否为真实AI响应
     */
    private boolean isRealAiResponse;

    /**
     * AI响应内容
     */
    private String aiResponseContent;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 是否需要记录使用次数
     */
    private boolean shouldRecordUsage;

    /**
     * 是否生成了标题
     */
    private boolean titleGenerated;

    /**
     * 生成的标题
     */
    private String generatedTitle;

    /**
     * 是否被中断
     */
    private boolean interrupted;

    /**
     * 是否已通过续写完成保存
     */
    private boolean savedByContinuation;

    public ChatResponseDTO() {
        this.success = true;
        this.hasContentSent = false;
        this.isRealAiResponse = false;
        this.shouldRecordUsage = false;
        this.titleGenerated = false;
        this.aiResponseContent = "";
        this.interrupted = false;
        this.savedByContinuation = false;
    }

    /**
     * 创建成功响应
     */
    public static ChatResponseDTO success(boolean hasContentSent, boolean isRealAiResponse, String aiResponseContent) {
        ChatResponseDTO response = new ChatResponseDTO();
        response.setSuccess(true);
        response.setHasContentSent(hasContentSent);
        response.setRealAiResponse(isRealAiResponse);
        response.setAiResponseContent(aiResponseContent != null ? aiResponseContent : "");
        response.setShouldRecordUsage(isRealAiResponse);
        return response;
    }

    /**
     * 创建失败响应
     */
    public static ChatResponseDTO failure(String errorMessage) {
        ChatResponseDTO response = new ChatResponseDTO();
        response.setSuccess(false);
        response.setErrorMessage(errorMessage);
        response.setHasContentSent(false);
        response.setRealAiResponse(false);
        response.setShouldRecordUsage(false);
        return response;
    }

    /**
     * 创建错误响应（有内容发送但不是真实AI响应）
     */
    public static ChatResponseDTO errorWithContent(String errorContent) {
        ChatResponseDTO response = new ChatResponseDTO();
        response.setSuccess(false);
        response.setHasContentSent(true);
        response.setRealAiResponse(false);
        response.setAiResponseContent(errorContent);
        response.setErrorMessage(errorContent); // 同时设置错误消息，确保getErrorMessage()不返回null
        response.setShouldRecordUsage(false);
        return response;
    }

    /**
     * 创建中断响应
     */
    public static ChatResponseDTO interrupted(boolean hasContentSent, String partialContent) {
        ChatResponseDTO response = new ChatResponseDTO();
        response.setSuccess(true);
        response.setHasContentSent(hasContentSent);
        response.setRealAiResponse(false); // 中断响应不应被当作真实AI响应，避免重复保存
        response.setAiResponseContent(partialContent != null ? partialContent : "");
        response.setShouldRecordUsage(hasContentSent); // 如果有内容发送，则记录使用次数
        response.setInterrupted(true);
        return response;
    }
}
