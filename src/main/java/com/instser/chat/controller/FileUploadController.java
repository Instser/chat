package com.instser.chat.controller;

import com.instser.chat.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 文件上传控制器
 * 处理文件上传功能
 *
 * @author instser
 * @since 2025-06-14
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class FileUploadController {

    @Value("${file.upload.path:uploads}")
    private String uploadPath;

    @Value("${file.upload.max-size:10485760}") // 10MB
    private long maxFileSize;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    /**
     * 文件上传接口
     */
    @PostMapping("/upload")
    public Result<Map<String, Object>> uploadFiles(
            @RequestParam("files") MultipartFile[] files) {

        if (files == null || files.length == 0) {
            return Result.validateFailed("没有文件上传");
        }

        List<Map<String, Object>> uploadedFiles = new ArrayList<>();

        try {
            // 确保上传目录存在
            Path uploadDir = Paths.get(uploadPath);
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    continue;
                }

                // 检查文件大小
                if (file.getSize() > maxFileSize) {
                    return Result.failed("文件 " + file.getOriginalFilename() + " 超过最大大小限制");
                }

                // 检查文件类型
                String contentType = file.getContentType();
                if (!isAllowedFileType(contentType)) {
                    return Result.failed("不支持的文件类型：" + contentType);
                }

                // 生成唯一文件名
                String originalFilename = file.getOriginalFilename();
                String fileExtension = getFileExtension(originalFilename);
                String uniqueFilename = generateUniqueFilename(fileExtension);

                // 保存文件
                Path filePath = uploadDir.resolve(uniqueFilename);
                Files.copy(file.getInputStream(), filePath);

                // 构建文件信息
                Map<String, Object> fileInfo = new HashMap<>();
                fileInfo.put("name", uniqueFilename);
                fileInfo.put("originalName", originalFilename);
                fileInfo.put("size", file.getSize());
                fileInfo.put("type", contentType);
                fileInfo.put("url", buildFileUrl(uniqueFilename));
                fileInfo.put("uploadedAt", LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")));

                uploadedFiles.add(fileInfo);

                log.info("文件上传成功：{} -> {}", originalFilename, uniqueFilename);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("files", uploadedFiles);
            response.put("message", "文件上传成功");

            return Result.success(response, "文件上传成功");

        } catch (IOException e) {
            log.error("文件上传失败", e);
            return Result.failed("文件上传失败：" + e.getMessage());
        }
    }

    /**
     * 检查是否为允许的文件类型
     */
    private boolean isAllowedFileType(String contentType) {
        if (contentType == null) {
            return false;
        }
        
        // 允许的文件类型
        Set<String> allowedTypes = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp",
            "text/plain", "text/csv",
            "application/pdf",
            "application/msword", 
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/json"
        );
        
        return allowedTypes.contains(contentType.toLowerCase());
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
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
     * 构建文件访问URL
     */
    private String buildFileUrl(String filename) {
        String baseUrl = contextPath.isEmpty() ? "" : contextPath;
        return baseUrl + "/uploads/" + filename;
    }

    /**
     * 获取文件信息
     */
    @GetMapping("/files/{filename}")
    public Result<Map<String, Object>> getFileInfo(@PathVariable String filename) {
        try {
            Path filePath = Paths.get(uploadPath, filename);
            if (!Files.exists(filePath)) {
                return Result.failed("文件不存在");
            }

            Map<String, Object> fileInfo = new HashMap<>();
            fileInfo.put("name", filename);
            fileInfo.put("size", Files.size(filePath));
            fileInfo.put("url", buildFileUrl(filename));
            fileInfo.put("lastModified", Files.getLastModifiedTime(filePath).toString());

            return Result.success(fileInfo, "获取文件信息成功");

        } catch (IOException e) {
            log.error("获取文件信息失败", e);
            return Result.failed("获取文件信息失败");
        }
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/files/{filename}")
    public Result<Void> deleteFile(@PathVariable String filename) {
        try {
            Path filePath = Paths.get(uploadPath, filename);
            if (!Files.exists(filePath)) {
                return Result.failed("文件不存在");
            }

            Files.delete(filePath);
            log.info("文件删除成功：{}", filename);

            return Result.success(null, "文件删除成功");

        } catch (IOException e) {
            log.error("文件删除失败", e);
            return Result.failed("文件删除失败");
        }
    }
}
