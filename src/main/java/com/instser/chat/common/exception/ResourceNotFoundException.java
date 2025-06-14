package com.instser.chat.common.exception;

/**
 * 资源未找到异常
 *
 * @author instser
 * @since 2024-01-01
 */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String message) {
        super(404, message);
    }

    public ResourceNotFoundException(String message, String detail) {
        super(404, message, detail);
    }

    /**
     * 用户未找到
     */
    public static ResourceNotFoundException userNotFound(Long userId) {
        return new ResourceNotFoundException("用户不存在", "用户ID: " + userId);
    }

    /**
     * 订单未找到
     */
    public static ResourceNotFoundException orderNotFound(String orderId) {
        return new ResourceNotFoundException("订单不存在", "订单ID: " + orderId);
    }

    /**
     * 套餐未找到
     */
    public static ResourceNotFoundException packageNotFound(Long packageId) {
        return new ResourceNotFoundException("套餐不存在", "套餐ID: " + packageId);
    }

    /**
     * AI模型未找到
     */
    public static ResourceNotFoundException aiModelNotFound(Long modelId) {
        return new ResourceNotFoundException("AI模型不存在", "模型ID: " + modelId);
    }

    /**
     * 聊天会话未找到
     */
    public static ResourceNotFoundException chatSessionNotFound(Long sessionId) {
        return new ResourceNotFoundException("聊天会话不存在", "会话ID: " + sessionId);
    }

    /**
     * 聊天消息未找到
     */
    public static ResourceNotFoundException chatMessageNotFound(Long messageId) {
        return new ResourceNotFoundException("聊天消息不存在", "消息ID: " + messageId);
    }
}
