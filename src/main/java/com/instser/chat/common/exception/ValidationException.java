package com.instser.chat.common.exception;

import com.instser.chat.common.result.ResultCode;

/**
 * 数据验证异常
 *
 * @author instser
 * @since 2024-01-01
 */
public class ValidationException extends BusinessException {

    public ValidationException(String message) {
        super(ResultCode.VALIDATE_FAILED.getCode(), message);
    }

    public ValidationException(String message, String detail) {
        super(ResultCode.VALIDATE_FAILED.getCode(), message, detail);
    }

    /**
     * 参数为空异常
     */
    public static ValidationException parameterRequired(String paramName) {
        return new ValidationException("参数不能为空", "参数名: " + paramName);
    }

    /**
     * 参数格式错误
     */
    public static ValidationException invalidFormat(String paramName, String value) {
        return new ValidationException("参数格式错误", "参数名: " + paramName + ", 值: " + value);
    }

    /**
     * 参数长度超限
     */
    public static ValidationException lengthExceeded(String paramName, int maxLength) {
        return new ValidationException("参数长度超限", "参数名: " + paramName + ", 最大长度: " + maxLength);
    }

    /**
     * 邮箱格式错误
     */
    public static ValidationException invalidEmail(String email) {
        return new ValidationException("邮箱格式错误", "邮箱: " + email);
    }

    /**
     * 密码强度不足
     */
    public static ValidationException weakPassword() {
        return new ValidationException("密码强度不足", "密码至少包含8位字符，包括字母和数字");
    }

    /**
     * 用户名已存在
     */
    public static ValidationException usernameExists(String username) {
        return new ValidationException("用户名已存在", "用户名: " + username);
    }

    /**
     * 邮箱已存在
     */
    public static ValidationException emailExists(String email) {
        return new ValidationException("邮箱已存在", "邮箱: " + email);
    }
}
