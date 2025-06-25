package com.instser.chat.service;

import com.instser.chat.entity.FileUpload;
import com.instser.chat.service.serviceimpl.FileUploadServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文件上传服务测试类
 * 测试文件上传和临时文件清理功能
 *
 * @author instser
 * @since 2025-06-25
 */
@SpringBootTest
@TestPropertySource(properties = {
    "file.upload.path=src/test/resources/temp-files",
    "file.upload.max-size=10485760"
})
public class FileUploadServiceTest {

    @TempDir
    Path tempDir;

    /**
     * 测试文件上传后临时文件是否被正确清理
     */
    @Test
    public void testTempFileCleanupAfterUpload() throws IOException {
        // 创建测试文件内容
        String testContent = "这是一个测试文件内容";
        MockMultipartFile mockFile = new MockMultipartFile(
            "file",
            "test.txt",
            "text/plain",
            testContent.getBytes()
        );

        // 模拟文件上传服务
        // 注意：这里需要实际的数据库连接才能完整测试
        // 在单元测试中，我们主要验证临时文件清理逻辑
        
        // 验证临时文件清理方法
        FileUpload testFileUpload = new FileUpload();
        testFileUpload.setFileId("test-file-id");
        testFileUpload.setStoragePath(tempDir.resolve("test-temp-file.txt").toString());
        
        // 创建临时文件
        Path tempFile = Paths.get(testFileUpload.getStoragePath());
        Files.write(tempFile, testContent.getBytes());
        
        // 验证文件存在
        assertTrue(Files.exists(tempFile), "临时文件应该存在");
        
        // 创建服务实例并测试清理方法
        FileUploadServiceImpl service = new FileUploadServiceImpl(null);
        service.cleanupTempFile(testFileUpload);
        
        // 验证文件已被删除
        assertFalse(Files.exists(tempFile), "临时文件应该被删除");
    }

    /**
     * 测试批量临时文件清理
     */
    @Test
    public void testBatchTempFileCleanup() throws IOException {
        // 创建多个测试文件
        String testContent = "测试内容";
        
        FileUpload file1 = new FileUpload();
        file1.setFileId("test-file-1");
        file1.setStoragePath(tempDir.resolve("temp-file-1.txt").toString());
        
        FileUpload file2 = new FileUpload();
        file2.setFileId("test-file-2");
        file2.setStoragePath(tempDir.resolve("temp-file-2.txt").toString());
        
        // 创建临时文件
        Path tempFile1 = Paths.get(file1.getStoragePath());
        Path tempFile2 = Paths.get(file2.getStoragePath());
        Files.write(tempFile1, testContent.getBytes());
        Files.write(tempFile2, testContent.getBytes());
        
        // 验证文件存在
        assertTrue(Files.exists(tempFile1), "临时文件1应该存在");
        assertTrue(Files.exists(tempFile2), "临时文件2应该存在");
        
        // 批量清理
        FileUploadServiceImpl service = new FileUploadServiceImpl(null);
        service.cleanupTempFiles(java.util.Arrays.asList(file1, file2));
        
        // 验证文件已被删除
        assertFalse(Files.exists(tempFile1), "临时文件1应该被删除");
        assertFalse(Files.exists(tempFile2), "临时文件2应该被删除");
    }

    /**
     * 测试清理不存在的文件不会抛出异常
     */
    @Test
    public void testCleanupNonExistentFile() {
        FileUpload testFileUpload = new FileUpload();
        testFileUpload.setFileId("non-existent-file");
        testFileUpload.setStoragePath("/path/to/non-existent-file.txt");
        
        // 清理不存在的文件应该不抛出异常
        FileUploadServiceImpl service = new FileUploadServiceImpl(null);
        assertDoesNotThrow(() -> service.cleanupTempFile(testFileUpload));
    }

    /**
     * 测试清理null文件对象不会抛出异常
     */
    @Test
    public void testCleanupNullFile() {
        FileUploadServiceImpl service = new FileUploadServiceImpl(null);
        assertDoesNotThrow(() -> service.cleanupTempFile(null));
        assertDoesNotThrow(() -> service.cleanupTempFiles(null));
        assertDoesNotThrow(() -> service.cleanupTempFiles(java.util.Collections.emptyList()));
    }
}
