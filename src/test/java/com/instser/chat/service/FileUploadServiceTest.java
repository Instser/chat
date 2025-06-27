package com.instser.chat.service;

import com.instser.chat.dao.FileUploadDao;
import com.instser.chat.entity.FileUpload;
import com.instser.chat.service.serviceimpl.FileUploadServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
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
        
        // 验证临时文件清理方法（新架构下直接测试Path参数的方法）
        Path tempFile = tempDir.resolve("test-temp-file.txt");
        Files.write(tempFile, testContent.getBytes());

        // 验证文件存在
        assertTrue(Files.exists(tempFile), "临时文件应该存在");

        // 创建服务实例并测试清理方法
        FileUploadDao mockDao = Mockito.mock(FileUploadDao.class);
        DocumentParserService mockParserService = Mockito.mock(DocumentParserService.class);
        FileUploadServiceImpl service = new FileUploadServiceImpl(mockDao, mockParserService);
        service.cleanupTempFile(tempFile);

        // 验证文件已被删除
        assertFalse(Files.exists(tempFile), "临时文件应该被删除");
    }

    /**
     * 测试批量临时文件清理
     */
    @Test
    public void testBatchTempFileCleanup() throws IOException {
        // 创建多个测试文件（新架构下直接创建Path对象）
        String testContent = "测试内容";

        Path tempFile1 = tempDir.resolve("temp-file-1.txt");
        Path tempFile2 = tempDir.resolve("temp-file-2.txt");
        Files.write(tempFile1, testContent.getBytes());
        Files.write(tempFile2, testContent.getBytes());

        // 验证文件存在
        assertTrue(Files.exists(tempFile1), "临时文件1应该存在");
        assertTrue(Files.exists(tempFile2), "临时文件2应该存在");

        // 批量清理
        FileUploadDao mockDao = Mockito.mock(FileUploadDao.class);
        DocumentParserService mockParserService = Mockito.mock(DocumentParserService.class);
        FileUploadServiceImpl service = new FileUploadServiceImpl(mockDao, mockParserService);

        // 新架构下使用Path参数的清理方法
        service.cleanupTempFile(tempFile1);
        service.cleanupTempFile(tempFile2);

        // 验证文件已被删除
        assertFalse(Files.exists(tempFile1), "临时文件1应该被删除");
        assertFalse(Files.exists(tempFile2), "临时文件2应该被删除");
    }

    /**
     * 测试清理不存在的文件不会抛出异常
     */
    @Test
    public void testCleanupNonExistentFile() {
        // 新架构下直接测试Path参数的清理方法
        Path nonExistentPath = Paths.get("/path/to/non-existent-file.txt");

        // 清理不存在的文件应该不抛出异常
        FileUploadDao mockDao = Mockito.mock(FileUploadDao.class);
        DocumentParserService mockParserService = Mockito.mock(DocumentParserService.class);
        FileUploadServiceImpl service = new FileUploadServiceImpl(mockDao, mockParserService);
        assertDoesNotThrow(() -> service.cleanupTempFile(nonExistentPath));
    }

    /**
     * 测试清理null文件对象不会抛出异常
     */
    @Test
    public void testCleanupNullFile() {
        FileUploadDao mockDao = Mockito.mock(FileUploadDao.class);
        DocumentParserService mockParserService = Mockito.mock(DocumentParserService.class);
        FileUploadServiceImpl service = new FileUploadServiceImpl(mockDao, mockParserService);

        // 测试新架构下的Path参数清理方法
        assertDoesNotThrow(() -> service.cleanupTempFile((Path) null));

        // 测试接口兼容性方法
        assertDoesNotThrow(() -> service.cleanupTempFile((FileUpload) null));
        assertDoesNotThrow(() -> service.cleanupTempFiles(null));
        assertDoesNotThrow(() -> service.cleanupTempFiles(java.util.Collections.emptyList()));
    }
}
