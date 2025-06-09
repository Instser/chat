package com.instser.chat.common.exception;

import com.instser.chat.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
    public Result<String> handle(BusinessException e) {
        log.error("业务异常：{}", e.getMessage());
        return Result.failed(e.getMessage());
    }

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public Result<String> handleValidException(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        String message = null;
        if (bindingResult.hasErrors()) {
            FieldError fieldError = bindingResult.getFieldError();
            if (fieldError != null) {
                message = fieldError.getField() + fieldError.getDefaultMessage();
            }
        }
        log.error("参数校验异常：{}", message);
        return Result.validateFailed(message);
    }

    /**
     * 处理参数绑定异常
     */
    @ExceptionHandler(value = BindException.class)
    public Result<String> handleValidException(BindException e) {
        BindingResult bindingResult = e.getBindingResult();
        String message = null;
        if (bindingResult.hasErrors()) {
            FieldError fieldError = bindingResult.getFieldError();
            if (fieldError != null) {
                message = fieldError.getField() + fieldError.getDefaultMessage();
            }
        }
        log.error("参数绑定异常：{}", message);
        return Result.validateFailed(message);
    }

    /**
     * 处理其他异常
     */
    @ExceptionHandler(value = Exception.class)
    public Result<String> handle(Exception e) {
        log.error("未知异常：", e);
        return Result.failed("系统异常，请联系管理员");
    }
}
