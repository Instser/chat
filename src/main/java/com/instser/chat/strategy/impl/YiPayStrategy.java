package com.instser.chat.strategy.impl;

import com.instser.chat.common.exception.PaymentException;
import com.instser.chat.config.YiPayConfig;
import com.instser.chat.entity.Package;
import com.instser.chat.entity.UserOrder;
import com.instser.chat.service.OrderService;
import com.instser.chat.service.PackageService;
import com.instser.chat.strategy.PaymentStrategy;
import com.instser.chat.util.MoneyUtil;
import com.instser.chat.util.PaymentSecurityUtil;
import com.instser.chat.vo.PaymentRequestVO;
import com.instser.chat.vo.PaymentResponseVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 易支付策略实现
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YiPayStrategy implements PaymentStrategy {

    private final YiPayConfig yiPayConfig;
    private final OrderService orderService;
    private final PackageService packageService;

    @Override
    public String getPaymentMethod() {
        return "yipay";
    }

    @Override
    public PaymentResponseVO createPayment(PaymentRequestVO paymentRequest) {
        try {
            // 检查配置
            if (!isConfigComplete()) {
                log.warn("易支付配置不完整，无法创建支付");
                throw new PaymentException("易支付配置未完成，请联系管理员");
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

            // 确定支付类型
            String paymentType = determineYiPayType(paymentRequest.getCurrency());

            // 构建支付URL
            String paymentUrl = buildYiPayUrl(order, paymentType);
            if (!StringUtils.hasText(paymentUrl)) {
                log.error("无法构建易支付URL，订单号: {}", order.getOrderNo());
                throw new PaymentException("无法生成支付链接");
            }

            // 构建响应
            PaymentResponseVO response = new PaymentResponseVO();
            response.setSuccess(true);
            response.setOrderNo(order.getOrderNo());
            response.setPaymentMethod(getPaymentMethod());
            response.setAmount(order.getAmount());
            response.setCurrency(order.getCurrency());
            response.setPaymentUrl(paymentUrl);
            response.setMessage("易支付订单创建成功");

            log.info("易支付订单创建成功，订单号: {}, 支付类型: {}", order.getOrderNo(), paymentType);
            return response;

        } catch (Exception e) {
            log.error("创建易支付订单异常", e);
            throw new PaymentException("创建支付失败", e.getMessage());
        }
    }

    @Override
    public boolean handleCallback(String callbackData) {
        // TODO: 实现易支付回调处理逻辑
        log.info("处理易支付回调: {}", callbackData);
        return true;
    }

    @Override
    public boolean isConfigComplete() {
        return StringUtils.hasText(yiPayConfig.getPid()) &&
               StringUtils.hasText(yiPayConfig.getKey()) &&
               StringUtils.hasText(yiPayConfig.getUrl());
    }

    @Override
    public String queryPaymentStatus(String orderNo) {
        // TODO: 实现支付状态查询
        log.info("查询易支付状态，订单号: {}", orderNo);
        return "PENDING";
    }

    /**
     * 确定易支付类型
     */
    private String determineYiPayType(String currency) {
        if ("CNY".equalsIgnoreCase(currency)) {
            return yiPayConfig.getDefaultType(); // 默认支付宝
        } else {
            return "alipay"; // 海外默认支付宝
        }
    }

    /**
     * 构建易支付URL
     */
    private String buildYiPayUrl(UserOrder order, String paymentType) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("pid", yiPayConfig.getPid());
            params.put("type", paymentType);
            params.put("out_trade_no", order.getOrderNo());
            params.put("notify_url", yiPayConfig.getNotifyUrl());
            params.put("return_url", yiPayConfig.getReturnUrl());
            params.put("name", "套餐购买");
            params.put("money", MoneyUtil.formatAmountForYiPay(order.getAmount()));

            // 生成签名
            String sign = PaymentSecurityUtil.generateYiPaySign(params, yiPayConfig.getKey());
            params.put("sign", sign);
            params.put("sign_type", "MD5");

            // 构建URL
            StringBuilder urlBuilder = new StringBuilder(yiPayConfig.getUrl());
            urlBuilder.append("/submit.php?");

            boolean first = true;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (!first) {
                    urlBuilder.append("&");
                }
                urlBuilder.append(entry.getKey())
                         .append("=")
                         .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
                first = false;
            }

            return urlBuilder.toString();

        } catch (Exception e) {
            log.error("构建易支付URL失败，订单号: {}", order.getOrderNo(), e);
            return null;
        }
    }
}
