package com.instser.chat.common.exception;

/**
 * 网络搜索内容修改异常
 *
 * @author instser
 * @since 2025-07-20
 */
public class WebSearchException extends RuntimeException {

    private final WebSearchErrorCode errorCode;
    private final String detail;

    /**
     * 构造函数
     *
     * @param errorCode 错误码
     */
    public WebSearchException(WebSearchErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detail = null;
    }

    /**
     * 构造函数
     *
     * @param errorCode 错误码
     * @param detail    详细信息
     */
    public WebSearchException(WebSearchErrorCode errorCode, String detail) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detail = detail;
    }

    /**
     * 构造函数
     *
     * @param errorCode 错误码
     * @param cause     原因异常
     */
    public WebSearchException(WebSearchErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.detail = cause != null ? cause.getMessage() : null;
    }

    /**
     * 构造函数
     *
     * @param errorCode 错误码
     * @param detail    详细信息
     * @param cause     原因异常
     */
    public WebSearchException(WebSearchErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.detail = detail;
    }

    /**
     * 获取错误码
     *
     * @return 错误码
     */
    public WebSearchErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * 获取详细信息
     *
     * @return 详细信息
     */
    public String getDetail() {
        return detail;
    }

    /**
     * 获取错误码数值
     *
     * @return 错误码数值
     */
    public int getCode() {
        return errorCode.getCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("WebSearchException{");
        sb.append("code=").append(errorCode.getCode());
        sb.append(", message='").append(errorCode.getMessage()).append('\'');
        if (detail != null) {
            sb.append(", detail='").append(detail).append('\'');
        }
        sb.append('}');
        return sb.toString();
    }
}
