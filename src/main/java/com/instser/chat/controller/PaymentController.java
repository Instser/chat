package com.instser.chat.controller;

import com.instser.chat.common.result.Result;
import com.instser.chat.common.util.ErrorResponseUtil;
import com.instser.chat.common.util.LogUtils;
import com.instser.chat.entity.Package;
import com.instser.chat.entity.UserOrder;
import com.instser.chat.service.OrderService;
import com.instser.chat.service.ExchangeRateService;
import com.instser.chat.service.PackageService;
import com.instser.chat.service.PackageActivationService;
import com.instser.chat.service.PaymentService;
import com.instser.chat.service.PaymentStatusSyncService;
import com.instser.chat.service.PaymentSyncService;
import com.instser.chat.service.UserPaymentStatusService;
import com.instser.chat.service.StripeService;
import org.springframework.context.ApplicationContext;

import com.instser.chat.vo.*;
import com.instser.chat.vo.converter.VOConverter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 支付控制器
 *
 * @author instser
 * @since 2024-01-01
 */
@Slf4j
@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
@Validated
@CrossOrigin(originPatterns = {
    "http://localhost:*", "https://localhost:*",
    "http://127.0.0.1:*", "https://127.0.0.1:*",
    "http://192.168.1.100:*", "https://192.168.1.100:*",
    "http://192.168.1.101:*", "https://192.168.1.101:*",
    "https://chat.example.com", "http://chat.example.com",
    "http://203.0.113.16:3000"
}, allowedHeaders = "*", allowCredentials = "true")
public class PaymentController {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final PackageService packageService;
    private final PaymentSyncService paymentSyncService;
    private final PackageActivationService packageActivationService;
    private final ExchangeRateService exchangeRateService;
    private final UserPaymentStatusService userPaymentStatusService;
    private final PaymentStatusSyncService paymentStatusSyncService;
    private final StripeService stripeService;

