package com.instser.chat.vo;

import lombok.Data;

/**
 * 支付方式VO类
 *
 * @author instser
 * @since 2024-01-01
 */
@Data
public class PaymentMethodVO {

    /**
     * 支付方式ID
     */
    private String id;

    /**
     * 显示名称
     */
    private String name;

    /**
     * 支付方式类型
     */
    private String type;

    /**
     * 是否可用
     */
    private Boolean available;

    public PaymentMethodVO() {}

    public PaymentMethodVO(String id, String name, String type, Boolean available) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.available = available;
    }
}
