package com.instser.chat.strategy.impl;

import com.alipay.global.api.AlipayClient;
import com.alipay.global.api.model.Result;
import com.alipay.global.api.model.ams.*;
import com.alipay.global.api.request.ams.pay.AlipayPayRequest;
import com.alipay.global.api.response.ams.pay.AlipayPayResponse;
import com.instser.chat.common.exception.ExternalServiceException;
import com.instser.chat.common.exception.PaymentException;
import com.instser.chat.config.AlipayConfig;
import com.instser.chat.entity.Package;
import com.instser.chat.entity.UserOrder;
import com.instser.chat.service.OrderService;
import com.instser.chat.service.PackageService;
import com.instser.chat.strategy.PaymentStrategy;
import com.instser.chat.util.MoneyUtil;
import com.instser.chat.vo.PaymentRequestVO;
import com.instser.chat.vo.PaymentResponseVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * 支付宝支付策略实现
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlipayStrategy implements PaymentStrategy {

    private final AlipayClient alipayClient;
    private final AlipayConfig alipayConfig;
    private final OrderService orderService;
    private final PackageService packageService;

    @Override
    public String getPaymentMethod() {
        return "alipay";
    }

    @Override
    public PaymentResponseVO createPayment(PaymentRequestVO paymentRequest) {
        try {
            // 检查配置
            if (!isConfigComplete()) {
                log.warn("支付宝配置不完整，无法创建支付");
                throw new PaymentException("支付宝配置未完成，请联系管理员");
            }

            // 创建订单
            UserOrder order = orderService.createOrder(
                paymentRequest.getUserId(),
                paymentRequest.getPackageId(),
                paymentRequest.getCurrency(),
                getPaymentMethod()
            );

            // 获取套餐信息
            Package packageInfo = packageService.getPackageById(paymentRequest.getPackageId());
            if (packageInfo == null) {
                throw new PaymentException("套餐不存在");
            }

            // 构建支付宝支付请求
            AlipayPayRequest alipayPayRequest = buildAlipayPayRequest(order, packageInfo, paymentRequest);

            // 调用支付宝API
            AlipayPayResponse alipayPayResponse = alipayClient.execute(alipayPayRequest);

            if (alipayPayResponse == null || alipayPayResponse.getResult() == null) {
                log.error("支付宝API响应为空，订单号: {}", order.getOrderNo());
                throw ExternalServiceException.paymentServiceError("alipay", "API响应为空");
            }

            Result result = alipayPayResponse.getResult();
            if (!"SUCCESS".equals(result.getResultCode())) {
                log.error("支付请求失败，订单号: {}, 错误: {}", order.getOrderNo(), result.getResultMessage());
                throw ExternalServiceException.paymentServiceError("alipay", result.getResultMessage());
            }

            // 提取支付URL
            String paymentUrl = extractPaymentUrl(alipayPayResponse);
            if (paymentUrl == null || paymentUrl.trim().isEmpty()) {
                log.error("无法提取支付URL，订单号: {}", order.getOrderNo());
                throw new PaymentException("无法获取支付链接");
            }

            // 构建响应
            PaymentResponseVO response = new PaymentResponseVO();
            response.setSuccess(true);
            response.setOrderNo(order.getOrderNo());
            response.setPaymentMethod(getPaymentMethod());
            response.setAmount(order.getAmount());
            response.setCurrency(order.getCurrency());
            response.setPaymentUrl(paymentUrl);
            response.setMessage("支付订单创建成功");

            log.info("支付宝支付订单创建成功，订单号: {}", order.getOrderNo());
            return response;

        } catch (Exception e) {
            log.error("创建支付宝支付异常", e);
            throw new PaymentException("创建支付失败", e.getMessage());
        }
    }

    @Override
    public boolean handleCallback(String callbackData) {
        // TODO: 实现支付宝回调处理逻辑
        log.info("处理支付宝回调: {}", callbackData);
        return true;
    }

    @Override
    public boolean isConfigComplete() {
        return StringUtils.hasText(alipayConfig.getClientId()) &&
               StringUtils.hasText(alipayConfig.getPrivateKey()) &&
               StringUtils.hasText(alipayConfig.getPublicKey());
    }

    @Override
    public String queryPaymentStatus(String orderNo) {
        // TODO: 实现支付状态查询
        log.info("查询支付宝支付状态，订单号: {}", orderNo);
        return "PENDING";
    }

    /**
     * 构建支付宝支付请求
     */
    private AlipayPayRequest buildAlipayPayRequest(UserOrder order, Package packageInfo, PaymentRequestVO paymentRequest) {
        AlipayPayRequest request = new AlipayPayRequest();

        // 设置支付金额
        Amount amount = new Amount();
        amount.setCurrency(order.getCurrency());
        amount.setValue(MoneyUtil.formatAmountForAlipay(order.getAmount()));

        // 设置订单信息
        Order orderInfo = new Order();
        orderInfo.setReferenceOrderId(order.getOrderNo());
        orderInfo.setOrderDescription("套餐购买: " + packageInfo.getPackageName());
        orderInfo.setOrderAmount(amount);

        // 设置商品信息
        Goods goods = new Goods();
        goods.setReferenceGoodsId(packageInfo.getId().toString());
        goods.setGoodsName(packageInfo.getPackageName());
        goods.setGoodsCategory("DIGITAL_GOODS");
        goods.setGoodsUnitAmount(amount);
        goods.setGoodsQuantity("1");

        orderInfo.setGoods(java.util.Arrays.asList(goods));

        // 设置支付方式
        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.setPaymentMethodType("ALIPAY_CN");

        // 设置请求参数
        request.setPaymentRequestId(order.getPaymentRequestId());
        request.setOrder(orderInfo);
        request.setPaymentAmount(amount);
        request.setPaymentMethod(paymentMethod);
        request.setPaymentRedirectUrl(alipayConfig.getReturnUrl());
        request.setPaymentNotifyUrl(alipayConfig.getNotifyUrl());

        return request;
    }

    /**
     * 从支付响应中提取支付URL
     */
    private String extractPaymentUrl(AlipayPayResponse response) {
        try {
            // 根据支付宝SDK文档，支付URL通常在以下字段中

            // 1. 检查paymentActionForm字段（HTML表单）
            if (response.getPaymentActionForm() != null && !response.getPaymentActionForm().trim().isEmpty()) {
                return response.getPaymentActionForm();
            }

            // 2. 检查normalUrl字段
            if (response.getNormalUrl() != null && !response.getNormalUrl().trim().isEmpty()) {
                return response.getNormalUrl();
            }

            // 3. 尝试从redirectActionForm中提取URL（如果是对象类型）
            if (response.getRedirectActionForm() != null) {
                // redirectActionForm可能是一个对象，需要从中提取URL
                try {
                    // 这里可能需要根据实际的SDK结构调整
                    log.info("RedirectActionForm类型: {}", response.getRedirectActionForm().getClass().getName());
                    // 暂时返回toString，实际使用时需要根据具体结构调整
                    String redirectForm = response.getRedirectActionForm().toString();
                    if (redirectForm != null && !redirectForm.trim().isEmpty()) {
                        return redirectForm;
                    }
                } catch (Exception e) {
                    log.warn("处理redirectActionForm异常: {}", e.getMessage());
                }
            }

            // 如果都没有，记录警告并返回空
            log.warn("无法从支付响应中提取支付URL，可能需要根据实际API响应调整");
            log.debug("支付响应详情: {}", response);
            return null;
        } catch (Exception e) {
            log.error("提取支付URL异常", e);
            return null;
        }
    }
}
