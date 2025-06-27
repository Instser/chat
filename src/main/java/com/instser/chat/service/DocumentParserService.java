package com.instser.chat.service;

/**
 * 文档解析服务接口
 * 支持Word、Excel、PowerPoint、PDF等文档格式的文本内容提取
 *
 * @author instser
 * @since 2025-06-27
 */
public interface DocumentParserService {

    /**
     * 解析文档内容，提取文本信息
     *
     * @param fileBytes 文件字节数组
     * @param fileName  文件名
     * @param fileType  MIME类型
     * @return 解析后的文本内容
     * @throws RuntimeException 解析失败时抛出异常
     */
    String parseDocument(byte[] fileBytes, String fileName, String fileType);

    /**
     * 判断是否为支持解析的文档类型
     *
     * @param fileType MIME类型
     * @return 是否支持解析
     */
    boolean isDocumentFile(String fileType);
}
