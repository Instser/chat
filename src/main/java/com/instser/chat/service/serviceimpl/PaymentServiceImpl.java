package com.instser.chat.service.serviceimpl;

import com.alipay.global.api.AlipayClient;
import com.alipay.global.api.exception.AlipayApiException;
import com.alipay.global.api.model.Result;
import com.alipay.global.api.model.ams.*;
import com.alipay.global.api.request.ams.pay.AlipayPayRequest;
import com.alipay.global.api.response.ams.pay.AlipayPayResponse;
import com.alipay.global.api.tools.WebhookTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.common.exception.BusinessException;
import com.instser.chat.config.AlipayConfig;
import com.instser.chat.config.YiPayConfig;
import com.instser.chat.entity.Package;
import com.instser.chat.entity.UserOrder;
import com.instser.chat.service.OrderService;
import com.instser.chat.service.PackageService;
import com.instser.chat.service.PaymentService;

import com.instser.chat.vo.PaymentRequestVO;
import com.instser.chat.vo.PaymentResponseVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 支付服务实现类
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final AlipayClient alipayClient;
    private final AlipayConfig alipayConfig;
    private final YiPayConfig yiPayConfig;
    private final OrderService orderService;
    private final PackageService packageService;

    // 创建ObjectMapper实例，避免依赖注入问题
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public PaymentResponseVO createPayment(PaymentRequestVO paymentRequest) {
        PaymentResponseVO response = new PaymentResponseVO();

        try {
            // 根据paymentMethod字段选择支付方式
            String paymentMethod = paymentRequest.getPaymentMethod();

            if ("alipay".equals(paymentMethod)) {
                // 使用支付宝SDK
                return createAlipayPayment(paymentRequest);
            } else if ("yipay".equals(paymentMethod)) {
                // 使用易支付
                return createYiPayPayment(paymentRequest);
            } else {
                response.setSuccess(false);
                response.setErrorMessage("不支持的支付方式: " + paymentMethod);
                return response;
            }
        } catch (Exception e) {
            log.error("创建支付异常", e);
            response.setSuccess(false);
            response.setErrorMessage("系统异常，请稍后重试");
            return response;
        }
    }

    /**
     * 创建支付宝支付订单
     */
    private PaymentResponseVO createAlipayPayment(PaymentRequestVO paymentRequest) {
        PaymentResponseVO response = new PaymentResponseVO();

        try {
            // 检查配置
            if (!alipayConfig.isConfigComplete()) {
                log.warn("支付宝配置不完整，无法创建支付");
                response.setSuccess(false);
                response.setErrorMessage("支付宝配置未完成，请联系管理员");
                return response;
            }

            // 创建订单
            UserOrder order = orderService.createOrder(
                paymentRequest.getUserId(),
                paymentRequest.getPackageId(),
                paymentRequest.getCurrency(),
                paymentRequest.getPaymentMethod()
            );

            // 获取套餐信息
            Package packageInfo = packageService.getPackageById(paymentRequest.getPackageId());

            // 构建支付宝支付请求
            AlipayPayRequest alipayPayRequest = buildAlipayPayRequest(order, packageInfo, paymentRequest);

            // 调用支付宝API
            AlipayPayResponse alipayPayResponse = alipayClient.execute(alipayPayRequest);

            if (alipayPayResponse != null && alipayPayResponse.getResult() != null) {
                Result result = alipayPayResponse.getResult();

                if ("SUCCESS".equals(result.getResultCode())) {
                    // 支付请求成功
                    String paymentUrl = extractPaymentUrl(alipayPayResponse);
                    
                    // 更新订单支付URL
                    order.setPaymentUrl(paymentUrl);
                    orderService.updateById(order);

                    response.setSuccess(true);
                    response.setOrderNo(order.getOrderNo());
                    response.setPaymentRequestId(order.getPaymentRequestId());
                    response.setPaymentUrl(paymentUrl);
                    response.setAmount(order.getAmount());
                    response.setCurrency(order.getCurrency());
                    response.setPaymentStatus(order.getPaymentStatus());

                    log.info("支付请求创建成功，订单号: {}, 支付URL: {}", order.getOrderNo(), paymentUrl);
                } else {
                    // 支付请求失败
                    response.setSuccess(false);
                    response.setErrorMessage("支付请求失败: " + result.getResultMessage());
                    log.error("支付请求失败，订单号: {}, 错误: {}", order.getOrderNo(), result.getResultMessage());
                }
            } else {
                response.setSuccess(false);
                response.setErrorMessage("支付服务响应异常");
                log.error("支付宝API响应为空，订单号: {}", order.getOrderNo());
            }

        } catch (AlipayApiException e) {
            log.error("调用支付宝API异常", e);
            response.setSuccess(false);
            response.setErrorMessage("支付服务异常: " + e.getMessage());
        } catch (BusinessException e) {
            log.error("业务异常", e);
            response.setSuccess(false);
            response.setErrorMessage(e.getMessage());
        } catch (Exception e) {
            log.error("创建支付异常", e);
            response.setSuccess(false);
            response.setErrorMessage("系统异常，请稍后重试");
        }

        return response;
    }

    /**
     * 创建易支付订单
     */
    private PaymentResponseVO createYiPayPayment(PaymentRequestVO paymentRequest) {
        PaymentResponseVO response = new PaymentResponseVO();

        try {
            // 检查配置
            if (!yiPayConfig.isConfigComplete()) {
                log.warn("易支付配置不完整，无法创建支付");
                response.setSuccess(false);
                response.setErrorMessage("易支付配置未完成，请联系管理员");
                return response;
            }

            // 创建订单
            UserOrder order = orderService.createOrder(
                paymentRequest.getUserId(),
                paymentRequest.getPackageId(),
                paymentRequest.getCurrency(),
                paymentRequest.getPaymentMethod()
            );

            // 确定支付类型
            String paymentType = determineYiPayType(paymentRequest.getCurrency());

            // 构建支付URL
            String paymentUrl = buildYiPayUrl(order, paymentType);

            // 更新订单支付URL
            order.setPaymentUrl(paymentUrl);
            orderService.updateById(order);

            response.setSuccess(true);
            response.setOrderNo(order.getOrderNo());
            response.setPaymentRequestId(order.getPaymentRequestId());
            response.setPaymentUrl(paymentUrl);
            response.setAmount(order.getAmount());
            response.setCurrency(order.getCurrency());
            response.setPaymentStatus(order.getPaymentStatus());

            log.info("易支付订单创建成功，订单号: {}, 支付URL: {}", order.getOrderNo(), paymentUrl);

        } catch (BusinessException e) {
            log.error("业务异常", e);
            response.setSuccess(false);
            response.setErrorMessage(e.getMessage());
        } catch (Exception e) {
            log.error("创建易支付订单异常", e);
            response.setSuccess(false);
            response.setErrorMessage("系统异常，请稍后重试");
        }

        return response;
    }

    @Override
    public UserOrder queryPaymentStatus(String orderNo) {
        return orderService.getOrderByOrderNo(orderNo);
    }

    @Override
    public boolean handlePaymentNotify(String notifyBody, String signature, String requestTime, String clientId) {
        try {
            log.info("收到支付回调通知，clientId: {}, requestTime: {}", clientId, requestTime);

            // 验证签名
            if (!verifyNotifySignature("/api/payment/alipay/notify", "POST", clientId, requestTime, signature, notifyBody)) {
                log.error("支付回调签名验证失败");
                return false;
            }

            // 解析回调内容
            JsonNode notifyJson = objectMapper.readTree(notifyBody);
            
            // 提取关键信息
            String paymentRequestId = extractFromNotify(notifyJson, "paymentRequestId");
            String paymentStatus = extractFromNotify(notifyJson, "paymentStatus");
            String transactionId = extractFromNotify(notifyJson, "paymentId");

            if (!StringUtils.hasText(paymentRequestId)) {
                log.error("回调中缺少paymentRequestId");
                return false;
            }

            // 查找订单
            UserOrder order = orderService.getOrderByPaymentRequestId(paymentRequestId);
            if (order == null) {
                log.error("未找到对应订单，paymentRequestId: {}", paymentRequestId);
                return false;
            }

            // 更新订单状态
            Integer newStatus = parsePaymentStatus(paymentStatus);
            if (newStatus != null) {
                boolean updated = orderService.updatePaymentStatus(order.getOrderNo(), newStatus, transactionId);
                if (updated) {
                    log.info("订单状态更新成功，订单号: {}, 新状态: {}", order.getOrderNo(), newStatus);
                    return true;
                } else {
                    log.error("订单状态更新失败，订单号: {}", order.getOrderNo());
                }
            }

        } catch (Exception e) {
            log.error("处理支付回调异常", e);
        }

        return false;
    }

    @Override
    public boolean cancelPayment(String orderNo) {
        return orderService.cancelOrder(orderNo);
    }

    @Override
    public boolean verifyNotifySignature(String requestUri, String requestMethod, String clientId,
                                       String requestTime, String signature, String notifyBody) {
        try {
            if (!alipayConfig.isConfigComplete()) {
                log.warn("支付宝配置不完整，无法验证签名");
                return false;
            }

            return WebhookTool.checkSignature(requestUri, requestMethod, clientId, requestTime,
                                            signature, notifyBody, alipayConfig.getPublicKey());
        } catch (Exception e) {
            log.error("验证回调签名异常", e);
            return false;
        }
    }

    @Override
    public boolean handleYiPayNotify(Map<String, String> notifyParams) {
        try {
            // 验证必要参数
            String tradeNo = notifyParams.get("trade_no");
            String outTradeNo = notifyParams.get("out_trade_no");
            String tradeStatus = notifyParams.get("trade_status");
            String sign = notifyParams.get("sign");

            if (outTradeNo == null || sign == null) {
                log.error("易支付回调参数不完整");
                return false;
            }

            // 验证签名
            Map<String, String> signParams = new HashMap<>(notifyParams);
            signParams.remove("sign");
            signParams.remove("sign_type");

            boolean signValid = verifyYiPaySign(signParams, sign);
            if (!signValid) {
                log.error("易支付回调签名验证失败");
                return false;
            }

            // 查找订单
            UserOrder order = orderService.getOrderByOrderNo(outTradeNo);
            if (order == null) {
                log.error("未找到对应订单，订单号: {}", outTradeNo);
                return false;
            }

            // 更新订单状态
            Integer newStatus = parseYiPayTradeStatus(tradeStatus);
            if (newStatus != null) {
                boolean updated = orderService.updatePaymentStatus(outTradeNo, newStatus, tradeNo);
                if (updated) {
                    log.info("易支付订单状态更新成功，订单号: {}, 新状态: {}", outTradeNo, newStatus);
                    return true;
                } else {
                    log.error("易支付订单状态更新失败，订单号: {}", outTradeNo);
                }
            }

        } catch (Exception e) {
            log.error("处理易支付回调异常", e);
        }

        return false;
    }

    /**
     * 构建支付宝支付请求
     */
    private AlipayPayRequest buildAlipayPayRequest(UserOrder order, Package packageInfo, PaymentRequestVO paymentRequest) {
        AlipayPayRequest request = new AlipayPayRequest();
        
        // 设置客户端ID
        request.setClientId(alipayConfig.getClientId());
        request.setPath("/ams/api/v1/payments/pay");

        // 设置环境信息
        Env env = new Env();
        env.setTerminalType(TerminalType.WEB);
        request.setEnv(env);

        // 设置支付金额
        Amount paymentAmount = new Amount();
        paymentAmount.setCurrency(order.getCurrency());
        paymentAmount.setValue(order.getAmount().toString());
        request.setPaymentAmount(paymentAmount);

        // 设置订单信息
        Order orderInfo = new Order();
        orderInfo.setReferenceOrderId(order.getOrderNo());
        orderInfo.setOrderDescription(packageInfo.getPackageName() + " - " + packageInfo.getDescription());
        
        Amount orderAmount = new Amount();
        orderAmount.setCurrency(order.getCurrency());
        orderAmount.setValue(order.getAmount().toString());
        orderInfo.setOrderAmount(orderAmount);
        request.setOrder(orderInfo);

        // 设置支付方式
        PaymentMethod paymentMethod = new PaymentMethod();
        if ("USD".equals(order.getCurrency())) {
            paymentMethod.setPaymentMethodType("ALIPAY_HK"); // 海外支付宝
        } else {
            paymentMethod.setPaymentMethodType("ALIPAY_CN"); // 国内支付宝
        }
        request.setPaymentMethod(paymentMethod);

        // 设置支付请求ID
        request.setPaymentRequestId(order.getPaymentRequestId());

        // 设置产品代码
        request.setProductCode(ProductCodeType.CASHIER_PAYMENT);

        // 设置跳转URL
        String returnUrl = StringUtils.hasText(paymentRequest.getReturnUrl()) ? 
                          paymentRequest.getReturnUrl() : alipayConfig.getReturnUrl();
        if (StringUtils.hasText(returnUrl)) {
            request.setPaymentRedirectUrl(returnUrl);
        }

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

    /**
     * 从回调通知中提取字段值
     */
    private String extractFromNotify(JsonNode notifyJson, String fieldName) {
        JsonNode fieldNode = notifyJson.get(fieldName);
        return fieldNode != null ? fieldNode.asText() : null;
    }

    /**
     * 解析支付状态
     */
    private Integer parsePaymentStatus(String paymentStatus) {
        if (!StringUtils.hasText(paymentStatus)) {
            return null;
        }
        
        switch (paymentStatus.toUpperCase()) {
            case "SUCCESS":
            case "PAID":
                return 1; // 已支付
            case "CANCELLED":
            case "CANCELED":
                return 2; // 已取消
            case "REFUNDED":
                return 3; // 已退款
            default:
                return 0; // 未支付
        }
    }

    /**
     * 确定易支付类型
     */
    private String determineYiPayType(String currency) {
        // 根据货币类型确定支付方式
        if ("CNY".equalsIgnoreCase(currency)) {
            return StringUtils.hasText(yiPayConfig.getDefaultType()) ?
                   yiPayConfig.getDefaultType() : "alipay";
        } else {
            // USD等外币暂时也使用支付宝
            return "alipay";
        }
    }

    /**
     * 构建易支付URL
     */
    private String buildYiPayUrl(UserOrder order, String paymentType) {
        try {
            // 构建支付参数
            Map<String, String> params = new HashMap<>();
            params.put("pid", yiPayConfig.getPid());
            params.put("type", paymentType);
            params.put("out_trade_no", order.getOrderNo());
            params.put("notify_url", yiPayConfig.getNotifyUrl());
            params.put("return_url", yiPayConfig.getReturnUrl());
            params.put("name", "套餐购买");
            params.put("money", order.getAmount().toString());

            // 生成签名
            String sign = generateYiPaySign(params);
            params.put("sign", sign);
            params.put("sign_type", "MD5");

            // 构建URL参数
            String queryString = buildYiPayUrlParams(params);

            return yiPayConfig.getSubmitUrl() + "?" + queryString;

        } catch (Exception e) {
            log.error("构建易支付URL异常", e);
            throw new RuntimeException("构建支付URL失败", e);
        }
    }

    /**
     * 生成易支付签名
     */
    private String generateYiPaySign(Map<String, String> params) {
        try {
            // 过滤空值参数并排序
            String signString = params.entrySet().stream()
                .filter(entry -> StringUtils.hasText(entry.getValue()))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));

            // 添加密钥
            signString += yiPayConfig.getKey();

            log.debug("易支付签名原串: {}", signString);

            // MD5加密
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(signString.getBytes("UTF-8"));

            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }

            String sign = sb.toString();
            log.debug("易支付签名结果: {}", sign);

            return sign;
        } catch (Exception e) {
            log.error("生成易支付签名异常", e);
            throw new RuntimeException("签名生成失败", e);
        }
    }

    /**
     * 构建易支付URL参数
     */
    private String buildYiPayUrlParams(Map<String, String> params) {
        return params.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + urlEncode(entry.getValue()))
            .collect(Collectors.joining("&"));
    }

    /**
     * URL编码
     */
    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * 验证易支付签名
     */
    private boolean verifyYiPaySign(Map<String, String> params, String sign) {
        try {
            // 过滤空值参数并排序
            String signString = params.entrySet().stream()
                .filter(entry -> StringUtils.hasText(entry.getValue()))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));

            // 添加密钥
            signString += yiPayConfig.getKey();

            log.debug("易支付签名原串: {}", signString);

            // MD5加密
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(signString.getBytes("UTF-8"));

            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }

            String generatedSign = sb.toString();
            log.debug("易支付签名结果: {}", generatedSign);

            boolean isValid = generatedSign.equals(sign);
            if (!isValid) {
                log.warn("易支付签名验证失败，期望: {}, 实际: {}", generatedSign, sign);
            }

            return isValid;
        } catch (Exception e) {
            log.error("验证易支付签名异常", e);
            return false;
        }
    }

    /**
     * 解析易支付交易状态
     */
    private Integer parseYiPayTradeStatus(String tradeStatus) {
        if (tradeStatus == null || tradeStatus.trim().isEmpty()) {
            return null;
        }

        switch (tradeStatus.toUpperCase()) {
            case "TRADE_SUCCESS":
            case "SUCCESS":
                return 1; // 已支付
            case "TRADE_CLOSED":
            case "CLOSED":
                return 2; // 已取消
            case "TRADE_REFUND":
            case "REFUND":
                return 3; // 已退款
            default:
                return 0; // 未支付
        }
    }
}
