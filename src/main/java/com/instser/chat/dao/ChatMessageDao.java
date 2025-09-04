package com.instser.chat.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.instser.chat.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 聊天消息数据访问层
 *
 * @author instser
 * @since 2024-01-01
 */
@Mapper
public interface ChatMessageDao extends BaseMapper<ChatMessage> {

    /**
     * 根据会话ID查询消息列表
     *
     * @param sessionId 会话ID
     * @return 消息列表
     */
    List<ChatMessage> selectBySessionId(@Param("sessionId") String sessionId);

    /**
     * 根据用户ID查询最近的消息
     *
     * @param userId 用户ID
     * @param limit  限制数量
     * @return 消息列表
     */
    List<ChatMessage> selectRecentByUserId(@Param("userId") Long userId, @Param("limit") Integer limit);

    /**
     * 根据消息ID查询消息
     *
     * @param messageId 消息ID（UUID）
     * @return 消息信息
     */
    ChatMessage selectByMessageId(@Param("messageId") String messageId);

    /**
     * 根据会话ID查询消息列表（用于AI上下文，排除错误响应）
     *
     * @param sessionId 会话ID
     * @return 消息列表（排除status=3的错误响应）
     */
    List<ChatMessage> selectBySessionIdForAiContext(@Param("sessionId") String sessionId);
}
