package com.instser.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.instser.chat.entity.FileUpload;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 文件上传服务接口
 *
 * @author instser
 * @since 2025-06-25
 */
public interface FileUploadService extends IService<FileUpload> {

    /**
     * 上传文件
     *
     * @param file      上传的文件
     * @param userId    用户ID
     * @param sessionId 会话ID（可选）
     * @param messageId 消息ID（可选）
     * @return 文件上传结果
     */
    FileUpload uploadFile(MultipartFile file, Long userId, String sessionId, String messageId);

    /**
     * 批量上传文件
     *
     * @param files     上传的文件数组
     * @param userId    用户ID
     * @param sessionId 会话ID（可选）
     * @param messageId 消息ID（可选）
     * @return 文件上传结果列表
     */
    List<FileUpload> uploadFiles(MultipartFile[] files, Long userId, String sessionId, String messageId);

    /**
     * 根据文件ID获取文件信息
     *
     * @param fileId 文件ID
     * @return 文件信息
     */
    FileUpload getByFileId(String fileId);

    /**
     * 根据用户ID获取文件列表
     *
     * @param userId 用户ID
     * @return 文件列表
     */
    List<FileUpload> getByUserId(Long userId);

    /**
     * 根据会话ID获取文件列表
     *
     * @param sessionId 会话ID
     * @return 文件列表
     */
    List<FileUpload> getBySessionId(String sessionId);

    /**
     * 根据消息ID获取文件列表
     *
     * @param messageId 消息ID
     * @return 文件列表
     */
    List<FileUpload> getByMessageId(String messageId);

    /**
     * 根据消息ID获取文件ID列表（逗号分隔）
     *
     * @param messageId 消息ID
     * @return 文件ID列表（逗号分隔），如果没有文件则返回null
     */
    String getFileIdsByMessageId(String messageId);

    /**
     * 根据消息ID删除关联的文件（物理删除）
     *
     * @param messageId 消息ID
     * @param userId 用户ID（用于权限验证）
     * @return 删除的文件数量
     */
    int deleteFilesByMessageId(String messageId, Long userId);

    /**
     * 根据消息ID列表批量删除关联的文件（物理删除）
     *
     * @param messageIds 消息ID列表
     * @param userId 用户ID（用于权限验证）
     * @return 删除的文件数量
     */
    int deleteFilesByMessageIds(List<String> messageIds, Long userId);

    /**
     * 根据消息ID列表批量删除关联的文件（物理删除），支持跳过指定文件
     *
     * @param messageIds 消息ID列表
     * @param userId 用户ID（用于权限验证）
     * @param skipFileIds 需要跳过删除的文件ID列表（用于edit-resend场景）
     * @return 删除的文件数量
     */
    int deleteFilesByMessageIds(List<String> messageIds, Long userId, List<String> skipFileIds);

    /**
     * 根据会话ID删除关联的文件（物理删除）
     *
     * @param sessionId 会话ID
     * @param userId 用户ID（用于权限验证）
     * @return 删除的文件数量
     */
    int deleteFilesBySessionId(String sessionId, Long userId);

    /**
     * 删除文件（物理删除）
     *
     * @param fileId 文件ID
     * @param userId 用户ID（用于权限验证）
     * @return 是否删除成功
     */
    boolean deleteFile(String fileId, Long userId);

    /**
     * 物理删除文件（同时删除数据库记录和磁盘文件）
     *
     * @param fileId 文件ID
     * @param userId 用户ID（用于权限验证）
     * @return 是否删除成功
     */
    boolean deleteFilePhysically(String fileId, Long userId);

    /**
     * 获取文件内容
     *
     * @param fileId 文件ID
     * @param userId 用户ID（用于权限验证）
     * @return 文件内容
     */
    String getFileContent(String fileId, Long userId);

