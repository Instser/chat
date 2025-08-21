package com.instser.chat.service.serviceimpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.instser.chat.common.exception.FileUploadException;
import com.instser.chat.common.util.LogUtils;
import com.instser.chat.dao.FileUploadDao;
import com.instser.chat.entity.FileUpload;
import com.instser.chat.service.DocumentParserService;
import com.instser.chat.service.FileUploadService;
import com.instser.chat.util.MagicNumberDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 文件上传服务实现类
 *
 * @author instser
 * @since 2025-06-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadServiceImpl extends ServiceImpl<FileUploadDao, FileUpload> implements FileUploadService {

    private final FileUploadDao fileUploadDao;
    private final DocumentParserService documentParserService;

    @Value("${file.upload.path:src/main/resources/file}")
    private String uploadPath;

    @Value("${file.upload.max-size:10485760}") // 10MB
    private long maxFileSize;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    // 注意：文件类型验证现在使用DocumentParserFactory.isDocumentFile()方法

    @Override
    public FileUpload uploadFile(MultipartFile file, Long userId, String sessionId, String messageId) {
        Path tempFilePath = null;
        String originalFilename = file.getOriginalFilename(); // 移到try外面，确保在catch中可以访问

        LogUtils.logUserOperation(log, userId, "文件上传", "文件名：{}，大小：{} KB，类型：{}",
            originalFilename, file.getSize() / 1024, file.getContentType());

        try {
            log.debug("【文件验证】开始验证文件，文件名：{}", originalFilename);

            // 验证文件
            validateFile(file);

            log.debug("【类型检测】开始检测文件类型，文件名：{}", originalFilename);

            // 检测文件类型
            String detectedType = detectFileType(file);

            log.debug("【类型检测】文件名：{}，检测类型：{}，魔数验证结果：通过", originalFilename, detectedType);

            if (!isFileTypeAllowed(detectedType)) {
                log.warn("【类型不支持】用户ID：{}，文件类型：{}", userId, detectedType);
                throw new FileUploadException("FILE_TYPE_NOT_SUPPORTED", "File type not supported: " + detectedType);
            }

            // 生成文件ID
            String fileId = UUID.randomUUID().toString().replace("-", "");

            log.debug("【文件处理】生成文件ID：{}，开始创建临时文件", fileId);

            // 创建临时文件用于解析
            tempFilePath = createTempFile(file, fileId);
            log.debug("【文件处理】创建临时文件：{}", tempFilePath.toString());

            log.debug("【内容提取】开始读取文件内容，文件ID：{}", fileId);

            // 读取文件内容进行解析
            byte[] fileBytes = Files.readAllBytes(tempFilePath);
            String fileContent = null;

            // 根据文件类型处理内容
            if (documentParserService.isDocumentFile(detectedType)) {
                log.debug("【内容提取】文件ID：{}，提取方式：{}，原始大小：{} bytes",
                    fileId, "文档解析器", fileBytes.length);

                // 使用统一的文档解析器处理所有支持的文件类型（包括文本文件）
                try {
                    fileContent = documentParserService.parseDocument(fileBytes, originalFilename, detectedType);
                    log.info("【文件解析】文件ID：{}，解析器：{}，内容长度：{}",
                        fileId, "DocumentParser", fileContent.length());
                } catch (Exception e) {
                    log.error("【解析失败】文件ID：{}，解析器：{}，错误：{}",
                        fileId, "DocumentParser", e.getMessage(), e);
                    throw new RuntimeException("文档解析失败：" + e.getMessage());
                }
            } else {
                // 其他文件类型不支持
                log.warn("【类型不支持】文件ID：{}，文件类型：{}", fileId, detectedType);
                throw new FileUploadException("FILE_TYPE_NOT_SUPPORTED", "File type not supported: " + detectedType);
            }

            log.debug("【文件保存】开始创建文件记录，文件ID：{}", fileId);

            // 创建文件记录
            FileUpload fileUpload = new FileUpload();
            fileUpload.setFileId(fileId);
            fileUpload.setUserId(userId);
            fileUpload.setSessionId(sessionId);
            fileUpload.setMessageId(messageId);
            fileUpload.setFileName(originalFilename);
            fileUpload.setFileType(detectedType);
            fileUpload.setFileSize(file.getSize());
            fileUpload.setContent(fileContent); // 保存解析后的文本内容
            fileUpload.setStatus(FileUpload.Status.AVAILABLE);

            log.debug("【文件保存】开始保存到数据库，文件ID：{}", fileId);

            // 使用重试机制保存到数据库
            boolean saved = saveFileWithRetry(fileUpload, 3);
            if (!saved) {
                log.error("【文件保存】保存失败，文件ID：{}，重试次数已用完", fileId);
                throw new FileUploadException("FILE_SAVE_FAILED", "Failed to save file to database after retries");
            }

            LogUtils.logUserOperation(log, userId, "文件上传", "文件上传成功，文件ID：{}，文件名：{}，大小：{} KB，内容长度：{}",
                fileId, originalFilename, file.getSize() / 1024, fileContent.length());

            return fileUpload;

        } catch (FileUploadException e) {
            LogUtils.logUserWarning(log, userId, "文件上传", "业务异常，文件名：{}，错误：{}", originalFilename, e.getMessage());
            // FileUploadException直接重新抛出，不要转换
            throw e;
        } catch (IllegalArgumentException e) {
            // 参数验证错误，记录具体原因并抛出特定异常
            LogUtils.logUserWarning(log, userId, "文件上传", "参数验证失败，文件名：{}，错误：{}", originalFilename, e.getMessage());
            // 根据具体错误类型抛出不同的异常
            if (e.getMessage().contains("文件不能为空")) {
                throw new FileUploadException("FILE_EMPTY", "File cannot be empty");
            } else if (e.getMessage().contains("文件大小超过限制")) {
                throw new FileUploadException("FILE_SIZE_EXCEEDED", "File size exceeds limit");
            } else if (e.getMessage().contains("不支持的文件类型")) {
                throw new FileUploadException("FILE_TYPE_NOT_SUPPORTED", "File type not supported");
            } else {
                throw new FileUploadException("FILE_UPLOAD_FAILED", "File upload failed");
            }
        } catch (IOException e) {
            // IO错误，记录具体原因但返回统一错误消息
            log.error("【文件上传】IO错误，用户ID：{}，文件名：{}，错误：{}", userId, originalFilename, e.getMessage(), e);
            throw new FileUploadException("FILE_UPLOAD_FAILED", "File upload failed");
        } catch (Exception e) {
            // 其他错误，记录具体原因但返回统一错误消息
            log.error("【文件上传】上传失败，用户ID：{}，文件名：{}，错误：{}", userId, originalFilename, e.getMessage(), e);
            if (e.getMessage().contains("文档解析失败")) {
                throw new FileUploadException("FILE_PARSE_FAILED", "File parsing failed");
            } else {
                throw new FileUploadException("FILE_UPLOAD_FAILED", "File upload failed");
            }
        } finally {
            // 无论成功还是失败，都要清理临时文件
            if (tempFilePath != null) {
                cleanupTempFile(tempFilePath);
            }
        }
    }

    @Override
    public List<FileUpload> uploadFiles(MultipartFile[] files, Long userId, String sessionId, String messageId) {
        List<FileUpload> uploadedFiles = new ArrayList<>();
        
        for (MultipartFile file : files) {
            if (!file.isEmpty()) {
                try {
                    FileUpload uploadedFile = uploadFile(file, userId, sessionId, messageId);
                    uploadedFiles.add(uploadedFile);
                } catch (FileUploadException e) {
                    log.error("批量上传中文件 {} 上传失败：{}", file.getOriginalFilename(), e.getMessage());
                    // FileUploadException需要重新抛出，让Controller处理
                    throw e;
                } catch (Exception e) {
                    log.error("批量上传中文件 {} 上传失败：{}", file.getOriginalFilename(), e.getMessage());
                    // 其他异常转换为FileUploadException
                    throw new FileUploadException("FILE_UPLOAD_FAILED", "File upload failed: " + e.getMessage(), e);
                }
            }
        }
        
        return uploadedFiles;
    }

    @Override
    public FileUpload getByFileId(String fileId) {
        return fileUploadDao.selectByFileId(fileId);
    }

    @Override
    public List<FileUpload> getByUserId(Long userId) {
        return fileUploadDao.selectByUserId(userId);
    }

    @Override
    public List<FileUpload> getBySessionId(String sessionId) {
        return fileUploadDao.selectBySessionId(sessionId);
    }

    @Override
    public List<FileUpload> getByMessageId(String messageId) {
        return fileUploadDao.selectByMessageId(messageId);
    }

    @Override
    public String getFileIdsByMessageId(String messageId) {
        if (messageId == null || messageId.trim().isEmpty()) {
            return null;
        }

        List<FileUpload> files = fileUploadDao.selectByMessageId(messageId);
        if (files == null || files.isEmpty()) {
            return null;
        }

        // 将文件ID列表转换为逗号分隔的字符串
        return files.stream()
                .map(FileUpload::getFileId)
                .collect(Collectors.joining(","));
    }

    @Override
    public int deleteFilesByMessageId(String messageId, Long userId) {
        if (messageId == null || messageId.trim().isEmpty() || userId == null) {
            log.warn("删除文件失败，参数不完整：messageId={}, userId={}", messageId, userId);
            return 0;
        }

        try {
            // 获取该消息关联的所有文件
            List<FileUpload> files = fileUploadDao.selectByMessageId(messageId);
            if (files == null || files.isEmpty()) {
                log.info("消息{}没有关联的文件，跳过删除", messageId);
                return 0;
            }

            // 验证文件所有权并执行物理删除
            int deletedCount = 0;
            for (FileUpload file : files) {
                if (file.getUserId().equals(userId)) {
                    // 执行物理删除
                    boolean deleted = deleteFilePhysicallyInternal(file);
                    if (deleted) {
                        deletedCount++;
                        log.info("文件{}已物理删除，关联消息：{}", file.getFileId(), messageId);
                    } else {
                        log.warn("文件{}物理删除失败，关联消息：{}", file.getFileId(), messageId);
                    }
                } else {
                    log.warn("文件{}不属于用户{}，跳过删除", file.getFileId(), userId);
                }
            }

            log.info("消息{}关联的文件删除完成，删除数量：{}/{}", messageId, deletedCount, files.size());
            return deletedCount;

        } catch (Exception e) {
            log.error("删除消息{}关联的文件失败: {}", messageId, e.getMessage());
            return 0;
        }
    }

    @Override
    public int deleteFilesByMessageIds(List<String> messageIds, Long userId) {
        return deleteFilesByMessageIds(messageIds, userId, null);
    }

    @Override
    public int deleteFilesByMessageIds(List<String> messageIds, Long userId, List<String> skipFileIds) {
        if (messageIds == null || messageIds.isEmpty() || userId == null) {
            log.warn("批量删除文件失败，参数不完整：messageIds={}, userId={}", messageIds, userId);
            return 0;
        }

        // 将跳过的文件ID转换为Set以提高查找效率
        Set<String> skipFileIdSet = skipFileIds != null ? new HashSet<>(skipFileIds) : new HashSet<>();

        int totalDeleted = 0;
        for (String messageId : messageIds) {
            if (messageId != null && !messageId.trim().isEmpty()) {
                int deleted = deleteFilesByMessageIdWithSkip(messageId, userId, skipFileIdSet);
                totalDeleted += deleted;
            }
        }

        log.info("批量删除消息关联文件完成，消息数量：{}，删除文件数量：{}，跳过文件数量：{}",
                messageIds.size(), totalDeleted, skipFileIdSet.size());
        return totalDeleted;
    }

    /**
     * 根据消息ID删除关联的文件，支持跳过指定文件
     *
     * @param messageId 消息ID
     * @param userId 用户ID
     * @param skipFileIds 需要跳过的文件ID集合
     * @return 删除的文件数量
     */
    private int deleteFilesByMessageIdWithSkip(String messageId, Long userId, Set<String> skipFileIds) {
        try {
            // 获取该消息关联的所有文件
            List<FileUpload> files = fileUploadDao.selectByMessageId(messageId);
            if (files == null || files.isEmpty()) {
                log.info("消息{}没有关联的文件，跳过删除", messageId);
                return 0;
            }

            // 验证文件所有权并执行物理删除
            int deletedCount = 0;
            for (FileUpload file : files) {
                if (file.getUserId().equals(userId)) {
                    // 检查是否需要跳过此文件
                    if (skipFileIds.contains(file.getFileId())) {
                        log.info("跳过删除文件{}，关联消息：{}", file.getFileId(), messageId);
                        continue;
                    }

                    // 执行物理删除
                    boolean deleted = deleteFilePhysicallyInternal(file);
                    if (deleted) {
                        deletedCount++;
                        log.info("文件{}已物理删除，关联消息：{}", file.getFileId(), messageId);
                    } else {
                        log.warn("文件{}物理删除失败，关联消息：{}", file.getFileId(), messageId);
                    }
                } else {
                    log.warn("文件{}不属于用户{}，跳过删除", file.getFileId(), userId);
                }
            }

            return deletedCount;

        } catch (Exception e) {
            log.error("删除消息{}关联的文件失败: {}", messageId, e.getMessage());
            return 0;
        }
    }

    @Override
    public int deleteFilesBySessionId(String sessionId, Long userId) {
        if (sessionId == null || sessionId.trim().isEmpty() || userId == null) {
            log.warn("删除会话文件失败，参数不完整：sessionId={}, userId={}", sessionId, userId);
            return 0;
        }

        try {
            // 获取该会话关联的所有文件
            List<FileUpload> files = fileUploadDao.selectBySessionId(sessionId);
            if (files == null || files.isEmpty()) {
                log.info("会话{}没有关联的文件，跳过删除", sessionId);
                return 0;
            }

            // 验证文件所有权并执行物理删除
            int deletedCount = 0;
            for (FileUpload file : files) {
                if (file.getUserId().equals(userId)) {
                    // 执行物理删除
                    boolean deleted = deleteFilePhysicallyInternal(file);
                    if (deleted) {
                        deletedCount++;
                        log.info("文件{}已物理删除，关联会话：{}", file.getFileId(), sessionId);
                    } else {
                        log.warn("文件{}物理删除失败，关联会话：{}", file.getFileId(), sessionId);
                    }
                } else {
                    log.warn("文件{}不属于用户{}，跳过删除", file.getFileId(), userId);
                }
            }

            log.info("会话{}关联的文件删除完成，删除数量：{}/{}", sessionId, deletedCount, files.size());
            return deletedCount;

        } catch (Exception e) {
            log.error("删除会话{}关联的文件失败: {}", sessionId, e.getMessage());
            return 0;
        }
    }

    @Override
    public int deleteFilesBySessionIds(List<String> sessionIds, Long userId) {
        if (sessionIds == null || sessionIds.isEmpty() || userId == null) {
            log.warn("批量删除会话文件失败，参数不完整：sessionIds={}, userId={}", sessionIds, userId);
            return 0;
        }

        try {
            // 获取所有相关会话的文件
            List<FileUpload> files = fileUploadDao.selectBySessionIds(sessionIds);
            if (files == null || files.isEmpty()) {
                log.info("会话列表 {} 没有关联的文件，跳过删除", sessionIds);
                return 0;
            }

            // 验证文件所有权并执行物理删除
            int deletedCount = 0;
            for (FileUpload file : files) {
                if (file.getUserId().equals(userId)) {
                    // 执行物理删除
                    boolean deleted = deleteFilePhysicallyInternal(file);
                    if (deleted) {
                        deletedCount++;
                        log.info("文件{}已物理删除，关联会话：{}", file.getFileId(), file.getSessionId());
                    } else {
                        log.warn("文件{}物理删除失败，关联会话：{}", file.getFileId(), file.getSessionId());
                    }
                } else {
                    log.warn("文件{}不属于用户{}，跳过删除", file.getFileId(), userId);
                }
            }

            log.info("批量删除会话关联文件完成，删除数量：{}/{}", deletedCount, files.size());
            return deletedCount;

        } catch (Exception e) {
            log.error("批量删除会话关联文件失败: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public boolean deleteFile(String fileId, Long userId) {
        FileUpload fileUpload = getByFileId(fileId);
        if (fileUpload == null) {
            return false;
        }

        // 验证文件所有权
        if (!fileUpload.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权删除此文件");
        }

        // 物理删除
        return deleteFilePhysicallyInternal(fileUpload);
    }

    /**
     * 内部物理删除方法
     *
     * @param fileUpload 文件实体
     * @return 是否删除成功
     */
    private boolean deleteFilePhysicallyInternal(FileUpload fileUpload) {
        try {
            // 新架构下不再存储物理文件，只需删除数据库记录
            // 所有文件内容都已解析为文本存储在content字段中

            // 删除数据库记录（物理删除，不使用逻辑删除）
            boolean deleted = fileUploadDao.deleteById(fileUpload.getId()) > 0;
            if (deleted) {
                log.info("数据库记录删除成功，文件ID：{}", fileUpload.getFileId());
            }
            return deleted;

        } catch (Exception e) {
            log.error("删除文件失败，文件ID：{}: {}", fileUpload.getFileId(), e.getMessage());
            return false;
        }
    }

    @Override
    public boolean deleteFilePhysically(String fileId, Long userId) {
        FileUpload fileUpload = getByFileId(fileId);
        if (fileUpload == null) {
            return false;
        }

        // 验证文件所有权
        if (!fileUpload.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权删除此文件");
        }

        // 使用内部物理删除方法
        return deleteFilePhysicallyInternal(fileUpload);
    }

    @Override
    public String getFileContent(String fileId, Long userId) {
        FileUpload fileUpload = getByFileId(fileId);
        if (fileUpload == null || !fileUpload.getUserId().equals(userId)) {
            return null;
        }

        // 直接从content字段获取（现在所有文件内容都存储在这里）
        return fileUpload.getContent();
    }

    /**
     * 获取文件的原始字节数据
     */
    public byte[] getFileBytes(String fileId, Long userId) {
        FileUpload fileUpload = getByFileId(fileId);
        if (fileUpload == null || !fileUpload.getUserId().equals(userId)) {
            return null;
        }

        String content = fileUpload.getContent();
        if (content == null || content.isEmpty()) {
            return null;
        }

        try {
            // 现在所有文件都存储为解析后的文本内容，直接转换为字节
            return content.getBytes("UTF-8");
        } catch (Exception e) {
            log.error("解析文件内容失败：{}", e.getMessage());
            return null;
        }
    }

    @Override
    public boolean updateFileContent(String fileId, String content, Long userId) {
        FileUpload fileUpload = getByFileId(fileId);
        if (fileUpload == null || !fileUpload.getUserId().equals(userId)) {
            return false;
        }

        // 只允许更新文本文件的内容
        if (!fileUpload.isTextFile()) {
            throw new IllegalArgumentException("只能更新文本文件的内容");
        }

        fileUpload.setContent(content);
        return updateById(fileUpload);
    }

    @Override
    public List<FileUpload> getByUserIdAndFileType(Long userId, String fileType) {
        return fileUploadDao.selectByUserIdAndFileType(userId, fileType);
    }

    @Override
    public List<FileUpload> getRecentByUserId(Long userId, Integer limit) {
        return fileUploadDao.selectRecentByUserId(userId, limit);
    }

    @Override
    public Map<String, Object> getUserFileStats(Long userId) {
        Long fileCount = fileUploadDao.countByUserId(userId);
        Long totalSize = fileUploadDao.sumFileSizeByUserId(userId);

        Map<String, Object> stats = new HashMap<>();
        stats.put("fileCount", fileCount);
        stats.put("totalSize", totalSize);
        stats.put("totalSizeFormatted", formatFileSize(totalSize));

        return stats;
    }

    @Override
    public List<FileUpload> searchFiles(Long userId, String keyword, String fileType) {
        // 简单实现：按文件名搜索
        List<FileUpload> files = fileUploadDao.selectByFileNameLike(userId, keyword);
        
        // 如果指定了文件类型，进行过滤
        if (fileType != null && !fileType.isEmpty()) {
            files = files.stream()
                    .filter(file -> file.getFileType().startsWith(fileType))
                    .toList();
        }
        
        return files;
    }

    @Override
    public boolean associateFileToMessage(String fileId, String messageId, Long userId) {
        FileUpload fileUpload = getByFileId(fileId);
        if (fileUpload == null || !fileUpload.getUserId().equals(userId)) {
            return false;
        }

        fileUpload.setMessageId(messageId);
        return updateById(fileUpload);
    }

    @Override
    public boolean disassociateFileFromMessage(String fileId, Long userId) {
        FileUpload fileUpload = getByFileId(fileId);
        if (fileUpload == null || !fileUpload.getUserId().equals(userId)) {
            return false;
        }

        fileUpload.setMessageId(null);
        return updateById(fileUpload);
    }

    @Override
    public boolean isFileOwnedByUser(String fileId, Long userId) {
        FileUpload fileUpload = getByFileId(fileId);
        return fileUpload != null && fileUpload.getUserId().equals(userId);
    }

    @Override
    public boolean updateFileAssociation(String fileId, String messageId, String sessionId, Long userId) {
        try {
            FileUpload fileUpload = getByFileId(fileId);
            if (fileUpload == null) {
                log.warn("文件不存在，无法更新关联信息，文件ID：{}", fileId);
                return false;
            }

            // 验证文件所有权
            if (!fileUpload.getUserId().equals(userId)) {
                log.warn("用户{}无权更新文件{}的关联信息", userId, fileId);
                return false;
            }

            // 更新关联信息
            fileUpload.setMessageId(messageId);
            fileUpload.setSessionId(sessionId);

            boolean updated = updateById(fileUpload);
            if (updated) {
                log.info("文件关联信息更新成功，文件ID：{}，消息ID：{}，会话ID：{}，用户ID：{}",
                        fileId, messageId, sessionId, userId);
            } else {
                log.warn("文件关联信息更新失败，文件ID：{}，用户ID：{}", fileId, userId);
            }

            return updated;

        } catch (Exception e) {
            log.error("更新文件关联信息时发生异常，文件ID：{}，用户ID：{}: {}", fileId, userId, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean updateFileStatus(String fileId, Integer status) {
        FileUpload fileUpload = getByFileId(fileId);
        if (fileUpload == null) {
            return false;
        }

        fileUpload.setStatus(status);
        return updateById(fileUpload);
    }



    @Override
    public int cleanupDeletedFilesAndRelatedRecords() {
        // 查询所有标记为删除状态的文件记录
        List<FileUpload> deletedFiles = fileUploadDao.selectDeletedFiles();

        if (deletedFiles == null || deletedFiles.isEmpty()) {
            log.info("没有找到标记为删除的文件记录，跳过清理");
            return 0;
        }

        int cleanedCount = 0;
        int relatedRecordsCleanedCount = 0;

        for (FileUpload file : deletedFiles) {
            try {
                // 1. 清理相关的关联记录（如果需要的话）
                // 注意：由于文件表有外键约束，这里主要是记录日志
                // 实际的关联清理由数据库外键约束自动处理

                // 2. 物理删除文件记录（彻底删除，不再使用逻辑删除）
                if (fileUploadDao.deleteById(file.getId()) > 0) {
                    cleanedCount++;
                    log.info("已清理标记为删除的文件记录，文件ID：{}，文件名：{}，关联会话：{}，关联消息：{}",
                            file.getFileId(), file.getFileName(), file.getSessionId(), file.getMessageId());
                }
            } catch (Exception e) {
                log.error("清理标记为删除的文件失败，文件ID：{}，错误：{}",
                        file.getFileId(), e.getMessage());
            }
        }

        log.info("清理标记为删除的文件和关联记录完成，清理文件数量：{}，关联记录数量：{}",
                cleanedCount, relatedRecordsCleanedCount);
        return cleanedCount;
    }

    @Override
    public String getFileDownloadUrl(String fileId, Long userId) {
        FileUpload fileUpload = getByFileId(fileId);
        if (fileUpload == null || !fileUpload.getUserId().equals(userId)) {
            return null;
        }

        // 构建下载URL
        String baseUrl = contextPath.isEmpty() ? "" : contextPath;
        return baseUrl + "/api/files/" + fileId + "/download";
    }

    @Override
    public boolean isFileTypeAllowed(String fileType) {
        // 使用DocumentParserFactory统一判断文件类型是否支持
        return documentParserService.isDocumentFile(fileType);
    }

    @Override
    public boolean isFileSizeAllowed(Long fileSize) {
        return fileSize != null && fileSize <= maxFileSize;
    }

    // ==================== 私有工具方法 ====================

    /**
     * 验证文件
     */
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        if (!isFileSizeAllowed(file.getSize())) {
            throw new IllegalArgumentException("文件大小超过限制：" + file.getSize() + " bytes");
        }
    }

    /**
     * 检测文件类型（使用魔数检测）
     */
    private String detectFileType(MultipartFile file) {
        try {
            // 第一层：使用魔数检测（最可靠）
            String magicNumberType = MagicNumberDetector.detectFileType(file);
            if (!"application/octet-stream".equals(magicNumberType)) {
                log.debug("魔数检测文件类型: {}", magicNumberType);
                return magicNumberType;
            }

            // 第二层：使用Content-Type（回退方案）
            String contentType = file.getContentType();
            if (contentType != null && !contentType.isEmpty() && !"application/octet-stream".equals(contentType)) {
                log.debug("Content-Type检测文件类型: {}", contentType);
                return contentType;
            }

            // 第三层：根据文件名猜测（最后备选）
            String filename = file.getOriginalFilename();
            String guessedType = MagicNumberDetector.guessTypeFromFilename(filename);
            log.debug("文件名猜测文件类型: {}", guessedType);
            return guessedType;

        } catch (Exception e) {
            log.warn("文件类型检测失败，使用默认类型: {}", e.getMessage());
            return "application/octet-stream";
        }
    }




    /**
     * 清理临时文件（接口兼容性方法）
     *
     * @param fileUpload 文件上传记录（新架构下此参数无效）
     */
    @Override
    public void cleanupTempFile(FileUpload fileUpload) {
        // 新架构下不再存储物理文件，此方法保持接口兼容性
        // 实际的临时文件清理在文件上传过程中自动完成
        log.debug("cleanupTempFile调用，新架构下无需清理物理文件");
    }

    /**
     * 创建临时文件用于解析
     *
     * @param file   上传的文件
     * @param fileId 文件ID
     * @return 临时文件路径
     * @throws IOException 创建文件失败
     */
    private Path createTempFile(MultipartFile file, String fileId) throws IOException {
        // 确保临时目录存在
        Path tempDir = Files.createTempDirectory("file-upload-");

        // 获取文件扩展名
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        // 创建临时文件
        Path tempFilePath = tempDir.resolve(fileId + extension);

        // 将上传的文件内容写入临时文件
        try (var inputStream = file.getInputStream()) {
            Files.copy(inputStream, tempFilePath);
        }

        log.debug("临时文件创建成功: {} (大小: {} bytes)", tempFilePath.toString(), file.getSize());
        return tempFilePath;
    }

    /**
     * 清理临时文件（新方法）
     *
     * @param tempFilePath 临时文件路径
     */
    public void cleanupTempFile(Path tempFilePath) {
        if (tempFilePath == null) {
            return;
        }

        try {
            if (Files.exists(tempFilePath)) {
                Files.delete(tempFilePath);
                log.debug("临时文件清理成功：{}", tempFilePath.toString());

                // 尝试删除临时目录（如果为空）
                Path parentDir = tempFilePath.getParent();
                if (parentDir != null && Files.exists(parentDir)) {
                    try {
                        Files.delete(parentDir);
                        log.debug("临时目录清理成功：{}", parentDir.toString());
                    } catch (IOException e) {
                        // 目录不为空或其他原因，忽略错误
                        log.debug("临时目录清理跳过：{}", parentDir.toString());
                    }
                }
            }
        } catch (IOException e) {
            log.warn("清理临时文件失败：{}, 错误：{}", tempFilePath.toString(), e.getMessage());
        }
    }

    /**
     * 批量清理临时文件
     *
     * @param fileUploads 文件上传记录列表
     */
    public void cleanupTempFiles(List<FileUpload> fileUploads) {
        if (fileUploads == null || fileUploads.isEmpty()) {
            return;
        }

        for (FileUpload fileUpload : fileUploads) {
            cleanupTempFile(fileUpload);
        }
    }

    /**
     * 格式化文件大小
     */
    private String formatFileSize(Long bytes) {
        if (bytes == null || bytes == 0) return "0 B";
        
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = bytes.doubleValue();
        
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        
        return String.format("%.2f %s", size, units[unitIndex]);
    }

    /**
     * 带重试机制的文件保存方法
     * @param fileUpload 要保存的文件对象
     * @param maxRetries 最大重试次数
     * @return 保存是否成功
     */
    private boolean saveFileWithRetry(FileUpload fileUpload, int maxRetries) {
        int retryCount = 0;
        long retryDelay = 500; // 初始重试延迟500ms

        while (retryCount <= maxRetries) {
            try {
                boolean saved = save(fileUpload);
                if (saved) {
                    if (retryCount > 0) {
                        log.info("【数据库重试】文件保存成功，文件ID：{}，重试次数：{}", fileUpload.getFileId(), retryCount);
                    }
                    return true;
                } else {
                    log.warn("【数据库重试】文件保存失败，文件ID：{}，尝试 {}/{}", fileUpload.getFileId(), retryCount + 1, maxRetries + 1);
                }
            } catch (Exception e) {
                log.error("【数据库重试】文件保存异常，文件ID：{}，尝试 {}/{}", fileUpload.getFileId(), retryCount + 1, maxRetries + 1, e);
            }

            // 如果不是最后一次尝试，等待后重试
            if (retryCount < maxRetries) {
                try {
                    Thread.sleep(retryDelay);
                    retryDelay *= 2; // 指数退避，下次等待时间翻倍
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("【数据库重试】文件保存重试被中断，文件ID：{}", fileUpload.getFileId());
                    break;
                }
            }

            retryCount++;
        }

        log.error("【数据库重试】文件保存失败，已达到最大重试次数，文件ID：{}", fileUpload.getFileId());
        return false;
    }
}
