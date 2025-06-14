package com.instser.chat.common.exception;

/**
 * 外部服务调用异常
 *
 * @author instser
 * @since 2024-01-01
 */
public class ExternalServiceException extends BusinessException {

    public ExternalServiceException(String message) {
        super(message);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExternalServiceException(String message, String detail) {
        super(500, message, detail);
    }

    /**
     * AI服务调用异常
     */
    public static ExternalServiceException aiServiceError(String modelName, String error) {
        return new ExternalServiceException("AI服务调用失败", "模型: " + modelName + ", 错误: " + error);
    }

    /**
     * 支付服务调用异常
     */
    public static ExternalServiceException paymentServiceError(String paymentMethod, String error) {
        return new ExternalServiceException("支付服务调用失败", "支付方式: " + paymentMethod + ", 错误: " + error);
    }

    /**
     * 网络连接超时
     */
    public static ExternalServiceException connectionTimeout(String serviceName) {
        return new ExternalServiceException("服务连接超时", "服务: " + serviceName);
    }

    /**
     * 服务不可用
     */
    public static ExternalServiceException serviceUnavailable(String serviceName) {
        return new ExternalServiceException("服务不可用", "服务: " + serviceName);
    }

    /**
     * API限流异常
     */
    public static ExternalServiceException rateLimitExceeded(String serviceName) {
        return new ExternalServiceException("API调用频率超限", "服务: " + serviceName);
    }

    /**
     * API密钥无效
     */
    public static ExternalServiceException invalidApiKey(String serviceName) {
        return new ExternalServiceException("API密钥无效", "服务: " + serviceName);
    }
}
