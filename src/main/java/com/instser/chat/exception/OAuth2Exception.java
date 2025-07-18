package com.instser.chat.exception;

/**
 * OAuth2异常类
 *
 * @author instser
 * @since 2025-07-16
 */
public class OAuth2Exception extends RuntimeException {
    
    private final String errorCode;

    public OAuth2Exception(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public OAuth2Exception(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

/**
 * OAuth2错误代码常量
 */
class OAuth2ErrorCode {
    public static final String INVALID_PROVIDER = "INVALID_PROVIDER";
    public static final String INVALID_STATE = "INVALID_STATE";
    public static final String EXPIRED_STATE = "EXPIRED_STATE";
    public static final String INVALID_CODE = "INVALID_CODE";
    public static final String NETWORK_ERROR = "NETWORK_ERROR";
    public static final String USER_INFO_ERROR = "USER_INFO_ERROR";
    public static final String TOKEN_ENCRYPTION_ERROR = "TOKEN_ENCRYPTION_ERROR";
    public static final String ACCOUNT_DISABLED = "ACCOUNT_DISABLED";
    public static final String BINDING_EXISTS = "BINDING_EXISTS";
    public static final String BINDING_NOT_FOUND = "BINDING_NOT_FOUND";
    public static final String URL_GENERATION_FAILED = "URL_GENERATION_FAILED";
    public static final String RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String SYSTEM_ERROR = "SYSTEM_ERROR";
}
