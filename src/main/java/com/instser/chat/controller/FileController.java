package com.instser.chat.controller;

import com.instser.chat.common.exception.FileUploadException;
import com.instser.chat.common.result.Result;
import com.instser.chat.common.result.ResultCode;
import com.instser.chat.common.util.ErrorResponseUtil;
import com.instser.chat.entity.FileUpload;
import com.instser.chat.security.UserDetailsServiceImpl;
import com.instser.chat.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import java.util.*;

/**
 * 文件管理控制器
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@RestController
@RequestMapping("")
@RequiredArgsConstructor
@Validated
@CrossOrigin(originPatterns = {
    "http://localhost:*", "https://localhost:*",
    "http://127.0.0.1:*", "https://127.0.0.1:*",
    "http://192.168.1.100:*", "https://192.168.1.100:*",
    "http://192.168.1.101:*", "https://192.168.1.101:*",
    "https://chat.example.com", "http://chat.example.com"
}, allowedHeaders = "*", allowCredentials = "true")
public class FileController {

    private final FileUploadService fileUploadService;

    /**
     * 获取当前认证用户ID
     */
    private Long getCurrentUserId(Authentication authentication) {
        UserDetailsServiceImpl.CustomUserDetails userDetails =
            (UserDetailsServiceImpl.CustomUserDetails) authentication.getPrincipal();
        return userDetails.getUserId();
    }

    /**
     * 文件上传接口
     */
    @PostMapping("/files/upload")
    public Result<Map<String, Object>> uploadFiles(
            @RequestParam("files") MultipartFile[] files,
            Authentication authentication) {

        if (files == null || files.length == 0) {
            return ErrorResponseUtil.fileEmpty();
        }

        Long userId = getCurrentUserId(authentication);

        try {
            // 使用服务层进行文件上传（不传递sessionId和messageId，这些将在chat成功后更新）
            List<FileUpload> uploadedFiles = fileUploadService.uploadFiles(files, userId, null, null);

            // 校验存储完成性
            List<String> validatedFileIds = new ArrayList<>();
            List<Map<String, Object>> validatedFiles = new ArrayList<>();
            List<FileUpload> failedFiles = new ArrayList<>();

            for (FileUpload fileUpload : uploadedFiles) {
                if (fileUpload != null && fileUpload.getFileId() != null) {
                    // 从数据库重新查询验证文件是否正确存储
                    FileUpload verifiedFile = fileUploadService.getByFileId(fileUpload.getFileId());
                    if (verifiedFile != null &&
                        verifiedFile.getContent() != null &&
                        !verifiedFile.getContent().isEmpty() &&
                        verifiedFile.getStatus() == FileUpload.Status.AVAILABLE) {

                        validatedFileIds.add(fileUpload.getFileId());

                        // 构建文件信息
                        Map<String, Object> fileInfo = new HashMap<>();
                        fileInfo.put("fileId", fileUpload.getFileId());
                        fileInfo.put("fileName", fileUpload.getFileName());
                        validatedFiles.add(fileInfo);

                        log.info("文件存储校验成功：文件ID={}, 文件名={}, 用户ID={}",
                                fileUpload.getFileId(), fileUpload.getFileName(), userId);
                    } else {
                        log.error("文件存储校验失败：文件ID={}, 文件名={}, 用户ID={}",
                                fileUpload.getFileId(), fileUpload.getFileName(), userId);
                        failedFiles.add(fileUpload);
                    }
                }
            }

            // 清理验证失败的文件的临时文件
            if (!failedFiles.isEmpty()) {
                fileUploadService.cleanupTempFiles(failedFiles);
                log.info("清理了 {} 个验证失败的临时文件", failedFiles.size());
            }

            // 检查是否有文件上传成功
            if (validatedFileIds.isEmpty()) {
                return ErrorResponseUtil.fileUploadFailed();
            }

            // 构建响应，按照指定格式
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("filesId", String.join(",", validatedFileIds)); // 逗号分隔的文件ID
            response.put("files", validatedFiles); // 文件详细信息数组
            response.put("message", "Upload succeed");

            return Result.success(response, "OK");

        } catch (FileUploadException e) {
            log.error("文件上传失败: {} - {}", e.getErrorType(), e.getErrorMessage());

            // 根据错误类型返回对应的错误响应
            switch (e.getErrorType()) {
                case "FILE_EMPTY":
                    return ErrorResponseUtil.fileEmpty();
                case "FILE_SIZE_EXCEEDED":
                    return ErrorResponseUtil.fileSizeExceeded();
                case "FILE_TYPE_NOT_SUPPORTED":
                    return ErrorResponseUtil.fileTypeNotSupported();
                case "FILE_PARSE_FAILED":
                    return ErrorResponseUtil.fileParseFailed();
                default:
                    return ErrorResponseUtil.fileUploadFailed();
            }
        } catch (Exception e) {
            // 只记录错误消息，不打印堆栈跟踪
            log.error("文件上传失败: {}", e.getMessage());
            return ErrorResponseUtil.fileUploadFailed();
        }
    }

    /**
     * 根据文件ID获取文件信息
     */
    @GetMapping("/files/{fileId}")
    public Result<Map<String, Object>> getFileInfo(@PathVariable @NotBlank String fileId,
                                                   Authentication authentication) {
        Long userId = getCurrentUserId(authentication);

        FileUpload fileUpload = fileUploadService.getByFileId(fileId);
        if (fileUpload == null) {
            return Result.failed("文件不存在");
        }

        // 验证文件所有权
        if (!fileUpload.getUserId().equals(userId)) {
            return Result.failed("无权访问此文件");
        }

        Map<String, Object> fileInfo = new HashMap<>();
        fileInfo.put("fileId", fileUpload.getFileId());
        fileInfo.put("name", fileUpload.getFileName());
        fileInfo.put("size", fileUpload.getFileSize());
        fileInfo.put("type", fileUpload.getFileType());
        fileInfo.put("status", fileUpload.getStatus());
        fileInfo.put("sessionId", fileUpload.getSessionId());
        fileInfo.put("messageId", fileUpload.getMessageId());
        fileInfo.put("hasContent", fileUpload.getContent() != null && !fileUpload.getContent().isEmpty());
        fileInfo.put("uploadedAt", fileUpload.getCreatedTime().toString());
        fileInfo.put("updatedAt", fileUpload.getUpdateTime().toString());

        return Result.success(fileInfo, "获取文件信息成功");
    }

    /**
     * 根据文件ID删除文件
     */
    @DeleteMapping("/files/{fileId}")
    public Result<Void> deleteFile(@PathVariable @NotBlank String fileId,
                                  Authentication authentication) {
        Long userId = getCurrentUserId(authentication);

        try {
            boolean deleted = fileUploadService.deleteFile(fileId, userId);
            if (!deleted) {
                return Result.failed("文件删除失败");
            }

            log.info("文件删除成功：用户ID={}, 文件ID={}", userId, fileId);
            return Result.success(null, "文件删除成功");

        } catch (IllegalArgumentException e) {
            return Result.failed(e.getMessage());
        } catch (Exception e) {
            log.error("文件删除失败", e);
            return Result.failed("文件删除失败");
        }
    }

    /**
     * 获取文件内容
     */
    @GetMapping("/files/{fileId}/content")
    public Result<Map<String, Object>> getFileContent(@PathVariable @NotBlank String fileId,
                                                      Authentication authentication) {
        Long userId = getCurrentUserId(authentication);

        String content = fileUploadService.getFileContent(fileId, userId);
        if (content == null) {
            return Result.failed("文件不存在或无法读取内容");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("fileId", fileId);
        response.put("content", content);

        return Result.success(response, "获取文件内容成功");
    }

    /**
     * 更新文件内容
     */
    @PutMapping("/files/{fileId}/content")
    public Result<Void> updateFileContent(@PathVariable @NotBlank String fileId,
                                         @RequestBody Map<String, String> request,
                                         Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        String content = request.get("content");

        if (content == null) {
            return Result.validateFailed("文件内容不能为空");
        }

        try {
            boolean updated = fileUploadService.updateFileContent(fileId, content, userId);
            if (!updated) {
                return Result.failed("文件内容更新失败");
            }

            return Result.success(null, "文件内容更新成功");

        } catch (IllegalArgumentException e) {
            return Result.failed(e.getMessage());
        } catch (Exception e) {
            log.error("文件内容更新失败", e);
            return Result.failed("文件内容更新失败");
        }
    }

    /**
     * 获取用户文件列表
     */
    @GetMapping("/files")
    public Result<Map<String, Object>> getUserFiles(@RequestParam(value = "fileType", required = false) String fileType,
                                                    @RequestParam(value = "limit", required = false) Integer limit,
                                                    Authentication authentication) {
        Long userId = getCurrentUserId(authentication);

        List<FileUpload> files;
        if (fileType != null && !fileType.isEmpty()) {
            files = fileUploadService.getByUserIdAndFileType(userId, fileType);
        } else if (limit != null) {
            files = fileUploadService.getRecentByUserId(userId, limit);
        } else {
            files = fileUploadService.getByUserId(userId);
        }

        List<Map<String, Object>> fileInfoList = new ArrayList<>();
        for (FileUpload fileUpload : files) {
            Map<String, Object> fileInfo = new HashMap<>();
            fileInfo.put("fileId", fileUpload.getFileId());
            fileInfo.put("name", fileUpload.getFileName());
            fileInfo.put("size", fileUpload.getFileSize());
            fileInfo.put("type", fileUpload.getFileType());
            fileInfo.put("status", fileUpload.getStatus());
            fileInfo.put("sessionId", fileUpload.getSessionId());
            fileInfo.put("messageId", fileUpload.getMessageId());
            fileInfo.put("hasContent", fileUpload.getContent() != null && !fileUpload.getContent().isEmpty());
            fileInfo.put("uploadedAt", fileUpload.getCreatedTime().toString());

            fileInfoList.add(fileInfo);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("files", fileInfoList);
        response.put("total", fileInfoList.size());

        return Result.success(response, "获取文件列表成功");
    }

    /**
     * 关联文件到消息
     */
    @PostMapping("/files/{fileId}/associate")
    public Result<Void> associateFileToMessage(@PathVariable @NotBlank String fileId,
                                              @RequestBody Map<String, String> request,
                                              Authentication authentication) {
        Long userId = getCurrentUserId(authentication);
        String messageId = request.get("messageId");

        if (messageId == null || messageId.trim().isEmpty()) {
            return Result.validateFailed("消息ID不能为空");
        }

        try {
            boolean associated = fileUploadService.associateFileToMessage(fileId, messageId, userId);
            if (!associated) {
                return Result.failed("文件关联失败");
            }

            return Result.success(null, "文件关联成功");

        } catch (IllegalArgumentException e) {
            return Result.failed(e.getMessage());
        } catch (Exception e) {
            log.error("文件关联失败", e);
            return Result.failed("文件关联失败");
        }
    }

    /**
     * 下载文件内容（返回content字段中的文本信息）
     */
    @GetMapping("/files/{fileId}/download")
    public Result<Map<String, Object>> downloadFile(@PathVariable @NotBlank String fileId,
                                                    Authentication authentication) {
        Long userId = getCurrentUserId(authentication);

        try {
            // 获取文件信息
            FileUpload fileUpload = fileUploadService.getByFileId(fileId);
            if (fileUpload == null) {
                return Result.failed("文件不存在");
            }

            // 验证文件所有权
            if (!fileUpload.getUserId().equals(userId)) {
                return Result.failed("无权访问此文件");
            }

            // 获取文件内容
            String content = fileUpload.getContent();
            if (content == null || content.isEmpty()) {
                return Result.failed("文件内容为空");
            }

            // 构建响应
            Map<String, Object> response = new HashMap<>();
            response.put("fileId", fileId);
            response.put("fileName", fileUpload.getFileName());
            response.put("fileType", fileUpload.getFileType());
            response.put("fileSize", fileUpload.getFileSize());
            response.put("content", content);
            response.put("uploadedAt", fileUpload.getCreatedTime().toString());

            return Result.success(response, "文件下载成功");

        } catch (Exception e) {
            log.error("文件下载失败", e);
            return Result.failed("文件下载失败：" + e.getMessage());
        }
    }
}
