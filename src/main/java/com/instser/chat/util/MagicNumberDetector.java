package com.instser.chat.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文件魔数检测工具类
 * 基于文件头部的二进制特征检测真实文件类型
 *
 * @author instser
 * @since 2025-06-27
 */
@Slf4j
public class MagicNumberDetector {

    /**
     * 检测MultipartFile的文件类型
     */
    public static String detectFileType(MultipartFile file) {
        try {
            byte[] fileBytes = file.getBytes();
            String detectedType = detectFileType(fileBytes);

            // 如果魔数检测到ZIP格式，但文件名表明是Office文档，则使用文件名判断
            if ("application/zip".equals(detectedType)) {
                String filename = file.getOriginalFilename();
                if (filename != null) {
                    String filenameBasedType = guessTypeFromFilename(filename);
                    if (isOfficeDocument(filenameBasedType)) {
                        log.debug("ZIP格式文件根据文件名识别为Office文档: {} -> {}", filename, filenameBasedType);
                        return filenameBasedType;
                    }
                }
            }

            return detectedType;
        } catch (IOException e) {
            log.warn("读取文件内容失败，使用Content-Type: {}", e.getMessage());
            String contentType = file.getContentType();
            return contentType != null ? contentType : "application/octet-stream";
        }
    }

    /**
     * 检测字节数组的文件类型
     */
    public static String detectFileType(byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length < 4) {
            return "application/octet-stream";
        }

        // 图片文件检测
        if (isPNG(fileBytes)) return "image/png";
        if (isJPEG(fileBytes)) return "image/jpeg";
        if (isGIF(fileBytes)) return "image/gif";
        if (isWebP(fileBytes)) return "image/webp";
        if (isBMP(fileBytes)) return "image/bmp";

        // 文档文件检测
        if (isPDF(fileBytes)) return "application/pdf";
        if (isZIP(fileBytes)) return detectZipBasedFormat(fileBytes);

        // Office文档检测（基于OLE格式）
        if (isOLEDocument(fileBytes)) return detectOLEDocumentType(fileBytes);

        // 文本文件检测（最后进行，因为需要更多计算）
        if (isTextFile(fileBytes)) return "text/plain";

