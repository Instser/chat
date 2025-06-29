package com.instser.chat.service;

import com.instser.chat.service.serviceimpl.FileUploadServiceImpl;
import com.instser.chat.service.DocumentParserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 文件上传错误处理测试类
 * 验证文件上传的统一错误处理机制
 */
@ExtendWith(MockitoExtension.class)
class FileUploadErrorHandlingTest {

    @Mock
    private DocumentParserService documentParserService;

    @InjectMocks
    private FileUploadServiceImpl fileUploadService;

    private Long testUserId = 1L;
    private String testSessionId = "test-session-123";
    private String testMessageId = "test-message-456";

    @BeforeEach
    void setUp() {
        // 测试设置
    }

    /**
     * 测试空文件上传错误处理
     */
    @Test
    void testEmptyFileUploadError() {
        // 创建空文件
        MultipartFile emptyFile = new MockMultipartFile(
            "file", 
            "empty.txt", 
            "text/plain", 
            new byte[0]
        );

        // 执行上传并验证异常
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            fileUploadService.uploadFile(emptyFile, testUserId, testSessionId, testMessageId);
        });

        // 验证返回统一错误消息
        assertEquals("File upload failed. Please try again later.", exception.getMessage());
    }

    /**
     * 测试文件大小超限错误处理
     */
    @Test
    void testFileSizeExceedsLimitError() {
        // 创建超大文件（6MB）
        byte[] largeFileContent = new byte[6 * 1024 * 1024];
        MultipartFile largeFile = new MockMultipartFile(
            "file", 
            "large.txt", 
            "text/plain", 
            largeFileContent
        );

        // 执行上传并验证异常
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            fileUploadService.uploadFile(largeFile, testUserId, testSessionId, testMessageId);
        });

        // 验证返回统一错误消息
        assertEquals("File upload failed. Please try again later.", exception.getMessage());
    }

    /**
     * 测试不支持的文件类型错误处理
     */
    @Test
    void testUnsupportedFileTypeError() {
        // 创建不支持的文件类型
        MultipartFile unsupportedFile = new MockMultipartFile(
            "file", 
            "test.exe", 
            "application/x-msdownload", 
            "fake executable content".getBytes()
        );

        // 执行上传并验证异常
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            fileUploadService.uploadFile(unsupportedFile, testUserId, testSessionId, testMessageId);
        });

        // 验证返回统一错误消息
        assertEquals("File upload failed. Please try again later.", exception.getMessage());
    }

    /**
     * 测试文档解析失败错误处理
     */
    @Test
    void testDocumentParsingFailureError() {
        // 创建有效的文本文件
        MultipartFile textFile = new MockMultipartFile(
            "file", 
            "test.txt", 
            "text/plain", 
            "test content".getBytes()
        );

        // 模拟文档解析服务支持该文件类型但解析失败
        when(documentParserService.isDocumentFile("text/plain")).thenReturn(true);
        when(documentParserService.parseDocument(any(), eq("test.txt"), eq("text/plain")))
            .thenThrow(new RuntimeException("AI服务调用失败"));

        // 执行上传并验证异常
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            fileUploadService.uploadFile(textFile, testUserId, testSessionId, testMessageId);
        });

        // 验证返回统一错误消息
        assertEquals("File upload failed. Please try again later.", exception.getMessage());
    }

    /**
     * 测试IO异常错误处理
     */
    @Test
    void testIOExceptionError() {
        // 创建模拟文件，但文件名为null会导致IO异常
        MultipartFile problematicFile = new MockMultipartFile(
            "file", 
            null, // null文件名会导致问题
            "text/plain", 
            "test content".getBytes()
        );

        // 执行上传并验证异常
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            fileUploadService.uploadFile(problematicFile, testUserId, testSessionId, testMessageId);
        });

        // 验证返回统一错误消息
        assertEquals("File upload failed. Please try again later.", exception.getMessage());
    }

    /**
     * 测试批量上传中单个文件失败的处理
     */
    @Test
    void testBatchUploadWithSomeFailures() {
        // 创建文件数组：一个正常文件，一个空文件
        MultipartFile[] files = {
            new MockMultipartFile("file1", "normal.txt", "text/plain", "normal content".getBytes()),
            new MockMultipartFile("file2", "empty.txt", "text/plain", new byte[0])
        };

        // 模拟第一个文件解析成功
        when(documentParserService.isDocumentFile("text/plain")).thenReturn(true);
        when(documentParserService.parseDocument(any(), eq("normal.txt"), eq("text/plain")))
            .thenReturn("parsed content");

        // 执行批量上传
        var result = fileUploadService.uploadFiles(files, testUserId, testSessionId, testMessageId);

        // 验证结果：只有成功的文件被返回
        assertEquals(1, result.size());
        assertEquals("normal.txt", result.get(0).getFileName());
    }

    /**
     * 测试错误消息的一致性
     */
    @Test
    void testErrorMessageConsistency() {
        // 测试不同类型的错误都返回相同的用户友好消息
        
        // 空文件错误
        MultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
        RuntimeException emptyFileException = assertThrows(RuntimeException.class, () -> {
            fileUploadService.uploadFile(emptyFile, testUserId, testSessionId, testMessageId);
        });

        // 大文件错误
        byte[] largeContent = new byte[6 * 1024 * 1024];
        MultipartFile largeFile = new MockMultipartFile("file", "large.txt", "text/plain", largeContent);
        RuntimeException largeFileException = assertThrows(RuntimeException.class, () -> {
            fileUploadService.uploadFile(largeFile, testUserId, testSessionId, testMessageId);
        });

        // 验证错误消息一致性
        assertEquals(emptyFileException.getMessage(), largeFileException.getMessage());
        assertEquals("File upload failed. Please try again later.", emptyFileException.getMessage());
    }

    /**
     * 测试错误不会影响后续操作
     */
    @Test
    void testErrorDoesNotAffectSubsequentOperations() {
        // 第一次上传失败
        MultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
        assertThrows(RuntimeException.class, () -> {
            fileUploadService.uploadFile(emptyFile, testUserId, testSessionId, testMessageId);
        });

        // 第二次上传正常文件应该成功
        MultipartFile normalFile = new MockMultipartFile("file", "normal.txt", "text/plain", "content".getBytes());
        when(documentParserService.isDocumentFile("text/plain")).thenReturn(true);
        when(documentParserService.parseDocument(any(), eq("normal.txt"), eq("text/plain")))
            .thenReturn("parsed content");

        // 验证第二次上传不受第一次失败影响
        assertDoesNotThrow(() -> {
            var result = fileUploadService.uploadFile(normalFile, testUserId, testSessionId, testMessageId);
            assertNotNull(result);
            assertEquals("normal.txt", result.getFileName());
        });
    }

    /**
     * 测试参数验证错误处理
     */
    @Test
    void testParameterValidationError() {
        // 测试null文件
        assertThrows(Exception.class, () -> {
            fileUploadService.uploadFile(null, testUserId, testSessionId, testMessageId);
        });

        // 测试null用户ID
        MultipartFile normalFile = new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes());
        assertThrows(Exception.class, () -> {
            fileUploadService.uploadFile(normalFile, null, testSessionId, testMessageId);
        });
    }
}