    /**
     * 根据用户支付状态过滤套餐列表
     *
     * @param packages 原始套餐列表
     * @param isUnpaidNewUser 是否为未支付新用户
     * @return 过滤后的套餐列表
     */
    private List<Package> filterPackagesByUserStatus(List<Package> packages, boolean isUnpaidNewUser) {
        if (packages == null || packages.isEmpty()) {
            return packages;
        }

        return packages.stream()
                .filter(pkg -> {
                    Long packageId = pkg.getId();
                    if (packageId == null) {
                        return true; // 保留ID为null的套餐（如果有的话）
                    }

                    if (isUnpaidNewUser) {
                        // 未支付新用户：移除ID为6的套餐
                        boolean shouldInclude = !packageId.equals(6L);
                        if (!shouldInclude) {
                            log.debug("未支付新用户过滤：移除套餐ID={}", packageId);
                        }
                        return shouldInclude;
                    } else {
                        // 已支付用户：移除ID为5的套餐
                        boolean shouldInclude = !packageId.equals(5L);
                        if (!shouldInclude) {
                            log.debug("已支付用户过滤：移除套餐ID={}", packageId);
                        }
                        return shouldInclude;
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取当前认证用户ID
     */
    private Long getCurrentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        try {
            // 检查是否是CustomUserDetails类型
            Object principal = authentication.getPrincipal();
            if (principal instanceof com.instser.chat.security.UserDetailsServiceImpl.CustomUserDetails) {
                com.instser.chat.security.UserDetailsServiceImpl.CustomUserDetails userDetails =
                    (com.instser.chat.security.UserDetailsServiceImpl.CustomUserDetails) principal;
                return userDetails.getUserId();
            }
            // 如果不是CustomUserDetails，返回null表示认证失败
            log.warn("认证主体不是CustomUserDetails类型：{}", principal.getClass().getName());
            return null;
        } catch (Exception e) {
            log.warn("获取用户ID失败", e);
            return null;
        }
    }

    /**
     * 获取套餐列表 - 返回前端兼容格式（支持实时汇率转换）
     */
    @GetMapping("/packages")
    public Result<List<FrontendPackageVO>> getPackages(@RequestParam(defaultValue = "USD") String currency,
                                                       Authentication authentication) {
        try {
            List<Package> packages = packageService.getEnabledPackages();

            // 获取当前用户ID，用于判断是否显示优惠价格
            Long userId = getCurrentUserId(authentication);
            if (userId == null) {
                log.warn("未登录用户尝试访问套餐列表");
                return Result.unauthorized("请先登录");
            }

            // 检查用户是否从未付费，如果是则显示优惠价格
            boolean showDiscountPrice = userPaymentStatusService.hasNeverPaid(userId);
            log.debug("用户{}支付状态检查: 从未付费={}, 显示优惠价格={}", userId, showDiscountPrice, showDiscountPrice);

            // 根据用户支付状态过滤套餐列表
            List<Package> filteredPackages = filterPackagesByUserStatus(packages, showDiscountPrice);
            log.debug("用户{}套餐过滤: 原始套餐数={}, 过滤后套餐数={}, 是否未支付新用户={}",
                    userId, packages.size(), filteredPackages.size(), showDiscountPrice);

            // 转换为前端格式（使用汇率服务进行价格转换）
            List<FrontendPackageVO> packageVOs = VOConverter.toFrontendPackageVOList(filteredPackages, currency, exchangeRateService, showDiscountPrice);

            return Result.success(packageVOs);
        } catch (Exception e) {
            log.error("获取套餐列表失败，货币: {}", currency, e);
            return Result.failed("获取套餐列表失败");
        }
    }

    /**
     * 获取套餐信息和支付方式 - 前端API兼容接口
     */
    @PostMapping("/package/info")
    public Result<PackageInfoResponseVO> getPackageInfo(@Valid @RequestBody PackageInfoRequestVO request,
                                                        Authentication authentication) {
        try {
            // 根据套餐ID获取套餐信息
            Package packageEntity = packageService.getPackageByFrontendId(request.getPackageId());
            if (packageEntity == null) {
                return Result.failed("套餐不存在");
            }

            if (packageEntity.getIsEnabled() != 1) {
                return Result.failed("套餐已下架");
            }

            // 获取当前用户ID，用于判断是否显示优惠价格
            Long userId = getCurrentUserId(authentication);
            if (userId == null) {
                log.warn("未登录用户尝试访问套餐信息，packageId: {}", request.getPackageId());
                return Result.unauthorized("请先登录");
            }

            // 检查用户是否从未付费，如果是则显示优惠价格
            boolean showDiscountPrice = userPaymentStatusService.hasNeverPaid(userId);
            log.debug("用户{}套餐信息查询: 从未付费={}, 显示优惠价格={}", userId, showDiscountPrice, showDiscountPrice);

            // 检查用户是否有权限访问该套餐
            Long packageId = packageEntity.getId();
            if (packageId != null) {
                if (showDiscountPrice && packageId.equals(6L)) {
                    // 未支付新用户不能访问ID为6的套餐
                    log.warn("未支付新用户{}尝试访问被限制的套餐ID={}", userId, packageId);
                    return Result.failed("该套餐暂不可用");
                } else if (!showDiscountPrice && packageId.equals(5L)) {
                    // 已支付用户不能访问ID为5的套餐
                    log.warn("已支付用户{}尝试访问被限制的套餐ID={}", userId, packageId);
                    return Result.failed("该套餐暂不可用");
                }
            }

            // 转换为前端格式（使用汇率服务进行价格转换）
            FrontendPackageVO packageVO = VOConverter.toFrontendPackageVO(packageEntity, request.getCurrency(), exchangeRateService, showDiscountPrice);

            // 获取可用支付方式
            List<PaymentMethodVO> paymentMethods = paymentService.getAvailablePaymentMethods();

            // 构建响应
            PackageInfoResponseVO response = new PackageInfoResponseVO(packageVO, paymentMethods);

            log.info("获取套餐信息成功，packageId: {}, 显示优惠价格: {}", request.getPackageId(), showDiscountPrice);
            return Result.success(response, "获取套餐信息成功");
        } catch (Exception e) {
            log.error("获取套餐信息失败，packageId: {}", request.getPackageId(), e);
            return Result.failed("获取套餐信息失败");
        }
    }

    /**
     * 根据类型获取套餐列表
     */
    @GetMapping("/packages/{type}")
    public Result<List<PackageVO>> getPackagesByType(@PathVariable String type, Authentication authentication) {
        try {
            List<Package> packages = packageService.getPackagesByType(type);

            // 获取当前用户ID，用于过滤套餐
            Long userId = getCurrentUserId(authentication);
            if (userId == null) {
                log.warn("未登录用户尝试访问套餐列表，类型: {}", type);
                return Result.unauthorized("请先登录");
            }

            // 检查用户是否从未付费，应用相同的过滤逻辑
            boolean isUnpaidNewUser = userPaymentStatusService.hasNeverPaid(userId);
            List<Package> filteredPackages = filterPackagesByUserStatus(packages, isUnpaidNewUser);
            log.debug("用户{}按类型{}获取套餐: 原始套餐数={}, 过滤后套餐数={}, 是否未支付新用户={}",
                    userId, type, packages.size(), filteredPackages.size(), isUnpaidNewUser);

            List<PackageVO> packageVOs = VOConverter.toPackageVOList(filteredPackages);
            return Result.success(packageVOs);
        } catch (Exception e) {
            log.error("获取套餐列表失败，类型: {}", type, e);
            return Result.failed("获取套餐列表失败");
        }
    }

    /**
     * 激活免费套餐
     */
    @PostMapping("/activate-free")
    public Result<?> activateFreePackage(@Valid @RequestBody FreePackageRequestVO request, Authentication authentication) {
        try {
            Long userId = getCurrentUserId(authentication);
            if (userId == null) {
                return ErrorResponseUtil.unauthorized("User not authenticated");
            }

            request.setUserId(userId);

            boolean success = packageActivationService.activateFreePackage(request);

            if (success) {
                return Result.success(null, "免费套餐激活成功");
            } else {
                return ErrorResponseUtil.paymentCreateFailed("免费套餐激活失败");
            }
        } catch (Exception e) {
            log.error("激活免费套餐失败", e);
            return ErrorResponseUtil.paymentCreateFailed("免费套餐激活失败: " + e.getMessage());
        }
    }

    /**
     * 创建支付订单
     */
    @PostMapping("/create")
    public Result<?> createPayment(@Valid @RequestBody PaymentRequestVO paymentRequest, Authentication authentication) {
        Long userId = getCurrentUserId(authentication);

        LogUtils.logUserOperation(log, userId, "API调用", "创建支付订单接口，套餐ID：{}，支付方式：{}", paymentRequest.getPackageId(), paymentRequest.getPaymentMethod());

        try {
            if (userId == null) {
                LogUtils.logUserWarning(log, null, "API拒绝", "用户未认证");
                return ErrorResponseUtil.unauthorized("User not authenticated");
            }

            paymentRequest.setUserId(userId);

            LogUtils.logUserDebug(log, userId, "支付处理", "开始创建支付订单");

            PaymentResponseVO response = paymentService.createPayment(paymentRequest);

            if (response.getSuccess()) {
                LogUtils.logUserOperation(log, userId, "API响应", "支付订单创建成功，订单号：{}", response.getOrderNo());
                return Result.success(response, "支付订单创建成功");
            } else {
                LogUtils.logUserWarning(log, userId, "API响应", "支付订单创建失败，错误：{}", response.getErrorMessage());
                return ErrorResponseUtil.paymentCreateFailed(response.getErrorMessage());
            }
        } catch (Exception e) {
            LogUtils.logUserError(log, userId, "API异常", "创建支付订单失败，错误：{}", e, e.getMessage());

            // 特殊处理重放攻击错误
            if (e.getMessage() != null && e.getMessage().contains("重复的支付请求")) {
                return new Result<>(4001, "重复的支付请求，请勿重复提交");
            }

            // 特殊处理支付频率限制错误
            if (e.getMessage() != null && e.getMessage().contains("支付频率过高")) {
                return new Result<>(4002, "支付频率过高，请稍后再试");
            }

            // 特殊处理并发支付错误
            if (e.getMessage() != null && e.getMessage().contains("当前有正在进行的支付")) {
                return new Result<>(4003, "当前有正在进行的支付，请稍后再试");
            }

            return ErrorResponseUtil.paymentCreateFailed("Payment creation failed. Please try again later.");
        }
    }

    /**
     * 查询支付状态 - 返回简化格式
     */
    @GetMapping("/status/{orderNo}")
    public Result<?> getPaymentStatus(@PathVariable String orderNo) {
        LogUtils.logUserOperation(log, null, "API调用", "查询支付状态接口，订单号：{}", orderNo);

        try {
            LogUtils.logUserDebug(log, null, "支付查询", "开始查询支付状态，订单号：{}", orderNo);

            UserOrder order = paymentService.queryPaymentStatus(orderNo);
            if (order == null) {
                LogUtils.logUserWarning(log, null, "API响应", "未找到订单，订单号：{}", orderNo);
                return ErrorResponseUtil.orderNotFound();
            }

            LogUtils.logUserDebug(log, order.getUserId(), "支付查询", "找到订单，订单号：{}，状态：{}", orderNo, order.getPaymentStatus());

            // 构建简化的状态响应
            PaymentStatusResponseVO response = new PaymentStatusResponseVO();
            response.setOrderNo(orderNo);
            response.setStatus(order.getPaymentStatus()); // 保持原始状态码
            response.setMessage(getPaymentStatusMessage(order.getPaymentStatus()));

            LogUtils.logUserOperation(log, order.getUserId(), "API响应", "查询支付状态成功，订单号：{}，状态：{}", orderNo, response.getMessage());
            return Result.success(response);
        } catch (Exception e) {
            LogUtils.logUserError(log, null, "API异常", "查询支付状态失败，订单号：{}，错误：{}", e, orderNo, e.getMessage());
            return ErrorResponseUtil.paymentStatusQueryFailed();
        }
    }

    /**
     * 获取支付状态描述信息
     */
    private String getPaymentStatusMessage(Integer paymentStatus) {
        if (paymentStatus == null) {
            return "状态未知";
        }
        switch (paymentStatus) {
            case 0:
                return "待支付";
            case 1:
                return "支付成功";
            case 2:
                return "已取消";
            case 3:
                return "已退款";
            default:
                return "状态未知";
        }
    }

    /**
     * 获取订单详细信息 - 返回完整格式
     */
    @GetMapping("/order/{orderNo}")
    public Result<UserOrderVO> getOrderDetail(@PathVariable String orderNo) {
        try {
            UserOrder order = paymentService.queryPaymentStatus(orderNo);
            if (order == null) {
                return Result.failed("订单不存在");
            }

            // 获取套餐信息
            Package packageInfo = packageService.getPackageById(order.getPackageId());
            UserOrderVO orderVO = VOConverter.toUserOrderVO(order, packageInfo);

            return Result.success(orderVO);
        } catch (Exception e) {
            log.error("获取订单详情失败，订单号: {}", orderNo, e);
            return Result.failed("获取订单详情失败");
        }
    }

    /**
     * 获取用户订单列表
     */
    @GetMapping("/orders")
    public Result<List<UserOrderVO>> getUserOrders(@RequestParam(defaultValue = "1") Long userId) {
        try {
            List<UserOrder> orders = orderService.getOrdersByUserId(userId);
            List<UserOrderVO> orderVOs = VOConverter.toUserOrderVOList(orders);
            return Result.success(orderVOs);
        } catch (Exception e) {
            log.error("获取用户订单列表失败，用户ID: {}", userId, e);
            return Result.failed("获取订单列表失败");
        }
    }

    /**
     * 取消支付订单
     */
    @PostMapping("/cancel/{orderNo}")
    public Result<String> cancelPayment(@PathVariable String orderNo) {
        try {
            boolean success = paymentService.cancelPayment(orderNo);
            if (success) {
                return Result.success("订单取消成功");
            } else {
                return Result.failed("订单取消失败");
            }
        } catch (Exception e) {
            log.error("取消支付订单失败，订单号: {}", orderNo, e);
            return Result.failed("取消订单失败: " + e.getMessage());
        }
    }

    /**
     * 易支付统一回调 - 支持GET和POST请求
     * 处理易支付微信和支付宝的回调通知
     * 采用快速响应 + 异步处理策略，避免重试风暴
     */
    @RequestMapping(value = "/yipay/notify", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> yipayNotify(HttpServletRequest request) {
        try {
            log.info("收到易支付回调通知，请求方法: {}", request.getMethod());

            // 记录原始请求
            if ("GET".equalsIgnoreCase(request.getMethod())) {
                String queryString = request.getQueryString();
                log.info("易支付回调原始请求 URL: {}{}", request.getRequestURL(), (queryString != null ? "?" + queryString : ""));
            }

            // 获取所有参数
            Map<String, String> notifyParams = new HashMap<>();
            request.getParameterMap().forEach((key, values) -> {
                if (values != null && values.length > 0) {
                    notifyParams.put(key, values[0]);
                }
            });

            log.info("易支付回调参数: {}", notifyParams);

            // 立即返回success，避免易支付重试，然后异步处理
            paymentService.processPaymentCallbackAsync(notifyParams);

            log.info("易支付回调已提交异步处理");
            return ResponseEntity.ok("success");

        } catch (Exception e) {
            log.error("处理易支付回调异常", e);
            // 即使异常也返回success，避免重试风暴
            return ResponseEntity.ok("success");
        }
    }

    /**
     * 查询支付状态并同步最新状态
     */
    @GetMapping("/status/sync/{orderNo}")
    public Result<PaymentStatusResponseVO> getPaymentStatusWithSync(@PathVariable String orderNo) {
        try {
            PaymentStatusResponseVO response = paymentService.queryAndSyncPaymentStatus(orderNo);
            return Result.success(response, "查询支付状态成功");
        } catch (Exception e) {
            log.error("查询并同步支付状态失败，订单号: {}", orderNo, e);
            return Result.failed("查询支付状态失败");
        }
    }

    /**
     * 手动同步单个订单状态
     */
    @PostMapping("/sync/{orderNo}")
    public Result<String> syncOrderStatus(@PathVariable String orderNo) {
        try {
            boolean success = paymentSyncService.syncOrderStatus(orderNo);
            if (success) {
                return Result.success("订单状态同步成功");
            } else {
                return Result.failed("订单状态同步失败");
            }
        } catch (Exception e) {
            log.error("同步订单状态失败，订单号: {}", orderNo, e);
            return Result.failed("同步订单状态失败: " + e.getMessage());
        }
    }

    /**
     * 手动触发批量状态同步
     */
    @PostMapping("/sync/batch")
    public Result<Map<String, Object>> batchSyncOrders(@RequestParam(defaultValue = "50") int maxCount) {
        try {
            int syncCount = paymentSyncService.syncPendingOrders(maxCount);

            Map<String, Object> result = new HashMap<>();
            result.put("syncCount", syncCount);
            result.put("maxCount", maxCount);
            result.put("message", "批量同步完成");

            return Result.success(result, "批量同步完成，同步订单数: " + syncCount);
        } catch (Exception e) {
            log.error("批量同步订单状态失败", e);
            return Result.failed("批量同步失败: " + e.getMessage());
        }
    }

    /**
     * 手动触发全量状态同步
     */
    @PostMapping("/sync/all")
    public Result<PaymentSyncService.SyncResult> fullSyncOrders() {
        try {
            PaymentSyncService.SyncResult result = paymentSyncService.manualSyncAll();
            return Result.success(result, "全量同步完成");
        } catch (Exception e) {
            log.error("全量同步订单状态失败", e);
            return Result.failed("全量同步失败: " + e.getMessage());
        }
    }

    /**
     * 手动同步单个订单状态（管理员接口）
     */
    @PostMapping("/admin/sync/order/{orderNo}")
    public Result<Boolean> syncSingleOrder(@PathVariable String orderNo) {
        try {
            log.info("手动同步订单状态，订单号: {}", orderNo);
            boolean result = paymentStatusSyncService.syncOrderStatus(orderNo);
            return Result.success(result, result ? "同步成功" : "同步失败");
        } catch (Exception e) {
            log.error("手动同步订单状态失败，订单号: {}", orderNo, e);
            return Result.failed("同步失败: " + e.getMessage());
        }
    }

    /**
     * Stripe Webhook回调处理端点
     * 处理Stripe支付事件通知
     * 采用快速响应 + 异步处理策略，避免重试风暴
     */
    @PostMapping("/stripe/webhook")
    public ResponseEntity<String> stripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature) {
        
        try {
            log.info("收到Stripe Webhook事件，签名: {}", signature);
            log.debug("Stripe Webhook载荷长度: {}", payload != null ? payload.length() : 0);

            // 验证签名并解析事件
            com.stripe.model.Event event = stripeService.handleWebhookEvent(payload, signature);

            // 异步处理事件，立即返回成功
            processStripeWebhookAsync(event, payload);

            log.info("Stripe Webhook已提交异步处理，事件ID: {}", event.getId());
            return ResponseEntity.ok("success");

        } catch (Exception e) {
            log.error("处理Stripe Webhook异常", e);
            // 即使异常也返回success，避免Stripe重试风暴
            return ResponseEntity.ok("success");
        }
    }

    /**
     * 异步处理Stripe Webhook事件
     */
    private void processStripeWebhookAsync(com.stripe.model.Event event, String payload) {
        log.debug("开始提交Stripe Webhook异步处理，事件ID: {}", event.getId());
        
        // 使用CompletableFuture异步处理，避免阻塞回调响应
        java.util.concurrent.Executor executor = getStripeWebhookExecutor();
        log.debug("使用的线程池类型: {}", executor.getClass().getSimpleName());
        
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            processStripeWebhookInternal(event, payload);
        }, executor).exceptionally(throwable -> {
            log.error("异步处理Stripe Webhook异常，事件ID: {}", event.getId(), throwable);
            return null;
        });
        
        log.debug("Stripe Webhook异步任务已提交，事件ID: {}", event.getId());
    }

    /**
     * 内部异步处理Stripe Webhook逻辑
     */
    private void processStripeWebhookInternal(com.stripe.model.Event event, String payload) {
        String eventId = event.getId();
        String eventType = event.getType();
        
        try {
            log.info("开始异步处理Stripe Webhook事件，ID: {}, 类型: {}", eventId, eventType);

            // 使用支付策略处理回调
            com.instser.chat.strategy.PaymentStrategy stripeStrategy = 
                paymentService.getPaymentStrategy("stripe");
            
            if (stripeStrategy != null) {
                boolean success = stripeStrategy.handleCallback(payload);
                
                if (success) {
                    log.info("异步处理Stripe Webhook成功，事件ID: {}", eventId);
                } else {
                    log.error("异步处理Stripe Webhook失败，事件ID: {}", eventId);
                }
            } else {
                log.error("未找到Stripe支付策略，无法处理Webhook事件");
            }

        } catch (Exception e) {
            log.error("异步处理Stripe Webhook异常，事件ID: {}", eventId, e);
        }
    }

    /**
     * PayPal Webhook回调处理端点
     * 处理PayPal支付事件通知
     * 采用快速响应 + 异步处理策略，避免重试风暴
     */
    @PostMapping("/paypal/webhook")
    public ResponseEntity<String> paypalWebhook(
            @RequestBody String payload,
            HttpServletRequest request) {
        
        try {
            log.info("收到PayPal Webhook事件");
            log.debug("PayPal Webhook载荷长度: {}", payload != null ? payload.length() : 0);

            // 获取请求头用于签名验证
            Map<String, String> headers = new HashMap<>();
            java.util.Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                if (headerName.toLowerCase().startsWith("paypal-")) {
                    headers.put(headerName.toLowerCase(), request.getHeader(headerName));
                }
            }

            // 异步处理事件，立即返回成功
            processPayPalWebhookAsync(payload, headers);

            log.info("PayPal Webhook已提交异步处理");
            return ResponseEntity.ok("success");

        } catch (Exception e) {
            log.error("处理PayPal Webhook异常", e);
            // 即使异常也返回success，避免PayPal重试风暴
            return ResponseEntity.ok("success");
        }
    }

