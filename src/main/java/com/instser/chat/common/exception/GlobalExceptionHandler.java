package com.instser.chat.common.exception;

import com.instser.chat.common.result.Result;
import com.instser.chat.common.result.ResultCode;
import com.instser.chat.common.util.ErrorResponseUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理自定义业务异常
     */
    @ExceptionHandler(value = BusinessException.class)
    public Result<String> handleBusinessException(BusinessException e, HttpServletRequest request) {
        String requestUrl = request.getRequestURL().toString();
        String method = request.getMethod();

        if (e.getDetail() != null) {
            log.error("业务异常 [{}] {}: {} - {}", method, requestUrl, e.getMessage(), e.getDetail());
        } else {
            log.error("业务异常 [{}] {}: {}", method, requestUrl, e.getMessage());
        }

        return new Result<>(e.getCode(), e.getMessage(), null);
    }

    /**
     * 处理认证授权异常
     */
    @ExceptionHandler(value = AuthException.class)
    public Result<String> handleAuthException(AuthException e, HttpServletRequest request) {
        String requestUrl = request.getRequestURL().toString();
        String method = request.getMethod();

        log.warn("认证异常 [{}] {}: {}", method, requestUrl, e.getMessage());
        return new Result<>(e.getCode(), e.getMessage(), null);
    }

    /**
     * 处理资源未找到异常
     */
    @ExceptionHandler(value = ResourceNotFoundException.class)
    public Result<String> handleResourceNotFoundException(ResourceNotFoundException e, HttpServletRequest request) {
        String requestUrl = request.getRequestURL().toString();
        String method = request.getMethod();

        log.warn("资源未找到 [{}] {}: {}", method, requestUrl, e.getMessage());
        return new Result<>(e.getCode(), e.getMessage(), null);
    }

    /**
     * 处理支付异常
     */
    @ExceptionHandler(value = PaymentException.class)
    public Result<String> handlePaymentException(PaymentException e, HttpServletRequest request) {
        String requestUrl = request.getRequestURL().toString();
        String method = request.getMethod();

        log.error("支付异常 [{}] {}: {} - {}", method, requestUrl, e.getMessage(), e.getDetail());
        return new Result<>(e.getCode(), e.getMessage(), null);
    }

    /**
     * 处理外部服务异常
     */
    @ExceptionHandler(value = ExternalServiceException.class)
    public Result<String> handleExternalServiceException(ExternalServiceException e, HttpServletRequest request) {
        String requestUrl = request.getRequestURL().toString();
        String method = request.getMethod();

        log.error("外部服务异常 [{}] {}: {} - {}", method, requestUrl, e.getMessage(), e.getDetail());
        return new Result<>(e.getCode(), e.getMessage(), null);
    }

    /**
     * 处理数据验证异常
     */
    @ExceptionHandler(value = ValidationException.class)
    public Result<String> handleValidationException(ValidationException e, HttpServletRequest request) {
        String requestUrl = request.getRequestURL().toString();
        String method = request.getMethod();

        log.warn("数据验证异常 [{}] {}: {} - {}", method, requestUrl, e.getMessage(), e.getDetail());
        return new Result<>(e.getCode(), e.getMessage(), null);
    }

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public Result<Map<String, Object>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e, HttpServletRequest request) {
        String requestUrl = request.getRequestURL().toString();
        String method = request.getMethod();

        BindingResult bindingResult = e.getBindingResult();
        String message = "参数校验失败";
        String details = "";

        if (bindingResult.hasErrors()) {
            details = bindingResult.getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .collect(Collectors.joining(", "));

            FieldError fieldError = bindingResult.getFieldError();
            if (fieldError != null) {
                message = fieldError.getField() + fieldError.getDefaultMessage();
            }
        }

        log.warn("参数校验异常 [{}] {}: {} - {}", method, requestUrl, message, details);
        return ErrorResponseUtil.validateFailed(message);
    }

    /**
     * 处理参数绑定异常
     */
    @ExceptionHandler(value = BindException.class)
    public Result<String> handleBindException(BindException e, HttpServletRequest request) {
        String requestUrl = request.getRequestURL().toString();
        String method = request.getMethod();

        BindingResult bindingResult = e.getBindingResult();
        String message = "参数绑定失败";
        String details = "";

        if (bindingResult.hasErrors()) {
            details = bindingResult.getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .collect(Collectors.joining(", "));

            FieldError fieldError = bindingResult.getFieldError();
            if (fieldError != null) {
                message = fieldError.getField() + fieldError.getDefaultMessage();
            }
        }

        log.warn("参数绑定异常 [{}] {}: {} - {}", method, requestUrl, message, details);
        return Result.validateFailed(message);
    }

    /**
     * 处理缺少请求参数异常
     */
    @ExceptionHandler(value = MissingServletRequestParameterException.class)
    public Result<String> handleMissingServletRequestParameterException(MissingServletRequestParameterException e, HttpServletRequest request) {
        String requestUrl = request.getRequestURL().toString();
        String method = request.getMethod();
        String message = "缺少必需的请求参数: " + e.getParameterName();

        log.warn("缺少请求参数 [{}] {}: {}", method, requestUrl, message);
        return new Result<>(ResultCode.BAD_REQUEST.getCode(), message, null);
    }

    /**
     * 处理参数类型不匹配异常
     */
    @ExceptionHandler(value = MethodArgumentTypeMismatchException.class)
    public Result<String> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        String requestUrl = request.getRequestURL().toString();
        String method = request.getMethod();
        String message = "参数类型不匹配: " + e.getName() + " 应为 " + e.getRequiredType().getSimpleName();

        log.warn("参数类型不匹配 [{}] {}: {}", method, requestUrl, message);
        return new Result<>(ResultCode.BAD_REQUEST.getCode(), message, null);
    }

    /**
     * 处理HTTP消息不可读异常
     */
    @ExceptionHandler(value = HttpMessageNotReadableException.class)
    public Result<String> handleHttpMessageNotReadableException(HttpMessageNotReadableException e, HttpServletRequest request) {
        String requestUrl = request.getRequestURL().toString();
        String method = request.getMethod();
        String message = "请求体格式错误";

        log.warn("HTTP消息不可读 [{}] {}: {}", method, requestUrl, e.getMessage());
        return new Result<>(ResultCode.BAD_REQUEST.getCode(), message, null);
    }

    /**
     * 处理请求方法不支持异常
     */
    @ExceptionHandler(value = HttpRequestMethodNotSupportedException.class)
    public Result<String> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        String requestUrl = request.getRequestURL().toString();
        String method = request.getMethod();
        String message = "不支持的请求方法: " + method;

        log.warn("请求方法不支持 [{}] {}: {}", method, requestUrl, message);
        return new Result<>(ResultCode.METHOD_NOT_ALLOWED.getCode(), message, null);
    }

    /**
     * 处理媒体类型不支持异常
     */
    @ExceptionHandler(value = HttpMediaTypeNotSupportedException.class)
    public Result<String> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        String requestUrl = request.getRequestURL().toString();
        String method = request.getMethod();
        String message = "不支持的媒体类型: " + e.getContentType();

        log.warn("媒体类型不支持 [{}] {}: {}", method, requestUrl, message);
        return new Result<>(ResultCode.BAD_REQUEST.getCode(), message, null);
    }

    /**
     * 处理404异常
     */
    @ExceptionHandler(value = NoHandlerFoundException.class)
    public Result<String> handleNoHandlerFoundException(NoHandlerFoundException e, HttpServletRequest request) {
        String requestUrl = request.getRequestURL().toString();
        String method = request.getMethod();
        String message = "请求的资源不存在";

        log.warn("404异常 [{}] {}: {}", method, requestUrl, message);
        return new Result<>(ResultCode.NOT_FOUND.getCode(), message, null);
    }

    /**
     * 处理文件上传大小超限异常
     */
    @ExceptionHandler(value = MaxUploadSizeExceededException.class)
    public Result<Map<String, Object>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e, HttpServletRequest request) {
        String requestUrl = request.getRequestURL().toString();
        String method = request.getMethod();
        String originalMessage = "上传文件大小超出限制";

        log.warn("文件上传大小超限 [{}] {}: {} - {}", method, requestUrl, originalMessage, e.getMessage());

        return ErrorResponseUtil.fileSizeExceeded();
    }

    /**
     * 处理数据库异常
     */
    @ExceptionHandler(value = {SQLException.class, DataAccessException.class})
    public Result<String> handleDatabaseException(Exception e, HttpServletRequest request) {
        String requestUrl = request.getRequestURL().toString();
        String method = request.getMethod();
        String message = "数据库操作异常";

        log.error("数据库异常 [{}] {}: {}", method, requestUrl, e.getMessage(), e);
        return new Result<>(ResultCode.FAILED.getCode(), message, null);
    }

    /**
     * 处理重复键异常
     */
    @ExceptionHandler(value = DuplicateKeyException.class)
    public Result<String> handleDuplicateKeyException(DuplicateKeyException e, HttpServletRequest request) {
        String requestUrl = request.getRequestURL().toString();
        String method = request.getMethod();
        String message = "数据重复，操作失败";

        log.warn("重复键异常 [{}] {}: {}", method, requestUrl, e.getMessage());
        return new Result<>(ResultCode.CONFLICT.getCode(), message, null);
    }

    /**
     * 处理其他未知异常
     */
    @ExceptionHandler(value = Exception.class)
    public Result<String> handleException(Exception e, HttpServletRequest request) {
        String requestUrl = request.getRequestURL().toString();
        String method = request.getMethod();
        String message = "系统异常，请联系管理员";

        // 生成异常ID用于追踪
        String exceptionId = System.currentTimeMillis() + "-" + Thread.currentThread().getId();

        log.error("未知异常 [{}] {} (异常ID: {}): {}", method, requestUrl, exceptionId, e.getMessage(), e);
        return new Result<>(ResultCode.FAILED.getCode(), message + " (异常ID: " + exceptionId + ")", null);
    }
}
