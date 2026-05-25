package com.instser.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ErrorAnalysisRequest {

    @NotBlank(message = "异常日志不能为空")
    private String errorLog;

    private String contextInfo;
}
