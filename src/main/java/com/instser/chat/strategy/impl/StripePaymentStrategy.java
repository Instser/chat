package com.instser.chat.strategy.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.common.exception.PaymentException;
import com.instser.chat.common.exception.ResourceNotFoundException;
import com.instser.chat.common.util.LogUtils;
import com.instser.chat.config.StripeConfig;
import com.instser.chat.entity.Package;
import com.instser.chat.entity.UserOrder;
import com.instser.chat.service.OrderService;
import com.instser.chat.service.PackageActivationService;
import com.instser.chat.service.PackageService;
import com.instser.chat.service.RedisPaymentSecurityService;
import com.instser.chat.service.StripeService;
import com.instser.chat.strategy.PaymentStrategy;
import com.instser.chat.vo.PaymentRequestVO;
import com.instser.chat.vo.PaymentResponseVO;
import com.instser.chat.vo.StripePaymentResponseVO;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Stripe支付策略实现类
 * 
 * @author instser
 * @since 2025-08-29
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StripePaymentStrategy implements PaymentStrategy {

    private final StripeConfig stripeConfig;
    private final StripeService stripeService;
    private final OrderService orderService;
    private final PackageService packageService;
    private final PackageActivationService packageActivationService;
    private final RedisPaymentSecurityService redisPaymentSecurityService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getPaymentMethod() {
        return "stripe";
    }

    @Override
    public PaymentResponseVO createPayment(PaymentRequestVO request) {
        LogUtils.logUserOperation(log, request.getUserId(), "Stripe支付创建", 
            "开始创建支付，套餐ID：{}，货币：{}", request.getPackageId(), request.getCurrency());

        try {
            // 1. 验证请求参数
            validatePaymentRequest(request);

            // 2. 获取套餐信息
            Package packageInfo = packageService.getPackageById(request.getPackageId());
            if (packageInfo == null) {
                throw ResourceNotFoundException.packageNotFound(request.getPackageId());
            }

            // 3. 创建订单记录（包含优惠价格选择逻辑）
            UserOrder order = orderService.createOrder(
                request.getUserId(), 
                request.getPackageId(), 
                request.getCurrency(), 
                getPaymentMethod()
            );

            LogUtils.logUserDebug(log, request.getUserId(), "Stripe支付创建", 
                "订单创建成功，订单号：{}", order.getOrderNo());

            // 4. 验证订单金额
            stripeService.validateAmount(order.getAmount(), order.getCurrency());

            LogUtils.logUserDebug(log, request.getUserId(), "Stripe支付创建",
                "订单金额验证通过，订单金额：{} {}，套餐原价：{} USD",
                order.getAmount(), order.getCurrency(), packageInfo.getPrice());

            // 5. 创建Stripe PaymentIntent
            PaymentIntent paymentIntent = stripeService.createPaymentIntent(
                order.getAmount(),
                order.getCurrency().toLowerCase(),
                order.getOrderNo(),
                request.getUserId()
            );

            // 6. 更新订单的PaymentIntent ID
            updateOrderPaymentIntentId(order.getOrderNo(), paymentIntent.getId());

            // 7. 构建响应
            StripePaymentResponseVO response = StripePaymentResponseVO.success(
                order.getOrderNo(),
                paymentIntent.getClientSecret(),
                stripeConfig.getPublicKey(),
                paymentIntent.getId(),
                order.getAmount(),
                order.getCurrency(),
                stripeConfig.getEnvironment()
            );

            LogUtils.logUserOperation(log, request.getUserId(), "Stripe支付创建", 
                "支付创建成功，订单号：{}，PaymentIntent：{}", order.getOrderNo(), paymentIntent.getId());

            return response;

        } catch (PaymentException | ResourceNotFoundException e) {
            LogUtils.logUserWarning(log, request.getUserId(), "Stripe支付创建", 
                "支付创建失败，原因：{}", e.getMessage());
            throw e;
        } catch (Exception e) {
            LogUtils.logUserError(log, request.getUserId(), "Stripe支付创建", 
                "支付创建异常，错误：{}", e, e.getMessage());
            throw new PaymentException("创建Stripe支付失败: " + e.getMessage());
        }
    }

    @Override
    public boolean handleCallback(String callbackData) {
        try {
            log.info("收到Stripe回调数据，数据长度：{}", callbackData != null ? callbackData.length() : 0);

            // 解析Webhook事件数据
            JsonNode eventData = objectMapper.readTree(callbackData);
            String eventType = eventData.get("type").asText();
            String eventId = eventData.get("id").asText();

            log.info("处理Stripe Webhook事件，ID：{}，类型：{}", eventId, eventType);

            // 只处理PaymentIntent相关事件
            if (!eventType.startsWith("payment_intent.")) {
                log.debug("忽略非PaymentIntent事件：{}", eventType);
                return true; // 非PaymentIntent事件直接返回成功
            }

            // 提取PaymentIntent对象
            JsonNode dataObject = eventData.get("data").get("object");
            String paymentIntentId = dataObject.get("id").asText();
            String status = dataObject.get("status").asText();
            
            // 从metadata中获取订单号
            JsonNode metadata = dataObject.get("metadata");
            String orderNo = metadata != null && metadata.has("order_id") ? 
                metadata.get("order_id").asText() : null;

            if (!StringUtils.hasText(orderNo)) {
                log.warn("Stripe Webhook事件中缺少订单号，PaymentIntent：{}", paymentIntentId);
                return false;
            }

            log.info("处理订单：{}，PaymentIntent：{}，状态：{}", orderNo, paymentIntentId, status);

            // 查找订单
            UserOrder order = orderService.getOrderByOrderNo(orderNo);
            if (order == null) {
                log.error("未找到对应订单，订单号：{}", orderNo);
                return false;
            }

            // 检查订单是否过期
            if (orderService.isOrderExpired(orderNo)) {
                log.warn("订单已过期，拒绝处理Stripe回调，订单号：{}", orderNo);
                return false;
            }

            // 转换支付状态
            Integer newStatus = stripeService.convertPaymentIntentStatus(status);
            
            // 如果订单已经是目标状态，直接返回成功（幂等性）
            if (order.getPaymentStatus().equals(newStatus)) {
                log.info("订单状态已经是目标状态，跳过更新，订单号：{}，状态：{}", orderNo, newStatus);
                return true;
            }

            // 验证状态流转的合法性
            if (!isValidStatusTransition(order.getPaymentStatus(), newStatus)) {
                log.error("非法的状态流转，订单号：{}，当前状态：{}，目标状态：{}", 
                    orderNo, order.getPaymentStatus(), newStatus);
                return false;
            }

            // 更新订单状态
            boolean updated = orderService.updatePaymentStatus(orderNo, newStatus, paymentIntentId);
            if (!updated) {
                log.error("更新订单状态失败，订单号：{}", orderNo);
                return false;
            }

            log.info("订单状态更新成功，订单号：{}，状态：{}，PaymentIntent：{}", 
                orderNo, newStatus, paymentIntentId);

            // 如果支付成功，自动激活套餐
            if (newStatus == 1) {
                try {
                    boolean activationSuccess = packageActivationService.activatePackageAfterPayment(orderNo);
                    if (activationSuccess) {
                        log.info("Stripe支付成功后套餐激活成功，订单号：{}", orderNo);
                    } else {
                        log.error("Stripe支付成功后套餐激活失败，订单号：{}", orderNo);
                    }
                } catch (Exception e) {
                    log.error("Stripe支付成功后套餐激活异常，订单号：{}", orderNo, e);
                }

                // 释放用户支付会话锁
                redisPaymentSecurityService.releasePaymentSession(order.getUserId());
                log.debug("释放用户{}支付会话锁，订单号：{}", order.getUserId(), orderNo);
            }

            return true;

        } catch (Exception e) {
            log.error("处理Stripe回调异常", e);
            return false;
        }
    }

    @Override
    public boolean isConfigComplete() {
        boolean complete = stripeConfig.isConfigComplete();
        if (!complete) {
            log.warn("Stripe支付策略配置不完整");
        }
        return complete;
    }

    @Override
    public String queryPaymentStatus(String orderNo) {
        try {
            LogUtils.logUserDebug(log, null, "Stripe支付查询", "查询订单支付状态，订单号：{}", orderNo);

            // 查找订单
            UserOrder order = orderService.getOrderByOrderNo(orderNo);
            if (order == null) {
                throw ResourceNotFoundException.orderNotFound(orderNo);
            }

            // 获取PaymentIntent ID（可能存储在transaction_id字段中）
            String paymentIntentId = order.getTransactionId();
            if (!StringUtils.hasText(paymentIntentId)) {
                log.warn("订单缺少PaymentIntent ID，订单号：{}", orderNo);
                return getStatusDescription(order.getPaymentStatus());
            }

            // 查询Stripe PaymentIntent状态
            PaymentIntent paymentIntent = stripeService.retrievePaymentIntent(paymentIntentId);
            Integer stripeStatus = stripeService.convertPaymentIntentStatus(paymentIntent.getStatus());

            // 如果Stripe状态与本地状态不一致，同步更新
            if (!order.getPaymentStatus().equals(stripeStatus)) {
                log.info("同步订单状态，订单号：{}，本地状态：{}，Stripe状态：{}", 
                    orderNo, order.getPaymentStatus(), stripeStatus);
                
                boolean updated = orderService.updatePaymentStatus(orderNo, stripeStatus, paymentIntentId);
                if (updated) {
                    log.info("订单状态同步成功，订单号：{}", orderNo);
                    
                    // 如果是支付成功，触发套餐激活
                    if (stripeStatus == 1) {
                        try {
                            packageActivationService.activatePackageAfterPayment(orderNo);
                        } catch (Exception e) {
                            log.error("状态同步后套餐激活异常，订单号：{}", orderNo, e);
                        }
                    }
                } else {
                    log.warn("订单状态同步失败，订单号：{}", orderNo);
                }
            }

            return getStatusDescription(stripeStatus);

        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询Stripe支付状态异常，订单号：{}", orderNo, e);
            throw new PaymentException("查询支付状态失败: " + e.getMessage());
        }
    }

    /**
     * 验证支付请求参数
     */
    private void validatePaymentRequest(PaymentRequestVO request) {
        if (request == null) {
            throw new PaymentException("支付请求不能为空");
        }
        if (request.getUserId() == null) {
            throw new PaymentException("用户ID不能为空");
        }
        if (request.getPackageId() == null) {
            throw new PaymentException("套餐ID不能为空");
        }
        if (!StringUtils.hasText(request.getCurrency())) {
            throw new PaymentException("货币类型不能为空");
        }
        if (!stripeConfig.isCurrencySupported(request.getCurrency())) {
            throw new PaymentException("不支持的货币类型: " + request.getCurrency());
        }
    }

    /**
     * 更新订单的PaymentIntent ID
     */
    private void updateOrderPaymentIntentId(String orderNo, String paymentIntentId) {
        try {
            // 将PaymentIntent ID存储到订单的transaction_id字段中
            orderService.updatePaymentStatus(orderNo, 0, paymentIntentId);
            log.debug("更新订单PaymentIntent ID，订单号：{}，PaymentIntent：{}", orderNo, paymentIntentId);
        } catch (Exception e) {
            log.warn("更新订单PaymentIntent ID失败，订单号：{}，PaymentIntent：{}", 
                orderNo, paymentIntentId, e);
        }
    }

    /**
     * 验证订单状态流转的合法性
     */
    private boolean isValidStatusTransition(Integer currentStatus, Integer newStatus) {
        if (currentStatus == null || newStatus == null) {
            return false;
        }

        // 相同状态无需流转
        if (currentStatus.equals(newStatus)) {
            return true;
        }

        // 已支付的订单只能变为已退款
        if (currentStatus == 1 && newStatus != 3) {
            return false;
        }

        // 已取消、已退款的订单不能变为其他状态
        if (currentStatus == 2 || currentStatus == 3) {
            return false;
        }

        // 未支付状态可以流转到任何其他状态
        if (currentStatus == 0) {
            return true;
        }

        return false;
    }

    /**
     * 获取状态描述
     */
    private String getStatusDescription(Integer status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case 0: return "未支付";
            case 1: return "已支付"; 
            case 2: return "已取消";
            case 3: return "已退款";
            default: return "未知状态(" + status + ")";
        }
    }
}
