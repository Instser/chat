package com.instser.chat.controller;

import com.instser.chat.common.result.Result;
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
 * 文件管理控制器
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@RestController
@RequestMapping("")
@Validated
@CrossOrigin(originPatterns = "*", allowedHeaders = "*", allowCredentials = "true")
public class FileController {

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

                // 检查文件类型（使用魔数检测）
                String detectedType = detectFileTypeByMagicNumber(file);
                if (!isAllowedFileType(detectedType)) {
                    return Result.failed("不支持的文件类型：" + detectedType);
                }

                // 生成唯一文件名
                String originalFilename = file.getOriginalFilename();
                String fileExtension = getFileExtensionByMagicNumber(file);
                String uniqueFilename = generateUniqueFilename(fileExtension);

                // 保存文件
                Path filePath = uploadDir.resolve(uniqueFilename);
                Files.copy(file.getInputStream(), filePath);

                // 构建文件信息
                Map<String, Object> fileInfo = new HashMap<>();
                fileInfo.put("name", uniqueFilename);
                fileInfo.put("originalName", originalFilename);
                fileInfo.put("size", file.getSize());
                fileInfo.put("type", detectedType);
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
     * 通过文件魔数检测文件类型并返回对应扩展名
     */
    private String getFileExtensionByMagicNumber(MultipartFile file) {
        try {
            byte[] fileBytes = file.getBytes();
            if (fileBytes.length < 4) {
                // 文件太小，无法检测魔数，回退到文件名检测
                return getFileExtensionFromFilename(file.getOriginalFilename());
            }

            String detectedType = detectFileTypeByMagicNumber(fileBytes);
            return getExtensionFromMimeType(detectedType);

        } catch (IOException e) {
            log.warn("无法读取文件内容进行魔数检测，回退到文件名检测: {}", e.getMessage());
            return getFileExtensionFromFilename(file.getOriginalFilename());
        }
    }

    /**
     * 通过文件名获取扩展名（回退方法）
     */
    private String getFileExtensionFromFilename(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }

    /**
     * 通过文件魔数检测文件类型（MultipartFile重载方法）
     */
    private String detectFileTypeByMagicNumber(MultipartFile file) {
        try {
            byte[] fileBytes = file.getBytes();
            return detectFileTypeByMagicNumber(fileBytes);
        } catch (IOException e) {
            log.warn("无法读取文件内容进行魔数检测: {}", e.getMessage());
            // 回退到使用浏览器提供的Content-Type
            String contentType = file.getContentType();
            return contentType != null ? contentType : "application/octet-stream";
        }
    }

    /**
     * 通过文件魔数检测文件类型
     */
    private String detectFileTypeByMagicNumber(byte[] fileBytes) {
        // 检查常见的文件魔数
        if (fileBytes.length >= 4) {
            // PNG: 89 50 4E 47
            if (fileBytes[0] == (byte) 0x89 && fileBytes[1] == 0x50 &&
                fileBytes[2] == 0x4E && fileBytes[3] == 0x47) {
                return "image/png";
            }

            // JPEG: FF D8 FF
            if (fileBytes[0] == (byte) 0xFF && fileBytes[1] == (byte) 0xD8 &&
                fileBytes[2] == (byte) 0xFF) {
                return "image/jpeg";
            }

            // GIF: 47 49 46 38 或 47 49 46 39
            if (fileBytes[0] == 0x47 && fileBytes[1] == 0x49 &&
                fileBytes[2] == 0x46 && (fileBytes[3] == 0x38 || fileBytes[3] == 0x39)) {
                return "image/gif";
            }

            // PDF: 25 50 44 46
            if (fileBytes[0] == 0x25 && fileBytes[1] == 0x50 &&
                fileBytes[2] == 0x44 && fileBytes[3] == 0x46) {
                return "application/pdf";
            }
        }

        if (fileBytes.length >= 8) {
            // WebP: 52 49 46 46 xx xx xx xx 57 45 42 50
            if (fileBytes[0] == 0x52 && fileBytes[1] == 0x49 &&
                fileBytes[2] == 0x46 && fileBytes[3] == 0x46 &&
                fileBytes[8] == 0x57 && fileBytes[9] == 0x45 &&
                fileBytes[10] == 0x42 && fileBytes[11] == 0x50) {
                return "image/webp";
            }
        }

        if (fileBytes.length >= 2) {
            // BMP: 42 4D
            if (fileBytes[0] == 0x42 && fileBytes[1] == 0x4D) {
                return "image/bmp";
            }
        }

        // 检查ZIP格式文件（包括Office文档）
        if (fileBytes.length >= 4) {
            // ZIP: 50 4B 03 04 或 50 4B 05 06 或 50 4B 07 08
            if (fileBytes[0] == 0x50 && fileBytes[1] == 0x4B &&
                (fileBytes[2] == 0x03 || fileBytes[2] == 0x05 || fileBytes[2] == 0x07)) {
                return detectOfficeDocumentType(fileBytes);
            }
        }

        // 检查文本文件（简单的UTF-8/ASCII检测）
        if (isTextFile(fileBytes)) {
            return "text/plain";
        }

        // 无法识别的文件类型
        return "application/octet-stream";
    }

    /**
     * 检测Office文档类型
     */
    private String detectOfficeDocumentType(byte[] fileBytes) {
        try {
            // 这里可以进一步检测ZIP内容来确定具体的Office文档类型
            // 为简化实现，我们返回通用的ZIP类型，让MIME类型检测来处理
            return "application/zip";
        } catch (Exception e) {
            return "application/zip";
        }
    }

    /**
     * 检测是否为文本文件
     */
    private boolean isTextFile(byte[] fileBytes) {
        // 检查前1024字节或整个文件（如果文件较小）
        int checkLength = Math.min(fileBytes.length, 1024);
        int nonTextBytes = 0;

        for (int i = 0; i < checkLength; i++) {
            byte b = fileBytes[i];
            // 检查是否为控制字符（除了常见的换行、制表符等）
            if (b < 0x20 && b != 0x09 && b != 0x0A && b != 0x0D) {
                nonTextBytes++;
            }
        }

        // 如果非文本字节比例小于5%，认为是文本文件
        return (double) nonTextBytes / checkLength < 0.05;
    }

    /**
     * 根据MIME类型获取对应的文件扩展名
     */
    private String getExtensionFromMimeType(String mimeType) {
        switch (mimeType.toLowerCase()) {
            case "image/png":
                return ".png";
            case "image/jpeg":
                return ".jpg";
            case "image/gif":
                return ".gif";
            case "image/webp":
                return ".webp";
            case "image/bmp":
                return ".bmp";
            case "application/pdf":
                return ".pdf";
            case "text/plain":
                return ".txt";
            case "application/json":
                return ".json";
            case "text/csv":
                return ".csv";
            case "application/zip":
                return ".zip";
            case "application/msword":
                return ".doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
                return ".docx";
            case "application/vnd.ms-excel":
                return ".xls";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":
                return ".xlsx";
            default:
                return ".bin"; // 未知类型使用.bin扩展名
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
     * 构建文件访问URL
     */
    private String buildFileUrl(String filename) {
        String baseUrl = contextPath.isEmpty() ? "" : contextPath;
        return baseUrl + "/uploads/" + filename;
    }
}
