package com.instser.chat.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.instser.chat.admin.audit.AdminAuditPublisher;
import com.instser.chat.admin.common.AdminErrorCodes;
import com.instser.chat.admin.common.PageResult;
import com.instser.chat.admin.dto.AdminOrderSyncRequest;
import com.instser.chat.admin.dto.AdminReasonRequest;
import com.instser.chat.admin.query.AdminOrderQuery;
import com.instser.chat.admin.vo.AdminOrderVO;
import com.instser.chat.common.result.Result;
import com.instser.chat.entity.PaymentAudit;
import com.instser.chat.entity.User;
import com.instser.chat.entity.UserOrder;
import com.instser.chat.service.OrderService;
import com.instser.chat.service.PaymentAnalyticsService;
import com.instser.chat.service.PaymentAuditService;
import com.instser.chat.service.PaymentSyncService;
import com.instser.chat.service.UserService;
import com.instser.chat.vo.PaymentAnalyticsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private final OrderService orderService;
    private final UserService userService;
    private final PaymentSyncService paymentSyncService;
    private final PaymentAuditService paymentAuditService;
    private final PaymentAnalyticsService paymentAnalyticsService;
    private final AdminAuditPublisher adminAuditPublisher;

    public PageResult<AdminOrderVO> listOrders(AdminOrderQuery query) {
        Page<UserOrder> page = new Page<>(query.safePage(), query.safeSize());
        LambdaQueryWrapper<UserOrder> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getOrderNo())) {
            wrapper.eq(UserOrder::getOrderNo, query.getOrderNo().trim());
        }
        if (query.getUserId() != null) {
            wrapper.eq(UserOrder::getUserId, query.getUserId());
        }
        if (query.getPaymentStatus() != null) {
            wrapper.eq(UserOrder::getPaymentStatus, query.getPaymentStatus());
        }
        if (StringUtils.hasText(query.getPaymentMethod())) {
            wrapper.eq(UserOrder::getPaymentMethod, query.getPaymentMethod().trim());
        }
        if (query.getPackageId() != null) {
            wrapper.eq(UserOrder::getPackageId, query.getPackageId());
        }
        if (query.getCreatedFrom() != null) {
            wrapper.ge(UserOrder::getCreateTime, query.getCreatedFrom());
        }
        if (query.getCreatedTo() != null) {
            wrapper.le(UserOrder::getCreateTime, query.getCreatedTo());
        }
        wrapper.orderByDesc(UserOrder::getCreateTime);

        Page<UserOrder> result = orderService.page(page, wrapper);
        return PageResult.of(result, this::toVo);
    }

    public Map<String, Object> getOrderDetail(String orderNo) {
        UserOrder order = requireOrder(orderNo);
        List<PaymentAudit> audits = paymentAuditService.getAuditByOrderNo(orderNo);
        Map<String, Object> data = new HashMap<>();
        data.put("order", toVo(order));
        data.put("paymentAudits", audits);
        return data;
    }

    public Result<Map<String, Object>> syncOrder(String orderNo, AdminReasonRequest request) {
        requireOrder(orderNo);
        boolean ok = paymentSyncService.syncOrderStatus(orderNo);
        Map<String, Object> data = new HashMap<>();
        data.put("orderNo", orderNo);
        data.put("synced", ok);
        data.put("order", toVo(orderService.getOrderByOrderNo(orderNo)));
        if (ok) {
            adminAuditPublisher.success("ORDER_SYNC", orderNo, "reason=" + request.getReason());
            return Result.success(data, "订单同步成功");
        }
        adminAuditPublisher.failed("ORDER_SYNC", orderNo, "reason=" + request.getReason());
        return new Result<>(AdminErrorCodes.ORDER_STATE_FORBIDDEN, "订单同步失败或状态未变化", data);
    }

    public Result<Map<String, Object>> batchSync(AdminOrderSyncRequest request) {
        int maxCount = request.getMaxCount() == null ? 50 : request.getMaxCount();
        String jobId = UUID.randomUUID().toString().replace("-", "");
        int success = paymentSyncService.syncPendingOrders(maxCount);
        Map<String, Object> data = new HashMap<>();
        data.put("jobId", jobId);
        data.put("maxCount", maxCount);
        data.put("successCount", success);
        data.put("status", "COMPLETED");
        adminAuditPublisher.success("ORDER_BATCH_SYNC", jobId,
                "maxCount=" + maxCount + ",success=" + success + ",reason=" + request.getReason());
        return Result.success(data, "批量同步已完成");
    }

    public Result<Map<String, Object>> cancelOrder(String orderNo, AdminReasonRequest request) {
        UserOrder order = requireOrder(orderNo);
        if (order.getPaymentStatus() != null && order.getPaymentStatus() != 0) {
            adminAuditPublisher.failed("ORDER_CANCEL", orderNo, "status=" + order.getPaymentStatus());
            return new Result<>(AdminErrorCodes.ORDER_STATE_FORBIDDEN, "仅未支付订单可取消");
        }
        boolean ok = orderService.cancelOrder(orderNo);
        if (!ok) {
            adminAuditPublisher.failed("ORDER_CANCEL", orderNo, request.getReason());
            return new Result<>(AdminErrorCodes.ORDER_STATE_FORBIDDEN, "取消订单失败");
        }
        adminAuditPublisher.success("ORDER_CANCEL", orderNo, "reason=" + request.getReason());
        Map<String, Object> data = new HashMap<>();
        data.put("order", toVo(orderService.getOrderByOrderNo(orderNo)));
        return Result.success(data, "订单已取消");
    }

    public PaymentAnalyticsVO analytics(LocalDate startDate, LocalDate endDate) {
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDate start = startDate != null ? startDate : end.minusDays(30);
        return paymentAnalyticsService.getPaymentOverview(start, end);
    }

    private UserOrder requireOrder(String orderNo) {
        UserOrder order = orderService.getOrderByOrderNo(orderNo);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在: " + orderNo);
        }
        return order;
    }

    private AdminOrderVO toVo(UserOrder order) {
        String username = null;
        if (order.getUserId() != null) {
            User user = userService.getById(order.getUserId());
            if (user != null) {
                username = user.getUsername();
            }
        }
        return AdminOrderVO.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .userId(order.getUserId())
                .username(username)
                .packageId(order.getPackageId())
                .amount(order.getAmount())
                .currency(order.getCurrency())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .paymentTime(order.getPaymentTime())
                .transactionId(order.getTransactionId())
                .expireTime(order.getExpireTime())
                .remark(order.getRemark())
                .createTime(order.getCreateTime())
                .updateTime(order.getUpdateTime())
                .build();
    }
}
