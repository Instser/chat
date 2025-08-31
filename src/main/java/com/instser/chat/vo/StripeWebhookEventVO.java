package com.instser.chat.vo;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Stripe Webhook事件VO
 * 
 * @author instser
 * @since 2025-08-29
 */
@Data
@Schema(description = "Stripe Webhook事件")
public class StripeWebhookEventVO {

    @Schema(description = "事件ID")
    private String id;

    @Schema(description = "事件类型")
    private String type;

    @Schema(description = "API版本")
    @JsonProperty("api_version")
    private String apiVersion;

    @Schema(description = "创建时间戳")
    private Long created;

    @Schema(description = "事件数据")
    private Object data;

    @Schema(description = "是否实时事件")
    private Boolean livemode;

    @Schema(description = "等待重试次数")
    @JsonProperty("pending_webhooks")
    private Integer pendingWebhooks;

    @Schema(description = "请求ID")
    private String request;

    /**
     * 获取PaymentIntent对象（当事件类型为payment_intent相关时）
     */
    @SuppressWarnings("unchecked")
    public Object getPaymentIntent() {
        if (data != null && data instanceof java.util.Map) {
            java.util.Map<String, Object> dataMap = (java.util.Map<String, Object>) data;
            return dataMap.get("object");
        }
        return null;
    }

    /**
     * 检查是否为PaymentIntent相关事件
     */
    public boolean isPaymentIntentEvent() {
        return type != null && type.startsWith("payment_intent.");
    }

    /**
     * 检查是否为支付成功事件
     */
    public boolean isPaymentSucceededEvent() {
        return "payment_intent.succeeded".equals(type);
    }

    /**
     * 检查是否为支付失败事件
     */
    public boolean isPaymentFailedEvent() {
        return "payment_intent.payment_failed".equals(type);
    }

    /**
     * 检查是否为支付取消事件
     */
    public boolean isPaymentCanceledEvent() {
        return "payment_intent.canceled".equals(type);
    }
}