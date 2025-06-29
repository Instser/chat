package com.instser.chat.common.exception;

/**
 * 文件上传异常类
 * 用于区分不同类型的文件上传错误
 *
 * @author instser
 * @since 2025-06-29
 */
public class FileUploadException extends RuntimeException {

    /**
     * 错误类型
     */
    private final String errorType;

    /**
     * 错误消息
     */
    private final String errorMessage;

    public FileUploadException(String errorType, String errorMessage) {
        super(errorMessage);
        this.errorType = errorType;
        this.errorMessage = errorMessage;
    }

    public FileUploadException(String errorType, String errorMessage, Throwable cause) {
        super(errorMessage, cause);
        this.errorType = errorType;
        this.errorMessage = errorMessage;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
