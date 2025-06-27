package com.instser.chat.parser.impl;

import com.instser.chat.parser.DocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.ByteArrayInputStream;
import java.util.List;

/**
 * Word文档解析器
 * 支持.doc和.docx格式
 *
 * @author instser
 * @since 2025-06-27
 */
@Slf4j
public class WordDocumentParser implements DocumentParser {

    @Override
    public String parseDocument(byte[] fileBytes, String fileName, String fileType) throws Exception {
        StringBuilder content = new StringBuilder();
        
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(fileBytes)) {
            if (fileType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
                // 处理.docx文件
                content.append(parseDocx(inputStream));
            } else if (fileType.equals("application/msword")) {
                // 处理.doc文件
                content.append(parseDoc(inputStream));
            } else {
                throw new IllegalArgumentException("不支持的Word文档格式: " + fileType);
            }
        }
        
        return content.toString().trim();
    }

    /**
     * 解析.docx文件
     */
    private String parseDocx(ByteArrayInputStream inputStream) throws Exception {
        StringBuilder content = new StringBuilder();
        
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            // 添加文档基本信息
            content.append("文档类型: Word文档 (.docx)\n");
            content.append("文档名称: ").append(getDocumentTitle(document)).append("\n\n");
            
            // 提取段落内容
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            for (XWPFParagraph paragraph : paragraphs) {
                String text = paragraph.getText().trim();
                if (!text.isEmpty()) {
                    content.append(text).append("\n");
                }
            }
            
            // 提取表格内容
            List<XWPFTable> tables = document.getTables();
            if (!tables.isEmpty()) {
                content.append("\n[表格内容]\n");
                for (int i = 0; i < tables.size(); i++) {
                    content.append("表格 ").append(i + 1).append(":\n");
                    content.append(parseTable(tables.get(i))).append("\n");
                }
            }
        }
        
        return content.toString();
    }

    /**
     * 解析.doc文件
     */
    private String parseDoc(ByteArrayInputStream inputStream) throws Exception {
        StringBuilder content = new StringBuilder();
        
        try (HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {
            
            content.append("文档类型: Word文档 (.doc)\n");
            content.append("文档内容:\n\n");
            
            String text = extractor.getText();
            content.append(text);
        }
        
        return content.toString();
    }

    /**
     * 解析表格内容
     */
    private String parseTable(XWPFTable table) {
        StringBuilder tableContent = new StringBuilder();
        
        List<XWPFTableRow> rows = table.getRows();
        for (XWPFTableRow row : rows) {
            List<XWPFTableCell> cells = row.getTableCells();
            for (int i = 0; i < cells.size(); i++) {
                String cellText = cells.get(i).getText().trim();
                tableContent.append(cellText);
                if (i < cells.size() - 1) {
                    tableContent.append(" | ");
                }
            }
            tableContent.append("\n");
        }
        
        return tableContent.toString();
    }

    /**
     * 获取文档标题
     */
    private String getDocumentTitle(XWPFDocument document) {
        try {
            String title = document.getProperties().getCoreProperties().getTitle();
            return title != null && !title.trim().isEmpty() ? title : "未命名文档";
        } catch (Exception e) {
            return "未命名文档";
        }
    }

    @Override
    public String[] getSupportedTypes() {
        return new String[]{
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        };
    }
}
