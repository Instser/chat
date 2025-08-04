package com.instser.chat.service.serviceimpl;

import com.alipay.global.api.tools.WebhookTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.common.exception.PaymentException;
import com.instser.chat.common.exception.ResourceNotFoundException;
import com.instser.chat.common.util.LogUtils;
import com.instser.chat.config.AlipayConfig;
import com.instser.chat.config.YiPayConfig;
import com.instser.chat.entity.Package;
import com.instser.chat.entity.UserOrder;
import com.instser.chat.service.OrderService;
import com.instser.chat.service.PackageService;
import com.instser.chat.service.PaymentAuditService;
import com.instser.chat.service.PaymentService;


import com.instser.chat.service.PackageActivationService;
import com.instser.chat.service.PaymentSecurityEnhancer;
import com.instser.chat.service.RedisPaymentSecurityService;
import com.instser.chat.strategy.PaymentStrategy;
import com.instser.chat.strategy.PaymentStrategyFactory;
import com.instser.chat.util.PaymentSecurityUtil;
import com.instser.chat.util.PaymentSessionManager;
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
    private final PaymentAuditService paymentAuditService;
    private final AlipayConfig alipayConfig;
    private final YiPayConfig yiPayConfig;
    private final PaymentSecurityUtil paymentSecurityUtil;
    private final PackageActivationService packageActivationService;
    private final PaymentSecurityEnhancer paymentSecurityEnhancer;
    private final RedisPaymentSecurityService redisPaymentSecurityService;

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
     * 注意：前端只显示支付宝和微信支付，不显示易支付选项
     */
    @Override
    public List<PaymentMethodVO> getAvailablePaymentMethods() {
        List<String> availableMethods = paymentStrategyFactory.getAvailablePaymentMethods();
        List<PaymentMethodVO> paymentMethods = new ArrayList<>();

        for (String method : availableMethods) {
            // 过滤掉易支付和微信支付选项，前端只显示支付宝
            if ("yipay".equalsIgnoreCase(method) || "wechatpay".equalsIgnoreCase(method)) {
                continue;
            }

            PaymentMethodVO paymentMethod = new PaymentMethodVO();
            paymentMethod.setId(method);
            paymentMethod.setType(method);
            paymentMethod.setAvailable(true);

            // 设置显示名称
            switch (method.toLowerCase()) {
                case "alipay":
                    paymentMethod.setName("AliPay");
                    break;
                case "wechatpay":
                    paymentMethod.setName("WeChatPay");
                    break;
                case "yipay":
                    paymentMethod.setName("YiPay");
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
        log.info("【支付创建】用户ID：{}，套餐ID：{}，支付方式：{}",
            paymentRequest.getUserId(), paymentRequest.getPackageId(), paymentRequest.getPaymentMethod());

        // ✅ 使用PaymentSessionManager确保会话锁正确释放
        try (PaymentSessionManager sessionManager = new PaymentSessionManager(
                redisPaymentSecurityService, paymentRequest.getUserId())) {

            log.debug("【支付处理】开始验证支付请求参数，用户ID：{}", paymentRequest.getUserId());

            // 1. 验证支付请求参数
            validatePaymentRequest(paymentRequest);

            log.debug("【支付处理】开始支付安全性验证，用户ID：{}", paymentRequest.getUserId());

            // 2. 支付安全性验证（不包含会话锁管理）
            validatePaymentSecurityWithoutSession(paymentRequest);

            log.debug("【支付处理】获取支付会话锁，用户ID：{}", paymentRequest.getUserId());

            // 3. 获取支付会话锁
            sessionManager.acquire();

            // 4. 根据paymentMethod字段选择支付方式
            String paymentMethod = paymentRequest.getPaymentMethod();

            log.debug("【支付策略】选择支付策略：{}，用户ID：{}", paymentMethod, paymentRequest.getUserId());

            // 5. 使用策略模式获取对应的支付策略
            PaymentStrategy strategy = paymentStrategyFactory.getStrategy(paymentMethod);

            log.debug("【支付处理】执行支付创建，策略：{}，用户ID：{}",
                strategy.getClass().getSimpleName(), paymentRequest.getUserId());

            // 6. 执行支付创建
            PaymentResponseVO response = strategy.createPayment(paymentRequest);

            // 7. 记录支付创建审计日志
            recordCreatePaymentAudit(paymentRequest, response);

            log.info("【支付创建】支付创建成功，用户ID：{}，订单号：{}",
                paymentRequest.getUserId(), response.getOrderNo());

            return response;

        } catch (PaymentException | ResourceNotFoundException e) {
            log.warn("【支付创建】支付创建失败，用户ID：{}，原因：{}",
                paymentRequest.getUserId(), e.getMessage());
            // 业务异常直接抛出，由全局异常处理器处理
            throw e;
        } catch (Exception e) {
            log.error("【支付创建】支付创建异常，用户ID：{}，错误：{}",
                paymentRequest.getUserId(), e.getMessage(), e);
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
     * 支付安全性验证（不包含会话锁管理）
     * 会话锁管理由PaymentSessionManager负责
     */
    private void validatePaymentSecurityWithoutSession(PaymentRequestVO paymentRequest) {
        Long userId = paymentRequest.getUserId();

        // 启用安全验证
        boolean securityEnabled = true; // 安全验证开关

        if (securityEnabled) {
            // 1. 支付请求频率限制验证（用户、IP、套餐级别）
            validatePaymentRateLimit(paymentRequest);

            // 2. 用户权限验证（未完成订单、每日限制等）
            validateUserPermissions(paymentRequest);

            // 3. 生成或验证支付请求ID（防重放攻击）
            String requestId = paymentRequest.getRequestId();
            if (requestId == null || requestId.trim().isEmpty()) {
                // 如果前端未提供requestId，后端自动生成
                requestId = generatePaymentRequestId(paymentRequest);
                paymentRequest.setRequestId(requestId);
            }

            // 4. 检查支付请求是否已处理（防重放攻击）
            // 【优化】移除重复支付请求检查，该逻辑已在OrderService.createOrder中处理
            // if (redisPaymentSecurityService.isPaymentRequestProcessed(requestId, userId)) {
            //     throw new PaymentException("重复的支付请求，请勿重复提交");
            // }

            // 5. 检查用户是否有活跃的支付会话，如果有则自动释放旧会话
            if (redisPaymentSecurityService.hasActivePaymentSession(userId)) {
                log.info("用户{}有活跃的支付会话，将自动释放旧会话以创建新订单", userId);
                redisPaymentSecurityService.releasePaymentSession(userId);
            }

            // 6. 检查支付频率限制（Redis分布式验证）
            if (!paymentSecurityEnhancer.checkPaymentFrequency(userId)) {
                throw new PaymentException("支付频率过高，请稍后再试");
            }

            // 7. 检测异常支付行为
            if (!paymentSecurityEnhancer.detectAbnormalPayment(paymentRequest)) {
                paymentSecurityEnhancer.recordSuspiciousBehavior(userId, "异常支付检测失败");
                throw new PaymentException("检测到异常支付行为，请联系客服");
            }

            log.info("用户{}支付安全验证通过，请求ID: {}", userId, requestId);
        } else {
            log.warn("支付安全验证已关闭，跳过所有安全检查");
        }

        // 支付金额验证 - 保留基本的金额验证
        validatePaymentAmount(paymentRequest);
    }

    /**
     * 生成支付请求唯一标识
     * 基于关键业务参数生成，确保相同业务请求有相同ID
     */
    private String generatePaymentRequestId(PaymentRequestVO paymentRequest) {
        // 获取套餐信息用于生成ID
        Package packageInfo = packageService.getPackageById(paymentRequest.getPackageId());
        String packageName = packageInfo != null ? packageInfo.getPackageName() : "";

        // 直接使用CNY价格
        BigDecimal amount = BigDecimal.ZERO;
        try {
            if (packageInfo != null) {
                amount = packageInfo.getPrice();
            }
        } catch (Exception e) {
            log.warn("获取套餐价格异常，使用默认值", e);
        }

        // 使用RedisPaymentSecurityService生成ID
        return redisPaymentSecurityService.generatePaymentRequestId(
            paymentRequest.getUserId(),
            packageName,
            amount.toString(),
            paymentRequest.getPaymentMethod()
        );
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

        // 获取原始USD价格
        BigDecimal packagePrice = packageInfo.getPrice();

        // 验证最小金额（0.01 USD）
        BigDecimal minAmount = new BigDecimal("0.01");
        if (packagePrice.compareTo(minAmount) < 0) {
            throw new PaymentException("支付金额不能小于 " + minAmount + " USD");
        }

        // 验证最大金额（美元最大1万）
        BigDecimal maxAmount = new BigDecimal("10000.00");
        if (packagePrice.compareTo(maxAmount) > 0) {
            throw new PaymentException("支付金额不能超过 " + maxAmount + " USD");
        }

        // 验证免费套餐价格
        if (packagePrice.compareTo(BigDecimal.ZERO) == 0) {
            // 免费套餐不需要支付，直接激活
            log.info("检测到免费套餐，用户ID: {}, 套餐ID: {}", paymentRequest.getUserId(), paymentRequest.getPackageId());
            throw new PaymentException("免费套餐无需支付，请使用 /api/payment/activate-free 接口直接激活");
        }

        log.debug("支付金额验证通过，套餐ID: {}, 金额: {} USD",
                 paymentRequest.getPackageId(), packagePrice);
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

        // 注释：移除数据库层的订单检查，统一使用Redis层的会话管理
        // 原因：Redis会话锁已经能够有效防止并发支付，避免重复检查提升性能
        // 如果需要清理历史待支付订单，可以通过定时任务或手动接口处理

        // 检查用户今日支付次数限制（每天最多10次支付）
        String dailyLimitKey = "payment:daily_limit:user:" + userId;
        if (!paymentSecurityUtil.checkDailyLimit(dailyLimitKey, 10)) {
            log.warn("用户今日支付次数超限，用户ID: {}", userId);
            throw new PaymentException("今日支付次数已达上限，请明日再试");
        }

        // 检查用户支付总金额限制（每天最多支付1400美元等值）
        BigDecimal dailyAmountLimit = new BigDecimal("1400.00");
        String dailyAmountKey = "payment:daily_amount:user:" + userId;

        // 获取套餐价格（USD价格）
        Package packageInfo = packageService.getPackageById(paymentRequest.getPackageId());
        BigDecimal packagePrice = packageInfo.getPrice();

        if (!paymentSecurityUtil.checkDailyAmountLimit(dailyAmountKey, packagePrice, dailyAmountLimit)) {
            log.warn("用户今日支付金额超限，用户ID: {}, 当前金额: {} USD", userId, packagePrice);
            throw new PaymentException("今日支付金额已达上限，请明日再试");
        }

        log.debug("用户权限验证通过，用户ID: {}", userId);
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

            // 记录原始状态
            Integer originalStatus = order.getPaymentStatus();

            // 查询支付状态（策略内部会调用API并同步状态）
            String paymentStatus = strategy.queryPaymentStatus(orderNo);

            // 重新获取订单信息（可能已被策略更新）
            UserOrder updatedOrder = orderService.getOrderByOrderNo(orderNo);
            if (updatedOrder == null) {
                updatedOrder = order; // 使用原订单信息作为备用
            }

            // 如果状态发生了变化，记录同步审计日志
            if (!originalStatus.equals(updatedOrder.getPaymentStatus())) {
                recordSyncAudit(orderNo, order.getPaymentMethod(), originalStatus, updatedOrder.getPaymentStatus(), "API_QUERY_SYNC");
            }

            // 构建响应
            PaymentStatusResponseVO response = new PaymentStatusResponseVO();
            response.setOrderNo(orderNo);
            response.setPaymentRequestId(updatedOrder.getPaymentRequestId());
            response.setPaymentStatus(paymentStatus);
            response.setAmount(updatedOrder.getAmount());
            response.setCurrency(updatedOrder.getCurrency());
            response.setPaymentMethod(updatedOrder.getPaymentMethod());
            response.setCreatedTime(updatedOrder.getCreateTime());

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
            log.info("【支付回调】收到支付回调通知，clientId：{}，requestTime：{}", clientId, requestTime);

            log.debug("【支付回调】生成回调唯一标识进行防重放检查");

            // 生成回调唯一标识进行防重放攻击检查
            String callbackId = generateCallbackId(clientId, requestTime, signature);
            if (redisPaymentSecurityService.isCallbackProcessed(callbackId)) {
                log.warn("【支付回调】检测到重放攻击，回调ID：{}", callbackId);
                return false;
            }

            log.debug("【支付回调】开始验证签名，回调ID：{}", callbackId);

            // 验证签名
            if (!verifyNotifySignature("/api/payment/alipay/notify", "POST", clientId, requestTime, signature, notifyBody)) {
                log.error("【支付回调】签名验证失败，回调ID：{}", callbackId);
                throw PaymentException.callbackVerificationFailed("签名验证失败");
            }

            log.debug("【支付回调】签名验证成功，回调ID：{}", callbackId);

            log.debug("【支付回调】开始解析回调内容");

            // 解析回调内容
            JsonNode notifyJson;
            try {
                notifyJson = objectMapper.readTree(notifyBody);
            } catch (Exception e) {
                log.error("【支付回调】解析回调内容失败：{}", LogUtils.truncateContent(notifyBody), e);
                throw PaymentException.callbackVerificationFailed("回调内容格式错误");
            }

            // 提取关键信息
            String paymentRequestId = extractFromNotify(notifyJson, "paymentRequestId");
            String paymentStatus = extractFromNotify(notifyJson, "paymentStatus");
            String transactionId = extractFromNotify(notifyJson, "paymentId");

            log.debug("【支付回调】提取关键信息 - paymentRequestId：{}，paymentStatus：{}，transactionId：{}",
                paymentRequestId, paymentStatus, transactionId);

            if (!StringUtils.hasText(paymentRequestId)) {
                log.error("【支付回调】回调中缺少paymentRequestId");
                throw PaymentException.callbackVerificationFailed("缺少paymentRequestId");
            }

            log.debug("【支付回调】查找订单，paymentRequestId：{}", paymentRequestId);

            // 查找订单
            UserOrder order = orderService.getOrderByPaymentRequestId(paymentRequestId);
            if (order == null) {
                log.error("【支付回调】未找到对应订单，paymentRequestId：{}", paymentRequestId);
                throw ResourceNotFoundException.orderNotFound(paymentRequestId);
            }

            log.debug("【支付回调】找到订单，订单号：{}，当前状态：{}", order.getOrderNo(), order.getPaymentStatus());

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

                // 记录支付回调审计日志
                recordCallbackAudit(order.getOrderNo(), order.getPaymentMethod(), oldStatus, newStatus, notifyBody);

                // 如果支付成功，自动激活套餐
                if (newStatus == 1) {
                    try {
                        boolean activationSuccess = packageActivationService.activatePackageAfterPayment(order.getOrderNo());
                        if (activationSuccess) {
                            log.info("支付成功后套餐激活成功，订单号: {}", order.getOrderNo());
                        } else {
                            log.error("支付成功后套餐激活失败，订单号: {}", order.getOrderNo());
                        }
                    } catch (Exception e) {
                        log.error("支付成功后套餐激活异常，订单号: {}", order.getOrderNo(), e);
                    }
                }

                // 释放用户支付会话锁
                redisPaymentSecurityService.releasePaymentSession(order.getUserId());
                log.debug("释放用户{}支付会话锁，订单号: {}", order.getUserId(), order.getOrderNo());

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
    public boolean handleAlipayNotify(Map<String, String> notifyParams) {
        try {
            // 验证必要参数
            String tradeNo = notifyParams.get("trade_no");
            String outTradeNo = notifyParams.get("out_trade_no");
            String tradeStatus = notifyParams.get("trade_status");

            if (!StringUtils.hasText(outTradeNo)) {
                log.error("支付宝回调缺少订单号");
                return false;
            }

            // 构建回调数据字符串
            StringBuilder callbackData = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, String> entry : notifyParams.entrySet()) {
                if (!first) {
                    callbackData.append("&");
                }
                callbackData.append(entry.getKey()).append("=").append(entry.getValue());
                first = false;
            }

            // 使用支付宝策略处理回调
            PaymentStrategy strategy = paymentStrategyFactory.getStrategy("alipay");
            boolean result = strategy.handleCallback(callbackData.toString());

            // 记录回调处理审计日志
            log.info("支付宝回调处理完成，订单号: {}, 交易号: {}, 状态: {}, 结果: {}",
                    outTradeNo, tradeNo, tradeStatus, result ? "成功" : "失败");

            return result;

        } catch (Exception e) {
            log.error("处理支付宝回调异常", e);
            return false;
        }
    }

    @Override
    public boolean handleWechatPayNotify(Map<String, String> notifyParams) {
        try {
            // 验证必要参数
            String tradeNo = notifyParams.get("trade_no");
            String outTradeNo = notifyParams.get("out_trade_no");
            String tradeStatus = notifyParams.get("trade_status");

            if (!StringUtils.hasText(outTradeNo)) {
                log.error("微信支付回调缺少订单号");
                return false;
            }

            // 构建回调数据字符串
            StringBuilder callbackData = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, String> entry : notifyParams.entrySet()) {
                if (!first) {
                    callbackData.append("&");
                }
                callbackData.append(entry.getKey()).append("=").append(entry.getValue());
                first = false;
            }

            // 使用微信支付策略处理回调
            PaymentStrategy strategy = paymentStrategyFactory.getStrategy("wechatpay");
            boolean result = strategy.handleCallback(callbackData.toString());

            // 记录回调处理审计日志
            // 注意：这里无法获取到订单状态变更信息，所以使用简化的日志记录
            log.info("微信支付回调处理完成，订单号: {}, 交易号: {}, 状态: {}, 结果: {}",
                    outTradeNo, tradeNo, tradeStatus, result ? "成功" : "失败");

            return result;

        } catch (Exception e) {
            log.error("处理微信支付回调异常", e);
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
            String paymentType = notifyParams.get("type"); // 获取支付类型

            if (outTradeNo == null || sign == null) {
                log.error("易支付回调参数不完整");
                return false;
            }

            // 使用确定性参数生成回调唯一标识，修复幂等性问题
            String callbackId = outTradeNo + "_" + (tradeNo != null ? tradeNo : "") + "_" + (paymentType != null ? paymentType : "");
            if (redisPaymentSecurityService.isCallbackProcessed(callbackId)) {
                log.warn("易支付回调重复处理，跳过。回调ID: {}", callbackId);
                return true; // 幂等性处理：重复通知应返回成功
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

            log.debug("易支付回调签名验证成功，callbackId: {}", callbackId);

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

                    // 如果支付成功，自动激活套餐
                    if (newStatus == 1) {
                        try {
                            boolean activationSuccess = packageActivationService.activatePackageAfterPayment(outTradeNo);
                            if (activationSuccess) {
                                log.info("易支付成功后套餐激活成功，订单号: {}", outTradeNo);
                            } else {
                                log.error("易支付成功后套餐激活失败，订单号: {}", outTradeNo);
                            }
                        } catch (Exception e) {
                            log.error("易支付成功后套餐激活异常，订单号: {}", outTradeNo, e);
                        }
                    }

                    // 释放用户支付会话锁
                    redisPaymentSecurityService.releasePaymentSession(order.getUserId());
                    log.debug("释放用户{}支付会话锁，订单号: {}", order.getUserId(), outTradeNo);

                    // 标记回调已成功处理
                    redisPaymentSecurityService.markCallbackAsProcessed(callbackId);
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

    /**
     * 生成回调唯一标识
     */
    private String generateCallbackId(String clientId, String requestTime, String signature) {
        String source = (clientId != null ? clientId : "unknown") +
                       (requestTime != null ? requestTime : String.valueOf(System.currentTimeMillis())) +
                       (signature != null ? signature : "");
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(source.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("生成回调ID异常", e);
            return source.hashCode() + "";
        }
    }

    // ==================== 审计日志相关方法 ====================

    /**
     * 记录支付创建审计日志
     */
    private void recordCreatePaymentAudit(PaymentRequestVO paymentRequest, PaymentResponseVO response) {
        try {
            Map<String, Object> auditDetails = new HashMap<>();
            auditDetails.put("paymentMethod", paymentRequest.getPaymentMethod());
            auditDetails.put("packageId", paymentRequest.getPackageId());
            auditDetails.put("currency", paymentRequest.getCurrency());
            auditDetails.put("timestamp", java.time.LocalDateTime.now().toString());

            if (response != null) {
                auditDetails.put("orderNo", response.getOrderNo());
                auditDetails.put("amount", response.getAmount());
                auditDetails.put("qrCodeUrl", response.getQrCodeUrl());
            }

            String details = createOperationDetails(auditDetails);
            paymentAuditService.recordCreateAudit(
                response != null ? response.getOrderNo() : "UNKNOWN",
                paymentRequest.getUserId(),
                details,
                getClientIpAddress(),
                getUserAgent()
            );
        } catch (Exception e) {
            log.warn("记录支付创建审计日志失败", e);
        }
    }

    /**
     * 记录支付回调审计日志
     */
    private void recordCallbackAudit(String orderNo, String paymentMethod, Integer oldStatus, Integer newStatus, String callbackData) {
        try {
            Map<String, Object> auditDetails = new HashMap<>();
            auditDetails.put("paymentMethod", paymentMethod);
            auditDetails.put("callbackData", callbackData);
            auditDetails.put("timestamp", java.time.LocalDateTime.now().toString());

            String details = createOperationDetails(auditDetails);
            paymentAuditService.recordStatusChangeAudit(
                orderNo,
                oldStatus,
                newStatus,
                paymentMethod + "回调状态变更",
                details
            );
        } catch (Exception e) {
            log.warn("记录支付回调审计日志失败，订单号: {}", orderNo, e);
        }
    }

    /**
     * 记录支付状态同步审计日志
     */
    private void recordSyncAudit(String orderNo, String paymentMethod, Integer oldStatus, Integer newStatus, String syncType) {
        try {
            Map<String, Object> auditDetails = new HashMap<>();
            auditDetails.put("paymentMethod", paymentMethod);
            auditDetails.put("syncType", syncType);
            auditDetails.put("timestamp", java.time.LocalDateTime.now().toString());

            String details = createOperationDetails(auditDetails);
            paymentAuditService.recordStatusChangeAudit(
                orderNo,
                oldStatus,
                newStatus,
                paymentMethod + "状态同步",
                details
            );
        } catch (Exception e) {
            log.warn("记录支付状态同步审计日志失败，订单号: {}", orderNo, e);
        }
    }

    /**
     * 创建操作详情JSON字符串
     */
    private String createOperationDetails(Map<String, Object> details) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(details);
        } catch (Exception e) {
            log.warn("创建操作详情JSON失败", e);
            return "{}";
        }
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIpAddress() {
        try {
            org.springframework.web.context.request.ServletRequestAttributes attributes =
                (org.springframework.web.context.request.ServletRequestAttributes)
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }

            jakarta.servlet.http.HttpServletRequest request = attributes.getRequest();

            // 尝试从各种头部获取真实IP
            String ip = request.getHeader("X-Forwarded-For");
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }

            ip = request.getHeader("X-Real-IP");
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }

            return request.getRemoteAddr();
        } catch (Exception e) {
            log.debug("无法获取客户端IP地址: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取用户代理
     */
    private String getUserAgent() {
        try {
            org.springframework.web.context.request.ServletRequestAttributes attributes =
                (org.springframework.web.context.request.ServletRequestAttributes)
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }

            jakarta.servlet.http.HttpServletRequest request = attributes.getRequest();
            return request.getHeader("User-Agent");
        } catch (Exception e) {
            log.debug("无法获取用户代理: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void processPaymentCallbackAsync(Map<String, String> notifyParams) {
        // 使用CompletableFuture异步处理，避免阻塞回调响应
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            processPaymentCallbackInternal(notifyParams);
        }, getPaymentCallbackExecutor());
    }

    /**
     * 内部异步处理支付回调逻辑
     */
    private void processPaymentCallbackInternal(Map<String, String> notifyParams) {
        String outTradeNo = notifyParams.get("out_trade_no");
        try {
            log.info("开始异步处理易支付回调，订单号: {}", outTradeNo);

            // 直接调用统一的易支付处理方法
            boolean success = handleYiPayNotify(notifyParams);

            if (success) {
                log.info("异步处理易支付回调成功，订单号: {}", outTradeNo);
            } else {
                log.error("异步处理易支付回调失败，订单号: {}", outTradeNo);
            }

        } catch (Exception e) {
            log.error("异步处理易支付回调异常，订单号: {}", outTradeNo, e);
        }
    }

    /**
     * 获取支付回调处理线程池
     */
    private java.util.concurrent.Executor getPaymentCallbackExecutor() {
        try {
            return (java.util.concurrent.Executor)
                com.instser.chat.util.ApplicationContextProvider.getBean("paymentCallbackExecutor");
        } catch (Exception e) {
            log.warn("获取支付回调线程池失败，使用默认线程池: {}", e.getMessage());
            return java.util.concurrent.ForkJoinPool.commonPool();
        }
    }
}
