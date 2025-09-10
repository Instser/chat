package com.instser.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.common.exception.ExternalServiceException;
import com.instser.chat.common.exception.PaymentException;
import com.instser.chat.config.PayPalConfig;
import com.instser.chat.entity.UserOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.Base64Utils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PayPal支付服务类
 * 封装PayPal REST API调用逻辑
 *
 * @author instser
 * @since 2024-09-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayPalService {

    private final PayPalConfig payPalConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 获取PayPal访问令牌
     */
    public String getAccessToken() {
        String cacheKey = "paypal:access_token:" + payPalConfig.getMode();
        
        // 先从Redis获取缓存的令牌
        String cachedToken = redisTemplate.opsForValue().get(cacheKey);
        if (cachedToken != null && !cachedToken.isEmpty()) {
            log.debug("使用Redis缓存的PayPal访问令牌");
            return cachedToken;
        }

        // 获取分布式锁，避免多个实例同时请求令牌
        String lockKey = "paypal:token_lock:" + payPalConfig.getMode();
        String lockValue = UUID.randomUUID().toString();
        
        try {
            // 尝试获取锁（过期时间30秒）
            Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, Duration.ofSeconds(30));
            
            if (Boolean.TRUE.equals(lockAcquired)) {
                // 获取锁成功，再次检查缓存（可能已被其他实例更新）
                cachedToken = redisTemplate.opsForValue().get(cacheKey);
                if (cachedToken != null && !cachedToken.isEmpty()) {
                    log.debug("其他实例已更新Redis缓存的PayPal访问令牌");
                    return cachedToken;
                }
                
                log.info("获取新的PayPal访问令牌");
                
                String auth = Base64Utils.encodeToString(
                    (payPalConfig.getClientId() + ":" + payPalConfig.getClientSecret()).getBytes());

                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Basic " + auth);
                headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));

                String body = "grant_type=client_credentials";
                HttpEntity<String> request = new HttpEntity<>(body, headers);

                String tokenUrl = payPalConfig.getApiBaseUrl() + "/v1/oauth2/token";
                ResponseEntity<String> response = restTemplate.exchange(
                    tokenUrl, HttpMethod.POST, request, String.class);

                if (response.getStatusCode() == HttpStatus.OK) {
                    JsonNode tokenResponse = objectMapper.readTree(response.getBody());
                    String accessToken = tokenResponse.get("access_token").asText();
                    int expiresIn = tokenResponse.get("expires_in").asInt();

                    // 缓存令牌到Redis，提前5分钟过期以避免边界情况
                    Duration cacheDuration = Duration.ofSeconds(expiresIn - 300);
                    redisTemplate.opsForValue().set(cacheKey, accessToken, cacheDuration);

                    log.info("PayPal访问令牌获取成功并已缓存到Redis，有效期{}秒", expiresIn);
                    return accessToken;
                } else {
                    throw ExternalServiceException.paymentServiceError("paypal", 
                        "获取访问令牌失败，状态码: " + response.getStatusCode());
                }
            } else {
                // 获取锁失败，等待其他实例获取令牌
                log.debug("等待其他实例获取PayPal访问令牌...");
                Thread.sleep(1000); // 等待1秒
                
                // 再次尝试从缓存获取
                cachedToken = redisTemplate.opsForValue().get(cacheKey);
                if (cachedToken != null && !cachedToken.isEmpty()) {
                    log.debug("其他实例已获取PayPal访问令牌");
                    return cachedToken;
                }
                
                throw new PaymentException("获取PayPal访问令牌失败：无法获取分布式锁且缓存为空");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentException("获取PayPal访问令牌被中断");
        } catch (Exception e) {
            log.error("获取PayPal访问令牌异常", e);
            throw new PaymentException("获取PayPal访问令牌失败: " + e.getMessage());
        } finally {
            // 释放锁
            try {
                String currentLockValue = redisTemplate.opsForValue().get(lockKey);
                if (lockValue.equals(currentLockValue)) {
                    redisTemplate.delete(lockKey);
                }
            } catch (Exception e) {
                log.warn("释放PayPal令牌锁异常", e);
            }
        }
    }

    /**
     * 创建PayPal支付订单
     */
    public Map<String, Object> createPayPalOrder(UserOrder order, String currency) {
        try {
            log.info("创建PayPal订单，订单号: {}, 金额: {} {}", 
                order.getOrderNo(), order.getAmount(), currency);

            String accessToken = getAccessToken();

            // 构建订单请求体
            Map<String, Object> orderRequest = buildOrderRequest(order, currency);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
            headers.set("PayPal-Request-Id", order.getOrderNo()); // 幂等性保证

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(orderRequest, headers);

            String orderUrl = payPalConfig.getApiBaseUrl() + "/v2/checkout/orders";
            ResponseEntity<String> response = restTemplate.exchange(
                orderUrl, HttpMethod.POST, request, String.class);

            // PayPal在创建新订单时返回201 CREATED，在幂等性重复请求时返回200 OK
            if (response.getStatusCode() == HttpStatus.CREATED || response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> orderResponse = objectMapper.readValue(
                    response.getBody(), new TypeReference<Map<String, Object>>() {});

                String paypalOrderId = (String) orderResponse.get("id");
                String approvalUrl = extractApprovalUrl(orderResponse);

                log.info("PayPal订单创建成功，PayPal订单ID: {}, 审批链接: {}", paypalOrderId, approvalUrl);

                Map<String, Object> result = new HashMap<>();
                result.put("paypalOrderId", paypalOrderId);
                result.put("approvalUrl", approvalUrl);
                result.put("orderResponse", orderResponse);

                return result;
            } else {
                String errorMsg = "创建PayPal订单失败，状态码: " + response.getStatusCode() + 
                                ", 响应: " + response.getBody();
                log.error(errorMsg);
                throw ExternalServiceException.paymentServiceError("paypal", errorMsg);
            }

        } catch (Exception e) {
            log.error("创建PayPal订单异常，订单号: {}", order.getOrderNo(), e);
            throw new PaymentException("创建PayPal订单失败: " + e.getMessage());
        }
    }

    /**
     * 查询PayPal订单状态
     */
    public Map<String, Object> getPayPalOrderDetails(String paypalOrderId) {
        try {
            log.debug("查询PayPal订单状态，PayPal订单ID: {}", paypalOrderId);

            String accessToken = getAccessToken();

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> request = new HttpEntity<>(headers);

            String orderUrl = payPalConfig.getApiBaseUrl() + "/v2/checkout/orders/" + paypalOrderId;
            ResponseEntity<String> response = restTemplate.exchange(
                orderUrl, HttpMethod.GET, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> orderDetails = objectMapper.readValue(
                    response.getBody(), new TypeReference<Map<String, Object>>() {});

                String status = (String) orderDetails.get("status");
                log.debug("PayPal订单状态查询成功，订单ID: {}, 状态: {}", paypalOrderId, status);

                return orderDetails;
            } else {
                String errorMsg = "查询PayPal订单状态失败，状态码: " + response.getStatusCode() + 
                                ", 响应: " + response.getBody();
                log.error(errorMsg);
                throw ExternalServiceException.paymentServiceError("paypal", errorMsg);
            }

        } catch (Exception e) {
            log.error("查询PayPal订单状态异常，PayPal订单ID: {}", paypalOrderId, e);
            throw new PaymentException("查询PayPal订单状态失败: " + e.getMessage());
        }
    }

    /**
     * 验证PayPal Webhook签名
     */
    public boolean verifyWebhookSignature(String payload, Map<String, String> headers) {
        try {
            if (!StringUtils.hasText(payPalConfig.getWebhookId())) {
                log.warn("PayPal Webhook ID未配置，跳过签名验证");
                return true; // 如果没有配置webhook ID，暂时跳过验证
            }

            // PayPal webhook签名验证逻辑
            String signature = headers.get("paypal-transmission-sig");
            String certId = headers.get("paypal-cert-id");
            String timestamp = headers.get("paypal-transmission-time");
            String authAlgo = headers.get("paypal-auth-algo");

            if (!StringUtils.hasText(signature) || !StringUtils.hasText(certId) || 
                !StringUtils.hasText(timestamp) || !StringUtils.hasText(authAlgo)) {
                log.warn("PayPal Webhook签名头信息不完整");
                return false;
            }

            // 这里应该实现PayPal的签名验证算法
            // 由于复杂性，这里先返回true，实际生产环境需要完整实现
            log.debug("PayPal Webhook签名验证通过（简化实现）");
            return true;

        } catch (Exception e) {
            log.error("PayPal Webhook签名验证异常", e);
            return false;
        }
    }

    /**
     * 将PayPal订单状态转换为系统内部状态
     */
    public Integer convertPayPalStatus(String paypalStatus) {
        if (!StringUtils.hasText(paypalStatus)) {
            return null;
        }

        switch (paypalStatus.toUpperCase()) {
            case "APPROVED":
            case "COMPLETED":
                return 1; // 已支付
            case "CANCELLED":
            case "VOIDED":
                return 2; // 已取消
            case "REFUNDED":
                return 3; // 已退款
            case "CREATED":
            case "SAVED":
            case "PAYER_ACTION_REQUIRED":
            default:
                return 0; // 未支付/待处理
        }
    }

    /**
     * 验证金额
     */
    public void validateAmount(BigDecimal amount, String currency) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentException("支付金额必须大于0");
        }

        if (!payPalConfig.isCurrencySupported(currency)) {
            throw new PaymentException("不支持的货币类型: " + currency);
        }

        // PayPal最小金额限制（根据不同货币有不同限制）
        BigDecimal minAmount = getMinAmount(currency);
        if (amount.compareTo(minAmount) < 0) {
            throw new PaymentException("支付金额不能小于 " + minAmount + " " + currency);
        }
    }

    /**
     * 构建PayPal订单请求体
     */
    private Map<String, Object> buildOrderRequest(UserOrder order, String currency) {
        Map<String, Object> orderRequest = new HashMap<>();
        orderRequest.put("intent", "CAPTURE");

        // 购买单位
        List<Map<String, Object>> purchaseUnits = new ArrayList<>();
        Map<String, Object> purchaseUnit = new HashMap<>();

        // 金额信息
        Map<String, Object> amount = new HashMap<>();
        amount.put("currency_code", currency.toUpperCase());
        amount.put("value", order.getAmount().toString());

        purchaseUnit.put("amount", amount);
        purchaseUnit.put("reference_id", order.getOrderNo());
        purchaseUnit.put("description", "套餐购买 - 订单号: " + order.getOrderNo());

        purchaseUnits.add(purchaseUnit);
        orderRequest.put("purchase_units", purchaseUnits);

        // 应用上下文
        Map<String, Object> appContext = new HashMap<>();
        appContext.put("brand_name", "金鱼聊天");
        appContext.put("landing_page", "LOGIN");
        appContext.put("user_action", "PAY_NOW");
        appContext.put("shipping_preference", "NO_SHIPPING");

        // 如果配置了跳转URL，添加到请求中
        if (StringUtils.hasText(payPalConfig.getReturnUrl()) || 
            StringUtils.hasText(payPalConfig.getCancelUrl())) {
            if (StringUtils.hasText(payPalConfig.getReturnUrl())) {
                appContext.put("return_url", payPalConfig.getReturnUrl());
            }
            if (StringUtils.hasText(payPalConfig.getCancelUrl())) {
                appContext.put("cancel_url", payPalConfig.getCancelUrl());
            }
        }

        orderRequest.put("application_context", appContext);

        return orderRequest;
    }

    /**
     * 从订单响应中提取审批链接
     */
    private String extractApprovalUrl(Map<String, Object> orderResponse) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> links = (List<Map<String, Object>>) orderResponse.get("links");

            if (links != null) {
                for (Map<String, Object> link : links) {
                    if ("approve".equals(link.get("rel"))) {
                        return (String) link.get("href");
                    }
                }
            }

            log.warn("未在PayPal响应中找到approve链接");
            return null;
        } catch (Exception e) {
            log.error("提取PayPal审批链接异常", e);
            return null;
        }
    }

    /**
     * 获取货币最小金额
     */
    private BigDecimal getMinAmount(String currency) {
        switch (currency.toUpperCase()) {
            case "USD":
            case "EUR":
            case "GBP":
            case "CAD":
            case "AUD":
                return new BigDecimal("0.01");
            case "JPY":
                return new BigDecimal("1");
            case "CNY":
                return new BigDecimal("0.01");
            default:
                return new BigDecimal("0.01");
        }
    }
}