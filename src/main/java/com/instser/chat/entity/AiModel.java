package com.instser.chat.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI模型实体类
 *
 * @author instser
 * @since 2024-01-01
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_ai_model")
public class AiModel implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 模型代码
     */
    @TableField("model_code")
    private String modelCode;

    /**
     * 模型名称
     */
    @TableField("model_name")
    private String modelName;

    /**
     * 模型描述
     */
    @TableField("description")
    private String description;

    /**
     * 提供商（OpenAI/Anthropic/自研等）
     */
    @TableField("provider")
    private String provider;

    /**
     * 版本号
     */
    @TableField("version")
    private String version;

    /**
     * Token上限
     */
    @TableField("token_limit")
    private Integer tokenLimit;

    /**
     * 每千token价格（美元）
     */
    @TableField("price_per_1k_tokens")
    private BigDecimal pricePer1kTokens;

    /**
     * 是否可用（0-不可用，1-可用）
     */
    @TableField("is_available")
    private Integer isAvailable;

    /**
     * 是否可见（0-不可见，1-可见）
     */
    @TableField("is_visible")
    private Integer isVisible;

    /**
     * API基础URL
     */
    @TableField("base_url")
    private String baseUrl;

    /**
     * API密钥
     */
    @TableField("api_key")
    private String apiKey;

    /**
     * 基础权重（1-100，密钥轮询用）
     * 有效权重 = max(1, weight − failPenalty × consecutiveFails)
     */
    @TableField("weight")
    private Integer weight;

    /**
     * 累计成功次数
     */
    @TableField("success_count")
    private Long successCount;

    /**
     * 累计失败次数
     */
    @TableField("fail_count")
    private Long failCount;

    /**
     * 连续失败次数（成功即清零，用于动态降权）
     */
    @TableField("consecutive_fails")
    private Integer consecutiveFails;

    /**
     * 最近一次失败错误摘要（截断255）
     */
    @TableField("last_error")
    private String lastError;

    /**
     * 最近一次使用时间
     */
    @TableField("last_used_time")
    private LocalDateTime lastUsedTime;

    /**
     * 最近一次成功时间
     */
    @TableField("last_success_time")
    private LocalDateTime lastSuccessTime;

    /**
     * 最近一次失败时间
     */
    @TableField("last_fail_time")
    private LocalDateTime lastFailTime;

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

    public String getModelCode() {
        return trimValue(modelCode);
    }

    public String getModelName() {
        return trimValue(modelName);
    }

    public String getProvider() {
        return trimValue(provider);
    }

    public String getBaseUrl() {
        return trimValue(baseUrl);
    }

    public String getApiKey() {
        return trimValue(apiKey);
    }

    private String trimValue(String value) {
        return value == null ? null : value.trim();
    }
}
