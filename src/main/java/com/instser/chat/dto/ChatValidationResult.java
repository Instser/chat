package com.instser.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天验证结果封装类
 * 用于统一管理验证过程中的结果和上下文
 * 
 * @author instser
 * @since 2025-09-05
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatValidationResult {
    
    /**
     * 验证是否成功
     */
    private boolean success;
    
    /**
     * 错误消息
     */
    private String errorMessage;
    
    /**
     * 错误响应对象（用于直接返回）
     */
    private ChatResponseDTO errorResponse;
    
    /**
     * 验证过程中的用户上下文（避免重复查询）
     */
    private Object userContext;
    
    // 静态工厂方法
    
    /**
     * 创建成功的验证结果
     */
    public static ChatValidationResult success() {
        return new ChatValidationResult(true, null, null, null);
    }
    
    /**
     * 创建成功的验证结果，带用户上下文
     */
    public static ChatValidationResult success(Object userContext) {
        return new ChatValidationResult(true, null, null, userContext);
    }
    
    /**
     * 创建失败的验证结果
     */
    public static ChatValidationResult failure(String errorMessage) {
        return new ChatValidationResult(false, errorMessage, null, null);
    }
    
    /**
     * 创建失败的验证结果，带错误响应
     */
    public static ChatValidationResult failure(String errorMessage, ChatResponseDTO errorResponse) {
        return new ChatValidationResult(false, errorMessage, errorResponse, null);
    }
    
    /**
     * 创建失败的验证结果，只有错误响应
     */
    public static ChatValidationResult failure(ChatResponseDTO errorResponse) {
        return new ChatValidationResult(false, null, errorResponse, null);
    }
    
    /**
     * 检查验证是否成功
     */
    public boolean isValid() {
        return success;
    }
    
    /**
     * 检查验证是否失败
     */
    public boolean isFailed() {
        return !success;
    }
}