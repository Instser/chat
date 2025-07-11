package com.instser.chat.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.instser.chat.entity.WebSearchContent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 联网查询内容数据访问层
 *
 * @author instser
 * @since 2025-07-10
 */
@Mapper
public interface WebSearchContentDao extends BaseMapper<WebSearchContent> {

    /**
     * 根据消息ID查询联网内容（核心查询）
     *
     * @param messageId 消息ID
     * @return 联网内容
     */
    @Select("SELECT * FROM t_web_search_content WHERE message_id = #{messageId} AND deleted = 0")
    WebSearchContent selectByMessageId(@Param("messageId") String messageId);

    /**
     * 获取用户联网查询历史（简单分页）
     *
     * @param userId 用户ID
     * @param offset 偏移量
     * @param size   页面大小
     * @return 联网内容列表
     */
    @Select("SELECT * FROM t_web_search_content WHERE user_id = #{userId} AND deleted = 0 ORDER BY create_time DESC LIMIT #{offset}, #{size}")
    List<WebSearchContent> selectUserHistory(@Param("userId") Long userId, @Param("offset") int offset, @Param("size") int size);

    /**
     * 获取会话的联网内容
     *
     * @param sessionId 会话ID
     * @return 联网内容列表
     */
    @Select("SELECT * FROM t_web_search_content WHERE session_id = #{sessionId} AND deleted = 0 ORDER BY create_time ASC")
    List<WebSearchContent> selectBySessionId(@Param("sessionId") String sessionId);

    /**
     * 更新联网内容（用于重新查询）
     *
     * @param messageId   消息ID
     * @param content     内容JSON
     * @param resultCount 结果数量
     * @return 更新行数
     */
    @Update("UPDATE t_web_search_content SET content = #{content}, result_count = #{resultCount}, update_time = NOW() WHERE message_id = #{messageId} AND deleted = 0")
    int updateContentByMessageId(@Param("messageId") String messageId, @Param("content") String content, @Param("resultCount") Integer resultCount);

    /**
     * 更新消息ID和内容（用于edit-resend）
     *
     * @param oldMessageId 旧消息ID
     * @param newMessageId 新消息ID
     * @param content      内容JSON
     * @param resultCount  结果数量
     * @return 更新行数
     */
    @Update("UPDATE t_web_search_content SET message_id = #{newMessageId}, content = #{content}, result_count = #{resultCount}, update_time = NOW() WHERE message_id = #{oldMessageId} AND deleted = 0")
    int updateContentAndMessageId(@Param("oldMessageId") String oldMessageId, @Param("newMessageId") String newMessageId, @Param("content") String content, @Param("resultCount") Integer resultCount);

    /**
     * 逻辑删除联网内容
     *
     * @param messageId 消息ID
     * @return 删除行数
     */
    @Update("UPDATE t_web_search_content SET deleted = 1, update_time = NOW() WHERE message_id = #{messageId} AND deleted = 0")
    int deleteByMessageId(@Param("messageId") String messageId);
}
