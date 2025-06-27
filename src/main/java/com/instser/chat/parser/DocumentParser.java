package com.instser.chat.parser;

/**
 * 文档解析器接口
 * 定义文档解析的通用方法
 *
 * @author instser
 * @since 2025-06-27
 */
public interface DocumentParser {

    /**
     * 解析文档内容
     *
     * @param fileBytes 文件字节数组
     * @param fileName  文件名
     * @param fileType  MIME类型
     * @return 解析后的文本内容
     * @throws Exception 解析失败时抛出异常
     */
    String parseDocument(byte[] fileBytes, String fileName, String fileType) throws Exception;

    /**
     * 获取支持的文件类型
     *
     * @return 支持的MIME类型数组
     */
    String[] getSupportedTypes();
}
