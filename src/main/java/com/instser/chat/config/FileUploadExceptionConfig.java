package com.instser.chat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.common.result.Result;
import com.instser.chat.common.util.ErrorResponseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * 文件上传异常配置
 * 处理在Servlet容器层面被拦截的文件上传大小超限异常
 * 
 * @author instser
 * @since 2025-06-30
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class FileUploadExceptionConfig implements ErrorController {

    private final ObjectMapper objectMapper;

    /**
     * 处理错误页面请求
     * 特别处理文件上传大小超限的情况
     */
    @RequestMapping("/error")
    @ResponseBody
    public Result<Map<String, Object>> handleError(HttpServletRequest request, HttpServletResponse response) {
        // 获取错误状态码
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        
        // 获取异常信息
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        Object exceptionType = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE);
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        
        String requestUrl = request.getRequestURL().toString();
        String method = request.getMethod();
        
        log.debug("错误处理 - 状态码: {}, 异常类型: {}, 异常: {}, 消息: {}", 
                status, exceptionType, exception, message);
        
        // 检查是否是文件上传大小超限异常
        if (isMaxUploadSizeExceeded(exception, exceptionType, message)) {
            log.error("文件上传大小超限 [{}] {}: 上传文件大小超出限制 - {}", 
                    method, requestUrl, message);
            
            // 设置响应状态码为200，错误信息在响应体中
            response.setStatus(HttpStatus.OK.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            
            Result<Map<String, Object>> result = ErrorResponseUtil.fileSizeExceeded();
            log.info("返回文件大小超限错误响应: code={}, message={}", 
                    result.getData().get("code"), 
                    result.getData().get("message"));
            
            return result;
        }
        
        // 其他错误的默认处理
        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());
            
            switch (statusCode) {
                case 404:
                    log.warn("页面未找到 [{}] {}", method, requestUrl);
                    return ErrorResponseUtil.createErrorResponse(
                        com.instser.chat.common.result.ResultCode.NOT_FOUND,
                        "Resource not found"
                    );
                case 500:
                    log.error("服务器内部错误 [{}] {}: {}", method, requestUrl, message);
                    return ErrorResponseUtil.createErrorResponse(
                        com.instser.chat.common.result.ResultCode.FAILED,
                        "Internal server error"
                    );
                default:
                    log.error("未知错误 [{}] {} - 状态码: {}, 消息: {}", method, requestUrl, statusCode, message);
                    return ErrorResponseUtil.createErrorResponse(
                        com.instser.chat.common.result.ResultCode.FAILED,
                        "Unknown error occurred"
                    );
            }
        }
        
        // 默认错误响应
        log.error("未知错误 [{}] {}: {}", method, requestUrl, message);
        return ErrorResponseUtil.createErrorResponse(
            com.instser.chat.common.result.ResultCode.FAILED,
            "Unknown error occurred"
        );
    }
    
    /**
     * 检查是否是文件上传大小超限异常
     */
    private boolean isMaxUploadSizeExceeded(Object exception, Object exceptionType, Object message) {
        // 检查异常实例
        if (exception instanceof MaxUploadSizeExceededException) {
            return true;
        }
        
        // 检查异常类型名称
        if (exceptionType != null && exceptionType.toString().contains("MaxUploadSizeExceededException")) {
            return true;
        }
        
        // 检查错误消息
        if (message != null) {
            String messageStr = message.toString().toLowerCase();
            return messageStr.contains("maximum upload size") || 
                   messageStr.contains("upload size exceeded") ||
                   messageStr.contains("文件大小超出限制");
        }
        
        return false;
    }
}
