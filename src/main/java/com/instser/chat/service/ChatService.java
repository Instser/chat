package com.instser.chat.service;

import com.instser.chat.dto.ChatRequestDTO;
import com.instser.chat.dto.ChatResponseDTO;
import com.instser.chat.service.stream.ChatStreamSink;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

/**
 * 聊天业务服务接口
 *
 * @author instser
 * @since 2025-06-25
 */
public interface ChatService {

    /**
     * 处理流式聊天请求
     *
     * @param request 聊天请求DTO
     * @param authentication 认证信息
     * @param response 流写出端口
     * @return 聊天响应结果
     */
    ChatResponseDTO processStreamChat(ChatRequestDTO request, Authentication authentication, ChatStreamSink sink);

    /**
     * 验证聊天请求参数
     *
     * @param request 聊天请求DTO
     * @return 验证结果，null表示验证通过，非null表示错误信息
     */
    String validateChatRequest(ChatRequestDTO request);

    /**
     * 验证用户认证和权限
     *
     * @param authentication 认证信息
     * @param userId 用户ID
     * @return 验证结果，null表示验证通过，非null表示错误信息
     */
    String validateUserPermission(Authentication authentication, Long userId);

    /**
     * 保存用户消息到数据库
     *
     * @param messages 消息列表
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @return 保存的消息ID，失败时返回null
     */
    String saveUserMessage(List<Map<String, String>> messages, String sessionId, Long userId);

    /**
     * 保存用户消息到数据库（支持指定模型ID）
     *
     * @param messages 消息列表
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @param modelId 模型ID
     * @return 保存的消息ID，失败时返回null
     */
    String saveUserMessage(List<Map<String, String>> messages, String sessionId, Long userId, Long modelId);



    /**
     * 保存AI响应到数据库
     *
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @param modelId 模型ID
     * @param content AI响应内容
     */
    void saveAiResponse(String sessionId, Long userId, Long modelId, String content);

    /**
     * 保存错误响应到数据库
     *
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @param modelId 模型ID
     * @param errorContent 错误内容
     */
    void saveErrorResponse(String sessionId, Long userId, Long modelId, String errorContent);

    /**
     * 生成会话标题
     *
     * @param sessionId 会话ID
     * @param messages 消息列表
     * @return 生成的标题，失败时返回null
     */
    String generateSessionTitle(String sessionId, List<Map<String, String>> messages);

    /**
     * 处理重新生成AI回复请求
     *
     * @param sessionId 会话ID（前端传入的chatId参数）
     * @param messageId 要重新生成的AI消息ID
     * @param modelId 模型ID（可选，为null时使用会话原有模型）
     * @param network 是否启用网络搜索（可选，为null时默认禁用）
     * @param authentication 认证信息
     * @param response 流写出端口
     */
    void processRetryChat(String sessionId, String messageId, Long modelId, Boolean network, Authentication authentication, ChatStreamSink sink);

    /**
     * 处理编辑用户消息并重新生成AI回复请求
     *
     * @param sessionId 会话ID（前端传入的chatId参数）
     * @param messageId 要编辑的用户消息ID
     * @param newContent 新的消息内容
     * @param filesId 文件ID列表（逗号分隔，可选）
     * @param modelId 模型ID（可选，为null时使用会话原有模型）
     * @param network 是否启用网络搜索（可选，为null时默认禁用）
     * @param authentication 认证信息
     * @param response 流写出端口
     */
    void processEditResendChat(String sessionId, String messageId, String newContent, String filesId, Long modelId, Boolean network, Authentication authentication, ChatStreamSink sink);

    /**
     * 检查是否为首次AI响应
     *
     * @param messages 消息列表
     * @return true表示是首次AI响应
     */
    boolean isFirstAiResponse(List<Map<String, String>> messages);

    /**
     * 处理文件内容并附加到用户消息中
     *
     * @param messages 消息列表
     * @param filesId 文件ID列表（逗号分隔）
     * @param userId 用户ID
     * @return 处理后的消息列表
     */
    List<Map<String, String>> processFilesContent(List<Map<String, String>> messages, String filesId, Long userId);

    /**
     * 保存部分AI响应到数据库（用于中断场景）
     *
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @param modelId 模型ID
     * @param partialContent 部分AI响应内容
     * @param isInterrupted 是否因中断而保存
     */
    void savePartialAiResponse(String sessionId, Long userId, Long modelId, String partialContent, boolean isInterrupted);
}
