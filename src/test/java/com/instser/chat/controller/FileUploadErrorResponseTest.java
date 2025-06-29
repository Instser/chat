package com.instser.chat.controller;

import com.instser.chat.common.exception.FileUploadException;
import com.instser.chat.common.result.Result;
import com.instser.chat.common.result.ResultCode;
import com.instser.chat.service.FileUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 文件上传错误响应格式测试
 * 验证文件上传错误时返回正确的响应格式
 */
@ExtendWith(MockitoExtension.class)
class FileUploadErrorResponseTest {

    @Mock
    private FileUploadService fileUploadService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private FileController fileController;

    @BeforeEach
    void setUp() {
        // 模拟用户认证
        when(authentication.getName()).thenReturn("testuser");
    }

    /**
     * 测试空文件上传的错误响应格式
     */
    @Test
    void testEmptyFileUploadErrorResponse() {
        // 测试空文件数组
        MultipartFile[] emptyFiles = new MultipartFile[0];
        
        Result<Map<String, Object>> result = fileController.uploadFiles(emptyFiles, authentication);
        
        // 验证响应格式
        assertNotNull(result);
        assertEquals(200, result.getCode()); // 使用Result.success，所以code是200
        assertEquals("操作成功", result.getMessage()); // Result.success的默认消息
        
        // 验证data中的错误信息
        Map<String, Object> data = result.getData();
        assertNotNull(data);
        assertEquals(ResultCode.FILE_EMPTY.getCode(), data.get("code"));
        assertEquals("No files uploaded", data.get("message"));
    }

    /**
     * 测试文件上传失败的错误响应格式
     */
    @Test
    void testFileUploadFailedErrorResponse() {
        MultipartFile[] files = {
            new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes())
        };
        
        // 模拟文件上传失败
        when(fileUploadService.uploadFiles(any(), anyLong(), isNull(), isNull()))
            .thenThrow(new FileUploadException("FILE_UPLOAD_FAILED", "File upload failed"));
        
        Result<Map<String, Object>> result = fileController.uploadFiles(files, authentication);
        
        // 验证响应格式
        assertNotNull(result);
        assertEquals(200, result.getCode()); // 使用Result.success
        assertEquals("操作成功", result.getMessage());
        
