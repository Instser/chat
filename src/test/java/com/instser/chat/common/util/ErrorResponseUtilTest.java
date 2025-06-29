package com.instser.chat.common.util;

import com.instser.chat.common.result.Result;
import com.instser.chat.common.result.ResultCode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 错误响应工具类测试
 * 验证统一错误响应格式的正确性
 */
class ErrorResponseUtilTest {

    /**
     * 测试基本错误响应格式
     */
    @Test
    void testCreateErrorResponse() {
        Result<Map<String, Object>> result = ErrorResponseUtil.createErrorResponse(
            ResultCode.FILE_UPLOAD_FAILED, 
            "Custom error message"
        );

        // 验证响应结构
        assertNotNull(result);
        assertEquals(200, result.getCode()); // Result.success的code
        assertEquals("操作成功", result.getMessage()); // Result.success的默认message
        
        // 验证data中的错误信息
        Map<String, Object> data = result.getData();
        assertNotNull(data);
        assertEquals(ResultCode.FILE_UPLOAD_FAILED.getCode(), data.get("code"));
        assertEquals("Custom error message", data.get("message"));
    }

    /**
     * 测试使用默认消息的错误响应
     */
    @Test
    void testCreateErrorResponseWithDefaultMessage() {
        Result<Map<String, Object>> result = ErrorResponseUtil.createErrorResponse(ResultCode.AUTH_LOGIN_FAILED);

        Map<String, Object> data = result.getData();
        assertNotNull(data);
        assertEquals(ResultCode.AUTH_LOGIN_FAILED.getCode(), data.get("code"));
        assertEquals(ResultCode.AUTH_LOGIN_FAILED.getMessage(), data.get("message"));
    }

    /**
     * 测试认证相关错误响应
     */
    @Test
    void testAuthErrorResponses() {
        // 登录失败
        Result<Map<String, Object>> loginFailed = ErrorResponseUtil.loginFailed("Invalid credentials");
        Map<String, Object> data = loginFailed.getData();
        assertEquals(6001, data.get("code"));
        assertEquals("Invalid credentials", data.get("message"));

        // 用户名已存在
        Result<Map<String, Object>> usernameExists = ErrorResponseUtil.usernameExists();
        data = usernameExists.getData();
        assertEquals(6003, data.get("code"));
        assertEquals("用户名已存在", data.get("message"));

        // 邮箱已存在
        Result<Map<String, Object>> emailExists = ErrorResponseUtil.emailExists();
        data = emailExists.getData();
        assertEquals(6004, data.get("code"));
        assertEquals("邮箱已存在", data.get("message"));

        // 用户名或密码错误
        Result<Map<String, Object>> invalidCredentials = ErrorResponseUtil.invalidCredentials();
        data = invalidCredentials.getData();
        assertEquals(6005, data.get("code"));
        assertEquals("用户名或密码错误", data.get("message"));
    }

    /**
     * 测试支付相关错误响应
     */
    @Test
    void testPaymentErrorResponses() {
        // 创建支付订单失败
        Result<Map<String, Object>> createFailed = ErrorResponseUtil.paymentCreateFailed("Payment gateway error");
        Map<String, Object> data = createFailed.getData();
        assertEquals(7001, data.get("code"));
        assertEquals("Payment gateway error", data.get("message"));

        // 订单不存在
        Result<Map<String, Object>> orderNotFound = ErrorResponseUtil.orderNotFound();
        data = orderNotFound.getData();
        assertEquals(7002, data.get("code"));
        assertEquals("订单不存在", data.get("message"));

        // 查询支付状态失败
        Result<Map<String, Object>> statusQueryFailed = ErrorResponseUtil.paymentStatusQueryFailed();
        data = statusQueryFailed.getData();
        assertEquals(7003, data.get("code"));
        assertEquals("查询支付状态失败", data.get("message"));
    }

    /**
     * 测试API接口相关错误响应
     */
    @Test
    void testApiErrorResponses() {
        // AI模型不存在
        Result<Map<String, Object>> modelNotFound = ErrorResponseUtil.modelNotFound();
        Map<String, Object> data = modelNotFound.getData();
        assertEquals(8001, data.get("code"));
        assertEquals("AI模型不存在", data.get("message"));

        // AI模型不可用
        Result<Map<String, Object>> modelUnavailable = ErrorResponseUtil.modelUnavailable();
        data = modelUnavailable.getData();
        assertEquals(8002, data.get("code"));
        assertEquals("AI模型不可用", data.get("message"));

        // 会话不存在
        Result<Map<String, Object>> sessionNotFound = ErrorResponseUtil.sessionNotFound();
        data = sessionNotFound.getData();
        assertEquals(8003, data.get("code"));
        assertEquals("会话不存在", data.get("message"));

        // 参数无效
        Result<Map<String, Object>> invalidParameter = ErrorResponseUtil.invalidParameter("Invalid user ID");
        data = invalidParameter.getData();
        assertEquals(8005, data.get("code"));
        assertEquals("Invalid user ID", data.get("message"));

        // 缺少必需参数
        Result<Map<String, Object>> missingParameter = ErrorResponseUtil.missingParameter("userId");
        data = missingParameter.getData();
        assertEquals(8006, data.get("code"));
        assertEquals("Missing required parameter: userId", data.get("message"));
    }

