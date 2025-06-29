package com.instser.chat.service.serviceimpl;

import com.instser.chat.parser.DocumentParser;
import com.instser.chat.parser.DocumentParserFactory;
import com.instser.chat.service.DocumentParserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 文档解析服务实现
 *
 * @author instser
 * @since 2025-06-27
 */
@Slf4j
@Service
public class DocumentParserServiceImpl implements DocumentParserService {

    @Override
    public String parseDocument(byte[] fileBytes, String fileName, String fileType) {
        if (fileBytes == null || fileBytes.length == 0) {
            log.warn("文档解析失败: {} - 文件内容为空", fileName);
            throw new RuntimeException("文件内容不能为空");
        }

        if (!isDocumentFile(fileType)) {
            log.warn("文档解析失败: {} - 不支持的文档类型: {}", fileName, fileType);
            throw new RuntimeException("不支持的文档类型: " + fileType);
        }

        try {
            log.info("开始解析文档: {} (类型: {}, 大小: {} bytes)", fileName, fileType, fileBytes.length);

            DocumentParser parser = DocumentParserFactory.getParser(fileType);
            String content = parser.parseDocument(fileBytes, fileName, fileType);

            if (content == null || content.trim().isEmpty()) {
                log.warn("文档解析失败: {} - 解析结果为空", fileName);
                throw new RuntimeException("文档解析结果为空");
            }

            log.info("文档解析成功: {} (解析内容长度: {} 字符)", fileName, content.length());
            return content;

        } catch (Exception e) {
            log.error("文档解析失败: {} - {}", fileName, e.getMessage(), e);
            throw new RuntimeException("文档解析失败: " + e.getMessage());
        }
    }

    @Override
    public boolean isDocumentFile(String fileType) {
        return DocumentParserFactory.isDocumentFile(fileType);
    }
}
