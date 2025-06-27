package com.instser.chat.parser.impl;

import com.instser.chat.parser.DocumentParser;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

/**
 * 文本文档解析器
 * 支持各种文本格式文件
 *
 * @author instser
 * @since 2025-06-27
 */
@Slf4j
public class TextDocumentParser implements DocumentParser {

    @Override
    public String parseDocument(byte[] fileBytes, String fileName, String fileType) throws Exception {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new IllegalArgumentException("文件内容不能为空");
        }

        // 直接解析文本内容，保持与原来的逻辑一致
        String textContent = parseTextWithEncoding(fileBytes);

        if (textContent == null || textContent.trim().isEmpty()) {
            throw new IllegalArgumentException("文件内容为空或无法解析");
        }

        return textContent;
    }

    /**
     * 尝试使用不同编码解析文本内容
     */
    private String parseTextWithEncoding(byte[] fileBytes) {
        // 首先尝试UTF-8编码
        try {
            String content = new String(fileBytes, StandardCharsets.UTF_8);
            // 检查是否包含过多的控制字符，如果是则可能编码不正确
            if (!containsTooManyControlChars(content)) {
                return content;
            }
        } catch (Exception e) {
            log.debug("UTF-8编码解析失败: {}", e.getMessage());
        }
        
        // 尝试GBK编码（中文）
        try {
            String content = new String(fileBytes, "GBK");
            if (!containsTooManyControlChars(content)) {
                return content;
            }
        } catch (Exception e) {
            log.debug("GBK编码解析失败: {}", e.getMessage());
        }
        
        // 尝试ISO-8859-1编码
        try {
            String content = new String(fileBytes, "ISO-8859-1");
            if (!containsTooManyControlChars(content)) {
                return content;
            }
        } catch (Exception e) {
            log.debug("ISO-8859-1编码解析失败: {}", e.getMessage());
        }
        
        // 最后使用系统默认编码
        try {
            return new String(fileBytes);
        } catch (Exception e) {
            log.warn("所有编码方式都失败，返回空内容: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 检查字符串是否包含过多的控制字符
     * 如果控制字符比例超过10%，可能是编码错误
     */
    private boolean containsTooManyControlChars(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        
        int controlCharCount = 0;
        int totalChars = content.length();
        
        for (char c : content.toCharArray()) {
            // 检查是否为控制字符（除了常见的换行、制表符等）
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') {
                controlCharCount++;
            }
        }
        
        // 如果控制字符超过10%，认为可能是编码错误
        double controlCharRatio = (double) controlCharCount / totalChars;
        return controlCharRatio > 0.1;
    }

    @Override
    public String[] getSupportedTypes() {
        return new String[]{
            "text/plain",
            "text/html",
            "text/css",
            "text/javascript",
            "text/xml",
            "text/csv",
            "text/markdown",
            "application/json",
            "application/xml",
            "application/javascript"
        };
    }
}
