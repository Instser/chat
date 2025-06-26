package com.instser.chat.service.serviceimpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.instser.chat.dao.FileUploadDao;
import com.instser.chat.entity.FileUpload;
import com.instser.chat.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Base64;
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

    @Value("${file.upload.path:src/main/resources/file}")
    private String uploadPath;

    @Value("${file.upload.max-size:10485760}") // 10MB
    private long maxFileSize;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    // 允许的文件类型（注意：所有text/*格式都被允许，这里只列出其他类型）
    private static final Set<String> ALLOWED_FILE_TYPES = Set.of(
        "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp",
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/json"
    );

    @Override
    public FileUpload uploadFile(MultipartFile file, Long userId, String sessionId, String messageId) {
        try {
            // 验证文件
            validateFile(file);

            // 检测文件类型
            String detectedType = detectFileType(file);
            if (!isFileTypeAllowed(detectedType)) {
                throw new IllegalArgumentException("不支持的文件类型：" + detectedType);
            }

            // 生成文件ID和唯一文件名
            String fileId = UUID.randomUUID().toString();
            String originalFilename = file.getOriginalFilename();
            String fileExtension = getFileExtension(detectedType);
            String uniqueFilename = generateUniqueFilename(fileExtension);

            // 确保上传目录存在
            Path uploadDir = Paths.get(uploadPath);
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            // 读取文件内容
            byte[] fileBytes = file.getBytes();
            String fileContent = null;

            // 根据文件类型处理内容
            if (isTextFile(detectedType)) {
                // 文本文件：直接转换为字符串
                try {
                    fileContent = new String(fileBytes, "UTF-8");
                } catch (Exception e) {
                    log.warn("文本文件编码转换失败，尝试使用默认编码: {}", e.getMessage());
                    fileContent = new String(fileBytes);
                }
            } else if (isBinaryFile(detectedType)) {
                // 二进制文件：转换为Base64编码
                fileContent = Base64.getEncoder().encodeToString(fileBytes);
            } else {
                // 其他文件：尝试文本方式，失败则使用Base64
                try {
                    fileContent = new String(fileBytes, "UTF-8");
                    // 检查是否包含过多的控制字符，如果是则使用Base64
                    if (containsTooManyControlChars(fileContent)) {
                        fileContent = Base64.getEncoder().encodeToString(fileBytes);
                    }
                } catch (Exception e) {
                    fileContent = Base64.getEncoder().encodeToString(fileBytes);
                }
            }

            // 临时保存文件到磁盘（用于备份和调试）
            Path filePath = uploadDir.resolve(uniqueFilename);
            Files.write(filePath, fileBytes);

            // 创建文件记录
            FileUpload fileUpload = new FileUpload();
            fileUpload.setFileId(fileId);
            fileUpload.setUserId(userId);
            fileUpload.setSessionId(sessionId);
            fileUpload.setMessageId(messageId);
            fileUpload.setFileName(originalFilename);
            fileUpload.setFileType(detectedType);
            fileUpload.setFileSize(file.getSize());
            fileUpload.setStoragePath(filePath.toString());
            fileUpload.setContent(fileContent); // 保存完整文件内容
            fileUpload.setStatus(FileUpload.Status.AVAILABLE);

            try {
                // 保存到数据库
                save(fileUpload);

                // 数据库保存成功后，删除临时文件
                try {
                    Files.deleteIfExists(filePath);
                    log.info("临时文件删除成功：{}", filePath.toString());
                } catch (IOException deleteException) {
                    log.warn("删除临时文件失败，但不影响文件上传：{}, 错误：{}",
                            filePath.toString(), deleteException.getMessage());
                }

                log.info("文件上传成功：用户ID={}, 文件ID={}, 原始文件名={}, 存储文件名={}",
                        userId, fileId, originalFilename, uniqueFilename);

                return fileUpload;

            } catch (Exception dbException) {
                // 数据库保存失败时，清理临时文件
                try {
                    Files.deleteIfExists(filePath);
                    log.info("数据库保存失败，临时文件清理成功：{}", filePath.toString());
                } catch (IOException deleteException) {
                    log.warn("数据库保存失败，临时文件清理也失败：{}, 错误：{}",
                            filePath.toString(), deleteException.getMessage());
                }
                throw dbException;
            }

        } catch (IOException e) {
            log.error("文件上传失败：{}", e.getMessage(), e);
            throw new RuntimeException("文件上传失败：" + e.getMessage());
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
                } catch (Exception e) {
                    log.error("批量上传中文件 {} 上传失败：{}", file.getOriginalFilename(), e.getMessage());
                    // 继续处理其他文件，不中断整个批量上传过程
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
    public boolean deleteFile(String fileId, Long userId) {
        FileUpload fileUpload = getByFileId(fileId);
        if (fileUpload == null) {
            return false;
        }

        // 验证文件所有权
        if (!fileUpload.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权删除此文件");
        }

        // 逻辑删除
        return removeById(fileUpload.getId());
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

        try {
            // 删除磁盘文件
            Path filePath = Paths.get(fileUpload.getStoragePath());
            if (Files.exists(filePath)) {
                Files.delete(filePath);
            }

            // 删除数据库记录
            return removeById(fileUpload.getId());

        } catch (IOException e) {
            log.error("删除磁盘文件失败：{}", e.getMessage(), e);
            return false;
        }
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
            // 如果是文本文件，直接转换为字节
            if (isTextFile(fileUpload.getFileType())) {
                return content.getBytes("UTF-8");
            } else {
                // 如果是二进制文件，从Base64解码
                return Base64.getDecoder().decode(content);
            }
        } catch (Exception e) {
            log.error("解析文件内容失败：{}", e.getMessage(), e);
            // 尝试从磁盘文件读取作为备选方案
            try {
                Path filePath = Paths.get(fileUpload.getStoragePath());
                if (Files.exists(filePath)) {
                    return Files.readAllBytes(filePath);
                }
            } catch (IOException ioException) {
                log.error("从磁盘读取文件失败：{}", ioException.getMessage(), ioException);
            }
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
            log.error("更新文件关联信息时发生异常，文件ID：{}，用户ID：{}", fileId, userId, e);
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
    public int cleanupExpiredFiles(LocalDateTime beforeTime) {
        // 查询过期文件
        List<FileUpload> expiredFiles = fileUploadDao.selectByTimeRange(
                LocalDateTime.of(2000, 1, 1, 0, 0), beforeTime);

        int deletedCount = 0;
        for (FileUpload file : expiredFiles) {
            try {
                // 删除磁盘文件
                Path filePath = Paths.get(file.getStoragePath());
                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                }

                // 删除数据库记录
                if (removeById(file.getId())) {
                    deletedCount++;
                }
            } catch (Exception e) {
                log.error("清理过期文件失败：{}", e.getMessage(), e);
            }
        }

        log.info("清理过期文件完成，删除文件数量：{}", deletedCount);
        return deletedCount;
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
        if (fileType == null) {
            return false;
        }

        String lowerCaseType = fileType.toLowerCase();

        // 支持所有text格式
        if (lowerCaseType.startsWith("text/")) {
            return true;
        }

        // 支持其他预定义的文件类型
        return ALLOWED_FILE_TYPES.contains(lowerCaseType);
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
            throw new IllegalArgumentException("文件大小超过限制");
        }
    }

    /**
     * 检测文件类型（这里简化实现，实际应该使用魔数检测）
     */
    private String detectFileType(MultipartFile file) {
        // 这里可以集成之前实现的魔数检测逻辑
        String contentType = file.getContentType();
        return contentType != null ? contentType : "application/octet-stream";
    }

    /**
     * 根据MIME类型获取文件扩展名
     */
    private String getFileExtension(String mimeType) {
        switch (mimeType.toLowerCase()) {
            case "image/png": return ".png";
            case "image/jpeg": return ".jpg";
            case "image/gif": return ".gif";
            case "image/webp": return ".webp";
            case "application/pdf": return ".pdf";
            case "text/plain": return ".txt";
            case "application/json": return ".json";
            case "text/csv": return ".csv";
            default: return ".bin";
        }
    }

    /**
     * 生成唯一文件名
     */
    private String generateUniqueFilename(String extension) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return timestamp + "_" + uuid + extension;
    }

    /**
     * 判断是否为文本文件
     */
    private boolean isTextFile(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        return mimeType.startsWith("text/") ||
               mimeType.equals("application/json") ||
               mimeType.equals("application/xml") ||
               mimeType.equals("application/javascript") ||
               mimeType.equals("application/css");
    }

    /**
     * 判断是否为二进制文件
     */
    private boolean isBinaryFile(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        return mimeType.startsWith("image/") ||
               mimeType.startsWith("audio/") ||
               mimeType.startsWith("video/") ||
               mimeType.equals("application/pdf") ||
               mimeType.equals("application/zip") ||
               mimeType.startsWith("application/vnd.") ||
               mimeType.equals("application/octet-stream");
    }

    /**
     * 检查字符串是否包含过多的控制字符
     */
    private boolean containsTooManyControlChars(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }

        int controlCharCount = 0;
        int checkLength = Math.min(content.length(), 1000); // 检查前1000个字符

        for (int i = 0; i < checkLength; i++) {
            char c = content.charAt(i);
            // 检查是否为控制字符（排除常见的换行、制表符等）
            if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                controlCharCount++;
            }
        }

        // 如果控制字符比例超过5%，认为是二进制文件
        return (double) controlCharCount / checkLength > 0.05;
    }

    /**
     * 判断是否应该存储内容到content字段（已废弃，现在所有文件都存储内容）
     */
    @Deprecated
    private boolean shouldStoreContent(String fileType, Long fileSize) {
        return true; // 现在所有文件都存储内容到content字段
    }

    /**
     * 清理临时文件
     *
     * @param fileUpload 文件上传记录
     */
    public void cleanupTempFile(FileUpload fileUpload) {
        if (fileUpload == null || fileUpload.getStoragePath() == null) {
            return;
        }

        try {
            Path tempFilePath = Paths.get(fileUpload.getStoragePath());
            if (Files.exists(tempFilePath)) {
                Files.delete(tempFilePath);
                log.info("临时文件清理成功：{}", tempFilePath.toString());
            }
        } catch (IOException e) {
            log.warn("清理临时文件失败：{}, 错误：{}",
                    fileUpload.getStoragePath(), e.getMessage());
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
}
