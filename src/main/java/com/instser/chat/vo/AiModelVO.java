package com.instser.chat.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * AI模型VO
 *
 * @author instser
 * @since 2024-01-01
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class AiModelVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 模型ID
     */
    private Long id;

    /**
     * 模型代码
     */
    private String modelCode;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 模型描述
     */
    private String description;

    /**
     * 提供商
     */
    private String provider;

    /**
     * 版本号
     */
    private String version;

    /**
     * Token上限
     */
    private Integer tokenLimit;

    /**
     * 每千token价格（美元）
     */
    private BigDecimal pricePer1kTokens;

    /**
     * 是否可用（0-不可用，1-可用）
     */
    private Integer isAvailable;

    /**
     * API基础URL
     */
    private String baseUrl;

    /**
     * API密钥
     */
    private String apiKey;
}
