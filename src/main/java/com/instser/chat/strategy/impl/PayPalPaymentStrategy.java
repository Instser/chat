package com.instser.chat.strategy.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.common.exception.PaymentException;
import com.instser.chat.common.exception.ResourceNotFoundException;
import com.instser.chat.common.util.LogUtils;
import com.instser.chat.config.PayPalConfig;
import com.instser.chat.entity.Package;
import com.instser.chat.entity.UserOrder;
import com.instser.chat.service.OrderService;
import com.instser.chat.service.PackageActivationService;
import com.instser.chat.service.PackageService;
import com.instser.chat.service.PayPalService;
import com.instser.chat.service.RedisPaymentSecurityService;
import com.instser.chat.strategy.PaymentStrategy;
import com.instser.chat.vo.PayPalResponseVO;
import com.instser.chat.vo.PaymentRequestVO;
import com.instser.chat.vo.PaymentResponseVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * PayPal支付策略实现类
 * 
 * @author instser
 * @since 2024-09-10
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayPalPaymentStrategy implements PaymentStrategy {

    private final PayPalConfig payPalConfig;
    private final PayPalService payPalService;
    private final OrderService orderService;
    private final PackageService packageService;
    private final PackageActivationService packageActivationService;
    private final RedisPaymentSecurityService redisPaymentSecurityService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getPaymentMethod() {
        return "paypal";
    }

    @Override
    public PaymentResponseVO createPayment(PaymentRequestVO request) {
        LogUtils.logUserOperation(log, request.getUserId(), "PayPal支付创建", 
            "开始创建支付，套餐ID：{}，货币：{}", request.getPackageId(), request.getCurrency());

        try {
            // 1. 验证请求参数
            validatePaymentRequest(request);

            // 2. 获取套餐信息
            Package packageInfo = packageService.getPackageById(request.getPackageId());
            if (packageInfo == null) {
                throw ResourceNotFoundException.packageNotFound(request.getPackageId());
            }

            // 3. 验证金额和货币
            payPalService.validateAmount(packageInfo.getPrice(), request.getCurrency());

            LogUtils.logUserDebug(log, request.getUserId(), "PayPal支付创建", 
                "套餐验证通过，套餐名：{}，价格：{} {}", packageInfo.getPackageName(), 
                packageInfo.getPrice(), request.getCurrency());

            // 4. 创建订单记录
            UserOrder order = orderService.createOrder(
                request.getUserId(), 
                request.getPackageId(), 
                request.getCurrency(), 
                getPaymentMethod()
            );

            LogUtils.logUserDebug(log, request.getUserId(), "PayPal支付创建", 
                "订单创建成功，订单号：{}", order.getOrderNo());

            // 5. 创建PayPal订单
            Map<String, Object> payPalOrderResult = payPalService.createPayPalOrder(order, request.getCurrency());

            String paypalOrderId = (String) payPalOrderResult.get("paypalOrderId");
            String approvalUrl = (String) payPalOrderResult.get("approvalUrl");

            // 6. 更新订单中的PayPal订单ID
            updateOrderPayPalId(order.getOrderNo(), paypalOrderId);

            // 7. 构建响应
            PayPalResponseVO response = PayPalResponseVO.success(
                order.getOrderNo(),
                paypalOrderId,
                approvalUrl,
                payPalConfig.getClientId(), // 前端SDK需要
                packageInfo.getPrice(),
                request.getCurrency(),
                payPalConfig.getEnvironment()
            );

            // 设置默认应用上下文
            response.setDefaultAppContext();
            if (StringUtils.hasText(payPalConfig.getReturnUrl()) || 
                StringUtils.hasText(payPalConfig.getCancelUrl())) {
                response.setRedirectUrls(payPalConfig.getReturnUrl(), payPalConfig.getCancelUrl());
            }

            LogUtils.logUserOperation(log, request.getUserId(), "PayPal支付创建", 
                "支付创建成功，订单号：{}，PayPal订单ID：{}", order.getOrderNo(), paypalOrderId);

            return response;

        } catch (PaymentException | ResourceNotFoundException e) {
            LogUtils.logUserWarning(log, request.getUserId(), "PayPal支付创建", 
                "支付创建失败，原因：{}", e.getMessage());
            throw e;
        } catch (Exception e) {
            LogUtils.logUserError(log, request.getUserId(), "PayPal支付创建", 
                "支付创建异常，错误：{}", e, e.getMessage());
            throw new PaymentException("创建PayPal支付失败: " + e.getMessage());
        }
    }

    @Override
    public boolean handleCallback(String callbackData) {
        try {
            log.info("收到PayPal回调数据，数据长度：{}", callbackData != null ? callbackData.length() : 0);

            // 解析Webhook事件数据
            Map<String, Object> eventData = objectMapper.readValue(callbackData, 
                new TypeReference<Map<String, Object>>() {});

            String eventType = (String) eventData.get("event_type");
            String eventId = (String) eventData.get("id");

            log.info("处理PayPal Webhook事件，ID：{}，类型：{}", eventId, eventType);

            // 处理支付相关事件
            if (!isPaymentRelatedEvent(eventType)) {
                log.debug("忽略非支付相关事件：{}", eventType);
                return true; // 非支付相关事件直接返回成功
            }

            // 提取资源对象
            @SuppressWarnings("unchecked")
            Map<String, Object> resource = (Map<String, Object>) eventData.get("resource");
            if (resource == null) {
                log.warn("PayPal Webhook事件中缺少resource对象");
                return false;
            }

            String paypalOrderId = (String) resource.get("id");
            String status = (String) resource.get("status");

            // 查找对应的订单
            UserOrder order = findOrderByPayPalId(paypalOrderId);
            if (order == null) {
                log.error("未找到对应订单，PayPal订单ID：{}", paypalOrderId);
                return false;
            }

            // 检查订单是否过期
            if (orderService.isOrderExpired(order.getOrderNo())) {
                log.warn("订单已过期，拒绝处理PayPal回调，订单号：{}", order.getOrderNo());
                return false;
            }

            log.info("处理订单：{}，PayPal订单ID：{}，状态：{}", 
                order.getOrderNo(), paypalOrderId, status);

            // 转换支付状态
            Integer newStatus = payPalService.convertPayPalStatus(status);
            if (newStatus == null) {
                log.error("无效的PayPal支付状态：{}", status);
                return false;
            }

            // 如果订单已经是目标状态，直接返回成功（幂等性）
            if (order.getPaymentStatus().equals(newStatus)) {
                log.info("订单状态已经是目标状态，跳过更新，订单号：{}，状态：{}", 
                    order.getOrderNo(), newStatus);
                return true;
            }

            // 验证状态流转的合法性
            if (!isValidStatusTransition(order.getPaymentStatus(), newStatus)) {
                log.error("非法的状态流转，订单号：{}，当前状态：{}，目标状态：{}", 
                    order.getOrderNo(), order.getPaymentStatus(), newStatus);
                return false;
            }

            // 更新订单状态
            boolean updated = orderService.updatePaymentStatus(order.getOrderNo(), newStatus, paypalOrderId);
            if (!updated) {
                log.error("更新订单状态失败，订单号：{}", order.getOrderNo());
                return false;
            }

            log.info("订单状态更新成功，订单号：{}，状态：{}，PayPal订单ID：{}", 
                order.getOrderNo(), newStatus, paypalOrderId);

            // 如果支付成功，自动激活套餐
            if (newStatus == 1) {
                try {
                    boolean activationSuccess = packageActivationService.activatePackageAfterPayment(order.getOrderNo());
                    if (activationSuccess) {
                        log.info("PayPal支付成功后套餐激活成功，订单号：{}", order.getOrderNo());
                    } else {
                        log.error("PayPal支付成功后套餐激活失败，订单号：{}", order.getOrderNo());
                    }
                } catch (Exception e) {
                    log.error("PayPal支付成功后套餐激活异常，订单号：{}", order.getOrderNo(), e);
                }

                // 释放用户支付会话锁
                redisPaymentSecurityService.releasePaymentSession(order.getUserId());
                log.debug("释放用户{}支付会话锁，订单号：{}", order.getUserId(), order.getOrderNo());
            }

            return true;

        } catch (Exception e) {
            log.error("处理PayPal回调异常", e);
            return false;
        }
    }

    @Override
    public boolean isConfigComplete() {
        boolean complete = payPalConfig.isConfigComplete();
        if (!complete) {
            log.warn("PayPal支付策略配置不完整");
        }
        return complete;
    }

    @Override
    public String queryPaymentStatus(String orderNo) {
        try {
            LogUtils.logUserDebug(log, null, "PayPal支付查询", "查询订单支付状态，订单号：{}", orderNo);

            // 查找订单
            UserOrder order = orderService.getOrderByOrderNo(orderNo);
            if (order == null) {
                throw ResourceNotFoundException.orderNotFound(orderNo);
            }

            // 获取PayPal订单ID
            String paypalOrderId = order.getTransactionId();
            if (!StringUtils.hasText(paypalOrderId)) {
                log.warn("订单缺少PayPal订单ID，订单号：{}", orderNo);
                return getStatusDescription(order.getPaymentStatus());
            }

            // 查询PayPal订单状态
            Map<String, Object> paypalOrderDetails = payPalService.getPayPalOrderDetails(paypalOrderId);
            String paypalStatus = (String) paypalOrderDetails.get("status");
            Integer newStatus = payPalService.convertPayPalStatus(paypalStatus);

            // 如果PayPal状态与本地状态不一致，同步更新
            if (newStatus != null && !order.getPaymentStatus().equals(newStatus)) {
                log.info("同步订单状态，订单号：{}，本地状态：{}，PayPal状态：{}", 
                    orderNo, order.getPaymentStatus(), newStatus);
                
                boolean updated = orderService.updatePaymentStatus(orderNo, newStatus, paypalOrderId);
                if (updated) {
                    log.info("订单状态同步成功，订单号：{}", orderNo);
                    
                    // 如果是支付成功，触发套餐激活
                    if (newStatus == 1) {
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

            return getStatusDescription(newStatus != null ? newStatus : order.getPaymentStatus());

        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询PayPal支付状态异常，订单号：{}", orderNo, e);
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
        if (!payPalConfig.isCurrencySupported(request.getCurrency())) {
            throw new PaymentException("不支持的货币类型: " + request.getCurrency());
        }
    }

    /**
     * 更新订单的PayPal订单ID
     */
    private void updateOrderPayPalId(String orderNo, String paypalOrderId) {
        try {
            // 将PayPal订单ID存储到订单的transaction_id字段中
            orderService.updatePaymentStatus(orderNo, 0, paypalOrderId);
            log.debug("更新订单PayPal订单ID，订单号：{}，PayPal订单ID：{}", orderNo, paypalOrderId);
        } catch (Exception e) {
            log.warn("更新订单PayPal订单ID失败，订单号：{}，PayPal订单ID：{}", 
                orderNo, paypalOrderId, e);
        }
    }

    /**
     * 根据PayPal订单ID查找本地订单
     */
    private UserOrder findOrderByPayPalId(String paypalOrderId) {
        try {
            log.debug("根据PayPal订单ID查找订单：{}", paypalOrderId);
            
            // 使用OrderService的getOrderByTransactionId方法
            UserOrder order = orderService.getOrderByTransactionId(paypalOrderId);
            
            if (order != null) {
                log.debug("找到PayPal对应订单：订单号={}，用户ID={}，状态={}",
                    order.getOrderNo(), order.getUserId(), order.getPaymentStatus());
            } else {
                log.debug("未找到PayPal订单ID对应的本地订单：{}", paypalOrderId);
            }
            
            return order;
        } catch (Exception e) {
            log.error("根据PayPal订单ID查找订单异常，PayPal订单ID：{}", paypalOrderId, e);
            return null;
        }
    }

    /**
     * 检查是否是支付相关事件
     */
    private boolean isPaymentRelatedEvent(String eventType) {
        if (!StringUtils.hasText(eventType)) {
            return false;
        }
        
        return eventType.startsWith("CHECKOUT.ORDER") ||
               eventType.startsWith("PAYMENT.CAPTURE") ||
               eventType.startsWith("PAYMENT.ORDER");
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