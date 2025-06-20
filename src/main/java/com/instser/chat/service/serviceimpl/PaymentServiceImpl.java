package com.instser.chat.service.serviceimpl;

import com.alipay.global.api.tools.WebhookTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.common.exception.PaymentException;
import com.instser.chat.common.exception.ResourceNotFoundException;
import com.instser.chat.config.AlipayConfig;
import com.instser.chat.config.YiPayConfig;
import com.instser.chat.entity.Package;
import com.instser.chat.entity.UserOrder;
import com.instser.chat.service.OrderService;
import com.instser.chat.service.PackageService;
import com.instser.chat.service.PaymentService;
import com.instser.chat.strategy.PaymentStrategy;
import com.instser.chat.strategy.PaymentStrategyFactory;
import com.instser.chat.util.PaymentSecurityUtil;
import com.instser.chat.vo.PaymentRequestVO;
import com.instser.chat.vo.PaymentResponseVO;
import com.instser.chat.vo.PaymentMethodVO;
import com.instser.chat.vo.PaymentStatusResponseVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    private final PaymentStrategyFactory paymentStrategyFactory;
    private final OrderService orderService;
    private final PackageService packageService;
    private final AlipayConfig alipayConfig;
    private final YiPayConfig yiPayConfig;
    private final PaymentSecurityUtil paymentSecurityUtil;

    // 创建ObjectMapper实例，避免依赖注入问题
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取支持的支付方式
     */
    public List<String> getSupportedPaymentMethods() {
        return paymentStrategyFactory.getSupportedPaymentMethods();
    }

    /**
     * 获取可用的支付方式（配置完整）
     */
    @Override
    public List<PaymentMethodVO> getAvailablePaymentMethods() {
        List<String> availableMethods = paymentStrategyFactory.getAvailablePaymentMethods();
        List<PaymentMethodVO> paymentMethods = new ArrayList<>();

        for (String method : availableMethods) {
            PaymentMethodVO paymentMethod = new PaymentMethodVO();
            paymentMethod.setId(method);
            paymentMethod.setType(method);
            paymentMethod.setAvailable(true);

            // 设置显示名称
            switch (method.toLowerCase()) {
                case "alipay":
                    paymentMethod.setName("支付宝");
                    break;
                case "wechat":
                    paymentMethod.setName("微信支付");
                    break;
                case "yipay":
                    paymentMethod.setName("易支付");
                    break;
                default:
                    paymentMethod.setName(method);
                    break;
            }

            paymentMethods.add(paymentMethod);
        }

        return paymentMethods;
    }

    @Override
    public PaymentResponseVO createPayment(PaymentRequestVO paymentRequest) {
        try {
            // 1. 验证支付请求参数
            validatePaymentRequest(paymentRequest);

            // 2. 支付安全性验证
            validatePaymentSecurity(paymentRequest);

            // 3. 根据paymentMethod字段选择支付方式
            String paymentMethod = paymentRequest.getPaymentMethod();

            // 4. 使用策略模式获取对应的支付策略
            PaymentStrategy strategy = paymentStrategyFactory.getStrategy(paymentMethod);

            // 5. 执行支付创建
            return strategy.createPayment(paymentRequest);

        } catch (PaymentException | ResourceNotFoundException e) {
            // 业务异常直接抛出，由全局异常处理器处理
            throw e;
        } catch (Exception e) {
            log.error("创建支付异常", e);
            throw new PaymentException("创建支付失败", e.getMessage());
        }
    }

    /**
     * 验证支付请求参数
     */
    private void validatePaymentRequest(PaymentRequestVO paymentRequest) {
        if (paymentRequest == null) {
            throw new PaymentException("支付请求不能为空");
        }
        if (paymentRequest.getUserId() == null) {
            throw new PaymentException("用户ID不能为空");
        }
        if (paymentRequest.getPackageId() == null) {
            throw new PaymentException("套餐ID不能为空");
        }
        if (!StringUtils.hasText(paymentRequest.getPaymentMethod())) {
            throw new PaymentException("支付方式不能为空");
        }
        if (!StringUtils.hasText(paymentRequest.getCurrency())) {
            throw new PaymentException("货币类型不能为空");
        }

        // 验证套餐是否存在
        Package packageInfo = packageService.getPackageById(paymentRequest.getPackageId());
        if (packageInfo == null) {
            throw ResourceNotFoundException.packageNotFound(paymentRequest.getPackageId());
        }
    }

    /**
     * 支付安全性验证
     */
    private void validatePaymentSecurity(PaymentRequestVO paymentRequest) {
        // 1. 支付请求频率限制验证
        validatePaymentRateLimit(paymentRequest);

        // 2. 支付金额验证
        validatePaymentAmount(paymentRequest);

        // 3. 用户权限验证
        validateUserPermissions(paymentRequest);
    }

    /**
     * 支付请求频率限制验证
     */
    private void validatePaymentRateLimit(PaymentRequestVO paymentRequest) {
        Long userId = paymentRequest.getUserId();

        // 检查用户支付频率限制（每分钟最多3次支付请求）
        String userRateLimitKey = "payment:rate_limit:user:" + userId;
        if (!paymentSecurityUtil.checkRateLimit(userRateLimitKey, 3, 60)) {
            log.warn("用户支付请求频率过高，用户ID: {}", userId);
            throw new PaymentException("支付请求过于频繁，请稍后再试");
        }

        // 检查IP支付频率限制（每分钟最多10次支付请求）
        String clientIp = getClientIpAddress();
        if (StringUtils.hasText(clientIp)) {
            String ipRateLimitKey = "payment:rate_limit:ip:" + clientIp;
            if (!paymentSecurityUtil.checkRateLimit(ipRateLimitKey, 10, 60)) {
                log.warn("IP支付请求频率过高，IP: {}", clientIp);
                throw new PaymentException("当前网络支付请求过于频繁，请稍后再试");
            }
        }

        // 检查同一套餐的支付频率限制（每小时最多5次）
        String packageRateLimitKey = "payment:rate_limit:package:" + userId + ":" + paymentRequest.getPackageId();
        if (!paymentSecurityUtil.checkRateLimit(packageRateLimitKey, 5, 3600)) {
            log.warn("用户对同一套餐支付请求频率过高，用户ID: {}, 套餐ID: {}", userId, paymentRequest.getPackageId());
            throw new PaymentException("对该套餐的支付请求过于频繁，请稍后再试");
        }
    }

    /**
     * 支付金额验证
     */
    private void validatePaymentAmount(PaymentRequestVO paymentRequest) {
        // 获取套餐信息
        Package packageInfo = packageService.getPackageById(paymentRequest.getPackageId());
        if (packageInfo == null) {
            throw ResourceNotFoundException.packageNotFound(paymentRequest.getPackageId());
        }

        // 获取套餐价格
        BigDecimal packagePrice;
        String currency = paymentRequest.getCurrency();
        if ("CNY".equalsIgnoreCase(currency)) {
            packagePrice = packageInfo.getPriceCny();
        } else if ("USD".equalsIgnoreCase(currency)) {
            packagePrice = packageInfo.getPriceUsd();
        } else {
            throw new PaymentException("不支持的货币类型: " + currency);
        }

        // 验证最小金额（0.01）
        BigDecimal minAmount = new BigDecimal("0.01");
        if (packagePrice.compareTo(minAmount) < 0) {
            throw new PaymentException("支付金额不能小于 " + minAmount + " " + currency);
        }

        // 验证最大金额（根据货币类型设置不同限制）
        BigDecimal maxAmount;
        if ("CNY".equalsIgnoreCase(currency)) {
            maxAmount = new BigDecimal("50000.00"); // 人民币最大5万
        } else {
            maxAmount = new BigDecimal("10000.00"); // 美元最大1万
        }

        if (packagePrice.compareTo(maxAmount) > 0) {
            throw new PaymentException("支付金额不能超过 " + maxAmount + " " + currency);
        }

        // 验证免费套餐价格
        if (packagePrice.compareTo(BigDecimal.ZERO) == 0) {
            // 免费套餐不需要支付，直接激活
            log.info("检测到免费套餐，用户ID: {}, 套餐ID: {}", paymentRequest.getUserId(), paymentRequest.getPackageId());
            throw new PaymentException("免费套餐无需支付，请直接激活");
        }

        log.debug("支付金额验证通过，套餐ID: {}, 金额: {} {}",
                 paymentRequest.getPackageId(), packagePrice, currency);
    }

    /**
     * 用户权限验证
     */
    private void validateUserPermissions(PaymentRequestVO paymentRequest) {
        Long userId = paymentRequest.getUserId();

        // 检查用户是否存在且状态正常
        // 这里假设有UserService来获取用户信息
        // User user = userService.getUserById(userId);
        // 由于当前没有UserService，我们先做基本验证

        // 检查用户是否有未完成的支付订单
        List<UserOrder> pendingOrders = orderService.getPendingOrdersByUserId(userId);
        if (pendingOrders != null && pendingOrders.size() >= 3) {
            log.warn("用户有过多未完成的支付订单，用户ID: {}, 未完成订单数: {}", userId, pendingOrders.size());
            throw new PaymentException("您有过多未完成的支付订单，请先完成或取消现有订单");
        }

        // 检查用户今日支付次数限制（每天最多10次支付）
        String dailyLimitKey = "payment:daily_limit:user:" + userId;
        if (!paymentSecurityUtil.checkDailyLimit(dailyLimitKey, 10)) {
            log.warn("用户今日支付次数超限，用户ID: {}", userId);
            throw new PaymentException("今日支付次数已达上限，请明日再试");
        }

        // 检查用户支付总金额限制（每天最多支付1万元人民币等值）
        BigDecimal dailyAmountLimit = new BigDecimal("10000.00");
        String dailyAmountKey = "payment:daily_amount:user:" + userId;

        // 获取套餐价格
        Package packageInfo = packageService.getPackageById(paymentRequest.getPackageId());
        BigDecimal packagePrice;
        String currency = paymentRequest.getCurrency();
        if ("CNY".equalsIgnoreCase(currency)) {
            packagePrice = packageInfo.getPriceCny();
        } else if ("USD".equalsIgnoreCase(currency)) {
            packagePrice = packageInfo.getPriceUsd();
            // 简单汇率转换，实际应该调用汇率API
            packagePrice = packagePrice.multiply(new BigDecimal("7.2")); // 假设1美元=7.2人民币
        } else {
            packagePrice = packageInfo.getPriceCny(); // 默认使用人民币价格
        }

        if (!paymentSecurityUtil.checkDailyAmountLimit(dailyAmountKey, packagePrice, dailyAmountLimit)) {
            log.warn("用户今日支付金额超限，用户ID: {}, 当前金额: {}", userId, packagePrice);
            throw new PaymentException("今日支付金额已达上限，请明日再试");
        }

        log.debug("用户权限验证通过，用户ID: {}", userId);
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIpAddress() {
        // 这里应该从HttpServletRequest中获取真实IP
        // 由于当前上下文中没有Request对象，返回默认值
        // 实际实现中应该通过RequestContextHolder获取
        try {
            // 可以通过以下方式获取：
            // HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            // return getClientIpFromRequest(request);
            return "127.0.0.1"; // 临时返回本地IP
        } catch (Exception e) {
            log.debug("无法获取客户端IP地址: {}", e.getMessage());
            return null;
        }
    }





    @Override
    public UserOrder queryPaymentStatus(String orderNo) {
        return orderService.getOrderByOrderNo(orderNo);
    }

    @Override
    public PaymentStatusResponseVO queryAndSyncPaymentStatus(String orderNo) {
        try {
            log.info("查询并同步支付状态，订单号: {}", orderNo);

            // 查找订单
            UserOrder order = orderService.getOrderByOrderNo(orderNo);
            if (order == null) {
                log.error("未找到订单，订单号: {}", orderNo);
                throw ResourceNotFoundException.orderNotFound(orderNo);
            }

            // 获取支付策略
            PaymentStrategy strategy = paymentStrategyFactory.getStrategy(order.getPaymentMethod());
            if (strategy == null) {
                log.error("未找到支付策略，支付方式: {}", order.getPaymentMethod());
                throw new PaymentException("不支持的支付方式: " + order.getPaymentMethod());
            }

            // 查询支付状态（策略内部会调用API并同步状态）
            String paymentStatus = strategy.queryPaymentStatus(orderNo);

            // 重新获取订单信息（可能已被策略更新）
            UserOrder updatedOrder = orderService.getOrderByOrderNo(orderNo);
            if (updatedOrder == null) {
                updatedOrder = order; // 使用原订单信息作为备用
            }

            // 构建响应
            PaymentStatusResponseVO response = new PaymentStatusResponseVO();
            response.setOrderNo(orderNo);
            response.setPaymentRequestId(updatedOrder.getPaymentRequestId());
            response.setPaymentStatus(paymentStatus);
            response.setAmount(updatedOrder.getAmount());
            response.setCurrency(updatedOrder.getCurrency());
            response.setPaymentMethod(updatedOrder.getPaymentMethod());
            response.setCreatedTime(updatedOrder.getCreatedTime());

            log.info("支付状态查询并同步成功，订单号: {}, 状态: {}", orderNo, paymentStatus);
            return response;

        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询并同步支付状态异常，订单号: {}", orderNo, e);
            throw new PaymentException("查询支付状态失败", e.getMessage());
        }
    }

    @Override
    public boolean handlePaymentNotify(String notifyBody, String signature, String requestTime, String clientId) {
        try {
            log.info("收到支付回调通知，clientId: {}, requestTime: {}", clientId, requestTime);

            // 验证签名
            if (!verifyNotifySignature("/api/payment/alipay/notify", "POST", clientId, requestTime, signature, notifyBody)) {
                log.error("支付回调签名验证失败");
                throw PaymentException.callbackVerificationFailed("签名验证失败");
            }

            // 解析回调内容
            JsonNode notifyJson;
            try {
                notifyJson = objectMapper.readTree(notifyBody);
            } catch (Exception e) {
                log.error("解析回调内容失败: {}", notifyBody, e);
                throw PaymentException.callbackVerificationFailed("回调内容格式错误");
            }

            // 提取关键信息
            String paymentRequestId = extractFromNotify(notifyJson, "paymentRequestId");
            String paymentStatus = extractFromNotify(notifyJson, "paymentStatus");
            String transactionId = extractFromNotify(notifyJson, "paymentId");

            if (!StringUtils.hasText(paymentRequestId)) {
                log.error("回调中缺少paymentRequestId");
                throw PaymentException.callbackVerificationFailed("缺少paymentRequestId");
            }

            // 查找订单
            UserOrder order = orderService.getOrderByPaymentRequestId(paymentRequestId);
            if (order == null) {
                log.error("未找到对应订单，paymentRequestId: {}", paymentRequestId);
                throw ResourceNotFoundException.orderNotFound(paymentRequestId);
            }

            // 检查订单是否过期
            if (orderService.isOrderExpired(order.getOrderNo())) {
                log.warn("订单已过期，拒绝处理支付回调，订单号: {}", order.getOrderNo());
                throw PaymentException.invalidOrderStatus(order.getOrderNo(), "已过期");
            }

            // 检查重复支付（幂等性验证）
            Integer newStatus = parsePaymentStatus(paymentStatus);
            if (newStatus == null) {
                log.error("无效的支付状态: {}", paymentStatus);
                throw PaymentException.callbackVerificationFailed("无效的支付状态: " + paymentStatus);
            }

            // 如果订单已经是目标状态，直接返回成功（幂等性）
            if (order.getPaymentStatus().equals(newStatus)) {
                log.info("订单状态已经是目标状态，跳过更新，订单号: {}, 状态: {}", order.getOrderNo(), newStatus);
                return true;
            }

            // 验证状态流转的合法性
            if (!isValidStatusTransition(order.getPaymentStatus(), newStatus)) {
                String errorMsg = String.format("非法的状态流转，订单号: %s, 当前状态: %s(%s), 目标状态: %s(%s)",
                        order.getOrderNo(),
                        order.getPaymentStatus(), getStatusDescription(order.getPaymentStatus()),
                        newStatus, getStatusDescription(newStatus));
                log.error(errorMsg);
                throw PaymentException.invalidOrderStatus(order.getOrderNo(), errorMsg);
            }

            // 记录状态更新前的信息
            Integer oldStatus = order.getPaymentStatus();
            String oldTransactionId = order.getTransactionId();

            try {
                boolean updated = orderService.updatePaymentStatus(order.getOrderNo(), newStatus, transactionId);
                if (!updated) {
                    String errorMsg = String.format("订单状态更新失败，订单号: %s, 状态: %s -> %s",
                            order.getOrderNo(), getStatusDescription(oldStatus), getStatusDescription(newStatus));
                    log.error(errorMsg);
                    throw new PaymentException(errorMsg);
                }

                log.info("订单状态更新成功，订单号: {}, 状态: {}({}) -> {}({}), 交易号: {} -> {}",
                        order.getOrderNo(),
                        oldStatus, getStatusDescription(oldStatus),
                        newStatus, getStatusDescription(newStatus),
                        oldTransactionId, transactionId);
                return true;

            } catch (Exception e) {
                log.error("订单状态更新异常，订单号: {}, 状态: {} -> {}",
                        order.getOrderNo(), getStatusDescription(oldStatus), getStatusDescription(newStatus), e);
                throw new PaymentException("订单状态更新失败: " + e.getMessage());
            }

        } catch (PaymentException | ResourceNotFoundException e) {
            // 业务异常记录日志但不抛出，返回false让支付平台重试
            log.error("处理支付回调业务异常: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("处理支付回调系统异常", e);
            return false;
        }
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

            // 验证时间戳有效性（防止重放攻击）
            if (StringUtils.hasText(requestTime)) {
                try {
                    long timestamp = Long.parseLong(requestTime);
                    if (!paymentSecurityUtil.isTimestampValid(timestamp, 30)) { // 30分钟有效期
                        log.error("回调时间戳无效或过期: {}", requestTime);
                        return false;
                    }
                } catch (NumberFormatException e) {
                    log.error("回调时间戳格式错误: {}", requestTime);
                    return false;
                }
            }

            // 生成回调请求唯一标识，防止重复处理
            String requestId = paymentSecurityUtil.generateCallbackRequestId(
                clientId != null ? clientId : "unknown",
                requestTime != null ? requestTime : String.valueOf(System.currentTimeMillis()),
                signature
            );

            if (!paymentSecurityUtil.checkAndRecordCallback(requestId)) {
                log.error("检测到重复的回调请求: {}", requestId);
                return false;
            }

            // 验证签名
            boolean signatureValid = WebhookTool.checkSignature(requestUri, requestMethod, clientId, requestTime,
                                            signature, notifyBody, alipayConfig.getPublicKey());

            if (!signatureValid) {
                log.error("支付宝回调签名验证失败");
                return false;
            }

            log.debug("支付宝回调签名验证成功，requestId: {}", requestId);
            return true;

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

            // 生成回调请求唯一标识，防止重复处理
            String requestId = paymentSecurityUtil.generateCallbackRequestId(
                outTradeNo,
                String.valueOf(System.currentTimeMillis()),
                sign
            );

            if (!paymentSecurityUtil.checkAndRecordCallback(requestId)) {
                log.error("检测到重复的易支付回调请求: {}", requestId);
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

            log.debug("易支付回调签名验证成功，requestId: {}", requestId);

            // 查找订单
            UserOrder order = orderService.getOrderByOrderNo(outTradeNo);
            if (order == null) {
                log.error("未找到对应订单，订单号: {}", outTradeNo);
                return false;
            }

            // 检查订单是否过期
            if (orderService.isOrderExpired(order.getOrderNo())) {
                log.warn("订单已过期，拒绝处理易支付回调，订单号: {}", order.getOrderNo());
                return false;
            }

            // 更新订单状态
            Integer newStatus = parseYiPayTradeStatus(tradeStatus);
            if (newStatus == null) {
                log.error("易支付回调中包含无效的交易状态: {}", tradeStatus);
                return false;
            }

            // 如果订单已经是目标状态，直接返回成功（幂等性）
            if (order.getPaymentStatus().equals(newStatus)) {
                log.info("易支付订单状态已经是目标状态，跳过更新，订单号: {}, 状态: {}({})",
                        order.getOrderNo(), newStatus, getStatusDescription(newStatus));
                return true;
            }

            // 验证状态流转的合法性
            if (!isValidStatusTransition(order.getPaymentStatus(), newStatus)) {
                String errorMsg = String.format("易支付非法的状态流转，订单号: %s, 当前状态: %s(%s), 目标状态: %s(%s)",
                        order.getOrderNo(),
                        order.getPaymentStatus(), getStatusDescription(order.getPaymentStatus()),
                        newStatus, getStatusDescription(newStatus));
                log.error(errorMsg);
                return false;
            }

            // 记录状态更新前的信息
            Integer oldStatus = order.getPaymentStatus();
            String oldTransactionId = order.getTransactionId();

            try {
                boolean updated = orderService.updatePaymentStatus(outTradeNo, newStatus, tradeNo);
                if (updated) {
                    log.info("易支付订单状态更新成功，订单号: {}, 状态: {}({}) -> {}({}), 交易号: {} -> {}",
                            outTradeNo,
                            oldStatus, getStatusDescription(oldStatus),
                            newStatus, getStatusDescription(newStatus),
                            oldTransactionId, tradeNo);
                    return true;
                } else {
                    log.error("易支付订单状态更新失败，订单号: {}, 状态: {} -> {}",
                            outTradeNo, getStatusDescription(oldStatus), getStatusDescription(newStatus));
                    return false;
                }
            } catch (Exception e) {
                log.error("易支付订单状态更新异常，订单号: {}, 状态: {} -> {}",
                        outTradeNo, getStatusDescription(oldStatus), getStatusDescription(newStatus), e);
                return false;
            }

        } catch (Exception e) {
            log.error("处理易支付回调异常", e);
        }

        return false;
    }





    /**
     * 验证订单状态流转的合法性
     *
     * @param currentStatus 当前状态
     * @param newStatus     新状态
     * @return 是否合法
     */
    private boolean isValidStatusTransition(Integer currentStatus, Integer newStatus) {
        // 状态定义：0-未支付，1-已支付，2-已取消，3-已退款

        if (currentStatus == null || newStatus == null) {
            log.error("状态流转验证失败：状态值不能为空，当前状态: {}, 新状态: {}", currentStatus, newStatus);
            return false;
        }

        // 状态值范围验证
        if (currentStatus < 0 || currentStatus > 3 || newStatus < 0 || newStatus > 3) {
            log.error("状态流转验证失败：状态值超出范围，当前状态: {}, 新状态: {}", currentStatus, newStatus);
            return false;
        }

        // 相同状态不需要流转
        if (currentStatus.equals(newStatus)) {
            log.debug("状态相同，无需流转：{}", currentStatus);
            return true;
        }

        // 已支付的订单只能变为已退款
        if (currentStatus == 1) {
            if (newStatus == 3) {
                log.debug("状态流转：已支付 -> 已退款");
                return true;
            } else {
                log.warn("非法状态流转：已支付订单不能变为状态 {}", newStatus);
                return false;
            }
        }

        // 已取消的订单不能变为其他状态
        if (currentStatus == 2) {
            log.warn("非法状态流转：已取消订单不能变为状态 {}", newStatus);
            return false;
        }

        // 已退款的订单不能变为其他状态
        if (currentStatus == 3) {
            log.warn("非法状态流转：已退款订单不能变为状态 {}", newStatus);
            return false;
        }

        // 未支付状态可以流转到任何其他状态
        if (currentStatus == 0) {
            log.debug("状态流转：未支付 -> {}", getStatusDescription(newStatus));
            return true;
        }

        // 默认不允许
        log.warn("未知的状态流转：{} -> {}", currentStatus, newStatus);
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

    /**
     * 从回调通知中提取字段值
     */
    private String extractFromNotify(JsonNode notifyJson, String fieldName) {
        JsonNode fieldNode = notifyJson.get(fieldName);
        if (fieldNode != null && !fieldNode.isNull()) {
            return fieldNode.asText();
        }

        // 尝试从嵌套对象中提取
        String[] fieldParts = fieldName.split("\\.");
        if (fieldParts.length > 1) {
            JsonNode currentNode = notifyJson;
            for (String part : fieldParts) {
                currentNode = currentNode.get(part);
                if (currentNode == null || currentNode.isNull()) {
                    return null;
                }
            }
            return currentNode.asText();
        }

        return null;
    }

    /**
     * 解析支付状态
     */
    private Integer parsePaymentStatus(String paymentStatus) {
        if (!StringUtils.hasText(paymentStatus)) {
            return null;
        }

        String status = paymentStatus.toUpperCase().trim();

        // 成功状态
        if (status.equals("SUCCESS") || status.equals("PAID") ||
            status.equals("COMPLETED") || status.equals("TRADE_SUCCESS") ||
            status.equals("PAYMENT_SUCCESS") || status.equals("PAY_SUCCESS")) {
            return 1; // 已支付
        }

        // 取消/失败状态
        if (status.equals("CANCELLED") || status.equals("CANCELED") ||
            status.equals("FAILED") || status.equals("TRADE_CLOSED") ||
            status.equals("PAYMENT_FAILED") || status.equals("PAY_FAILED") ||
            status.equals("CLOSED") || status.equals("TIMEOUT")) {
            return 2; // 已取消
        }

        // 退款状态
        if (status.equals("REFUNDED") || status.equals("TRADE_REFUND") ||
            status.equals("REFUND_SUCCESS") || status.equals("PARTIAL_REFUNDED")) {
            return 3; // 已退款
        }

        // 处理中状态
        if (status.equals("PENDING") || status.equals("PROCESSING") ||
            status.equals("TRADE_PENDING") || status.equals("WAIT_BUYER_PAY") ||
            status.equals("CREATED") || status.equals("INITIATED")) {
            return 0; // 未支付/处理中
        }

        // 未知状态，记录日志并返回未支付
        log.warn("未知的支付状态: {}", paymentStatus);
        return 0; // 默认未支付
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

        String status = tradeStatus.toUpperCase().trim();

        // 成功状态
        if (status.equals("TRADE_SUCCESS") || status.equals("SUCCESS") ||
            status.equals("PAID") || status.equals("COMPLETED") ||
            status.equals("PAY_SUCCESS") || status.equals("PAYMENT_SUCCESS")) {
            return 1; // 已支付
        }

        // 取消/失败状态
        if (status.equals("TRADE_CLOSED") || status.equals("CLOSED") ||
            status.equals("FAILED") || status.equals("CANCELLED") ||
            status.equals("CANCELED") || status.equals("TIMEOUT") ||
            status.equals("PAY_FAILED") || status.equals("PAYMENT_FAILED")) {
            return 2; // 已取消
        }

        // 退款状态
        if (status.equals("TRADE_REFUND") || status.equals("REFUND") ||
            status.equals("REFUNDED") || status.equals("REFUND_SUCCESS") ||
            status.equals("PARTIAL_REFUNDED")) {
            return 3; // 已退款
        }

        // 处理中状态
        if (status.equals("TRADE_PENDING") || status.equals("PENDING") ||
            status.equals("PROCESSING") || status.equals("WAIT_BUYER_PAY") ||
            status.equals("CREATED") || status.equals("INITIATED")) {
            return 0; // 未支付/处理中
        }

        // 未知状态，记录日志并返回未支付
        log.warn("未知的易支付交易状态: {}", tradeStatus);
        return 0; // 默认未支付
    }
}
