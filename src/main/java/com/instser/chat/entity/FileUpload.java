package com.instser.chat.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 文件上传实体类
 *
 * @author instser
 * @since 2025-06-25
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_file_upload")
public class FileUpload {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 文件ID（UUID）
     */
    @TableField("file_id")
    private String fileId;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 关联的会话ID
     */
    @TableField("session_id")
    private String sessionId;

    /**
     * 关联的消息ID
     */
    @TableField("message_id")
    private String messageId;

    /**
     * 文件名
     */
    @TableField("file_name")
    private String fileName;

    /**
     * 文件类型（MIME类型）
     */
    @TableField("file_type")
    private String fileType;

    /**
     * 文件大小（字节）
     */
    @TableField("file_size")
    private Long fileSize;

    /**
     * 存储路径
     */
    @TableField("storage_path")
    private String storagePath;

    /**
     * 文件内容（用于存储文本文件或小文件的完整内容）
     */
    @TableField("content")
    private String content;

    /**
     * 状态（0-处理中，1-可用，2-失败）
     */
    @TableField("status")
    private Integer status;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标志（0-未删除，1-已删除）
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    /**
     * 文件状态枚举
     */
    public static class Status {
        public static final int PROCESSING = 0; // 处理中
        public static final int AVAILABLE = 1;  // 可用
        public static final int FAILED = 2;     // 失败
    }

    /**
     * 判断是否为文本文件
     */
    public boolean isTextFile() {
        if (fileType == null) {
            return false;
        }
        return fileType.startsWith("text/") || 
               fileType.equals("application/json") ||
               fileType.equals("application/xml");
    }

    /**
     * 判断是否为图片文件
     */
    public boolean isImageFile() {
        if (fileType == null) {
            return false;
        }
        return fileType.startsWith("image/");
    }

    /**
     * 判断是否应该存储内容到content字段
     * 规则：文本文件且小于16MB
     */
    public boolean shouldStoreContent() {
        return isTextFile() && fileSize != null && fileSize < 16 * 1024 * 1024;
    }

    /**
     * 获取文件扩展名
     */
    public String getFileExtension() {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }

    /**
     * 获取不带扩展名的文件名
     */
    public String getFileNameWithoutExtension() {
        if (fileName == null || !fileName.contains(".")) {
            return fileName;
        }
        return fileName.substring(0, fileName.lastIndexOf("."));
    }
}
