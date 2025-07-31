package com.instser.chat.service.serviceimpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.config.YiPayConfig;
import com.instser.chat.entity.UserOrder;
import com.instser.chat.service.OrderService;
import com.instser.chat.service.PackageActivationService;
import com.instser.chat.service.PaymentService;
import com.instser.chat.service.PaymentStatusSyncService;
import com.instser.chat.util.PaymentSecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 支付状态同步服务实现
 * 
 * @author instser
 * @since 2025-07-29
 */
@Slf4j
@Service
public class PaymentStatusSyncServiceImpl implements PaymentStatusSyncService {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final PackageActivationService packageActivationService;
    private final YiPayConfig yiPayConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public PaymentStatusSyncServiceImpl(OrderService orderService,
                                       PaymentService paymentService,
                                       PackageActivationService packageActivationService,
                                       YiPayConfig yiPayConfig,
                                       @Qualifier("yipayRestTemplate") RestTemplate restTemplate,
                                       ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.packageActivationService = packageActivationService;
        this.yiPayConfig = yiPayConfig;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean syncOrderStatus(String orderNo) {
        try {
            UserOrder order = orderService.getOrderByOrderNo(orderNo);
            if (order == null) {
                log.warn("订单不存在: {}", orderNo);
                return false;
            }

            if (order.getPaymentStatus() != 0) {
                log.debug("订单{}状态不是待支付，跳过同步", orderNo);
                return true;
            }

            // 查询易支付订单状态
            PaymentQueryResult result = queryYiPayOrderStatus(orderNo);
            
            if (result.isPaid()) {
                // 订单已支付，更新本地状态
                boolean updated = orderService.updatePaymentStatus(orderNo, 1, result.getTradeNo());
                if (updated) {
                    log.info("同步订单状态成功，订单{}已支付", orderNo);

                    // 激活用户套餐
                    try {
                        boolean activationSuccess = packageActivationService.activatePackageAfterPayment(orderNo);
                        if (activationSuccess) {
                            log.info("同步支付状态后套餐激活成功，订单号: {}", orderNo);
                        } else {
                            log.error("同步支付状态后套餐激活失败，订单号: {}", orderNo);
                        }
                    } catch (Exception e) {
                        log.error("同步支付状态后套餐激活异常，订单号: {}", orderNo, e);
                    }

                    return true;
                } else {
                    log.error("更新订单{}支付状态失败", orderNo);
                    return false;
                }
            } else {
                log.debug("订单{}尚未支付，状态: {}", orderNo, result.getStatus());
                return true;
            }

        } catch (Exception e) {
            log.error("同步订单{}状态异常", orderNo, e);
            return false;
        }
    }

    /**
     * 定时同步待支付订单状态
     *
     * 注意：此定时任务已被禁用，功能已迁移到 OptimizedScheduledTaskService.unifiedPaymentSyncTask()
     * 如需启用此独立任务，请设置 app.scheduled.payment-status-sync.enabled=true
     */
    @Override
    @Scheduled(fixedDelay = 300000) // 每5分钟执行一次
    @ConditionalOnProperty(name = "app.scheduled.payment-status-sync.enabled", havingValue = "true", matchIfMissing = false)
    public void syncPendingOrdersStatus() {
        try {
            log.debug("开始定时同步待支付订单状态（独立任务）");
            
            // 获取5-30分钟前的待支付订单
            List<UserOrder> pendingOrders = orderService.getPendingOrdersInTimeRange(5, 30);
            
            if (pendingOrders.isEmpty()) {
                log.debug("没有需要同步状态的订单");
                return;
            }

            log.info("发现{}个需要同步状态的订单", pendingOrders.size());
            
            int successCount = 0;
            for (UserOrder order : pendingOrders) {
                try {
                    if (syncOrderStatus(order.getOrderNo())) {
                        successCount++;
                    }
                    
                    // 避免频繁请求，每个订单间隔1秒
                    Thread.sleep(1000);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("同步订单{}状态异常", order.getOrderNo(), e);
                }
            }
            
            log.info("定时同步完成，成功同步{}/{}个订单", successCount, pendingOrders.size());
            
        } catch (Exception e) {
            log.error("定时同步订单状态异常", e);
        }
    }

    @Override
    public PaymentQueryResult queryYiPayOrderStatus(String orderNo) {
        try {
            log.debug("查询易支付订单状态: {}", orderNo);

            // 构建查询参数
            Map<String, String> params = new TreeMap<>();
            params.put("act", "order");
            params.put("pid", yiPayConfig.getPid());
            params.put("out_trade_no", orderNo);
            params.put("sign_type", "MD5");

            log.debug("易支付查询参数（签名前）: {}", params);
            log.debug("易支付商户密钥: {}", yiPayConfig.getKey());

            // 生成签名 - 使用标准的易支付签名方法
            String sign = PaymentSecurityUtil.generateYiPaySign(params, yiPayConfig.getKey());
            params.put("sign", sign);

            log.debug("生成的签名: {}", sign);
            log.debug("易支付查询参数（签名后）: {}", params);

            // 构建查询URL - 使用专门的查询地址
            String queryBaseUrl = "https://your-pay-gateway.example.com/api.php";
            String queryUrl = buildQueryUrl(queryBaseUrl, params);

            log.debug("易支付查询URL: {}", queryUrl);

            // 发送HTTP请求
            String response = restTemplate.getForObject(queryUrl, String.class);

            log.debug("易支付查询响应: {}", response);

            // 解析响应结果
            return parseQueryResponse(response);

        } catch (Exception e) {
            log.error("查询易支付订单{}状态异常", orderNo, e);
            return new PaymentQueryResult(false, "ERROR", null, "查询失败: " + e.getMessage());
        }
    }



    /**
     * 构建查询URL
     */
    private String buildQueryUrl(String baseUrl, Map<String, String> params) {
        try {
            StringBuilder url = new StringBuilder(baseUrl);
            url.append("?");

            for (Map.Entry<String, String> entry : params.entrySet()) {
                url.append(entry.getKey())
                   .append("=")
                   .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                   .append("&");
            }

            // 移除最后的&
            if (url.charAt(url.length() - 1) == '&') {
                url.deleteCharAt(url.length() - 1);
            }

            return url.toString();

        } catch (Exception e) {
            log.error("构建查询URL失败", e);
            throw new RuntimeException("构建查询URL失败", e);
        }
    }

    /**
     * 解析查询响应结果
     */
    private PaymentQueryResult parseQueryResponse(String response) {
        try {
            if (response == null || response.trim().isEmpty()) {
                return new PaymentQueryResult(false, "ERROR", null, "响应为空");
            }

            JsonNode jsonNode = objectMapper.readTree(response);

            int code = jsonNode.get("code").asInt();
            String msg = jsonNode.get("msg").asText();

            if (code != 1) {
                log.warn("易支付查询失败，code: {}, msg: {}", code, msg);
                return new PaymentQueryResult(false, "ERROR", null, msg);
            }

            // 获取订单状态
            int status = jsonNode.get("status").asInt();
            String tradeNo = jsonNode.has("trade_no") ? jsonNode.get("trade_no").asText() : null;

            // 转换状态
            String paymentStatus;
            if (status == 1) {
                paymentStatus = "TRADE_SUCCESS";
            } else {
                paymentStatus = "WAIT_BUYER_PAY";
            }

            log.debug("易支付订单状态解析结果 - status: {}, paymentStatus: {}, tradeNo: {}",
                     status, paymentStatus, tradeNo);

            return new PaymentQueryResult(true, paymentStatus, tradeNo, msg);

        } catch (Exception e) {
            log.error("解析易支付查询响应失败: {}", response, e);
            return new PaymentQueryResult(false, "ERROR", null, "解析响应失败: " + e.getMessage());
        }
    }
}
