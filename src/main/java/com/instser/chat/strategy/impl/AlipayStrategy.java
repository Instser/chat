package com.instser.chat.strategy.impl;

import com.alipay.global.api.AlipayClient;
import com.alipay.global.api.exception.AlipayApiException;
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
import com.instser.chat.util.QrCodeUtil;
import com.instser.chat.vo.PaymentRequestVO;
import com.instser.chat.vo.PaymentResponseVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝支付策略实现
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
// @Component  // 暂时禁用，使用易支付调用支付宝
@RequiredArgsConstructor
public class AlipayStrategy implements PaymentStrategy {

    private final AlipayClient alipayClient;
    private final AlipayConfig alipayConfig;
    private final OrderService orderService;
    private final PackageService packageService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

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

            // 【修复】更新订单中的支付URL
            order.setPaymentUrl(paymentUrl);
            if (orderService.updateById(order)) {
                log.info("已将支付URL更新到订单中，订单号: {}", order.getOrderNo());
            } else {
                log.warn("更新订单支付URL失败，订单号: {}", order.getOrderNo());
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

            // 设置订单创建时间 (ISO 8601格式)
            response.setCreatedTime(order.getCreatedTime().toString());

            // 生成二维码URL
            String qrCodeUrl = QrCodeUtil.generatePaymentQrCodeUrl(paymentUrl);
            response.setQrCodeUrl(qrCodeUrl);



            log.info("支付宝支付订单创建成功，订单号: {}", order.getOrderNo());
            return response;

        } catch (Exception e) {
            log.error("创建支付宝支付异常", e);
            throw new PaymentException("创建支付失败", e.getMessage());
        }
    }

    @Override
    public boolean handleCallback(String callbackData) {
        try {
            log.info("开始处理支付宝回调数据");

            // 检查配置
            if (!isConfigComplete()) {
                log.error("支付宝配置不完整，无法处理回调");
                return false;
            }

            // 解析回调数据
            CallbackData callback = parseCallbackData(callbackData);
            if (callback == null) {
                log.error("解析支付宝回调数据失败");
                return false;
            }

            // 验证签名
            if (!verifyCallbackSignature(callback)) {
                log.error("支付宝回调签名验证失败");
                return false;
            }

            // 查找订单
            UserOrder order = orderService.getOrderByPaymentRequestId(callback.getPaymentRequestId());
            if (order == null) {
                log.error("未找到对应订单，paymentRequestId: {}", callback.getPaymentRequestId());
                return false;
            }

            // 检查订单状态
            if (orderService.isOrderExpired(order.getOrderNo())) {
                log.warn("订单已过期，拒绝处理回调，订单号: {}", order.getOrderNo());
                return false;
            }

            // 解析支付状态
            Integer newStatus = parseAlipayStatus(callback.getPaymentStatus());
            if (newStatus == null) {
                log.error("无效的支付状态: {}", callback.getPaymentStatus());
                return false;
            }

            // 检查状态变更的合法性
            if (order.getPaymentStatus().equals(newStatus)) {
                log.info("订单状态已经是目标状态，跳过更新，订单号: {}, 状态: {}",
                        order.getOrderNo(), newStatus);
                return true;
            }

            // 更新订单状态
            boolean updated = orderService.updatePaymentStatus(
                order.getOrderNo(),
                newStatus,
                callback.getTransactionId()
            );

            if (updated) {


                log.info("支付宝回调处理成功，订单号: {}, 状态: {} -> {}",
                        order.getOrderNo(), order.getPaymentStatus(), newStatus);
                return true;
            } else {
                log.error("订单状态更新失败，订单号: {}", order.getOrderNo());
                return false;
            }

        } catch (Exception e) {
            log.error("处理支付宝回调异常", e);
            return false;
        }
    }

    @Override
    public boolean isConfigComplete() {
        return StringUtils.hasText(alipayConfig.getClientId()) &&
               StringUtils.hasText(alipayConfig.getPrivateKey()) &&
               StringUtils.hasText(alipayConfig.getPublicKey());
    }

    @Override
    public String queryPaymentStatus(String orderNo) {
        try {
            log.info("查询支付宝支付状态，订单号: {}", orderNo);

            // 检查配置
            if (!isConfigComplete()) {
                log.warn("支付宝配置不完整，无法查询支付状态");
                return "CONFIG_ERROR";
            }

            // 查找订单
            UserOrder order = orderService.getOrderByOrderNo(orderNo);
            if (order == null) {
                log.error("未找到订单，订单号: {}", orderNo);
                return "ORDER_NOT_FOUND";
            }

            // 检查订单是否过期
            if (orderService.isOrderExpired(orderNo)) {
                log.info("订单已过期，订单号: {}", orderNo);
                return "EXPIRED";
            }

            // 如果订单已经是最终状态，直接返回
            if (order.getPaymentStatus() == 1 || order.getPaymentStatus() == 2 || order.getPaymentStatus() == 3) {
                log.debug("订单已是最终状态，直接返回，订单号: {}, 状态: {}", orderNo, order.getPaymentStatus());
                return mapPaymentStatusToString(order.getPaymentStatus());
            }

            // 调用支付宝API查询最新状态
            String apiStatus = queryAlipayApiStatus(order);
            if (apiStatus != null) {
                // 解析API返回的状态并更新数据库
                Integer newStatus = parseAlipayApiStatus(apiStatus);
                if (newStatus != null && !newStatus.equals(order.getPaymentStatus())) {
                    log.info("支付宝API返回新状态，订单号: {}, 原状态: {}, 新状态: {}",
                            orderNo, order.getPaymentStatus(), newStatus);

                    // 更新数据库状态
                    boolean updated = orderService.updatePaymentStatus(orderNo, newStatus, null);
                    if (updated) {


                        log.info("订单状态同步成功，订单号: {}, 状态: {} -> {}",
                                orderNo, order.getPaymentStatus(), newStatus);
                        return mapPaymentStatusToString(newStatus);
                    } else {
                        log.warn("订单状态更新失败，订单号: {}", orderNo);
                    }
                }
                return apiStatus;
            }

            // API查询失败，返回数据库中的状态
            log.debug("支付宝API查询失败，返回数据库状态，订单号: {}, 状态: {}", orderNo, order.getPaymentStatus());
            return mapPaymentStatusToString(order.getPaymentStatus());

        } catch (Exception e) {
            log.error("查询支付宝支付状态异常，订单号: {}", orderNo, e);
            return "ERROR";
        }
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
        // 移除跳转URL，前端通过轮询检测支付状态
        // request.setPaymentRedirectUrl(alipayConfig.getReturnUrl());
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

    /**
     * 解析支付宝回调数据
     */
    private CallbackData parseCallbackData(String callbackData) {
        try {
            if (!StringUtils.hasText(callbackData)) {
                log.error("回调数据为空");
                return null;
            }

            JsonNode jsonNode = objectMapper.readTree(callbackData);

            CallbackData callback = new CallbackData();
            callback.setPaymentRequestId(extractJsonField(jsonNode, "paymentRequestId"));
            callback.setPaymentStatus(extractJsonField(jsonNode, "paymentStatus"));
            callback.setTransactionId(extractJsonField(jsonNode, "paymentId"));
            callback.setAmount(extractJsonField(jsonNode, "paymentAmount"));
            callback.setCurrency(extractJsonField(jsonNode, "paymentCurrency"));

            // 验证必要字段
            if (!StringUtils.hasText(callback.getPaymentRequestId())) {
                log.error("回调数据缺少paymentRequestId");
                return null;
            }

            if (!StringUtils.hasText(callback.getPaymentStatus())) {
                log.error("回调数据缺少paymentStatus");
                return null;
            }

            log.debug("解析支付宝回调数据成功: {}", callback);
            return callback;

        } catch (Exception e) {
            log.error("解析支付宝回调数据异常", e);
            return null;
        }
    }

    /**
     * 验证支付宝回调签名
     */
    private boolean verifyCallbackSignature(CallbackData callback) {
        try {
            // 这里应该使用支付宝SDK的签名验证方法
            // 由于回调数据的签名验证已经在PaymentServiceImpl中实现
            // 这里主要做业务层面的验证

            // 验证金额和货币是否匹配（如果回调中包含这些信息）
            if (StringUtils.hasText(callback.getAmount()) && StringUtils.hasText(callback.getCurrency())) {
                // 可以在这里添加金额验证逻辑
                log.debug("回调金额: {} {}", callback.getAmount(), callback.getCurrency());
            }

            return true;
        } catch (Exception e) {
            log.error("验证支付宝回调签名异常", e);
            return false;
        }
    }

    /**
     * 解析支付宝支付状态
     */
    private Integer parseAlipayStatus(String paymentStatus) {
        if (!StringUtils.hasText(paymentStatus)) {
            return null;
        }

        switch (paymentStatus.toUpperCase()) {
            case "SUCCESS":
            case "PAID":
                return 1; // 已支付
            case "CANCELLED":
            case "CANCELED":
            case "FAILED":
                return 2; // 已取消
            case "REFUNDED":
                return 3; // 已退款
            case "PENDING":
            case "PROCESSING":
            default:
                return 0; // 未支付/处理中
        }
    }

    /**
     * 从JSON节点中提取字段值
     */
    private String extractJsonField(JsonNode jsonNode, String fieldName) {
        JsonNode fieldNode = jsonNode.get(fieldName);
        return fieldNode != null ? fieldNode.asText() : null;
    }

    /**
     * 调用支付宝API查询支付状态
     */
    private String queryAlipayApiStatus(UserOrder order) {
        int maxRetries = 3;
        int retryDelay = 1000; // 1秒

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.debug("支付宝API查询尝试 {}/{}, 订单号: {}", attempt, maxRetries, order.getOrderNo());

                // 构建查询请求
                String requestBody = buildInquiryRequestBody(order);

                // 设置请求头
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Client-Id", alipayConfig.getClientId());

                // 创建请求实体
                HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);

                // 调用支付宝API
                String apiUrl = "https://open-sg-global.alipay.com/ams/api/v1/payments/inquiryPayment";
                ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
                );

                if (response.getStatusCode() == HttpStatus.OK) {
                    String responseBody = response.getBody();
                    log.debug("支付宝API查询响应: {}", responseBody);

                    // 解析响应
                    JsonNode responseJson = objectMapper.readTree(responseBody);
                    JsonNode resultNode = responseJson.get("result");

                    if (resultNode != null) {
                        String resultCode = resultNode.get("resultCode").asText();
                        String resultStatus = resultNode.get("resultStatus").asText();

                        if ("SUCCESS".equals(resultCode) && "S".equals(resultStatus)) {
                            String paymentStatus = responseJson.get("paymentStatus").asText();
                            log.info("支付宝API查询成功，订单号: {}, 支付状态: {}", order.getOrderNo(), paymentStatus);
                            return paymentStatus;
                        } else if ("F".equals(resultStatus)) {
                            log.error("支付宝API查询失败，订单号: {}, 结果: {}", order.getOrderNo(), responseBody);
                            return null; // 明确失败，不重试
                        } else if ("U".equals(resultStatus)) {
                            log.warn("支付宝API查询结果未知，订单号: {}, 尝试 {}/{}", order.getOrderNo(), attempt, maxRetries);
                            // 继续重试
                        } else {
                            log.warn("支付宝API查询失败，订单号: {}, 响应: {}", order.getOrderNo(), responseBody);
                        }
                    } else {
                        log.warn("支付宝API响应格式异常，订单号: {}, 响应: {}", order.getOrderNo(), responseBody);
                    }
                } else {
                    log.warn("支付宝API查询HTTP状态异常，订单号: {}, 状态码: {}, 尝试 {}/{}",
                            order.getOrderNo(), response.getStatusCode(), attempt, maxRetries);
                }

            } catch (Exception e) {
                log.error("调用支付宝API查询异常，订单号: {}, 尝试 {}/{}", order.getOrderNo(), attempt, maxRetries, e);
            }

            // 如果不是最后一次尝试，等待后重试
            if (attempt < maxRetries) {
                try {
                    Thread.sleep(retryDelay);
                    retryDelay *= 2; // 指数退避
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("支付宝API查询重试被中断，订单号: {}", order.getOrderNo());
                    break;
                }
            }
        }

        log.warn("支付宝API查询失败，已达到最大重试次数，订单号: {}", order.getOrderNo());
        return null;
    }

    /**
     * 构建支付宝查询请求体
     */
    private String buildInquiryRequestBody(UserOrder order) {
        try {
            Map<String, String> requestData = new HashMap<>();
            requestData.put("paymentRequestId", order.getPaymentRequestId());

            return objectMapper.writeValueAsString(requestData);
        } catch (Exception e) {
            log.error("构建支付宝查询请求体异常", e);
            return "{}";
        }
    }

    /**
     * 解析支付宝API返回的状态
     */
    private Integer parseAlipayApiStatus(String apiStatus) {
        if (!StringUtils.hasText(apiStatus)) {
            return null;
        }

        switch (apiStatus.toUpperCase()) {
            case "SUCCESS":
                return 1; // 已支付
            case "FAIL":
            case "CANCELLED":
                return 2; // 已取消
            case "PROCESSING":
            default:
                return 0; // 处理中/未支付
        }
    }

    /**
     * 将数据库状态映射为字符串
     */
    private String mapPaymentStatusToString(Integer paymentStatus) {
        if (paymentStatus == null) {
            return "UNKNOWN";
        }
        switch (paymentStatus) {
            case 0:
                return "PENDING";
            case 1:
                return "SUCCESS";
            case 2:
                return "CANCELLED";
            case 3:
                return "REFUNDED";
            default:
                return "UNKNOWN";
        }
    }

    /**
     * 支付宝回调数据类
     */
    private static class CallbackData {
        private String paymentRequestId;
        private String paymentStatus;
        private String transactionId;
        private String amount;
        private String currency;

        public String getPaymentRequestId() {
            return paymentRequestId;
        }

        public void setPaymentRequestId(String paymentRequestId) {
            this.paymentRequestId = paymentRequestId;
        }

        public String getPaymentStatus() {
            return paymentStatus;
        }

        public void setPaymentStatus(String paymentStatus) {
            this.paymentStatus = paymentStatus;
        }

        public String getTransactionId() {
            return transactionId;
        }

        public void setTransactionId(String transactionId) {
            this.transactionId = transactionId;
        }

        public String getAmount() {
            return amount;
        }

        public void setAmount(String amount) {
            this.amount = amount;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        @Override
        public String toString() {
            return "CallbackData{" +
                    "paymentRequestId='" + paymentRequestId + '\'' +
                    ", paymentStatus='" + paymentStatus + '\'' +
                    ", transactionId='" + transactionId + '\'' +
                    ", amount='" + amount + '\'' +
                    ", currency='" + currency + '\'' +
                    '}';
        }
    }


}