    /**
     * 异步处理PayPal Webhook事件
     */
    private void processPayPalWebhookAsync(String payload, Map<String, String> headers) {
        log.debug("开始提交PayPal Webhook异步处理");
        
        // 使用CompletableFuture异步处理，避免阻塞回调响应
        java.util.concurrent.Executor executor = getPayPalWebhookExecutor();
        log.debug("使用的线程池类型: {}", executor.getClass().getSimpleName());
        
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            processPayPalWebhookInternal(payload, headers);
        }, executor).exceptionally(throwable -> {
            log.error("异步处理PayPal Webhook异常", throwable);
            return null;
        });
        
        log.debug("PayPal Webhook异步任务已提交");
    }

    /**
     * 内部异步处理PayPal Webhook逻辑
     */
    private void processPayPalWebhookInternal(String payload, Map<String, String> headers) {
        try {
            log.info("开始异步处理PayPal Webhook事件");

            // 使用支付策略处理回调
            com.instser.chat.strategy.PaymentStrategy paypalStrategy = 
                paymentService.getPaymentStrategy("paypal");
            
            if (paypalStrategy != null) {
                boolean success = paypalStrategy.handleCallback(payload);
                
                if (success) {
                    log.info("异步处理PayPal Webhook成功");
                } else {
                    log.error("异步处理PayPal Webhook失败");
                }
            } else {
                log.error("未找到PayPal支付策略，无法处理Webhook事件");
            }

        } catch (Exception e) {
            log.error("异步处理PayPal Webhook异常", e);
        }
    }

    /**
     * 获取PayPal Webhook处理线程池
     */
    private java.util.concurrent.Executor getPayPalWebhookExecutor() {
        try {
            ApplicationContext context = com.instser.chat.util.ApplicationContextProvider.getApplicationContext();
            if (context != null) {
                // 复用Stripe的webhook线程池，或者创建专用的PayPal线程池
                return (java.util.concurrent.Executor) context.getBean("stripeWebhookExecutor");
            }
            log.warn("ApplicationContext为null，使用默认线程池");
            return java.util.concurrent.ForkJoinPool.commonPool();
        } catch (Exception e) {
            log.error("获取PayPal Webhook线程池失败，使用默认线程池: {}", e.getMessage(), e);
            return java.util.concurrent.ForkJoinPool.commonPool();
        }
    }

    /**
     * 获取Stripe Webhook处理线程池
     */
    private java.util.concurrent.Executor getStripeWebhookExecutor() {
        try {
            ApplicationContext context = com.instser.chat.util.ApplicationContextProvider.getApplicationContext();
            if (context != null) {
                return (java.util.concurrent.Executor) context.getBean("stripeWebhookExecutor");
            }
            log.warn("ApplicationContext为null，使用默认线程池");
            return java.util.concurrent.ForkJoinPool.commonPool();
        } catch (Exception e) {
            log.error("获取Stripe Webhook线程池失败，使用默认线程池: {}", e.getMessage(), e);
            return java.util.concurrent.ForkJoinPool.commonPool();
        }
    }

}
