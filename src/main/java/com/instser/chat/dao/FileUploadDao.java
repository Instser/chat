package com.instser.chat.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.instser.chat.entity.FileUpload;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件上传数据访问层
 *
 * @author instser
 * @since 2025-06-25
 */
@Mapper
public interface FileUploadDao extends BaseMapper<FileUpload> {

    /**
     * 根据文件ID查询文件
     *
     * @param fileId 文件ID
     * @return 文件信息
     */
    @Select("SELECT * FROM t_file_upload WHERE file_id = #{fileId} AND deleted = 0")
    FileUpload selectByFileId(@Param("fileId") String fileId);

    /**
     * 根据用户ID查询文件列表
     *
     * @param userId 用户ID
     * @return 文件列表
     */
    @Select("SELECT * FROM t_file_upload WHERE user_id = #{userId} AND deleted = 0 ORDER BY create_time DESC")
    List<FileUpload> selectByUserId(@Param("userId") Long userId);

    /**
     * 根据会话ID查询文件列表
     *
     * @param sessionId 会话ID
     * @return 文件列表
     */
    @Select("SELECT * FROM t_file_upload WHERE session_id = #{sessionId} AND deleted = 0 ORDER BY create_time DESC")
    List<FileUpload> selectBySessionId(@Param("sessionId") String sessionId);

    /**
     * 根据消息ID查询文件列表
     *
     * @param messageId 消息ID
     * @return 文件列表
     */
    @Select("SELECT * FROM t_file_upload WHERE message_id = #{messageId} AND deleted = 0 ORDER BY create_time DESC")
    List<FileUpload> selectByMessageId(@Param("messageId") String messageId);

    /**
     * 根据用户ID和文件类型查询文件列表
     *
     * @param userId   用户ID
     * @param fileType 文件类型
     * @return 文件列表
     */
    @Select("SELECT * FROM t_file_upload WHERE user_id = #{userId} AND file_type LIKE CONCAT(#{fileType}, '%') AND deleted = 0 ORDER BY create_time DESC")
    List<FileUpload> selectByUserIdAndFileType(@Param("userId") Long userId, @Param("fileType") String fileType);

    /**
     * 根据用户ID查询最近上传的文件
     *
     * @param userId 用户ID
     * @param limit  限制数量
     * @return 文件列表
     */
    @Select("SELECT * FROM t_file_upload WHERE user_id = #{userId} AND deleted = 0 ORDER BY create_time DESC LIMIT #{limit}")
    List<FileUpload> selectRecentByUserId(@Param("userId") Long userId, @Param("limit") Integer limit);

    /**
     * 根据状态查询文件列表
     *
     * @param status 状态
     * @return 文件列表
     */
    @Select("SELECT * FROM t_file_upload WHERE status = #{status} AND deleted = 0 ORDER BY create_time DESC")
    List<FileUpload> selectByStatus(@Param("status") Integer status);

    /**
     * 查询指定时间范围内的文件
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 文件列表
     */
    @Select("SELECT * FROM t_file_upload WHERE create_time BETWEEN #{startTime} AND #{endTime} AND deleted = 0 ORDER BY create_time DESC")
    List<FileUpload> selectByTimeRange(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    /**
     * 统计用户上传的文件数量
     *
     * @param userId 用户ID
     * @return 文件数量
     */
    @Select("SELECT COUNT(*) FROM t_file_upload WHERE user_id = #{userId} AND deleted = 0")
    Long countByUserId(@Param("userId") Long userId);

    /**
     * 统计用户上传的文件总大小
     *
     * @param userId 用户ID
     * @return 文件总大小（字节）
     */
    @Select("SELECT COALESCE(SUM(file_size), 0) FROM t_file_upload WHERE user_id = #{userId} AND deleted = 0")
    Long sumFileSizeByUserId(@Param("userId") Long userId);

    /**
     * 根据存储路径查询文件
     *
     * @param storagePath 存储路径
     * @return 文件信息
     */
    @Select("SELECT * FROM t_file_upload WHERE storage_path = #{storagePath} AND deleted = 0")
    FileUpload selectByStoragePath(@Param("storagePath") String storagePath);

    /**
     * 查询包含内容的文件列表（用于内容搜索）
     *
     * @param userId 用户ID
     * @return 文件列表
     */
    @Select("SELECT * FROM t_file_upload WHERE user_id = #{userId} AND content IS NOT NULL AND content != '' AND deleted = 0 ORDER BY create_time DESC")
    List<FileUpload> selectWithContentByUserId(@Param("userId") Long userId);

    /**
     * 根据文件名模糊查询
     *
     * @param userId   用户ID
     * @param fileName 文件名（支持模糊匹配）
     * @return 文件列表
     */
    @Select("SELECT * FROM t_file_upload WHERE user_id = #{userId} AND file_name LIKE CONCAT('%', #{fileName}, '%') AND deleted = 0 ORDER BY create_time DESC")
    List<FileUpload> selectByFileNameLike(@Param("userId") Long userId, @Param("fileName") String fileName);

    /**
     * 查询所有标记为删除状态的文件记录
     *
     * @return 标记为删除的文件列表
     */
    @Select("SELECT * FROM t_file_upload WHERE deleted = 1 ORDER BY update_time ASC")
    List<FileUpload> selectDeletedFiles();
}
