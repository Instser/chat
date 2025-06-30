package com.instser.chat.common.exception;

import com.instser.chat.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * GlobalExceptionHandler测试类
 * 验证文件上传大小超限异常处理是否正确
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler globalExceptionHandler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        globalExceptionHandler = new GlobalExceptionHandler();
    }

    /**
     * 测试文件上传大小超限异常处理
     */
    @Test
    void testHandleMaxUploadSizeExceededException() {
        // 准备测试数据
        MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(10 * 1024 * 1024);
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/api/files/upload"));
        when(request.getMethod()).thenReturn("POST");

        // 执行测试
        Result<Map<String, Object>> result = globalExceptionHandler.handleMaxUploadSizeExceededException(exception, request);

        // 验证结果
        assertNotNull(result);
        assertEquals(200, result.getCode()); // Result.success的code
        assertEquals("操作成功", result.getMessage()); // Result.success的默认message
        
        // 验证data中的错误信息
        Map<String, Object> data = result.getData();
        assertNotNull(data);
        assertEquals(5002, data.get("code")); // FILE_SIZE_EXCEEDED错误码
        assertEquals("File size exceeds limit", data.get("message"));
    }
}
