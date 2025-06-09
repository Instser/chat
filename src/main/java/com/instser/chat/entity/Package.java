package com.instser.chat.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 套餐实体类
 *
 * @author instser
 * @since 2024-01-01
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_package")
public class Package implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 套餐名称
     */
    @TableField("package_name")
    private String packageName;

    /**
     * 套餐描述
     */
    @TableField("description")
    private String description;

    /**
     * 套餐类型（basic/premium/enterprise等）
     */
    @TableField("package_type")
    private String packageType;

    /**
     * 价格（美元）
     */
    @TableField("price_usd")
    private BigDecimal priceUsd;

    /**
     * 价格（人民币）
     */
    @TableField("price_cny")
    private BigDecimal priceCny;

    /**
     * 有效期（天数）
     */
    @TableField("validity_days")
    private Integer validityDays;

    /**
     * 包含的AI对话次数
     */
    @TableField("chat_count")
    private Integer chatCount;

    /**
     * 包含的AI模型权限（JSON格式存储模型ID列表）
     */
    @TableField("model_permissions")
    private String modelPermissions;

    /**
     * 是否启用（0-禁用，1-启用）
     */
    @TableField("is_enabled")
    private Integer isEnabled;

    /**
     * 排序权重
     */
    @TableField("sort_order")
    private Integer sortOrder;

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
