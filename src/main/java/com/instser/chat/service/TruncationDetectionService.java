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
     * 检测内容是否被截断（带Token数量限制）
     *
     * @param content AI回复内容
     * @param tokenCount 内容的Token数量
     * @return true-完整，false-截断
     */
    boolean isContentComplete(String content, int tokenCount);

    /**
     * 内容对齐算法
     *
     * @param originalContent 原始内容
     * @param continuationContent 续写内容
     * @return 对齐后的完整内容
     */
    String alignContent(String originalContent, String continuationContent);
    
    /**
     * 判断是否应用截断检测
     *
     * @param interfaceType 接口类型：chat、retry、edit-resend等
     * @return true-应用截断检测，false-不应用
     */
    boolean shouldApplyTruncationDetection(String interfaceType);
}
