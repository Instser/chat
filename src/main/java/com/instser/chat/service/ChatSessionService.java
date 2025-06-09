package com.instser.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.instser.chat.entity.ChatSession;

import java.util.List;

/**
 * 聊天会话服务接口
 *
 * @author instser
 * @since 2024-01-01
 */
public interface ChatSessionService extends IService<ChatSession> {

    /**
     * 创建新会话
     *
     * @param userId  用户ID
     * @param modelId 模型ID
     * @return 会话ID
     */
    String createSession(Long userId, Long modelId);

    /**
     * 获取用户会话列表
     *
     * @param userId 用户ID
     * @return 会话列表
     */
    List<ChatSession> getUserSessions(Long userId);

    /**
     * 获取会话信息
     *
     * @param sessionId 会话ID
     * @return 会话信息
     */
    ChatSession getSessionInfo(String sessionId);

    /**
     * 更新会话标题
     *
     * @param sessionId 会话ID
     * @param title     标题
     * @return 是否更新成功
     */
    boolean updateSessionTitle(String sessionId, String title);

    /**
     * 更新会话最后活跃时间
     *
     * @param sessionId   会话ID
     * @param lastMessage 最后一条消息
     */
    void updateLastActiveTime(String sessionId, String lastMessage);

    /**
     * 删除会话
     *
     * @param sessionId 会话ID
     * @return 是否删除成功
     */
    boolean deleteSession(String sessionId);
}