        return "application/octet-stream";
    }

    /**
     * 检测PNG文件
     * 魔数: 89 50 4E 47 0D 0A 1A 0A
     */
    private static boolean isPNG(byte[] fileBytes) {
        if (fileBytes.length < 8) return false;
        return fileBytes[0] == (byte) 0x89 &&
               fileBytes[1] == 0x50 &&
               fileBytes[2] == 0x4E &&
               fileBytes[3] == 0x47 &&
               fileBytes[4] == 0x0D &&
               fileBytes[5] == 0x0A &&
               fileBytes[6] == 0x1A &&
               fileBytes[7] == 0x0A;
    }

    /**
     * 检测JPEG文件
     * 魔数: FF D8 FF
     */
    private static boolean isJPEG(byte[] fileBytes) {
        if (fileBytes.length < 3) return false;
        return fileBytes[0] == (byte) 0xFF &&
               fileBytes[1] == (byte) 0xD8 &&
               fileBytes[2] == (byte) 0xFF;
    }

    /**
     * 检测GIF文件
     * 魔数: 47 49 46 38 (37/39) 61
     */
    private static boolean isGIF(byte[] fileBytes) {
        if (fileBytes.length < 6) return false;
        return fileBytes[0] == 0x47 &&
               fileBytes[1] == 0x49 &&
               fileBytes[2] == 0x46 &&
               fileBytes[3] == 0x38 &&
               (fileBytes[4] == 0x37 || fileBytes[4] == 0x39) &&
               fileBytes[5] == 0x61;
    }

    /**
     * 检测WebP文件
     * 魔数: 52 49 46 46 ... 57 45 42 50
     */
    private static boolean isWebP(byte[] fileBytes) {
        if (fileBytes.length < 12) return false;
        return fileBytes[0] == 0x52 &&
               fileBytes[1] == 0x49 &&
               fileBytes[2] == 0x46 &&
               fileBytes[3] == 0x46 &&
               fileBytes[8] == 0x57 &&
               fileBytes[9] == 0x45 &&
               fileBytes[10] == 0x42 &&
               fileBytes[11] == 0x50;
    }

    /**
     * 检测BMP文件
     * 魔数: 42 4D
     */
    private static boolean isBMP(byte[] fileBytes) {
        if (fileBytes.length < 2) return false;
        return fileBytes[0] == 0x42 && fileBytes[1] == 0x4D;
    }

    /**
     * 检测PDF文件
     * 魔数: 25 50 44 46
     */
    private static boolean isPDF(byte[] fileBytes) {
        if (fileBytes.length < 4) return false;
        return fileBytes[0] == 0x25 &&
               fileBytes[1] == 0x50 &&
               fileBytes[2] == 0x44 &&
               fileBytes[3] == 0x46;
    }

    /**
     * 检测ZIP文件
     * 魔数: 50 4B 03 04 或 50 4B 05 06 或 50 4B 07 08
     */
    private static boolean isZIP(byte[] fileBytes) {
        if (fileBytes.length < 4) return false;
        return fileBytes[0] == 0x50 &&
               fileBytes[1] == 0x4B &&
               (fileBytes[2] == 0x03 || fileBytes[2] == 0x05 || fileBytes[2] == 0x07) &&
               (fileBytes[3] == 0x04 || fileBytes[3] == 0x06 || fileBytes[3] == 0x08);
    }

    /**
     * 检测OLE文档（老版本Office文档）
     * 魔数: D0 CF 11 E0 A1 B1 1A E1
     */
    private static boolean isOLEDocument(byte[] fileBytes) {
        if (fileBytes.length < 8) return false;
        return fileBytes[0] == (byte) 0xD0 &&
               fileBytes[1] == (byte) 0xCF &&
               fileBytes[2] == 0x11 &&
               fileBytes[3] == (byte) 0xE0 &&
               fileBytes[4] == (byte) 0xA1 &&
               fileBytes[5] == (byte) 0xB1 &&
               fileBytes[6] == 0x1A &&
               fileBytes[7] == (byte) 0xE1;
    }

    /**
     * 检测基于ZIP的文档格式（新版本Office文档）
     */
    private static String detectZipBasedFormat(byte[] fileBytes) {
        // 这里可以进一步检测ZIP内容来确定具体的Office文档类型
        // 简化实现，返回通用ZIP类型
        return "application/zip";
    }

    /**
     * 检测OLE文档的具体类型
     */
    private static String detectOLEDocumentType(byte[] fileBytes) {
        // 这里可以进一步分析OLE结构来确定是Word、Excel还是PowerPoint
        // 简化实现，返回通用的Word类型
        return "application/msword";
    }

    /**
     * 检测文本文件
     * 通过分析字符分布判断是否为文本文件
     */
    private static boolean isTextFile(byte[] fileBytes) {
        if (fileBytes.length == 0) return false;

        int sampleSize = Math.min(fileBytes.length, 1024); // 检查前1024字节
        int textChars = 0;
        int totalChars = 0;

        // 检查UTF-8 BOM
        int startIndex = 0;
        if (fileBytes.length >= 3 &&
            fileBytes[0] == (byte) 0xEF &&
            fileBytes[1] == (byte) 0xBB &&
            fileBytes[2] == (byte) 0xBF) {
            startIndex = 3; // 跳过BOM
            textChars += 3;
            totalChars += 3;
        }

        for (int i = startIndex; i < sampleSize; i++) {
            byte b = fileBytes[i];
            totalChars++;

            // 可打印ASCII字符 (32-126)
            if (b >= 32 && b <= 126) {
                textChars++;
            }
            // 常见的文本控制字符
            else if (b == 9 || b == 10 || b == 13) { // Tab, LF, CR
                textChars++;
            }
            // UTF-8多字节字符的后续字节 (128-191)
            else if ((b & 0x80) != 0 && (b & 0xC0) == 0x80) {
                textChars++;
            }
            // UTF-8多字节字符的起始字节
            else if ((b & 0xE0) == 0xC0 || (b & 0xF0) == 0xE0 || (b & 0xF8) == 0xF0) {
                textChars++;
            }
            // 其他字符不计入文本字符
        }

        // 如果文本字符比例超过90%，认为是文本文件
        double textRatio = (double) textChars / totalChars;
        return textRatio > 0.90;
    }

    /**
     * 根据文件扩展名猜测MIME类型（回退方案）
     */
    public static String guessTypeFromFilename(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "application/octet-stream";
        }

        String extension = filename.substring(filename.lastIndexOf(".")).toLowerCase();
        
        switch (extension) {
            case ".txt": return "text/plain";
            case ".html": case ".htm": return "text/html";
            case ".css": return "text/css";
            case ".js": return "application/javascript";
            case ".json": return "application/json";
            case ".xml": return "application/xml";
            case ".csv": return "text/csv";
            case ".md": return "text/markdown";
            
            case ".jpg": case ".jpeg": return "image/jpeg";
            case ".png": return "image/png";
            case ".gif": return "image/gif";
            case ".webp": return "image/webp";
            case ".bmp": return "image/bmp";
            
            case ".pdf": return "application/pdf";
            case ".zip": return "application/zip";
            
            case ".doc": return "application/msword";
            case ".docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case ".xls": return "application/vnd.ms-excel";
            case ".xlsx": return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case ".ppt": return "application/vnd.ms-powerpoint";
            case ".pptx": return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            
            default: return "application/octet-stream";
        }
    }

    /**
     * 判断是否为Office文档类型
     */
    private static boolean isOfficeDocument(String mimeType) {
        if (mimeType == null) return false;

        return mimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") ||
               mimeType.equals("application/msword") ||
               mimeType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") ||
               mimeType.equals("application/vnd.ms-excel") ||
               mimeType.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation") ||
               mimeType.equals("application/vnd.ms-powerpoint");
    }

    /**
     * 判断是否为图片文件
     */
    public static boolean isImageFile(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }
}
