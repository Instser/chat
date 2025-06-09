package com.instser.chat.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.instser.chat.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 聊天会话数据访问层
 *
 * @author instser
 * @since 2024-01-01
 */
@Mapper
public interface ChatSessionDao extends BaseMapper<ChatSession> {

    /**
     * 根据用户ID查询会话列表
     *
     * @param userId 用户ID
     * @return 会话列表
     */
    List<ChatSession> selectByUserId(@Param("userId") Long userId);

    /**
     * 根据会话ID查询会话信息
     *
     * @param sessionId 会话ID
     * @return 会话信息
     */
    ChatSession selectBySessionId(@Param("sessionId") String sessionId);
}
