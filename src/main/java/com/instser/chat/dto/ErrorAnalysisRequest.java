package com.instser.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ErrorAnalysisRequest {

    /**
     * 异常日志原文，长度限制防止超大 payload 触发昂贵 AI 调用。
     */
    @NotBlank(message = "异常日志不能为空")
    @Size(max = 8000, message = "异常日志长度不能超过8000字符")
    private String errorLog;

    @Size(max = 2000, message = "上下文信息长度不能超过2000字符")
    private String contextInfo;
}
