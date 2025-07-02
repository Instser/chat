package com.instser.chat.service;

/**
 * 截断检测服务接口
 *
 * @author instser
 * @since 2025-07-02
 */
public interface TruncationDetectionService {

    /**
     * 检测内容是否被截断
     * 
     * @param content AI回复内容
     * @return true-完整，false-截断
     */
    boolean isContentComplete(String content);
    
    /**
     * 内容对齐算法
     * 
     * @param originalContent 原始内容
     * @param continuationContent 续写内容
     * @return 对齐后的完整内容
     */
    String alignContent(String originalContent, String continuationContent);
    
    /**
     * 判断是否需要排除截断检测
     * 
     * @param hasFiles 是否包含文件
     * @param isTitleGeneration 是否为标题生成
     * @return true-需要排除，false-需要检测
     */
    boolean shouldSkipTruncationDetection(boolean hasFiles, boolean isTitleGeneration);
}
