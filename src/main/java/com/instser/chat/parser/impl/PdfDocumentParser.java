package com.instser.chat.parser.impl;

import com.instser.chat.parser.DocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Calendar;

/**
 * PDF文档解析器
 * 支持PDF格式文档的文本提取
 *
 * @author instser
 * @since 2025-06-27
 */
@Slf4j
public class PdfDocumentParser implements DocumentParser {

    @Override
    public String parseDocument(byte[] fileBytes, String fileName, String fileType) throws Exception {
        StringBuilder content = new StringBuilder();
        
        PDDocument document = null;
        try {
            // 使用PDFBox 2.0.29的API
            document = PDDocument.load(new ByteArrayInputStream(fileBytes));

            content.append("文档类型: PDF文档\n");

            // 获取文档信息
            PDDocumentInformation info = document.getDocumentInformation();
            if (info != null) {
                String title = info.getTitle();
                if (title != null && !title.trim().isEmpty()) {
                    content.append("标题: ").append(title.trim()).append("\n");
                }

                String author = info.getAuthor();
                if (author != null && !author.trim().isEmpty()) {
                    content.append("作者: ").append(author.trim()).append("\n");
                }

                String subject = info.getSubject();
                if (subject != null && !subject.trim().isEmpty()) {
                    content.append("主题: ").append(subject.trim()).append("\n");
                }

                Calendar creationDate = info.getCreationDate();
                if (creationDate != null) {
                    content.append("创建时间: ").append(creationDate.getTime()).append("\n");
                }
            }

            int pageCount = document.getNumberOfPages();
            content.append("页数: ").append(pageCount).append("页\n\n");

            // 提取文本内容
            PDFTextStripper stripper = new PDFTextStripper();

            // 限制最大页数，避免处理过大的文件
            int maxPages = Math.min(pageCount, 100);

            if (maxPages < pageCount) {
                content.append("注意: 文档页数过多，仅提取前").append(maxPages).append("页内容\n\n");
                stripper.setEndPage(maxPages);
            }

            // 设置排序模式，保持文本顺序
            stripper.setSortByPosition(true);

            String text = stripper.getText(document);

            if (text != null && !text.trim().isEmpty()) {
                // 清理文本内容
                String cleanedText = cleanPdfText(text);
                content.append("[文档内容]\n");
                content.append(cleanedText);
            } else {
                content.append("未能提取到文本内容（可能是图片PDF或加密PDF）\n");
            }

            if (pageCount > maxPages) {
                content.append("\n\n... (文档过长，仅显示前").append(maxPages).append("页内容)");
            }
        } finally {
            if (document != null) {
                try {
                    document.close();
                } catch (IOException e) {
                    log.warn("关闭PDF文档失败: {}", e.getMessage());
                }
            }
        }
        
        return content.toString().trim();
    }

    /**
     * 清理PDF提取的文本内容
     */
    private String cleanPdfText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        
        // 移除过多的空行
        String cleaned = text.replaceAll("\n{3,}", "\n\n");
        
        // 移除行首行尾的空白字符
        String[] lines = cleaned.split("\n");
        StringBuilder result = new StringBuilder();
        
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (!trimmedLine.isEmpty()) {
                result.append(trimmedLine).append("\n");
            } else if (result.length() > 0 && !result.toString().endsWith("\n\n")) {
                // 保留段落间的空行，但不超过一个
                result.append("\n");
            }
        }
        
        return result.toString().trim();
    }

    @Override
    public String[] getSupportedTypes() {
        return new String[]{
            "application/pdf"
        };
    }
}
