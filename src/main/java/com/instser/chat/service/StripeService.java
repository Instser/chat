package com.instser.chat.service;

import com.instser.chat.common.exception.PaymentException;
import com.instser.chat.config.StripeConfig;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Stripe业务服务类
 * 封装Stripe API操作
 *
 * @author instser
 * @since 2025-08-29
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StripeService {

    private final StripeConfig stripeConfig;

    /**
     * 创建PaymentIntent
     *
     * @param amount   金额（USD）
     * @param currency 货币类型
     * @param orderId  订单ID
     * @param userId   用户ID
     * @return PaymentIntent对象
     */
    public PaymentIntent createPaymentIntent(BigDecimal amount, String currency, String orderId, Long userId) {
        try {
            // 验证配置
            if (!stripeConfig.isConfigComplete()) {
                throw new PaymentException("Stripe配置不完整，无法创建支付");
            }

            // 验证货币支持
            if (!stripeConfig.isCurrencySupported(currency)) {
                throw new PaymentException("不支持的货币类型: " + currency);
            }

            // 转换为最小货币单位（美分、分等）
            long amountInCents = convertToMinorUnit(amount, currency);

            log.debug("创建Stripe PaymentIntent，金额: {} {} ({}), 订单: {}, 用户: {}", 
                amount, currency.toUpperCase(), amountInCents, orderId, userId);

            // 构建PaymentIntent参数
            PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency(currency.toLowerCase())
                .putMetadata("order_id", orderId)
                .putMetadata("user_id", userId.toString())
                .putMetadata("source", "jinyu_chat_system")
                .putMetadata("environment", stripeConfig.getEnvironment())
                .setAutomaticPaymentMethods(
                    PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .build()
                );

            // 设置描述信息
            paramsBuilder.setDescription(String.format("金语聊天系统套餐支付 - 订单号: %s", orderId));

            // 创建PaymentIntent
            PaymentIntent paymentIntent = PaymentIntent.create(paramsBuilder.build());

            log.info("创建Stripe PaymentIntent成功");
            log.info("- PaymentIntent ID: {}", paymentIntent.getId());
            log.info("- 金额: {} {} ({})", amount, currency.toUpperCase(), amountInCents);
            log.info("- 状态: {}", paymentIntent.getStatus());
            log.info("- 订单号: {}", orderId);
            log.info("- 用户ID: {}", userId);

            return paymentIntent;

        } catch (StripeException e) {
            log.error("创建Stripe PaymentIntent失败");
            log.error("- 订单号: {}", orderId);
            log.error("- 金额: {} {}", amount, currency);
            log.error("- Stripe错误类型: {}", e.getClass().getSimpleName());
            log.error("- 错误代码: {}", e.getCode());
            log.error("- 错误消息: {}", e.getMessage());
            log.error("- 用户消息: {}", e.getUserMessage());

            String userMessage = e.getUserMessage() != null ? e.getUserMessage() : "创建支付失败，请稍后重试";
            throw new PaymentException("创建Stripe支付失败: " + userMessage);
        } catch (Exception e) {
            log.error("创建Stripe PaymentIntent异常，订单: {}", orderId, e);
            throw new PaymentException("创建支付异常: " + e.getMessage());
        }
    }

    /**
     * 查询PaymentIntent状态
     *
     * @param paymentIntentId PaymentIntent ID
     * @return PaymentIntent对象
     */
    public PaymentIntent retrievePaymentIntent(String paymentIntentId) {
        try {
            if (!stripeConfig.isConfigComplete()) {
                throw new PaymentException("Stripe配置不完整，无法查询支付状态");
            }

            log.debug("查询Stripe PaymentIntent: {}", paymentIntentId);

            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);

            log.debug("查询Stripe PaymentIntent成功，ID: {}, 状态: {}, 金额: {}", 
                paymentIntent.getId(), paymentIntent.getStatus(), paymentIntent.getAmount());

            return paymentIntent;

        } catch (StripeException e) {
            log.error("查询Stripe PaymentIntent失败");
            log.error("- PaymentIntent ID: {}", paymentIntentId);
            log.error("- Stripe错误类型: {}", e.getClass().getSimpleName());
            log.error("- 错误代码: {}", e.getCode());
            log.error("- 错误消息: {}", e.getMessage());

            String userMessage = e.getUserMessage() != null ? e.getUserMessage() : "查询支付状态失败";
            throw new PaymentException("查询Stripe支付状态失败: " + userMessage);
        } catch (Exception e) {
            log.error("查询Stripe PaymentIntent异常，ID: {}", paymentIntentId, e);
            throw new PaymentException("查询支付状态异常: " + e.getMessage());
        }
    }

    /**
     * 处理Webhook事件
     *
     * @param payload   Webhook载荷
     * @param signature Stripe签名
     * @return Event对象
     */
    public Event handleWebhookEvent(String payload, String signature) {
        try {
            if (!stripeConfig.isConfigComplete()) {
                throw new PaymentException("Stripe配置不完整，无法处理Webhook事件");
            }

            log.debug("处理Stripe Webhook事件，签名: {}", signature);

            // 验证签名并构建事件
            Event event = Webhook.constructEvent(payload, signature, stripeConfig.getWebhookSecret());

            log.info("收到Stripe Webhook事件");
            log.info("- 事件ID: {}", event.getId());
            log.info("- 事件类型: {}", event.getType());
            log.info("- API版本: {}", event.getApiVersion());
            log.info("- 创建时间: {}", new java.util.Date(event.getCreated() * 1000));

            return event;

        } catch (com.stripe.exception.SignatureVerificationException e) {
            log.error("Stripe Webhook签名验证失败");
            log.error("- 签名: {}", signature);
            log.error("- 错误消息: {}", e.getMessage());
            throw new PaymentException("Webhook签名验证失败");
        } catch (Exception e) {
            log.error("处理Stripe Webhook异常，签名: {}", signature, e);
            throw new PaymentException("处理Webhook事件失败: " + e.getMessage());
        }
    }

    /**
     * 根据PaymentIntent状态转换为系统订单状态
     *
     * @param paymentIntentStatus Stripe PaymentIntent状态
     * @return 系统订单状态 (0-未支付，1-已支付，2-已取消，3-已退款)
     */
    public Integer convertPaymentIntentStatus(String paymentIntentStatus) {
        if (paymentIntentStatus == null) {
            return 0; // 默认未支付
        }

        switch (paymentIntentStatus.toLowerCase()) {
            case "succeeded":
                return 1; // 已支付
            case "canceled":
            case "cancelled":
                return 2; // 已取消
            case "requires_payment_method":
            case "requires_confirmation":
            case "requires_action":
            case "processing":
                return 0; // 未支付/处理中
            default:
                log.warn("未知的PaymentIntent状态: {}", paymentIntentStatus);
                return 0; // 默认未支付
        }
    }

    /**
     * 将金额转换为最小货币单位
     *
     * @param amount   金额
     * @param currency 货币类型
     * @return 最小货币单位金额
     */
    private long convertToMinorUnit(BigDecimal amount, String currency) {
        // 大部分货币使用100的倍数（美分、分等）
        // 日元、韩元等不需要转换（已经是最小单位）
        switch (currency.toLowerCase()) {
            case "jpy": // 日元
            case "krw": // 韩元  
            case "pyg": // 巴拉圭瓜拉尼
            case "rwf": // 卢旺达法郎
            case "ugx": // 乌干达先令
            case "vnd": // 越南盾
            case "vuv": // 瓦努阿图瓦图
            case "xaf": // 中非法郎
            case "xof": // 西非法郎
            case "xpf": // 太平洋法郎
                return amount.longValue(); // 不需要转换
            default:
                return amount.multiply(new BigDecimal("100")).longValue(); // 转换为分
        }
    }

    /**
     * 验证金额有效性
     *
     * @param amount   金额
     * @param currency 货币类型
     */
    public void validateAmount(BigDecimal amount, String currency) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentException("支付金额必须大于0");
        }

        // 设置金额限制
        BigDecimal minAmount;
        BigDecimal maxAmount;

        switch (currency.toLowerCase()) {
            case "usd":
            case "eur":
            case "gbp":
            case "cad":
            case "aud":
            case "chf":
                minAmount = new BigDecimal("0.50");  // 最小0.5元
                maxAmount = new BigDecimal("10000"); // 最大10000元
                break;
            case "jpy":
                minAmount = new BigDecimal("50");    // 最小50日元
                maxAmount = new BigDecimal("1000000"); // 最大100万日元
                break;
            default:
                minAmount = new BigDecimal("0.50");
                maxAmount = new BigDecimal("10000");
                break;
        }

        if (amount.compareTo(minAmount) < 0) {
            throw new PaymentException(String.format("支付金额不能小于 %s %s", minAmount, currency.toUpperCase()));
        }

        if (amount.compareTo(maxAmount) > 0) {
            throw new PaymentException(String.format("支付金额不能超过 %s %s", maxAmount, currency.toUpperCase()));
        }
    }
}