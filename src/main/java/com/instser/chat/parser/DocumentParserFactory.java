package com.instser.chat.parser;

import com.instser.chat.parser.impl.ExcelDocumentParser;
import com.instser.chat.parser.impl.ImageDocumentParser;
import com.instser.chat.parser.impl.PdfDocumentParser;
import com.instser.chat.parser.impl.PowerPointDocumentParser;
import com.instser.chat.parser.impl.TextDocumentParser;
import com.instser.chat.parser.impl.WordDocumentParser;
import com.instser.chat.util.MagicNumberDetector;

/**
 * 文档解析器工厂
 * 根据文件类型返回对应的解析器
 *
 * @author instser
 * @since 2025-06-27
 */
public class DocumentParserFactory {

    /**
     * 根据文件类型获取对应的解析器
     *
     * @param fileType MIME类型
     * @return 文档解析器实例
     * @throws IllegalArgumentException 不支持的文件类型
     */
    public static DocumentParser getParser(String fileType) {
        if (fileType == null || fileType.trim().isEmpty()) {
            throw new IllegalArgumentException("文件类型不能为空");
        }

        String lowerCaseType = fileType.toLowerCase().trim();

        // 检查是否为压缩文件（不支持）
        if (MagicNumberDetector.isCompressedFile(lowerCaseType)) {
            throw new IllegalArgumentException("不支持压缩文件类型: " + fileType);
        }

        // 检查是否为图片文件
        if (MagicNumberDetector.isImageFile(lowerCaseType)) {
            return new ImageDocumentParser();
        }

        // 检查是否为文本文件
        if (lowerCaseType.startsWith("text/") || isTextBasedFormat(lowerCaseType)) {
            return new TextDocumentParser();
        }

        switch (lowerCaseType) {
            // Word文档
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
            case "application/msword":
                return new WordDocumentParser();

            // Excel文档
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":
            case "application/vnd.ms-excel":
                return new ExcelDocumentParser();

            // PowerPoint文档
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation":
            case "application/vnd.ms-powerpoint":
                return new PowerPointDocumentParser();

            // PDF文档
            case "application/pdf":
                return new PdfDocumentParser();

            default:
                throw new IllegalArgumentException("不支持的文档类型: " + fileType);
        }
    }

    /**
     * 判断是否为支持的文档类型
     *
     * @param fileType MIME类型
     * @return 是否支持
     */
    public static boolean isDocumentFile(String fileType) {
        if (fileType == null || fileType.trim().isEmpty()) {
            return false;
        }

        String lowerCaseType = fileType.toLowerCase().trim();

        // 不支持压缩文件
        if (MagicNumberDetector.isCompressedFile(lowerCaseType)) {
            return false;
        }

        // 支持图片文件
        if (MagicNumberDetector.isImageFile(lowerCaseType)) {
            return true;
        }

        // 支持文本文件
        if (lowerCaseType.startsWith("text/") || isTextBasedFormat(lowerCaseType)) {
            return true;
        }

        // 支持Office文档和PDF
        return lowerCaseType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") ||
               lowerCaseType.equals("application/msword") ||
               lowerCaseType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") ||
               lowerCaseType.equals("application/vnd.ms-excel") ||
               lowerCaseType.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation") ||
               lowerCaseType.equals("application/vnd.ms-powerpoint") ||
               lowerCaseType.equals("application/pdf");
    }

    /**
     * 判断是否为基于文本的格式
     *
     * @param fileType MIME类型（已转为小写）
     * @return 是否为文本格式
     */
    private static boolean isTextBasedFormat(String fileType) {
        return fileType.equals("application/json") ||
               fileType.equals("application/xml") ||
               fileType.equals("application/javascript");
    }
}
