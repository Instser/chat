package com.instser.chat.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户每日使用记录实体类
 *
 * @author instser
 * @since 2025-06-18
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_user_daily_usage")
public class UserDailyUsage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 用户ID
     */
    @TableField("user_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /**
     * 套餐ID
     */
    @TableField("package_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long packageId;

    /**
     * 使用日期
     */
    @TableField("usage_date")
    private LocalDate usageDate;

    /**
     * 已使用聊天次数
     */
    @TableField("chat_count_used")
    private Integer chatCountUsed;

    /**
     * 当日限制次数
     */
    @TableField("daily_limit")
    private Integer dailyLimit;

    /**
     * 最后聊天时间
     */
    @TableField("last_chat_time")
    private LocalDateTime lastChatTime;

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
}
