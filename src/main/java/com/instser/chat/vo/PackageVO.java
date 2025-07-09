package com.instser.chat.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 套餐VO类
 *
 * @author instser
 * @since 2024-01-01
 */
@Data
public class PackageVO {

    /**
     * 主键ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private String id;

    /**
     * 套餐名称
     */
    private String packageName;

    /**
     * 套餐描述
     */
    private String description;

    /**
     * 套餐类型
     */
    private String packageType;

    /**
     * 价格（美元）
     */
    private BigDecimal priceUsd;

    /**
     * 价格（人民币）
     */
    private BigDecimal priceCny;

    /**
     * 优惠价格（人民币）
     */
    private BigDecimal discountPrice;

    /**
     * 有效期（天数）
     */
    private Integer validityDays;

    /**
     * 包含的AI对话次数
     */
    private Integer chatCount;

    /**
     * 每日聊天次数限制
     */
    private Integer dailyChatLimit;

    /**
     * 限制类型（daily-每日限制，total-总次数限制）
     */
    private String limitType;

    /**
     * 包含的AI模型权限
     */
    private List<String> modelPermissions;

    /**
     * 是否启用
     */
    private Boolean isEnabled;

    /**
     * 排序权重
     */
    private Integer sortOrder;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;
}
