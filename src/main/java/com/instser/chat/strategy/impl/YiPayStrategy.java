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
    private final RestTemplate restTemplate = new RestTemplate();

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

            // 创建订单（易支付会自动转换为CNY）
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

            // 设置订单创建时间 (ISO 8601格式)
            response.setCreatedTime(order.getCreatedTime().toString());

            // 生成二维码URL
            String qrCodeUrl = QrCodeUtil.generatePaymentQrCodeUrl(paymentUrl);
            response.setQrCodeUrl(qrCodeUrl);

            log.info("易支付订单创建成功，订单号: {}, 支付类型: {}", order.getOrderNo(), paymentType);
            return response;

        } catch (Exception e) {
            log.error("创建易支付订单异常", e);
            throw new PaymentException("创建支付失败", e.getMessage());
        }
    }

    @Override
    public boolean handleCallback(String callbackData) {
        try {
            log.info("开始处理易支付回调数据");

            // 检查配置
            if (!isConfigComplete()) {
                log.error("易支付配置不完整，无法处理回调");
                return false;
            }

            // 解析回调数据
            YiPayCallbackData callback = parseYiPayCallbackData(callbackData);
            if (callback == null) {
                log.error("解析易支付回调数据失败");
                return false;
            }

            // 验证签名
            if (!verifyYiPayCallbackSignature(callback)) {
                log.error("易支付回调签名验证失败");
                return false;
            }

            // 查找订单
            UserOrder order = orderService.getOrderByOrderNo(callback.getOutTradeNo());
            if (order == null) {
                log.error("未找到对应订单，订单号: {}", callback.getOutTradeNo());
                return false;
            }

            // 检查订单状态
            if (orderService.isOrderExpired(order.getOrderNo())) {
                log.warn("订单已过期，拒绝处理回调，订单号: {}", order.getOrderNo());
                return false;
            }

            // 解析支付状态
            Integer newStatus = parseYiPayStatus(callback.getTradeStatus());
            if (newStatus == null) {
                log.error("无效的支付状态: {}", callback.getTradeStatus());
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
                callback.getTradeNo()
            );

            if (updated) {
                log.info("易支付回调处理成功，订单号: {}, 状态: {} -> {}",
                        order.getOrderNo(), order.getPaymentStatus(), newStatus);
                return true;
            } else {
                log.error("订单状态更新失败，订单号: {}", order.getOrderNo());
                return false;
            }

        } catch (Exception e) {
            log.error("处理易支付回调异常", e);
            return false;
        }
    }

    @Override
    public boolean isConfigComplete() {
        return StringUtils.hasText(yiPayConfig.getPid()) &&
               StringUtils.hasText(yiPayConfig.getKey()) &&
               StringUtils.hasText(yiPayConfig.getUrl());
    }

    @Override
    public String queryPaymentStatus(String orderNo) {
        try {
            log.info("查询易支付状态，订单号: {}", orderNo);

            // 检查配置
            if (!isConfigComplete()) {
                log.warn("易支付配置不完整，无法查询支付状态");
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
                return mapYiPayStatusToString(order.getPaymentStatus());
            }

            // 调用易支付API查询最新状态
            YiPayQueryResult queryResult = queryYiPayApiStatus(order);
            if (queryResult != null && queryResult.isSuccess()) {
                // 解析API返回的状态并更新数据库
                Integer newStatus = parseYiPayApiStatus(queryResult.getStatus());
                if (newStatus != null && !newStatus.equals(order.getPaymentStatus())) {
                    log.info("易支付API返回新状态，订单号: {}, 原状态: {}, 新状态: {}",
                            orderNo, order.getPaymentStatus(), newStatus);

                    // 更新数据库状态
                    boolean updated = orderService.updatePaymentStatus(orderNo, newStatus, queryResult.getTradeNo());
                    if (updated) {
                        log.info("订单状态同步成功，订单号: {}, 状态: {} -> {}",
                                orderNo, order.getPaymentStatus(), newStatus);
                        return mapYiPayStatusToString(newStatus);
                    } else {
                        log.warn("订单状态更新失败，订单号: {}", orderNo);
                    }
                }
                return mapYiPayStatusToString(queryResult.getStatus());
            }

            // API查询失败，返回数据库中的状态
            log.debug("易支付API查询失败，返回数据库状态，订单号: {}, 状态: {}", orderNo, order.getPaymentStatus());
            return mapYiPayStatusToString(order.getPaymentStatus());

        } catch (Exception e) {
            log.error("查询易支付状态异常，订单号: {}", orderNo, e);
            return "ERROR";
        }
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
            params.put("return_url", yiPayConfig.getNotifyUrl());
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

    /**
     * 解析易支付回调数据
     */
    private YiPayCallbackData parseYiPayCallbackData(String callbackData) {
        try {
            if (!StringUtils.hasText(callbackData)) {
                log.error("易支付回调数据为空");
                return null;
            }

            // 易支付回调通常是URL参数格式，需要解析为Map
            Map<String, String> params = parseUrlParams(callbackData);
            if (params.isEmpty()) {
                log.error("解析易支付回调参数失败");
                return null;
            }

            YiPayCallbackData callback = new YiPayCallbackData();
            callback.setTradeNo(params.get("trade_no"));
            callback.setOutTradeNo(params.get("out_trade_no"));
            callback.setTradeStatus(params.get("trade_status"));
            callback.setMoney(params.get("money"));
            callback.setName(params.get("name"));
            callback.setSign(params.get("sign"));
            callback.setSignType(params.get("sign_type"));

            // 验证必要字段
            if (!StringUtils.hasText(callback.getOutTradeNo())) {
                log.error("易支付回调数据缺少out_trade_no");
                return null;
            }

            if (!StringUtils.hasText(callback.getSign())) {
                log.error("易支付回调数据缺少sign");
                return null;
            }

            log.debug("解析易支付回调数据成功: {}", callback);
            return callback;

        } catch (Exception e) {
            log.error("解析易支付回调数据异常", e);
            return null;
        }
    }

    /**
     * 验证易支付回调签名
     */
    private boolean verifyYiPayCallbackSignature(YiPayCallbackData callback) {
        try {
            // 构建签名参数
            Map<String, String> signParams = new HashMap<>();
            if (StringUtils.hasText(callback.getTradeNo())) {
                signParams.put("trade_no", callback.getTradeNo());
            }
            if (StringUtils.hasText(callback.getOutTradeNo())) {
                signParams.put("out_trade_no", callback.getOutTradeNo());
            }
            if (StringUtils.hasText(callback.getTradeStatus())) {
                signParams.put("trade_status", callback.getTradeStatus());
            }
            if (StringUtils.hasText(callback.getMoney())) {
                signParams.put("money", callback.getMoney());
            }
            if (StringUtils.hasText(callback.getName())) {
                signParams.put("name", callback.getName());
            }

            // 生成签名
            String generatedSign = PaymentSecurityUtil.generateYiPaySign(signParams, yiPayConfig.getKey());
            boolean isValid = generatedSign.equals(callback.getSign());

            if (!isValid) {
                log.warn("易支付回调签名验证失败，期望: {}, 实际: {}", generatedSign, callback.getSign());
            }

            return isValid;

        } catch (Exception e) {
            log.error("验证易支付回调签名异常", e);
            return false;
        }
    }

    /**
     * 解析易支付状态
     */
    private Integer parseYiPayStatus(String tradeStatus) {
        if (!StringUtils.hasText(tradeStatus)) {
            return null;
        }

        switch (tradeStatus.toUpperCase()) {
            case "TRADE_SUCCESS":
            case "SUCCESS":
                return 1; // 已支付
            case "TRADE_CLOSED":
            case "CLOSED":
            case "FAILED":
                return 2; // 已取消
            case "TRADE_REFUND":
            case "REFUND":
                return 3; // 已退款
            case "TRADE_PENDING":
            case "PENDING":
            default:
                return 0; // 未支付/处理中
        }
    }

    /**
     * 解析URL参数
     */
    private Map<String, String> parseUrlParams(String urlParams) {
        Map<String, String> params = new HashMap<>();
        if (!StringUtils.hasText(urlParams)) {
            return params;
        }

        String[] pairs = urlParams.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                params.put(keyValue[0], keyValue[1]);
            }
        }

        return params;
    }



    /**
     * 调用易支付API查询支付状态
     */
    private YiPayQueryResult queryYiPayApiStatus(UserOrder order) {
        int maxRetries = 3;
        int retryDelay = 1000; // 1秒

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.debug("易支付API查询尝试 {}/{}, 订单号: {}", attempt, maxRetries, order.getOrderNo());

                // 构建查询URL
                String queryUrl = buildYiPayQueryUrl(order.getOrderNo());

                log.debug("调用易支付查询API: {}", queryUrl);

                // 调用API
                ResponseEntity<String> response = restTemplate.getForEntity(queryUrl, String.class);

                if (response.getStatusCode() == HttpStatus.OK) {
                    String responseBody = response.getBody();
                    log.debug("易支付API查询响应: {}", responseBody);

                    YiPayQueryResult result = parseYiPayQueryResponse(responseBody);
                    if (result != null && result.isSuccess()) {
                        log.info("易支付API查询成功，订单号: {}", order.getOrderNo());
                        return result;
                    } else if (result != null && !result.isSuccess()) {
                        log.warn("易支付API查询失败，订单号: {}, 尝试 {}/{}", order.getOrderNo(), attempt, maxRetries);
                        // 继续重试
                    } else {
                        log.warn("易支付API响应解析失败，订单号: {}, 尝试 {}/{}", order.getOrderNo(), attempt, maxRetries);
                    }
                } else {
                    log.warn("易支付API查询HTTP状态异常，订单号: {}, 状态码: {}, 尝试 {}/{}",
                            order.getOrderNo(), response.getStatusCode(), attempt, maxRetries);
                }

            } catch (Exception e) {
                log.error("调用易支付API查询异常，订单号: {}, 尝试 {}/{}", order.getOrderNo(), attempt, maxRetries, e);
            }

            // 如果不是最后一次尝试，等待后重试
            if (attempt < maxRetries) {
                try {
                    Thread.sleep(retryDelay);
                    retryDelay *= 2; // 指数退避
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("易支付API查询重试被中断，订单号: {}", order.getOrderNo());
                    break;
                }
            }
        }

        log.warn("易支付API查询失败，已达到最大重试次数，订单号: {}", order.getOrderNo());
        return null;
    }

    /**
     * 构建易支付查询URL
     */
    private String buildYiPayQueryUrl(String orderNo) {
        try {
            StringBuilder url = new StringBuilder();
            url.append(yiPayConfig.getQueryUrl());
            url.append("?act=order");
            url.append("&pid=").append(yiPayConfig.getPid());
            url.append("&key=").append(yiPayConfig.getKey());
            url.append("&out_trade_no=").append(URLEncoder.encode(orderNo, StandardCharsets.UTF_8.toString()));

            return url.toString();
        } catch (Exception e) {
            log.error("构建易支付查询URL异常", e);
            return null;
        }
    }

    /**
     * 解析易支付查询响应
     */
    private YiPayQueryResult parseYiPayQueryResponse(String responseBody) {
        try {
            if (!StringUtils.hasText(responseBody)) {
                return null;
            }

            // 易支付API返回JSON格式
            JsonNode jsonNode = new ObjectMapper().readTree(responseBody);

            int code = jsonNode.get("code").asInt();
            if (code == 1) {
                // 查询成功
                YiPayQueryResult result = new YiPayQueryResult();
                result.setSuccess(true);
                result.setTradeNo(jsonNode.get("trade_no").asText());
                result.setOutTradeNo(jsonNode.get("out_trade_no").asText());
                result.setStatus(jsonNode.get("status").asInt());
                result.setMoney(jsonNode.get("money").asText());

                log.debug("易支付查询结果解析成功: {}", result);
                return result;
            } else {
                log.warn("易支付查询失败，响应码: {}, 消息: {}", code, jsonNode.get("msg").asText());
            }

        } catch (Exception e) {
            log.error("解析易支付查询响应异常", e);
        }

        return null;
    }

    /**
     * 解析易支付API返回的状态
     */
    private Integer parseYiPayApiStatus(Integer apiStatus) {
        if (apiStatus == null) {
            return null;
        }

        // 易支付API返回：1为支付成功，0为未支付
        switch (apiStatus) {
            case 1:
                return 1; // 已支付
            case 0:
            default:
                return 0; // 未支付
        }
    }

    /**
     * 将数据库状态映射为字符串
     */
    private String mapYiPayStatusToString(Integer paymentStatus) {
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
     * 易支付回调数据类
     */
    private static class YiPayCallbackData {
        private String tradeNo;
        private String outTradeNo;
        private String tradeStatus;
        private String money;
        private String name;
        private String sign;
        private String signType;

        public String getTradeNo() {
            return tradeNo;
        }

        public void setTradeNo(String tradeNo) {
            this.tradeNo = tradeNo;
        }

        public String getOutTradeNo() {
            return outTradeNo;
        }

        public void setOutTradeNo(String outTradeNo) {
            this.outTradeNo = outTradeNo;
        }

        public String getTradeStatus() {
            return tradeStatus;
        }

        public void setTradeStatus(String tradeStatus) {
            this.tradeStatus = tradeStatus;
        }

        public String getMoney() {
            return money;
        }

        public void setMoney(String money) {
            this.money = money;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSign() {
            return sign;
        }

        public void setSign(String sign) {
            this.sign = sign;
        }

        public String getSignType() {
            return signType;
        }

        public void setSignType(String signType) {
            this.signType = signType;
        }

        @Override
        public String toString() {
            return "YiPayCallbackData{" +
                    "tradeNo='" + tradeNo + '\'' +
                    ", outTradeNo='" + outTradeNo + '\'' +
                    ", tradeStatus='" + tradeStatus + '\'' +
                    ", money='" + money + '\'' +
                    ", name='" + name + '\'' +
                    ", sign='" + sign + '\'' +
                    ", signType='" + signType + '\'' +
                    '}';
        }
    }

    /**
     * 易支付查询结果类
     */
    private static class YiPayQueryResult {
        private boolean success;
        private String tradeNo;
        private String outTradeNo;
        private Integer status;
        private String money;

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getTradeNo() {
            return tradeNo;
        }

        public void setTradeNo(String tradeNo) {
            this.tradeNo = tradeNo;
        }

        public String getOutTradeNo() {
            return outTradeNo;
        }

        public void setOutTradeNo(String outTradeNo) {
            this.outTradeNo = outTradeNo;
        }

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }

        public String getMoney() {
            return money;
        }

        public void setMoney(String money) {
            this.money = money;
        }

        @Override
        public String toString() {
            return "YiPayQueryResult{" +
                    "success=" + success +
                    ", tradeNo='" + tradeNo + '\'' +
                    ", outTradeNo='" + outTradeNo + '\'' +
                    ", status=" + status +
                    ", money='" + money + '\'' +
                    '}';
        }
    }
}
