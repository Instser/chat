package com.instser.chat.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Stripe支付响应VO
 * 
 * @author instser
 * @since 2025-08-29
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Stripe支付响应")
public class StripePaymentResponseVO extends PaymentResponseVO {

    @Schema(description = "Stripe PaymentIntent客户端密钥，用于前端确认支付")
    @JsonProperty("client_secret")
    private String clientSecret;

    @Schema(description = "Stripe可发布公钥，用于前端初始化Stripe.js")
    @JsonProperty("publishable_key") 
    private String publishableKey;

    @Schema(description = "PaymentIntent ID")
    @JsonProperty("payment_intent_id")
    private String paymentIntentId;

    @Schema(description = "支付金额（原始金额）")
    private BigDecimal amount;

    @Schema(description = "货币类型")
    private String currency;

    @Schema(description = "支付方式类型")
    @JsonProperty("payment_method_types")
    private String[] paymentMethodTypes;

    @Schema(description = "是否需要用户确认")
    @JsonProperty("requires_action")
    private Boolean requiresAction;

    @Schema(description = "Stripe环境（测试/生产）")
    private String environment;

    /**
     * 构造函数
     */
    public StripePaymentResponseVO() {
        super();
        // 设置支付方式为stripe
        this.setPaymentMethod("stripe");
    }

    /**
     * 创建成功响应
     */
    public static StripePaymentResponseVO success(String orderNo, String clientSecret, 
                                                 String publishableKey, String paymentIntentId,
                                                 BigDecimal amount, String currency, 
                                                 String environment) {
        StripePaymentResponseVO response = new StripePaymentResponseVO();
        response.setSuccess(true);
        response.setOrderNo(orderNo);
        response.setClientSecret(clientSecret);
        response.setPublishableKey(publishableKey);
        response.setPaymentIntentId(paymentIntentId);
        response.setAmount(amount);
        response.setCurrency(currency);
        response.setEnvironment(environment);
        response.setPaymentMethodTypes(new String[]{"card", "google_pay", "apple_pay"});
        response.setRequiresAction(false);
        return response;
    }

    /**
     * 创建失败响应
     */
    public static StripePaymentResponseVO failed(String errorMessage) {
        StripePaymentResponseVO response = new StripePaymentResponseVO();
        response.setSuccess(false);
        response.setErrorMessage(errorMessage);
        return response;
    }
}