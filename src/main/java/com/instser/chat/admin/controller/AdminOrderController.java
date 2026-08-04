package com.instser.chat.admin.controller;

import com.instser.chat.admin.common.PageResult;
import com.instser.chat.admin.dto.AdminOrderSyncRequest;
import com.instser.chat.admin.dto.AdminReasonRequest;
import com.instser.chat.admin.query.AdminOrderQuery;
import com.instser.chat.admin.service.AdminOrderService;
import com.instser.chat.admin.vo.AdminOrderVO;
import com.instser.chat.common.result.Result;
import com.instser.chat.vo.PaymentAnalyticsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理员-订单", description = "订单查询与同步")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    @GetMapping("/admin/orders")
    @Operation(summary = "订单分页查询")
    public Result<PageResult<AdminOrderVO>> list(AdminOrderQuery query) {
        return Result.success(adminOrderService.listOrders(query));
    }

    @GetMapping("/admin/orders/{orderNo}")
    @Operation(summary = "订单详情与支付审计")
    public Result<Map<String, Object>> detail(@PathVariable String orderNo) {
        return Result.success(adminOrderService.getOrderDetail(orderNo));
    }

    @PostMapping("/admin/orders/{orderNo}/sync")
    @Operation(summary = "同步单个订单")
    public Result<Map<String, Object>> sync(@PathVariable String orderNo,
                                            @Valid @RequestBody AdminReasonRequest request) {
        return adminOrderService.syncOrder(orderNo, request);
    }

    @PostMapping("/admin/orders/sync")
    @Operation(summary = "受限批量同步待支付订单")
    public Result<Map<String, Object>> batchSync(@Valid @RequestBody AdminOrderSyncRequest request) {
        return adminOrderService.batchSync(request);
    }

    @PostMapping("/admin/orders/{orderNo}/cancel")
    @Operation(summary = "取消未支付订单")
    public Result<Map<String, Object>> cancel(@PathVariable String orderNo,
                                              @Valid @RequestBody AdminReasonRequest request) {
        return adminOrderService.cancelOrder(orderNo, request);
    }

    @GetMapping("/admin/payment/analytics")
    @Operation(summary = "支付分析概览")
    public Result<PaymentAnalyticsVO> analytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(adminOrderService.analytics(startDate, endDate));
    }
}
