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
}
