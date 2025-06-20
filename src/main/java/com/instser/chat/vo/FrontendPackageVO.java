package com.instser.chat.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * 前端套餐VO类 - 符合前端API期望格式
 *
 * @author instser
 * @since 2024-01-01
 */
@Data
public class FrontendPackageVO {

    /**
     * 套餐ID
     */
    private Integer id;

    /**
     * 套餐名称
     */
    private String name;

    /**
     * 套餐描述
     */
    private String description;

    /**
     * 价格
     */
    private BigDecimal price;

    /**
     * 货币类型
     */
    private String currency;

    /**
     * 有效期天数 (直接映射数据库validity_days字段)
     */
    private Integer period;

    /**
     * 功能特性列表
     */
    private List<String> features;
}