    /**
     * 获取文件的原始字节数据
     *
     * @param fileId 文件ID
     * @param userId 用户ID（用于权限验证）
     * @return 文件字节数据
     */
    byte[] getFileBytes(String fileId, Long userId);

    /**
     * 更新文件内容
     *
     * @param fileId  文件ID
     * @param content 文件内容
     * @param userId  用户ID（用于权限验证）
     * @return 是否更新成功
     */
    boolean updateFileContent(String fileId, String content, Long userId);

    /**
     * 根据文件类型获取文件列表
     *
     * @param userId   用户ID
     * @param fileType 文件类型（如 "image", "text" 等）
     * @return 文件列表
     */
    List<FileUpload> getByUserIdAndFileType(Long userId, String fileType);

    /**
     * 获取用户最近上传的文件
     *
     * @param userId 用户ID
     * @param limit  限制数量
     * @return 文件列表
     */
    List<FileUpload> getRecentByUserId(Long userId, Integer limit);

    /**
     * 获取用户文件统计信息
     *
     * @param userId 用户ID
     * @return 统计信息
     */
    Map<String, Object> getUserFileStats(Long userId);

    /**
     * 搜索用户文件
     *
     * @param userId   用户ID
     * @param keyword  搜索关键词
     * @param fileType 文件类型（可选）
     * @return 文件列表
     */
    List<FileUpload> searchFiles(Long userId, String keyword, String fileType);

    /**
     * 关联文件到消息
     *
     * @param fileId    文件ID
     * @param messageId 消息ID
     * @param userId    用户ID（用于权限验证）
     * @return 是否关联成功
     */
    boolean associateFileToMessage(String fileId, String messageId, Long userId);

    /**
     * 取消文件与消息的关联
     *
     * @param fileId 文件ID
     * @param userId 用户ID（用于权限验证）
     * @return 是否取消成功
     */
    boolean disassociateFileFromMessage(String fileId, Long userId);

    /**
     * 检查文件是否属于用户
     *
     * @param fileId 文件ID
     * @param userId 用户ID
     * @return 是否属于该用户
     */
    boolean isFileOwnedByUser(String fileId, Long userId);

    /**
     * 更新文件的关联信息（messageId和sessionId）
     *
     * @param fileId    文件ID
     * @param messageId 消息ID
     * @param sessionId 会话ID
     * @param userId    用户ID（用于权限验证）
     * @return 是否更新成功
     */
    boolean updateFileAssociation(String fileId, String messageId, String sessionId, Long userId);

    /**
     * 更新文件状态
     *
     * @param fileId 文件ID
     * @param status 新状态
     * @return 是否更新成功
     */
    boolean updateFileStatus(String fileId, Integer status);

    /**
     * 清理标记为删除状态的文件记录和关联数据
     *
     * 此方法会：
     * 1. 查询所有deleted=1的文件记录
     * 2. 清理相关的关联记录（如果有的话）
     * 3. 物理删除文件记录
     *
     * @return 清理的文件数量
     */
    int cleanupDeletedFilesAndRelatedRecords();

    /**
     * 获取文件下载URL
     *
     * @param fileId 文件ID
     * @param userId 用户ID（用于权限验证）
     * @return 下载URL
     */
    String getFileDownloadUrl(String fileId, Long userId);

    /**
     * 验证文件类型是否允许
     *
     * @param fileType 文件类型
     * @return 是否允许
     */
    boolean isFileTypeAllowed(String fileType);

    /**
     * 验证文件大小是否在限制范围内
     *
     * @param fileSize 文件大小
     * @return 是否在限制范围内
     */
    boolean isFileSizeAllowed(Long fileSize);

    /**
     * 清理临时文件
     *
     * @param fileUpload 文件上传记录
     */
    void cleanupTempFile(FileUpload fileUpload);

    /**
     * 批量清理临时文件
     *
     * @param fileUploads 文件上传记录列表
     */
    void cleanupTempFiles(List<FileUpload> fileUploads);
}
