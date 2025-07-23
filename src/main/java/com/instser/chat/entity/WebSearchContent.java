package com.instser.chat.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 联网查询内容实体类
 *
 * @author instser
 * @since 2025-07-10
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_web_search_content")
public class WebSearchContent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 联网内容ID（UUID）
     */
    @TableField("web_search_id")
    private String webSearchId;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 会话ID
     */
    @TableField("session_id")
    private String sessionId;

    /**
     * 消息ID
     */
    @TableField("message_id")
    private String messageId;



    /**
     * 联网查询结果JSON（仅存储results数组）
     * 格式：[{"url":"","title":"","content":"","favicon":""}]
     */
    @TableField("content")
    private String content;

    /**
     * 结果数量
     */
    @TableField("result_count")
    private Integer resultCount;

    /**
     * 状态（0-失败，1-成功）
     */
    @TableField("status")
    private Integer status;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

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
}
