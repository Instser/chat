package com.instser.chat.parser.impl;

import com.instser.chat.parser.DocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextParagraph;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.sl.usermodel.Shape;
import org.apache.poi.sl.usermodel.TextShape;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import java.io.ByteArrayInputStream;
import java.util.List;

/**
 * PowerPoint文档解析器
 * 支持.ppt和.pptx格式
 *
 * @author instser
 * @since 2025-06-27
 */
@Slf4j
public class PowerPointDocumentParser implements DocumentParser {

    @Override
    public String parseDocument(byte[] fileBytes, String fileName, String fileType) throws Exception {
        StringBuilder content = new StringBuilder();
        
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(fileBytes)) {
            if (fileType.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation")) {
                // 处理.pptx文件
                content.append(parsePptx(inputStream, fileName));
            } else if (fileType.equals("application/vnd.ms-powerpoint")) {
                // 处理.ppt文件
                content.append(parsePpt(inputStream, fileName));
            } else {
                throw new IllegalArgumentException("不支持的PowerPoint文档格式: " + fileType);
            }
        }
        
        return content.toString().trim();
    }

    /**
     * 解析.pptx文件
     */
    private String parsePptx(ByteArrayInputStream inputStream, String fileName) throws Exception {
        StringBuilder content = new StringBuilder();

        try (XMLSlideShow ppt = new XMLSlideShow(inputStream)) {
            content.append("文档类型: PowerPoint文档 (.pptx)\n");
            content.append("文件名: ").append(getFileName(fileName)).append("\n");
            content.append("元数据文档名: ").append(getDocumentTitleFromMetadata(ppt)).append("\n");

            List<XSLFSlide> slides = ppt.getSlides();
            content.append("幻灯片总数: ").append(slides.size()).append("\n\n");
            
            for (int i = 0; i < slides.size(); i++) {
                XSLFSlide slide = slides.get(i);
                content.append(parseXSLFSlide(slide, i + 1));
            }
        }
        
        return content.toString();
    }

    /**
     * 解析.ppt文件
     */
    private String parsePpt(ByteArrayInputStream inputStream, String fileName) throws Exception {
        StringBuilder content = new StringBuilder();

        try (HSLFSlideShow ppt = new HSLFSlideShow(inputStream)) {
            content.append("文档类型: PowerPoint文档 (.ppt)\n");
            content.append("文件名: ").append(getFileName(fileName)).append("\n");
            content.append("元数据文档名: ").append(getDocumentTitleFromMetadata(ppt)).append("\n");

            List<HSLFSlide> slides = ppt.getSlides();
            content.append("幻灯片总数: ").append(slides.size()).append("\n\n");
            
            for (int i = 0; i < slides.size(); i++) {
                HSLFSlide slide = slides.get(i);
                content.append(parseHSLFSlide(slide, i + 1));
            }
        }
        
        return content.toString();
    }

    /**
     * 解析XSLF幻灯片（.pptx）
     */
    private String parseXSLFSlide(XSLFSlide slide, int slideNumber) {
        StringBuilder slideContent = new StringBuilder();
        slideContent.append("[幻灯片").append(slideNumber).append("]\n");
        
        // 获取幻灯片标题
        String title = slide.getTitle();
        if (title != null && !title.trim().isEmpty()) {
            slideContent.append("标题: ").append(title.trim()).append("\n");
        }
        
        // 获取所有形状中的文本
        for (Shape<?, ?> shape : slide.getShapes()) {
            if (shape instanceof XSLFTextShape) {
                XSLFTextShape textShape = (XSLFTextShape) shape;
                String text = textShape.getText();
                if (text != null && !text.trim().isEmpty() && !text.equals(title)) {
                    slideContent.append("内容: ").append(text.trim()).append("\n");
                }
            }
        }
        
        // 获取其他形状中的文本
        for (Shape<?, ?> shape : slide.getShapes()) {
            if (shape instanceof TextShape) {
                TextShape<?, ?> textShape = (TextShape<?, ?>) shape;
                String text = textShape.getText();
                if (text != null && !text.trim().isEmpty() && !text.equals(title)) {
                    slideContent.append("文本: ").append(text.trim()).append("\n");
                }
            }
        }
        
        // 获取备注 - 简化处理
        try {
            if (slide.getNotes() != null) {
                slideContent.append("备注: 包含备注内容\n");
            }
        } catch (Exception e) {
            log.debug("获取幻灯片备注失败: {}", e.getMessage());
        }
        
        slideContent.append("\n");
        return slideContent.toString();
    }

    /**
     * 解析HSLF幻灯片（.ppt）
     */
    private String parseHSLFSlide(HSLFSlide slide, int slideNumber) {
        StringBuilder slideContent = new StringBuilder();
        slideContent.append("[幻灯片").append(slideNumber).append("]\n");
        
        // 获取幻灯片标题
        String title = slide.getTitle();
        if (title != null && !title.trim().isEmpty()) {
            slideContent.append("标题: ").append(title.trim()).append("\n");
        }
        
        // 获取所有形状中的文本
        for (Shape<?, ?> shape : slide.getShapes()) {
            if (shape instanceof HSLFTextShape) {
                HSLFTextShape textShape = (HSLFTextShape) shape;
                String text = textShape.getText();
                if (text != null && !text.trim().isEmpty() && !text.equals(title)) {
                    slideContent.append("内容: ").append(text.trim()).append("\n");
                }
            }
        }

        // 获取备注 - 简化处理
        try {
            if (slide.getNotes() != null) {
                slideContent.append("备注: 包含备注内容\n");
            }
        } catch (Exception e) {
            log.debug("获取幻灯片备注失败: {}", e.getMessage());
        }
        
        slideContent.append("\n");
        return slideContent.toString();
    }



    /**
     * 获取文件名
     * 返回原始文件名，如果为空则返回默认值
     */
    private String getFileName(String fileName) {
        if (fileName != null && !fileName.trim().isEmpty()) {
            return fileName.trim();
        }
        return "未知文件";
    }

    /**
     * 从PowerPoint文档元数据获取标题
     * 支持PPTX格式
     */
    private String getDocumentTitleFromMetadata(XMLSlideShow ppt) {
        if (ppt != null) {
            try {
                if (ppt.getProperties() != null &&
                    ppt.getProperties().getCoreProperties() != null) {
                    String title = ppt.getProperties().getCoreProperties().getTitle();
                    if (title != null && !title.trim().isEmpty()) {
                        return title.trim();
                    }
                }
            } catch (Exception e) {
                log.debug("无法获取PPTX文档元数据标题: {}", e.getMessage());
            }
        }
        return "无标题";
    }

    /**
     * 从PowerPoint文档元数据获取标题
     * 支持PPT格式
     */
    private String getDocumentTitleFromMetadata(HSLFSlideShow ppt) {
        if (ppt != null) {
            try {
                // PPT格式的元数据获取相对复杂，这里简化处理
                // 可以尝试从文档属性中获取，但API可能不同
                // 暂时返回默认值，避免API兼容性问题
                log.debug("PPT格式暂不支持元数据标题获取");
            } catch (Exception e) {
                log.debug("无法获取PPT文档元数据标题: {}", e.getMessage());
            }
        }
        return "无标题";
    }

    @Override
    public String[] getSupportedTypes() {
        return new String[]{
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        };
    }
}
