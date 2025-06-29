package com.instser.chat.common.util;

import com.instser.chat.common.result.Result;
import com.instser.chat.common.result.ResultCode;

import java.util.HashMap;
import java.util.Map;

/**
 * 错误响应工具类
 * 用于统一处理非聊天类API的错误响应格式
 * 
 * @author instser
 * @since 2025-06-29
 */
public class ErrorResponseUtil {

    /**
     * 创建标准错误响应
     * 使用Result.success返回，在data中包含错误码和错误消息
     * 
     * @param resultCode 错误码枚举
     * @param message 自定义错误消息（可选）
     * @return 标准错误响应
     */
    public static Result<Map<String, Object>> createErrorResponse(ResultCode resultCode, String message) {
        Map<String, Object> errorData = new HashMap<>();
        errorData.put("code", resultCode.getCode());
        errorData.put("message", message != null ? message : resultCode.getMessage());
        return Result.success(errorData);
    }

    /**
     * 创建标准错误响应（使用默认消息）
     * 
     * @param resultCode 错误码枚举
     * @return 标准错误响应
     */
    public static Result<Map<String, Object>> createErrorResponse(ResultCode resultCode) {
        return createErrorResponse(resultCode, null);
    }

    // ==================== 认证授权相关错误 ====================

    /**
     * 登录失败错误
     */
    public static Result<Map<String, Object>> loginFailed(String message) {
        return createErrorResponse(ResultCode.AUTH_LOGIN_FAILED, message);
    }

    /**
     * 注册失败错误
     */
    public static Result<Map<String, Object>> registerFailed(String message) {
        return createErrorResponse(ResultCode.AUTH_REGISTER_FAILED, message);
    }

    /**
     * 用户名已存在错误
     */
    public static Result<Map<String, Object>> usernameExists() {
        return createErrorResponse(ResultCode.AUTH_USERNAME_EXISTS);
    }

    /**
     * 邮箱已存在错误
     */
    public static Result<Map<String, Object>> emailExists() {
        return createErrorResponse(ResultCode.AUTH_EMAIL_EXISTS);
    }

    /**
     * 用户名或密码错误
     */
    public static Result<Map<String, Object>> invalidCredentials() {
        return createErrorResponse(ResultCode.AUTH_INVALID_CREDENTIALS);
    }

    /**
     * 用户不存在错误
     */
    public static Result<Map<String, Object>> userNotFound() {
        return createErrorResponse(ResultCode.AUTH_USER_NOT_FOUND);
    }

    /**
     * 权限不足错误
     */
    public static Result<Map<String, Object>> permissionDenied() {
        return createErrorResponse(ResultCode.AUTH_PERMISSION_DENIED);
    }

    // ==================== 支付相关错误 ====================

    /**
     * 创建支付订单失败
     */
    public static Result<Map<String, Object>> paymentCreateFailed(String message) {
        return createErrorResponse(ResultCode.PAYMENT_CREATE_FAILED, message);
    }

    /**
     * 订单不存在错误
     */
    public static Result<Map<String, Object>> orderNotFound() {
        return createErrorResponse(ResultCode.PAYMENT_ORDER_NOT_FOUND);
    }

    /**
     * 查询支付状态失败
     */
    public static Result<Map<String, Object>> paymentStatusQueryFailed() {
        return createErrorResponse(ResultCode.PAYMENT_STATUS_QUERY_FAILED);
    }

    /**
     * 支付回调处理失败
     */
    public static Result<Map<String, Object>> paymentCallbackFailed() {
        return createErrorResponse(ResultCode.PAYMENT_CALLBACK_FAILED);
    }

    /**
     * 套餐不存在错误
     */
    public static Result<Map<String, Object>> packageNotFound() {
        return createErrorResponse(ResultCode.PAYMENT_PACKAGE_NOT_FOUND);
    }

    // ==================== API接口相关错误 ====================

    /**
     * AI模型不存在错误
     */
    public static Result<Map<String, Object>> modelNotFound() {
        return createErrorResponse(ResultCode.API_MODEL_NOT_FOUND);
    }

    /**
     * AI模型不可用错误
     */
    public static Result<Map<String, Object>> modelUnavailable() {
        return createErrorResponse(ResultCode.API_MODEL_UNAVAILABLE);
    }

    /**
     * 会话不存在错误
     */
    public static Result<Map<String, Object>> sessionNotFound() {
        return createErrorResponse(ResultCode.API_SESSION_NOT_FOUND);
    }

    /**
     * 消息不存在错误
     */
    public static Result<Map<String, Object>> messageNotFound() {
        return createErrorResponse(ResultCode.API_MESSAGE_NOT_FOUND);
    }

    /**
     * 参数无效错误
     */
    public static Result<Map<String, Object>> invalidParameter(String message) {
        return createErrorResponse(ResultCode.API_INVALID_PARAMETER, message);
    }

    /**
     * 缺少必需参数错误
     */
    public static Result<Map<String, Object>> missingParameter(String parameterName) {
        return createErrorResponse(ResultCode.API_MISSING_PARAMETER, "Missing required parameter: " + parameterName);
    }

    /**
     * 操作失败错误
     */
    public static Result<Map<String, Object>> operationFailed(String message) {
        return createErrorResponse(ResultCode.API_OPERATION_FAILED, message);
    }

    /**
     * 资源不存在错误
     */
    public static Result<Map<String, Object>> resourceNotFound(String resource) {
        return createErrorResponse(ResultCode.API_RESOURCE_NOT_FOUND, resource + " not found");
    }

    // ==================== 文件上传相关错误 ====================

    /**
     * 文件上传失败错误
     */
    public static Result<Map<String, Object>> fileUploadFailed() {
        return createErrorResponse(ResultCode.FILE_UPLOAD_FAILED, "File upload failed. Please try again later.");
    }

    /**
     * 文件大小超限错误
     */
    public static Result<Map<String, Object>> fileSizeExceeded() {
        return createErrorResponse(ResultCode.FILE_SIZE_EXCEEDED, "File size exceeds limit");
    }

    /**
     * 不支持的文件类型错误
     */
    public static Result<Map<String, Object>> fileTypeNotSupported() {
        return createErrorResponse(ResultCode.FILE_TYPE_NOT_SUPPORTED, "File type not supported");
    }

    /**
     * 文件解析失败错误
     */
    public static Result<Map<String, Object>> fileParseFailed() {
        return createErrorResponse(ResultCode.FILE_PARSE_FAILED, "File parsing failed");
    }

    /**
     * 文件为空错误
     */
    public static Result<Map<String, Object>> fileEmpty() {
        return createErrorResponse(ResultCode.FILE_EMPTY, "No files uploaded");
    }

    /**
     * 文件不存在错误
     */
    public static Result<Map<String, Object>> fileNotFound() {
        return createErrorResponse(ResultCode.FILE_NOT_FOUND, "File not found");
    }

    // ==================== 通用错误 ====================

    /**
     * 参数验证失败错误
     */
    public static Result<Map<String, Object>> validateFailed(String message) {
        return createErrorResponse(ResultCode.VALIDATE_FAILED, message);
    }

    /**
     * 未授权错误
     */
    public static Result<Map<String, Object>> unauthorized(String message) {
        return createErrorResponse(ResultCode.UNAUTHORIZED, message);
    }

    /**
     * 服务器内部错误
     */
    public static Result<Map<String, Object>> internalError(String message) {
        return createErrorResponse(ResultCode.FAILED, message);
    }
}
