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
import com.instser.chat.service.PaymentSecurityEnhancer;
import com.instser.chat.service.PaymentSyncService;

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
    private final PaymentSecurityEnhancer paymentSecurityEnhancer;
    private final ExchangeRateService exchangeRateService;

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
    public Result<List<FrontendPackageVO>> getPackages(@RequestParam(defaultValue = "USD") String currency) {
        try {
            List<Package> packages = packageService.getEnabledPackages();
            // 转换为前端格式（使用汇率服务进行价格转换）
            List<FrontendPackageVO> packageVOs = VOConverter.toFrontendPackageVOList(packages, currency, exchangeRateService);

            log.info("获取套餐列表成功，货币: {}, 套餐数量: {}", currency, packageVOs.size());
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
    public Result<PackageInfoResponseVO> getPackageInfo(@Valid @RequestBody PackageInfoRequestVO request) {
        try {
            // 根据套餐ID获取套餐信息
            Package packageEntity = packageService.getPackageByFrontendId(request.getPackageId());
            if (packageEntity == null) {
                return Result.failed("套餐不存在");
            }

            if (packageEntity.getIsEnabled() != 1) {
                return Result.failed("套餐已下架");
            }

            // 转换为前端格式（使用汇率服务进行价格转换）
            FrontendPackageVO packageVO = VOConverter.toFrontendPackageVO(packageEntity, request.getCurrency(), exchangeRateService);

            // 获取可用支付方式
            List<PaymentMethodVO> paymentMethods = paymentService.getAvailablePaymentMethods();

            // 构建响应
            PackageInfoResponseVO response = new PackageInfoResponseVO(packageVO, paymentMethods);

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
    public Result<List<PackageVO>> getPackagesByType(@PathVariable String type) {
        try {
            List<Package> packages = packageService.getPackagesByType(type);
            List<PackageVO> packageVOs = VOConverter.toPackageVOList(packages);
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

        log.info("【API调用】创建支付订单接口，用户ID：{}，套餐ID：{}，支付方式：{}",
            userId, paymentRequest.getPackageId(), paymentRequest.getPaymentMethod());

        try {
            if (userId == null) {
                log.warn("【API拒绝】用户未认证");
                return ErrorResponseUtil.unauthorized("User not authenticated");
            }

            paymentRequest.setUserId(userId);

            log.debug("【支付处理】开始创建支付订单，用户ID：{}", userId);

            PaymentResponseVO response = paymentService.createPayment(paymentRequest);

            if (response.getSuccess()) {
                log.info("【API响应】支付订单创建成功，用户ID：{}，订单号：{}", userId, response.getOrderNo());
                return Result.success(response, "支付订单创建成功");
            } else {
                log.warn("【API响应】支付订单创建失败，用户ID：{}，错误：{}", userId, response.getErrorMessage());
                return ErrorResponseUtil.paymentCreateFailed(response.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("【API异常】创建支付订单失败，用户ID：{}，错误：{}", userId, e.getMessage(), e);

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
        log.info("【API调用】查询支付状态接口，订单号：{}", orderNo);

        try {
            log.debug("【支付查询】开始查询支付状态，订单号：{}", orderNo);

            UserOrder order = paymentService.queryPaymentStatus(orderNo);
            if (order == null) {
                log.warn("【API响应】未找到订单，订单号：{}", orderNo);
                return ErrorResponseUtil.orderNotFound();
            }

            log.debug("【支付查询】找到订单，订单号：{}，状态：{}", orderNo, order.getPaymentStatus());

            // 构建简化的状态响应
            PaymentStatusResponseVO response = new PaymentStatusResponseVO();
            response.setOrderNo(orderNo);
            response.setStatus(order.getPaymentStatus()); // 保持原始状态码
            response.setMessage(getPaymentStatusMessage(order.getPaymentStatus()));

            log.info("【API响应】查询支付状态成功，订单号：{}，状态：{}", orderNo, response.getMessage());
            return Result.success(response);
        } catch (Exception e) {
            log.error("【API异常】查询支付状态失败，订单号：{}，错误：{}", orderNo, e.getMessage(), e);
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
     * 支付宝支付回调（通过易支付）
     */
    @PostMapping("/alipay/notify")
    public ResponseEntity<String> alipayNotify(HttpServletRequest request) {
        try {
            log.info("收到支付宝回调通知");

            // 验证回调IP
            if (!paymentSecurityEnhancer.validateCallbackIP(request, "yipay")) {
                log.error("支付宝回调IP验证失败");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("IP not allowed");
            }

            // 获取所有参数
            Map<String, String> notifyParams = new HashMap<>();
            request.getParameterMap().forEach((key, values) -> {
                if (values != null && values.length > 0) {
                    notifyParams.put(key, values[0]);
                }
            });

            log.info("支付宝回调参数: {}", notifyParams);

            // 处理支付宝回调
            boolean success = paymentService.handleAlipayNotify(notifyParams);

            if (success) {
                log.info("支付宝回调处理成功");
                return ResponseEntity.ok("success");
            } else {
                log.error("支付宝回调处理失败");
                return ResponseEntity.ok("fail");
            }
        } catch (Exception e) {
            log.error("处理支付宝回调异常", e);
            return ResponseEntity.ok("fail");
        }
    }

    /**
     * 微信支付回调（通过易支付）
     */
    @PostMapping("/wechatpay/notify")
    public ResponseEntity<String> wechatpayNotify(HttpServletRequest request) {
        try {
            log.info("收到微信支付回调通知");

            // 验证回调IP
            if (!paymentSecurityEnhancer.validateCallbackIP(request, "yipay")) {
                log.error("微信支付回调IP验证失败");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("IP not allowed");
            }

            // 获取所有参数
            Map<String, String> notifyParams = new HashMap<>();
            request.getParameterMap().forEach((key, values) -> {
                if (values != null && values.length > 0) {
                    notifyParams.put(key, values[0]);
                }
            });

            log.info("微信支付回调参数: {}", notifyParams);

            // 处理微信支付回调
            boolean success = paymentService.handleWechatPayNotify(notifyParams);

            if (success) {
                log.info("微信支付回调处理成功");
                return ResponseEntity.ok("success");
            } else {
                log.error("微信支付回调处理失败");
                return ResponseEntity.ok("fail");
            }

        } catch (Exception e) {
            log.error("处理微信支付回调异常", e);
            return ResponseEntity.ok("fail");
        }
    }

    /**
     * 易支付回调
     */
    @PostMapping("/yipay/notify")
    public ResponseEntity<String> yipayNotify(HttpServletRequest request) {
        try {
            log.info("收到易支付回调通知");

            // 验证回调IP
            if (!paymentSecurityEnhancer.validateCallbackIP(request, "yipay")) {
                log.error("易支付回调IP验证失败");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("IP not allowed");
            }

            // 获取所有参数
            Map<String, String> notifyParams = new HashMap<>();
            request.getParameterMap().forEach((key, values) -> {
                if (values != null && values.length > 0) {
                    notifyParams.put(key, values[0]);
                }
            });

            log.info("易支付回调参数: {}", notifyParams);

            // 处理易支付回调
            boolean success = paymentService.handleYiPayNotify(notifyParams);

            if (success) {
                log.info("易支付回调处理成功");
                return ResponseEntity.ok("success");
            } else {
                log.error("易支付回调处理失败");
                return ResponseEntity.ok("fail");
            }

        } catch (Exception e) {
            log.error("处理易支付回调异常", e);
            return ResponseEntity.ok("fail");
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

}