    /**
     * 测试文件上传相关错误响应
     */
    @Test
    void testFileUploadErrorResponses() {
        // 文件上传失败
        Result<Map<String, Object>> uploadFailed = ErrorResponseUtil.fileUploadFailed();
        Map<String, Object> data = uploadFailed.getData();
        assertEquals(5001, data.get("code"));
        assertEquals("File upload failed. Please try again later.", data.get("message"));

        // 文件大小超限
        Result<Map<String, Object>> sizeExceeded = ErrorResponseUtil.fileSizeExceeded();
        data = sizeExceeded.getData();
        assertEquals(5002, data.get("code"));
        assertEquals("File size exceeds limit", data.get("message"));

        // 不支持的文件类型
        Result<Map<String, Object>> typeNotSupported = ErrorResponseUtil.fileTypeNotSupported();
        data = typeNotSupported.getData();
        assertEquals(5003, data.get("code"));
        assertEquals("File type not supported", data.get("message"));

        // 文件解析失败
        Result<Map<String, Object>> parseFailed = ErrorResponseUtil.fileParseFailed();
        data = parseFailed.getData();
        assertEquals(5004, data.get("code"));
        assertEquals("File parsing failed", data.get("message"));

        // 文件为空
        Result<Map<String, Object>> fileEmpty = ErrorResponseUtil.fileEmpty();
        data = fileEmpty.getData();
        assertEquals(5005, data.get("code"));
        assertEquals("No files uploaded", data.get("message"));
    }

    /**
     * 测试通用错误响应
     */
    @Test
    void testGenericErrorResponses() {
        // 参数验证失败
        Result<Map<String, Object>> validateFailed = ErrorResponseUtil.validateFailed("Invalid email format");
        Map<String, Object> data = validateFailed.getData();
        assertEquals(422, data.get("code"));
        assertEquals("Invalid email format", data.get("message"));

        // 未授权
        Result<Map<String, Object>> unauthorized = ErrorResponseUtil.unauthorized("Token expired");
        data = unauthorized.getData();
        assertEquals(401, data.get("code"));
        assertEquals("Token expired", data.get("message"));

        // 服务器内部错误
        Result<Map<String, Object>> internalError = ErrorResponseUtil.internalError("Database connection failed");
        data = internalError.getData();
        assertEquals(500, data.get("code"));
        assertEquals("Database connection failed", data.get("message"));
    }

    /**
     * 测试响应格式的一致性
     */
    @Test
    void testResponseFormatConsistency() {
        // 测试不同类型的错误响应都有相同的结构
        Result<Map<String, Object>> authError = ErrorResponseUtil.loginFailed("Login failed");
        Result<Map<String, Object>> paymentError = ErrorResponseUtil.orderNotFound();
        Result<Map<String, Object>> fileError = ErrorResponseUtil.fileUploadFailed();

        // 验证外层结构一致
        assertEquals(authError.getCode(), paymentError.getCode());
        assertEquals(paymentError.getCode(), fileError.getCode());
        assertEquals(authError.getMessage(), paymentError.getMessage());
        assertEquals(paymentError.getMessage(), fileError.getMessage());

        // 验证data结构一致
        assertTrue(authError.getData().containsKey("code"));
        assertTrue(authError.getData().containsKey("message"));
        assertTrue(paymentError.getData().containsKey("code"));
        assertTrue(paymentError.getData().containsKey("message"));
        assertTrue(fileError.getData().containsKey("code"));
        assertTrue(fileError.getData().containsKey("message"));

        // 验证数据类型一致
        assertTrue(authError.getData().get("code") instanceof Integer);
        assertTrue(authError.getData().get("message") instanceof String);
        assertTrue(paymentError.getData().get("code") instanceof Integer);
        assertTrue(paymentError.getData().get("message") instanceof String);
    }

    /**
     * 测试错误码的唯一性
     */
    @Test
    void testErrorCodeUniqueness() {
        // 验证不同错误类型有不同的错误码
        assertNotEquals(
            ErrorResponseUtil.loginFailed("test").getData().get("code"),
            ErrorResponseUtil.fileUploadFailed().getData().get("code")
        );
        
        assertNotEquals(
            ErrorResponseUtil.orderNotFound().getData().get("code"),
            ErrorResponseUtil.modelNotFound().getData().get("code")
        );
        
        assertNotEquals(
            ErrorResponseUtil.fileSizeExceeded().getData().get("code"),
            ErrorResponseUtil.fileTypeNotSupported().getData().get("code")
        );
    }
}