        // 验证data中的错误信息
        Map<String, Object> data = result.getData();
        assertNotNull(data);
        assertEquals(ResultCode.FILE_UPLOAD_FAILED.getCode(), data.get("code"));
        assertEquals("File upload failed", data.get("message"));
    }

    /**
     * 测试文件大小超限的错误响应格式
     */
    @Test
    void testFileSizeExceededErrorResponse() {
        MultipartFile[] files = {
            new MockMultipartFile("file", "large.txt", "text/plain", "content".getBytes())
        };
        
        // 模拟文件大小超限
        when(fileUploadService.uploadFiles(any(), anyLong(), isNull(), isNull()))
            .thenThrow(new FileUploadException("FILE_SIZE_EXCEEDED", "File size exceeds limit"));
        
        Result<Map<String, Object>> result = fileController.uploadFiles(files, authentication);
        
        // 验证响应格式
        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("操作成功", result.getMessage());
        
        // 验证data中的错误信息
        Map<String, Object> data = result.getData();
        assertNotNull(data);
        assertEquals(ResultCode.FILE_SIZE_EXCEEDED.getCode(), data.get("code"));
        assertEquals("File size exceeds limit", data.get("message"));
    }

    /**
     * 测试不支持的文件类型错误响应格式
     */
    @Test
    void testFileTypeNotSupportedErrorResponse() {
        MultipartFile[] files = {
            new MockMultipartFile("file", "test.exe", "application/x-msdownload", "content".getBytes())
        };
        
        // 模拟不支持的文件类型
        when(fileUploadService.uploadFiles(any(), anyLong(), isNull(), isNull()))
            .thenThrow(new FileUploadException("FILE_TYPE_NOT_SUPPORTED", "File type not supported"));
        
        Result<Map<String, Object>> result = fileController.uploadFiles(files, authentication);
        
        // 验证响应格式
        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("操作成功", result.getMessage());
        
        // 验证data中的错误信息
        Map<String, Object> data = result.getData();
        assertNotNull(data);
        assertEquals(ResultCode.FILE_TYPE_NOT_SUPPORTED.getCode(), data.get("code"));
        assertEquals("File type not supported", data.get("message"));
    }

    /**
     * 测试文件解析失败的错误响应格式
     */
    @Test
    void testFileParseFailedErrorResponse() {
        MultipartFile[] files = {
            new MockMultipartFile("file", "corrupt.pdf", "application/pdf", "corrupt content".getBytes())
        };
        
        // 模拟文件解析失败
        when(fileUploadService.uploadFiles(any(), anyLong(), isNull(), isNull()))
            .thenThrow(new FileUploadException("FILE_PARSE_FAILED", "File parsing failed"));
        
        Result<Map<String, Object>> result = fileController.uploadFiles(files, authentication);
        
        // 验证响应格式
        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("操作成功", result.getMessage());
        
        // 验证data中的错误信息
        Map<String, Object> data = result.getData();
        assertNotNull(data);
        assertEquals(ResultCode.FILE_PARSE_FAILED.getCode(), data.get("code"));
        assertEquals("File parsing failed", data.get("message"));
    }

    /**
     * 测试通用异常的错误响应格式
     */
    @Test
    void testGenericExceptionErrorResponse() {
        MultipartFile[] files = {
            new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes())
        };
        
        // 模拟通用异常
        when(fileUploadService.uploadFiles(any(), anyLong(), isNull(), isNull()))
            .thenThrow(new RuntimeException("Unexpected error"));
        
        Result<Map<String, Object>> result = fileController.uploadFiles(files, authentication);
        
        // 验证响应格式
        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("操作成功", result.getMessage());
        
        // 验证data中的错误信息
        Map<String, Object> data = result.getData();
        assertNotNull(data);
        assertEquals(ResultCode.FILE_UPLOAD_FAILED.getCode(), data.get("code"));
        assertEquals("File upload failed. Please try again later.", data.get("message"));
    }

    /**
     * 测试错误响应格式的一致性
     */
    @Test
    void testErrorResponseFormatConsistency() {
        // 测试不同错误类型的响应格式都一致
        
        // 空文件错误
        MultipartFile[] emptyFiles = new MultipartFile[0];
        Result<Map<String, Object>> emptyResult = fileController.uploadFiles(emptyFiles, authentication);
        
        // 文件上传失败错误
        MultipartFile[] files = {
            new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes())
        };
        when(fileUploadService.uploadFiles(any(), anyLong(), isNull(), isNull()))
            .thenThrow(new FileUploadException("FILE_UPLOAD_FAILED", "File upload failed"));
        Result<Map<String, Object>> failedResult = fileController.uploadFiles(files, authentication);
        
        // 验证响应结构一致性
        assertEquals(emptyResult.getCode(), failedResult.getCode());
        assertEquals(emptyResult.getMessage(), failedResult.getMessage());
        
        // 验证data结构一致性
        assertTrue(emptyResult.getData().containsKey("code"));
        assertTrue(emptyResult.getData().containsKey("message"));
        assertTrue(failedResult.getData().containsKey("code"));
        assertTrue(failedResult.getData().containsKey("message"));
        
        // 验证错误码类型一致性
        assertTrue(emptyResult.getData().get("code") instanceof Integer);
        assertTrue(failedResult.getData().get("code") instanceof Integer);
        assertTrue(emptyResult.getData().get("message") instanceof String);
        assertTrue(failedResult.getData().get("message") instanceof String);
    }

    /**
     * 测试错误码映射的正确性
     */
    @Test
    void testErrorCodeMapping() {
        // 验证各种错误类型对应的错误码
        assertEquals(5005, ResultCode.FILE_EMPTY.getCode());
        assertEquals(5001, ResultCode.FILE_UPLOAD_FAILED.getCode());
        assertEquals(5002, ResultCode.FILE_SIZE_EXCEEDED.getCode());
        assertEquals(5003, ResultCode.FILE_TYPE_NOT_SUPPORTED.getCode());
        assertEquals(5004, ResultCode.FILE_PARSE_FAILED.getCode());
    }
}
