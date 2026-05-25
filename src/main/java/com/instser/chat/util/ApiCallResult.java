package com.instser.chat.util;

/**
 * 外部 API 调用结果封装
 */
public class ApiCallResult<T> {

    private final boolean success;
    private final T data;
    private final int statusCode;
    private final long costMs;
    private final String errorType;
    private final String errorMessage;

    private ApiCallResult(boolean success, T data, int statusCode, long costMs,
                          String errorType, String errorMessage) {
        this.success = success;
        this.data = data;
        this.statusCode = statusCode;
        this.costMs = costMs;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
    }

    public static <T> ApiCallResult<T> success(T data, int statusCode, long costMs) {
        return new ApiCallResult<>(true, data, statusCode, costMs, null, null);
    }

    public static <T> ApiCallResult<T> clientError(int statusCode, T data, long costMs) {
        return new ApiCallResult<>(false, data, statusCode, costMs, "client_error", null);
    }

    public static <T> ApiCallResult<T> networkError(String errorType, String errorMessage, long costMs) {
        return new ApiCallResult<>(false, null, 0, costMs, errorType, errorMessage);
    }

    public boolean isSuccess() { return success; }
    public T getData() { return data; }
    public int getStatusCode() { return statusCode; }
    public long getCostMs() { return costMs; }
    public String getErrorType() { return errorType; }
    public String getErrorMessage() { return errorMessage; }
}
