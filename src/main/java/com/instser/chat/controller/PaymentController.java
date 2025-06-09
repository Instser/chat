package com.instser.chat.controller;

import com.instser.chat.common.result.Result;

import com.instser.chat.entity.Package;
import com.instser.chat.entity.UserOrder;
import com.instser.chat.service.OrderService;
import com.instser.chat.service.PackageService;
import com.instser.chat.service.PaymentService;

import com.instser.chat.vo.*;
import com.instser.chat.vo.converter.VOConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Validated
@CrossOrigin(originPatterns = "*", allowedHeaders = "*", allowCredentials = "true")
public class PaymentController {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final PackageService packageService;

    /**
     * 获取套餐列表
     */
    @GetMapping("/packages")
    public Result<List<PackageVO>> getPackages() {
        try {
            List<Package> packages = packageService.getEnabledPackages();
            List<PackageVO> packageVOs = VOConverter.toPackageVOList(packages);
            return Result.success(packageVOs);
        } catch (Exception e) {
            log.error("获取套餐列表失败", e);
            return Result.failed("获取套餐列表失败");
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
     * 创建支付订单
     */
    @PostMapping("/create")
    public Result<PaymentResponseVO> createPayment(@Valid @RequestBody PaymentRequestVO paymentRequest) {
        try {
            // 暂时使用固定用户ID，后续从JWT中获取
            paymentRequest.setUserId(1L);
            
            PaymentResponseVO response = paymentService.createPayment(paymentRequest);
            
            if (response.getSuccess()) {
                return Result.success(response, "支付订单创建成功");
            } else {
                return Result.failed(response.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("创建支付订单失败", e);
            return Result.failed("创建支付订单失败: " + e.getMessage());
        }
    }

    /**
     * 查询支付状态
     */
    @GetMapping("/status/{orderNo}")
    public Result<UserOrderVO> getPaymentStatus(@PathVariable String orderNo) {
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
            log.error("查询支付状态失败，订单号: {}", orderNo, e);
            return Result.failed("查询支付状态失败");
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
     * 支付宝支付回调
     */
    @PostMapping("/alipay/notify")
    public ResponseEntity<String> alipayNotify(HttpServletRequest request, @RequestBody String notifyBody) {
        try {
            log.info("收到支付宝回调通知");

            // 获取请求头信息
            String requestTime = request.getHeader("request-time");
            String clientId = request.getHeader("client-id");
            String signature = request.getHeader("signature");

            // 处理回调
            boolean success = paymentService.handlePaymentNotify(notifyBody, signature, requestTime, clientId);

            if (success) {
                log.info("支付回调处理成功");
                return ResponseEntity.ok("SUCCESS");
            } else {
                log.error("支付回调处理失败");
                return ResponseEntity.ok("FAIL");
            }
        } catch (Exception e) {
            log.error("处理支付回调异常", e);
            return ResponseEntity.ok("FAIL");
        }
    }

    /**
     * 易支付回调
     */
    @PostMapping("/yipay/notify")
    public ResponseEntity<String> yipayNotify(HttpServletRequest request) {
        try {
            log.info("收到易支付回调通知");

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


}
