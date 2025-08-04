package com.instser.chat.service.serviceimpl;

import com.instser.chat.entity.UserOrder;
import com.instser.chat.service.OrderExpirationService;
import com.instser.chat.service.OrderService;
import com.instser.chat.service.PaymentAuditService;
import com.instser.chat.service.serviceimpl.PaymentAuditServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 订单过期处理服务实现类
 *
 * @author instser
 * @since 2025-06-29
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderExpirationServiceImpl implements OrderExpirationService {

    private final OrderService orderService;
    private final PaymentAuditService paymentAuditService;

    @Override
    public void handleExpiredOrders() {
        try {
            log.debug("开始处理过期订单");
            
            List<UserOrder> expiredOrders = getExpiredOrders();
            if (expiredOrders.isEmpty()) {
                log.debug("没有发现过期订单");
                return;
            }

            log.info("发现{}个过期订单，开始处理", expiredOrders.size());
            
            int canceledCount = 0;
            for (UserOrder order : expiredOrders) {
                try {
                    if (cancelExpiredOrder(order.getOrderNo())) {
                        canceledCount++;
                        
                        // 发送过期通知
                        sendExpirationNotification(order);
                    }
                } catch (Exception e) {
                    log.error("处理过期订单失败，订单号: {}", order.getOrderNo(), e);
                }
            }
            
            log.info("过期订单处理完成，总数: {}, 成功取消: {}", expiredOrders.size(), canceledCount);
            
        } catch (Exception e) {
            log.error("处理过期订单异常", e);
        }
    }

    @Override
    @Transactional
    public boolean cancelExpiredOrder(String orderNo) {
        try {
            UserOrder order = orderService.getOrderByOrderNo(orderNo);
            if (order == null) {
                log.warn("订单不存在，无法取消: {}", orderNo);
                return false;
            }

            // 只取消未支付的订单
            if (order.getPaymentStatus() != 0) {
                log.debug("订单状态不是未支付，跳过取消: {}, 当前状态: {}", orderNo, order.getPaymentStatus());
                return false;
            }

            // 检查是否真的过期
            if (!isOrderExpired(orderNo)) {
                log.debug("订单未过期，跳过取消: {}", orderNo);
                return false;
            }

            // 更新订单状态为已取消
            boolean success = orderService.updatePaymentStatus(orderNo, 2, null);
            if (success) {
                log.info("过期订单取消成功: {}", orderNo);
                
                // 记录审计日志
                Map<String, Object> detailsMap = Map.of(
                    "reason", "订单过期自动取消",
                    "expireTime", order.getExpireTime().toString(),
                    "cancelTime", LocalDateTime.now().toString()
                );
                String details = ((PaymentAuditServiceImpl) paymentAuditService).createOperationDetails(detailsMap);
                
                paymentAuditService.recordStatusChangeAudit(
                    orderNo, 0, 2, "订单过期自动取消", details
                );
                
                return true;
            } else {
                log.error("过期订单取消失败: {}", orderNo);
                return false;
            }
            
        } catch (Exception e) {
            log.error("取消过期订单异常，订单号: {}", orderNo, e);
            return false;
        }
    }

    @Override
    public int batchCancelExpiredOrders(List<String> orderNos) {
        if (orderNos == null || orderNos.isEmpty()) {
            return 0;
        }

        int successCount = 0;
        for (String orderNo : orderNos) {
            if (cancelExpiredOrder(orderNo)) {
                successCount++;
            }
        }

        log.info("批量取消过期订单完成，总数: {}, 成功: {}", orderNos.size(), successCount);
        return successCount;
    }

    @Override
    public List<UserOrder> getExpiredOrders() {
        try {
            // 查询过期时间小于当前时间且状态为未支付的订单
            return orderService.getExpiredOrders();
        } catch (Exception e) {
            log.error("获取过期订单列表异常", e);
            return List.of();
        }
    }

    @Override
    public boolean isOrderExpired(String orderNo) {
        try {
            UserOrder order = orderService.getOrderByOrderNo(orderNo);
            if (order == null) {
                return false;
            }

            // 检查过期时间
            LocalDateTime expireTime = order.getExpireTime();
            if (expireTime == null) {
                return false;
            }

            return LocalDateTime.now().isAfter(expireTime);
            
        } catch (Exception e) {
            log.error("检查订单是否过期异常，订单号: {}", orderNo, e);
            return false;
        }
    }

    @Override
    public void sendExpirationNotification(UserOrder order) {
        try {
            // 这里可以实现发送邮件、短信或站内消息通知用户订单过期
            // 暂时只记录日志
            log.info("订单过期通知，用户ID: {}, 订单号: {}, 过期时间: {}", 
                    order.getUserId(), order.getOrderNo(), order.getExpireTime());
            
            // TODO: 实现具体的通知逻辑
            // - 发送邮件通知
            // - 发送站内消息
            // - 记录通知历史
            
        } catch (Exception e) {
            log.error("发送订单过期通知异常，订单号: {}", order.getOrderNo(), e);
        }
    }

    @Override
    public int archiveExpiredOrders(int daysBeforeArchive) {
        try {
            log.info("开始归档过期订单，归档{}天前的订单", daysBeforeArchive);
            
            LocalDateTime archiveTime = LocalDateTime.now().minusDays(daysBeforeArchive);
            
            // TODO: 需要在OrderService中实现getOrdersForArchive方法
            // 查询需要归档的订单
            // List<UserOrder> ordersToArchive = orderService.getOrdersForArchive(archiveTime);
            List<UserOrder> ordersToArchive = List.of(); // 暂时返回空列表
            
            if (ordersToArchive.isEmpty()) {
                log.info("没有需要归档的过期订单");
                return 0;
            }

            int archivedCount = 0;
            for (UserOrder order : ordersToArchive) {
                try {
                    // 这里可以实现将订单数据移动到归档表的逻辑
                    // 暂时只记录日志
                    log.debug("归档订单: {}, 创建时间: {}", order.getOrderNo(), order.getCreateTime());
                    archivedCount++;
                    
                    // 记录审计日志
                    Map<String, Object> archiveDetailsMap = Map.of(
                        "reason", "过期订单自动归档",
                        "archiveTime", LocalDateTime.now().toString(),
                        "originalCreateTime", order.getCreateTime().toString()
                    );
                    String details = ((PaymentAuditServiceImpl) paymentAuditService).createOperationDetails(archiveDetailsMap);
                    
                    paymentAuditService.recordAudit(
                        order.getOrderNo(), "ARCHIVE", order.getPaymentStatus(), order.getPaymentStatus(),
                        null, PaymentAuditService.OperatorType.SYSTEM, "过期订单自动归档", details,
                        null, null
                    );
                    
                } catch (Exception e) {
                    log.error("归档订单失败，订单号: {}", order.getOrderNo(), e);
                }
            }
            
            log.info("过期订单归档完成，总数: {}, 成功: {}", ordersToArchive.size(), archivedCount);
            return archivedCount;
            
        } catch (Exception e) {
            log.error("归档过期订单异常", e);
            return 0;
        }
    }


}
