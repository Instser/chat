package com.instser.chat.common.exception;

import com.instser.chat.common.result.ResultCode;

/**
 * 自定义业务异常
 *
 * @author instser
 * @since 2024-01-01
 */
public class BusinessException extends RuntimeException {

    /**
     * 错误码
     */
    private Integer code;

    /**
     * 错误详细信息
     */
    private String detail;

    public BusinessException() {
        super();
        this.code = ResultCode.FAILED.getCode();
    }

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.FAILED.getCode();
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.code = ResultCode.FAILED.getCode();
    }

    public BusinessException(Throwable cause) {
        super(cause);
        this.code = ResultCode.FAILED.getCode();
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String detail) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
        this.detail = detail;
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(Integer code, String message, String detail) {
        super(message);
        this.code = code;
        this.detail = detail;
    }

    public Integer getCode() {
        return code;
    }

    public String getDetail() {
        return detail;
    }
}
