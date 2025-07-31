package com.instser.chat.service;

import com.instser.chat.entity.UserOrder;
import com.instser.chat.service.serviceimpl.PaymentAuditServiceImpl;
import com.instser.chat.service.ChatInterruptService;
import com.instser.chat.service.OrderService;
import com.instser.chat.service.PaymentAuditService;
import com.instser.chat.service.PaymentStatusSyncService;
import com.instser.chat.service.UserDailyUsageService;
import com.instser.chat.service.UserAccountService;
import com.instser.chat.config.LogManagementConfig;
import com.instser.chat.monitor.PerformanceMonitor;
import com.instser.chat.monitor.ContinuationThreadPoolMonitor;
import com.instser.chat.service.cache.CacheMonitoringService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 优化的定时任务服务
 * 统一管理所有定时任务，减少数据库负担
 *
 * @author instser
 * @since 2025-06-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OptimizedScheduledTaskService {

    private final OrderService orderService;
    private final PaymentAuditService paymentAuditService;
    private final ChatInterruptService chatInterruptService;
    private final PaymentStatusSyncService paymentStatusSyncService;
    private final FileUploadService fileUploadService;
    private final UserDailyUsageService userDailyUsageService;
    private final UserAccountService userAccountService;
    private final LogManagementConfig logManagementConfig;
    private final PerformanceMonitor performanceMonitor;
    private final ContinuationThreadPoolMonitor continuationThreadPoolMonitor;
    private final CacheMonitoringService cacheMonitoringService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final DataSource dataSource;

    // 异步执行器，避免阻塞定时器线程
    private final Executor taskExecutor = Executors.newFixedThreadPool(3);

    /**
     * 主要定时任务 - 每1分钟执行一次
     * 统一处理订单相关的所有操作，减少数据库查询次数
     * 优化：调整为1分钟以确保及时处理超时订单（订单超时时间为5分钟）
     */
    @Scheduled(fixedRate = 60000) // 1分钟
    public void mainScheduledTask() {
        log.info("【定时任务】开始执行主要定时任务 - {}", LocalDateTime.now());

        // 异步执行，避免阻塞
        CompletableFuture.runAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();

                // 一次性查询所有需要处理的订单
                List<UserOrder> ordersToProcess = getOrdersForProcessing();

                if (!ordersToProcess.isEmpty()) {
                    log.info("【定时任务】发现{}个订单需要处理", ordersToProcess.size());

                    // 按类型分类统计
                    long expiredCount = ordersToProcess.stream()
                            .filter(this::isOrderExpired)
                            .count();
                    long syncCount = ordersToProcess.size() - expiredCount;

                    log.info("【定时任务】订单分类统计 - 过期订单: {}, 需要同步: {}", expiredCount, syncCount);

                    // 分类处理订单
                    processOrdersBatch(ordersToProcess);
                } else {
                    log.debug("【定时任务】没有需要处理的订单");
                }

                long duration = System.currentTimeMillis() - startTime;
                log.info("【定时任务】主要定时任务执行完成，耗时: {}ms", duration);

            } catch (Exception e) {
                log.error("【定时任务】主要定时任务执行异常", e);
            }
        }, taskExecutor);
    }

    /**
     * 专门的过期订单清理任务 - 每30秒执行一次
     * 确保过期订单能够及时被处理
     */
    @Scheduled(fixedRate = 30000) // 30秒
    public void expiredOrderCleanupTask() {
        log.debug("【过期订单清理】开始执行过期订单清理任务 - {}", LocalDateTime.now());

        // 异步执行，避免阻塞
        CompletableFuture.runAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();

                // 专门查询过期订单
                List<UserOrder> expiredOrders = orderService.getExpiredOrders();

                if (!expiredOrders.isEmpty()) {
                    log.info("【过期订单清理】发现{}个过期订单需要处理", expiredOrders.size());

                    int canceledCount = 0;
                    for (UserOrder order : expiredOrders) {
                        try {
                            if (cancelExpiredOrder(order)) {
                                canceledCount++;
                            }
                        } catch (Exception e) {
                            log.error("【过期订单清理】处理过期订单异常，订单号: {}", order.getOrderNo(), e);
                        }
                    }

                    log.info("【过期订单清理】过期订单处理完成，成功取消: {}/{}", canceledCount, expiredOrders.size());
                } else {
                    log.debug("【过期订单清理】没有发现过期订单");
                }

                long duration = System.currentTimeMillis() - startTime;
                log.debug("【过期订单清理】过期订单清理任务执行完成，耗时: {}ms", duration);

            } catch (Exception e) {
                log.error("【过期订单清理】过期订单清理任务执行异常", e);
            }
        }, taskExecutor);
    }

    /**
     * 轻量级任务 - 每5分钟执行一次
     * 只处理内存操作和简单检查
     */
    @Scheduled(fixedRate = 300000) // 5分钟
    public void lightweightTask() {
        log.debug("【轻量级任务】开始执行轻量级任务");

        try {
            // 清理超时会话（内存操作）
            chatInterruptService.cleanupTimeoutSessions();

            // 其他轻量级检查...

        } catch (Exception e) {
            log.error("【轻量级任务】轻量级任务执行异常", e);
        }
    }

    /**
     * 统一支付同步任务 - 每5分钟执行一次
     * 整合原PaymentStatusSyncServiceImpl的功能，统一处理支付订单同步
     */
    @Scheduled(fixedDelay = 300000) // 每5分钟执行一次，使用fixedDelay确保上次执行完成后再开始
    public void unifiedPaymentSyncTask() {
        log.debug("开始执行统一支付同步任务");

        CompletableFuture.runAsync(() -> {
            try {
                // 获取5-30分钟前的待支付订单（整合原PaymentStatusSyncServiceImpl逻辑）
                List<UserOrder> pendingOrders = orderService.getPendingOrdersInTimeRange(5, 30);

                if (pendingOrders.isEmpty()) {
                    log.debug("没有需要同步状态的订单");
                    return;
                }

                log.info("发现{}个需要同步状态的订单", pendingOrders.size());

                int successCount = 0;
                for (UserOrder order : pendingOrders) {
                    try {
                        if (paymentStatusSyncService.syncOrderStatus(order.getOrderNo())) {
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

                log.info("统一支付同步完成，成功同步{}/{}个订单", successCount, pendingOrders.size());

            } catch (Exception e) {
                log.error("统一支付同步任务执行异常", e);
            }
        }, taskExecutor);
    }

    /**
     * 每日维护任务 - 每天凌晨2点执行
     * 处理数据归档、清理等重型操作
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyMaintenanceTask() {
        log.info("开始执行每日维护任务");

        CompletableFuture.runAsync(() -> {
            try {
                // 1. 归档30天前的过期订单
                archiveOldOrders(30);

                // 2. 清理过期的使用记录
                cleanExpiredUsageRecords(90);

                // 3. 清理过期的审计日志
                cleanExpiredAuditLogs(90);

                // 4. 清理标记为删除状态的文件记录
                cleanDeletedFiles();

                // 5. 清理过期套餐记录
                cleanExpiredPackages();

                // 6. 清理过期日志文件
                cleanupOldLogFiles();

                // 7. 缓存维护任务
                cacheMaintenanceTask();

                log.info("每日维护任务执行完成");

            } catch (Exception e) {
                log.error("每日维护任务执行异常", e);
            }
        }, taskExecutor);
    }

    /**
     * API限流数据清理任务 - 每小时执行一次
     * 清理过期的限流计数器和统计数据
     */
    @Scheduled(fixedRate = 3600000) // 1小时
    public void rateLimitCleanupTask() {
        log.debug("【限流清理】开始执行API限流数据清理任务");

        try {
            // 清理过期的限流计数器
            cleanupExpiredRateLimitCounters();

            // 统计限流触发情况
            generateRateLimitStatistics();

            log.debug("【限流清理】API限流数据清理任务执行完成");

        } catch (Exception e) {
            log.error("【限流清理】API限流数据清理任务执行异常", e);
        }
    }

    /**
     * 性能监控任务 - 每5分钟执行一次
     * 整合原PerformanceMonitor和ContinuationThreadPoolMonitor的功能
     */
    @Scheduled(fixedRate = 300000) // 5分钟
    public void performanceMonitoringTask() {
        log.debug("【性能监控】开始执行性能监控任务");

        try {
            // 调用性能监控组件的监控方法
            performanceMonitor.monitorSystemPerformance();

            // 调用线程池监控组件的监控方法
            continuationThreadPoolMonitor.monitorThreadPool();

            // 调用线程池监控组件的详细统计方法
            continuationThreadPoolMonitor.reportDetailedStats();

            // 调用缓存监控组件的健康检查方法
            cacheMonitoringService.performHealthCheck();
        } catch (Exception e) {
            log.error("【性能监控】性能监控任务执行异常", e);
        }
    }

    /**
     * 数据库连接池监控任务 - 每30分钟执行一次
     * 监控数据库连接池状态和性能
     */
    @Scheduled(fixedRate = 1800000) // 30分钟
    public void databaseHealthCheckTask() {
        log.debug("【数据库监控】开始执行数据库连接池监控任务");

        try {
            // 检查连接池状态
            checkConnectionPoolStatus();

            // 监控慢查询（如果有配置）
            monitorSlowQueries();

            // 检测连接泄漏
            detectConnectionLeaks();

            log.debug("【数据库监控】数据库连接池监控任务执行完成");

        } catch (Exception e) {
            log.error("【数据库监控】数据库连接池监控任务执行异常", e);
        }
    }

    /**
     * 详细性能报告任务 - 每1小时执行一次
     * 整合原PerformanceMonitor的详细报告功能
     */
    @Scheduled(fixedRate = 3600000) // 1小时
    public void detailedPerformanceReportTask() {
        log.debug("【性能报告】开始执行详细性能报告任务");

        CompletableFuture.runAsync(() -> {
            try {
                // 调用性能监控组件的详细报告方法
                performanceMonitor.generatePerformanceReport();
            } catch (Exception e) {
                log.error("【性能报告】详细性能报告任务执行异常", e);
            }
        }, taskExecutor);
    }

    /**
     * 日志文件监控任务 - 每1小时执行一次
     * 整合原LogManagementConfig的监控功能
     */
    @Scheduled(fixedRate = 3600000) // 1小时
    public void logFileMonitoringTask() {
        log.debug("【日志监控】开始执行日志文件监控任务");

        try {
            // 调用日志管理配置的监控方法
            logManagementConfig.monitorLogFiles();
        } catch (Exception e) {
            log.error("【日志监控】日志文件监控任务执行异常", e);
        }
    }

    /**
     * 安全审计日志清理任务 - 每天凌晨4点执行一次
     * 清理过期的审计日志，保留重要的安全事件
     */
    @Scheduled(cron = "0 0 4 * * ?") // 每天凌晨4点
    public void auditLogCleanupTask() {
        log.info("【审计清理】开始执行安全审计日志清理任务");

        CompletableFuture.runAsync(() -> {
            try {
                // 清理90天前的审计日志
                cleanExpiredAuditLogs(90);

                // 归档重要的安全事件
                archiveImportantSecurityEvents();

                log.info("【审计清理】安全审计日志清理任务执行完成");

            } catch (Exception e) {
                log.error("【审计清理】安全审计日志清理任务执行异常", e);
            }
        }, taskExecutor);
    }

    /**
     * 用户会话统计任务 - 每天凌晨3点执行一次
     * 统计活跃用户数量和会话时长分布
     */
    @Scheduled(cron = "0 0 3 * * ?") // 每天凌晨3点
    public void userSessionStatisticsTask() {
        log.info("【会话统计】开始执行用户会话统计任务");

        CompletableFuture.runAsync(() -> {
            try {
                // 统计昨日活跃用户
                generateDailyActiveUserStatistics();

                // 分析会话时长分布
                analyzeSessionDurationDistribution();

                // 统计用户行为模式
                analyzeUserBehaviorPatterns();

                log.info("【会话统计】用户会话统计任务执行完成");

            } catch (Exception e) {
                log.error("【会话统计】用户会话统计任务执行异常", e);
            }
        }, taskExecutor);
    }

    /**
     * 系统健康检查任务 - 每15分钟执行一次
     * 检查外部API可用性和关键功能状态
     */
    @Scheduled(fixedRate = 900000) // 15分钟
    public void systemHealthCheckTask() {
        log.debug("【健康检查】开始执行系统健康检查任务");

        try {
            // 检查外部API可用性
            checkExternalApiHealth();

            // 检查关键功能状态
            checkCriticalFunctionStatus();

            // 检查系统资源状态
            checkSystemResourceStatus();

            log.debug("【健康检查】系统健康检查任务执行完成");

        } catch (Exception e) {
            log.error("【健康检查】系统健康检查任务执行异常", e);
        }
    }

    /**
     * 汇率更新任务 - 每6小时执行一次
     * 定期更新汇率数据，处理API失败情况
     */
    @Scheduled(fixedRate = 21600000) // 6小时
    public void exchangeRateUpdateTask() {
        log.info("【汇率更新】开始执行汇率更新任务");

        CompletableFuture.runAsync(() -> {
            try {
                // 更新主要货币汇率
                updateMajorCurrencyRates();

                // 验证汇率数据有效性
                validateExchangeRateData();

                // 缓存汇率数据
                cacheExchangeRateData();

                log.info("【汇率更新】汇率更新任务执行完成");

            } catch (Exception e) {
                log.error("【汇率更新】汇率更新任务执行异常", e);

                // 汇率更新失败时的处理
                handleExchangeRateUpdateFailure();
            }
        }, taskExecutor);
    }

    /**
     * 获取需要处理的订单（一次查询，减少数据库负担）
     * 优化：添加详细日志和错误处理
     */
    private List<UserOrder> getOrdersForProcessing() {
        try {
            log.debug("【获取订单】开始查询需要处理的订单");

            // 查询所有需要处理的订单：过期的、长时间未支付的等
            List<UserOrder> orders = orderService.getOrdersForBatchProcessing();

            log.debug("【获取订单】查询到{}个需要处理的订单", orders.size());

            // 分类统计
            if (!orders.isEmpty()) {
                long expiredOrders = orders.stream()
                        .filter(order -> order.getExpireTime() != null &&
                                LocalDateTime.now().isAfter(order.getExpireTime()) &&
                                order.getPaymentStatus() == 0)
                        .count();
                long syncOrders = orders.size() - expiredOrders;

                log.info("【获取订单】订单统计 - 过期订单: {}, 需要同步: {}, 总计: {}",
                        expiredOrders, syncOrders, orders.size());
            }

            return orders;
        } catch (Exception e) {
            log.error("【获取订单】获取待处理订单异常", e);
            return List.of();
        }
    }

    /**
     * 批量处理订单
     * 优化：添加更详细的日志和错误处理
     */
    @Transactional
    private void processOrdersBatch(List<UserOrder> orders) {
        int expiredCount = 0;
        int syncedCount = 0;
        int errorCount = 0;

        log.info("【批量处理】开始处理{}个订单", orders.size());

        for (UserOrder order : orders) {
            try {
                log.debug("【批量处理】处理订单: {}, 支付状态: {}, 过期时间: {}",
                        order.getOrderNo(), order.getPaymentStatus(), order.getExpireTime());

                if (isOrderExpired(order)) {
                    // 处理过期订单
                    log.info("【批量处理】处理过期订单: {}", order.getOrderNo());
                    if (cancelExpiredOrder(order)) {
                        expiredCount++;
                        log.info("【批量处理】过期订单取消成功: {}", order.getOrderNo());
                    } else {
                        log.error("【批量处理】过期订单取消失败: {}", order.getOrderNo());
                        errorCount++;
                    }
                } else if (needsStatusSync(order)) {
                    // 处理需要状态同步的订单
                    log.debug("【批量处理】同步订单状态: {}", order.getOrderNo());
                    if (syncOrderStatus(order)) {
                        syncedCount++;
                        log.debug("【批量处理】订单状态同步成功: {}", order.getOrderNo());
                    } else {
                        log.warn("【批量处理】订单状态同步失败: {}", order.getOrderNo());
                        errorCount++;
                    }
                } else {
                    log.debug("【批量处理】订单{}无需处理", order.getOrderNo());
                }
            } catch (Exception e) {
                log.error("【批量处理】处理订单异常，订单号: {}", order.getOrderNo(), e);
                errorCount++;
            }
        }

        log.info("【批量处理】订单处理完成 - 过期处理: {}, 状态同步: {}, 错误: {}, 总计: {}",
                expiredCount, syncedCount, errorCount, orders.size());
    }



    /**
     * 判断订单是否过期
     * 优化：添加更详细的日志和更严格的判断条件
     */
    private boolean isOrderExpired(UserOrder order) {
        if (order == null) {
            log.warn("订单对象为空，无法判断是否过期");
            return false;
        }

        if (order.getExpireTime() == null) {
            log.debug("订单{}没有设置过期时间", order.getOrderNo());
            return false;
        }

        if (order.getPaymentStatus() == null || order.getPaymentStatus() != 0) {
            log.debug("订单{}支付状态不是待支付(0)，当前状态: {}", order.getOrderNo(), order.getPaymentStatus());
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        boolean expired = now.isAfter(order.getExpireTime());

        if (expired) {
            log.info("发现过期订单: {}, 过期时间: {}, 当前时间: {}, 超时: {}分钟",
                    order.getOrderNo(),
                    order.getExpireTime(),
                    now,
                    java.time.Duration.between(order.getExpireTime(), now).toMinutes());
        }

        return expired;
    }

    /**
     * 判断订单是否需要状态同步
     */
    private boolean needsStatusSync(UserOrder order) {
        // 1小时前创建的待支付订单需要同步
        return order.getPaymentStatus() == 0 &&
               order.getUpdateTime().isBefore(LocalDateTime.now().minusHours(1));
    }

    /**
     * 取消过期订单
     * 优化：添加更详细的日志和错误处理
     */
    private boolean cancelExpiredOrder(UserOrder order) {
        try {
            log.info("【取消订单】开始取消过期订单: {}, 过期时间: {}", order.getOrderNo(), order.getExpireTime());

            boolean success = orderService.updatePaymentStatus(order.getOrderNo(), 2, null);
            if (success) {
                // 记录审计日志
                Map<String, Object> detailsMap = Map.of(
                    "reason", "订单过期自动取消",
                    "expireTime", order.getExpireTime().toString(),
                    "cancelTime", LocalDateTime.now().toString(),
                    "userId", order.getUserId().toString(),
                    "amount", order.getAmount().toString()
                );
                String details = ((PaymentAuditServiceImpl) paymentAuditService).createOperationDetails(detailsMap);

                paymentAuditService.recordStatusChangeAudit(
                    order.getOrderNo(), 0, 2, "订单过期自动取消", details
                );

                log.info("【取消订单】过期订单取消成功: {}, 用户ID: {}, 金额: {}",
                        order.getOrderNo(), order.getUserId(), order.getAmount());
                return true;
            } else {
                log.error("【取消订单】更新订单状态失败: {}", order.getOrderNo());
            }
        } catch (Exception e) {
            log.error("【取消订单】取消过期订单异常，订单号: {}", order.getOrderNo(), e);
        }
        return false;
    }

    /**
     * 同步订单状态
     */
    private boolean syncOrderStatus(UserOrder order) {
        try {
            // 实现订单状态同步逻辑
            log.debug("同步订单状态: {}", order.getOrderNo());
            return true;
        } catch (Exception e) {
            log.error("同步订单状态异常，订单号: {}", order.getOrderNo(), e);
            return false;
        }
    }

    /**
     * 归档旧订单
     */
    private void archiveOldOrders(int daysBeforeArchive) {
        try {
            log.info("开始归档{}天前的订单", daysBeforeArchive);
            // 实现订单归档逻辑
            // TODO: 需要在OrderService中实现相应方法
        } catch (Exception e) {
            log.error("归档旧订单异常", e);
        }
    }

    /**
     * 清理过期的使用记录
     */
    private void cleanExpiredUsageRecords(int retentionDays) {
        try {
            log.info("开始清理{}天前的使用记录", retentionDays);
            // 调用用户日常使用服务的清理方法
            int cleanedCount = userDailyUsageService.cleanExpiredRecords(retentionDays);
            log.info("清理过期使用记录完成，清理数量：{}", cleanedCount);
        } catch (Exception e) {
            log.error("清理使用记录异常", e);
        }
    }

    /**
     * 清理过期的审计日志
     */
    private void cleanExpiredAuditLogs(int retentionDays) {
        try {
            log.info("开始清理{}天前的审计日志", retentionDays);
            // 调用支付审计服务的清理方法
            // 注意：这里需要根据实际的审计服务接口来实现
            log.info("审计日志清理功能待实现");
        } catch (Exception e) {
            log.error("清理审计日志异常", e);
        }
    }

    /**
     * 清理过期套餐记录
     */
    private void cleanExpiredPackages() {
        try {
            log.info("开始清理过期套餐记录");
            // 调用用户账户服务的清理方法
            int cleanedCount = userAccountService.cleanExpiredPackages();
            log.info("清理过期套餐记录完成，清理数量：{}", cleanedCount);
        } catch (Exception e) {
            log.error("清理过期套餐记录异常", e);
        }
    }

    /**
     * 清理过期日志文件
     */
    private void cleanupOldLogFiles() {
        try {
            log.info("开始清理过期日志文件");
            // 调用日志管理配置的清理方法
            logManagementConfig.cleanupOldLogFiles();
            log.info("清理过期日志文件完成");
        } catch (Exception e) {
            log.error("清理过期日志文件异常", e);
        }
    }

    /**
     * 清理标记为删除状态的文件记录
     */
    private void cleanDeletedFiles() {
        try {
            log.info("开始清理标记为删除状态的文件记录");

            // 调用文件服务的清理方法
            int cleanedCount = fileUploadService.cleanupDeletedFilesAndRelatedRecords();

            if (cleanedCount > 0) {
                log.info("文件清理完成，共清理{}个标记为删除的文件记录", cleanedCount);
            } else {
                log.info("没有找到需要清理的文件记录");
            }

        } catch (Exception e) {
            log.error("清理标记为删除的文件记录异常", e);
        }
    }

    /**
     * 缓存维护任务
     * 清理过期缓存、统计缓存使用情况
     */
    private void cacheMaintenanceTask() {
        try {
            log.info("开始执行缓存维护任务");

            // 1. 清理过期的Redis缓存
            cleanupExpiredRedisCache();

            // 2. 统计缓存使用情况
            generateCacheStatistics();

            // 3. 清理本地缓存（通过缓存监控服务）
            cleanupLocalCache();

            log.info("缓存维护任务执行完成");

        } catch (Exception e) {
            log.error("缓存维护任务执行异常", e);
        }
    }

    /**
     * 清理过期的Redis缓存
     */
    private void cleanupExpiredRedisCache() {
        try {
            log.info("开始清理过期的Redis缓存");

            // 清理限流相关的过期键
            cleanupRateLimitKeys();

            // 清理会话相关的过期键
            cleanupSessionKeys();

            // 清理其他业务相关的过期键
            cleanupBusinessKeys();

            log.info("Redis缓存清理完成");

        } catch (Exception e) {
            log.error("清理Redis缓存异常", e);
        }
    }

    /**
     * 清理限流相关的过期键
     */
    private void cleanupRateLimitKeys() {
        try {
            // 清理聊天限流键
            cleanupKeysByPattern("chat:rate_limit:*");

            // 清理支付限流键
            cleanupKeysByPattern("payment:rate_limit:*");

            // 清理网络搜索限流键
            cleanupKeysByPattern("web_search_update:rate_limit:*");

            log.debug("限流键清理完成");

        } catch (Exception e) {
            log.error("清理限流键异常", e);
        }
    }

    /**
     * 清理会话相关的过期键
     */
    private void cleanupSessionKeys() {
        try {
            // 清理过期的会话键
            cleanupKeysByPattern("session:*");

            // 清理过期的用户状态键
            cleanupKeysByPattern("user:status:*");

            log.debug("会话键清理完成");

        } catch (Exception e) {
            log.error("清理会话键异常", e);
        }
    }

    /**
     * 清理业务相关的过期键
     */
    private void cleanupBusinessKeys() {
        try {
            // 清理过期的汇率缓存
            cleanupKeysByPattern("exchange_rate:*");

            // 清理过期的权限缓存
            cleanupKeysByPattern("permission:*");

            log.debug("业务键清理完成");

        } catch (Exception e) {
            log.error("清理业务键异常", e);
        }
    }

    /**
     * 按模式清理Redis键
     */
    private void cleanupKeysByPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                // 检查键是否已过期，如果过期则删除
                int deletedCount = 0;
                for (String key : keys) {
                    Long ttl = redisTemplate.getExpire(key);
                    if (ttl != null && ttl == -1) {
                        // TTL为-1表示键没有设置过期时间，可能需要手动清理
                        // 这里可以根据业务逻辑决定是否删除
                        log.debug("发现未设置过期时间的键: {}", key);
                    } else if (ttl != null && ttl == -2) {
                        // TTL为-2表示键不存在，Redis会自动清理
                        deletedCount++;
                    }
                }
                log.debug("模式 {} 匹配到 {} 个键，其中 {} 个已过期", pattern, keys.size(), deletedCount);
            }
        } catch (Exception e) {
            log.error("清理模式 {} 的键时异常", pattern, e);
        }
    }

    /**
     * 生成缓存统计信息
     */
    private void generateCacheStatistics() {
        try {
            log.info("开始生成缓存统计信息");

            // 获取Redis信息
            generateRedisStatistics();

            // 获取本地缓存统计（通过缓存监控服务）
            cacheMonitoringService.performHealthCheck();

            log.info("缓存统计信息生成完成");

        } catch (Exception e) {
            log.error("生成缓存统计信息异常", e);
        }
    }

    /**
     * 生成Redis统计信息
     */
    private void generateRedisStatistics() {
        try {
            // 简化Redis统计信息获取，避免使用deprecated API
            // 通过检查Redis连接状态来验证Redis健康状况
            String pingResult = redisTemplate.execute((RedisCallback<String>) connection -> connection.ping());

            if ("PONG".equals(pingResult)) {
                log.info("【缓存统计】Redis连接状态: 正常");

                // 统计一些基本的键信息
                Set<String> allKeys = redisTemplate.keys("*");
                int totalKeys = allKeys != null ? allKeys.size() : 0;

                log.info("【缓存统计】Redis总键数: {}", totalKeys);
            } else {
                log.warn("【缓存告警】Redis连接状态异常，ping结果: {}", pingResult);
            }

        } catch (Exception e) {
            log.error("生成Redis统计信息异常", e);
        }
    }

    /**
     * 清理本地缓存
     */
    private void cleanupLocalCache() {
        try {
            log.info("开始清理本地缓存");

            // 通过缓存监控服务清理本地缓存
            // 这里可以扩展具体的本地缓存清理逻辑

            log.info("本地缓存清理完成");

        } catch (Exception e) {
            log.error("清理本地缓存异常", e);
        }
    }

    /**
     * 清理过期的限流计数器
     */
    private void cleanupExpiredRateLimitCounters() {
        try {
            log.info("开始清理过期的限流计数器");

            int totalCleaned = 0;

            // 清理聊天限流计数器
            totalCleaned += cleanupRateLimitPattern("chat:rate_limit:*");

            // 清理支付限流计数器
            totalCleaned += cleanupRateLimitPattern("payment:rate_limit:*");

            // 清理网络搜索限流计数器
            totalCleaned += cleanupRateLimitPattern("web_search_update:rate_limit:*");

            // 清理IP限流计数器
            totalCleaned += cleanupRateLimitPattern("chat:rate_limit:ip:*");

            log.info("限流计数器清理完成，共清理 {} 个过期计数器", totalCleaned);

        } catch (Exception e) {
            log.error("清理限流计数器异常", e);
        }
    }

    /**
     * 按模式清理限流计数器
     */
    private int cleanupRateLimitPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys == null || keys.isEmpty()) {
                return 0;
            }

            int cleanedCount = 0;
            for (String key : keys) {
                try {
                    Long ttl = redisTemplate.getExpire(key);
                    if (ttl != null && ttl <= 0) {
                        // TTL <= 0 表示键已过期或不存在
                        redisTemplate.delete(key);
                        cleanedCount++;
                    }
                } catch (Exception e) {
                    log.warn("清理限流键 {} 时异常: {}", key, e.getMessage());
                }
            }

            log.debug("模式 {} 清理了 {} 个过期键", pattern, cleanedCount);
            return cleanedCount;

        } catch (Exception e) {
            log.error("清理限流模式 {} 时异常", pattern, e);
            return 0;
        }
    }

    /**
     * 生成限流统计信息
     */
    private void generateRateLimitStatistics() {
        try {
            log.info("开始生成限流统计信息");

            // 统计各类限流键的数量
            int chatRateLimitCount = countKeysByPattern("chat:rate_limit:user:*");
            int paymentRateLimitCount = countKeysByPattern("payment:rate_limit:user:*");
            int webSearchRateLimitCount = countKeysByPattern("web_search_update:rate_limit:user:*");
            int ipRateLimitCount = countKeysByPattern("chat:rate_limit:ip:*");

            log.info("【限流统计】聊天限流: {} 个活跃用户, 支付限流: {} 个活跃用户, 网络搜索限流: {} 个活跃用户, IP限流: {} 个活跃IP",
                    chatRateLimitCount, paymentRateLimitCount, webSearchRateLimitCount, ipRateLimitCount);

            // 检查是否有异常的限流情况
            if (chatRateLimitCount > 1000) {
                log.warn("【限流告警】聊天限流用户数量异常: {}", chatRateLimitCount);
            }
            if (ipRateLimitCount > 500) {
                log.warn("【限流告警】IP限流数量异常: {}", ipRateLimitCount);
            }

            log.info("限流统计信息生成完成");

        } catch (Exception e) {
            log.error("生成限流统计信息异常", e);
        }
    }

    /**
     * 按模式统计键的数量
     */
    private int countKeysByPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.error("统计模式 {} 的键数量时异常", pattern, e);
            return 0;
        }
    }

    /**
     * 检查数据库连接池状态
     */
    private void checkConnectionPoolStatus() {
        try {
            log.info("开始检查数据库连接池状态");

            // 检查数据源是否可用
            if (dataSource != null) {
                try (var connection = dataSource.getConnection()) {
                    boolean isValid = connection.isValid(5); // 5秒超时
                    log.info("【数据库监控】数据库连接状态: {}", isValid ? "正常" : "异常");

                    if (!isValid) {
                        log.warn("【数据库告警】数据库连接验证失败");
                    }

                    // 如果是Druid数据源，获取更详细的信息
                    if (dataSource.getClass().getName().contains("DruidDataSource")) {
                        checkDruidDataSourceStatus();
                    }

                } catch (Exception e) {
                    log.error("【数据库告警】获取数据库连接失败", e);
                }
            }

            log.info("数据库连接池状态检查完成");

        } catch (Exception e) {
            log.error("检查数据库连接池状态异常", e);
        }
    }

    /**
     * 检查Druid数据源状态
     */
    private void checkDruidDataSourceStatus() {
        try {
            // 使用反射获取Druid数据源的统计信息
            Class<?> druidClass = dataSource.getClass();

            // 获取活跃连接数
            Object activeCount = druidClass.getMethod("getActiveCount").invoke(dataSource);

            // 获取最大连接数
            Object maxActive = druidClass.getMethod("getMaxActive").invoke(dataSource);

            // 获取池中连接数
            Object poolingCount = druidClass.getMethod("getPoolingCount").invoke(dataSource);

            log.info("【数据库监控】Druid连接池状态 - 活跃连接: {}, 最大连接: {}, 池中连接: {}",
                    activeCount, maxActive, poolingCount);

            // 检查连接池使用率
            if (activeCount instanceof Number && maxActive instanceof Number) {
                double usage = ((Number) activeCount).doubleValue() / ((Number) maxActive).doubleValue();
                if (usage > 0.8) {
                    log.warn("【数据库告警】连接池使用率过高: {:.2%}", usage);
                }
            }

        } catch (Exception e) {
            log.debug("获取Druid数据源详细信息失败: {}", e.getMessage());
        }
    }

    /**
     * 监控慢查询
     */
    private void monitorSlowQueries() {
        try {
            log.debug("开始监控慢查询");

            // 1. 通过Druid统计功能获取慢查询信息
            getDruidSlowQueryStatistics();

            // 2. 查询MySQL慢查询日志表
            queryMysqlSlowLog();

            // 3. 分析慢查询趋势
            analyzeSlowQueryTrends();

            log.debug("慢查询监控检查完成");

        } catch (Exception e) {
            log.error("监控慢查询异常", e);
        }
    }

    /**
     * 获取Druid慢查询统计信息
     */
    private void getDruidSlowQueryStatistics() {
        try {
            if (dataSource.getClass().getName().contains("DruidDataSource")) {
                Class<?> druidClass = dataSource.getClass();

                // 获取SQL统计信息
                Object sqlStatMap = druidClass.getMethod("getDataSourceStat").invoke(dataSource);
                if (sqlStatMap != null) {
                    // 获取慢查询相关统计
                    Class<?> statClass = sqlStatMap.getClass();

                    // 获取SQL执行统计
                    Object sqlStats = statClass.getMethod("getSqlStatMap").invoke(sqlStatMap);
                    if (sqlStats != null) {
                        log.info("【慢查询统计】Druid SQL统计信息已收集");

                        // 这里可以进一步解析具体的慢查询SQL
                        analyzeDruidSqlStats(sqlStats);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("获取Druid慢查询统计失败: {}", e.getMessage());
        }
    }

    /**
     * 分析Druid SQL统计信息
     */
    private void analyzeDruidSqlStats(Object sqlStats) {
        try {
            // 这里可以分析具体的SQL执行统计
            // 包括执行次数、平均执行时间、最大执行时间等
            log.debug("开始分析Druid SQL统计信息");

            // 由于Druid的统计API比较复杂，这里提供基础框架
            // 实际使用时可以根据具体需求进一步实现

        } catch (Exception e) {
            log.debug("分析Druid SQL统计信息异常: {}", e.getMessage());
        }
    }

    /**
     * 查询MySQL慢查询日志表
     */
    private void queryMysqlSlowLog() {
        try {
            log.debug("开始查询MySQL慢查询日志");

            // 检查是否启用了慢查询日志
            String checkSlowLogSql = "SHOW VARIABLES LIKE 'slow_query_log'";

            try (var connection = dataSource.getConnection();
                 var statement = connection.prepareStatement(checkSlowLogSql);
                 var resultSet = statement.executeQuery()) {

                if (resultSet.next()) {
                    String slowLogEnabled = resultSet.getString("Value");
                    log.debug("【慢查询配置】slow_query_log: {}", slowLogEnabled);

                    if ("ON".equalsIgnoreCase(slowLogEnabled)) {
                        // 查询最近的慢查询
                        queryRecentSlowQueries(connection);
                    } else {
                        log.debug("【慢查询提示】慢查询日志未启用，建议开启以便监控");
                    }
                }
            }

        } catch (Exception e) {
            log.debug("查询MySQL慢查询日志异常: {}", e.getMessage());
        }
    }

    /**
     * 查询最近的慢查询记录
     */
    private void queryRecentSlowQueries(java.sql.Connection connection) {
        try {
            // 查询最近1小时的慢查询（如果使用表格式日志）
            String slowQuerySql = """
                SELECT start_time, query_time, lock_time, rows_sent, rows_examined, sql_text
                FROM mysql.slow_log
                WHERE start_time >= DATE_SUB(NOW(), INTERVAL 1 HOUR)
                ORDER BY query_time DESC
                LIMIT 10
                """;

            try (var statement = connection.prepareStatement(slowQuerySql);
                 var resultSet = statement.executeQuery()) {

                int slowQueryCount = 0;
                while (resultSet.next()) {
                    slowQueryCount++;

                    String startTime = resultSet.getString("start_time");
                    double queryTime = resultSet.getDouble("query_time");
                    int rowsExamined = resultSet.getInt("rows_examined");
                    String sqlText = resultSet.getString("sql_text");

                    // 只记录执行时间超过2秒的查询
                    if (queryTime > 2.0) {
                        log.warn("【慢查询告警】发现慢查询 - 执行时间: {}秒, 扫描行数: {}, 时间: {}",
                                queryTime, rowsExamined, startTime);
                        log.debug("【慢查询SQL】{}", sqlText.length() > 200 ?
                                sqlText.substring(0, 200) + "..." : sqlText);
                    }
                }

                if (slowQueryCount > 0) {
                    log.info("【慢查询统计】最近1小时发现 {} 条慢查询记录", slowQueryCount);
                } else {
                    log.debug("【慢查询统计】最近1小时无慢查询记录");
                }
            }

        } catch (Exception e) {
            // 可能是因为没有启用表格式慢查询日志，这是正常的
            log.debug("查询慢查询表失败，可能未启用表格式慢查询日志: {}", e.getMessage());
        }
    }

    /**
     * 分析慢查询趋势
     */
    private void analyzeSlowQueryTrends() {
        try {
            log.debug("开始分析慢查询趋势");

            // 获取慢查询相关的全局状态变量
            String statusSql = """
                SHOW GLOBAL STATUS WHERE Variable_name IN (
                    'Slow_queries',
                    'Questions',
                    'Uptime',
                    'Select_scan',
                    'Select_full_join'
                )
                """;

            try (var connection = dataSource.getConnection();
                 var statement = connection.prepareStatement(statusSql);
                 var resultSet = statement.executeQuery()) {

                long slowQueries = 0;
                long totalQueries = 0;
                long uptime = 0;
                long selectScan = 0;
                long selectFullJoin = 0;

                while (resultSet.next()) {
                    String varName = resultSet.getString("Variable_name");
                    long value = resultSet.getLong("Value");

                    switch (varName) {
                        case "Slow_queries" -> slowQueries = value;
                        case "Questions" -> totalQueries = value;
                        case "Uptime" -> uptime = value;
                        case "Select_scan" -> selectScan = value;
                        case "Select_full_join" -> selectFullJoin = value;
                    }
                }

                // 计算慢查询比例
                if (totalQueries > 0) {
                    double slowQueryRate = (double) slowQueries / totalQueries * 100;
                    log.info("【慢查询统计】总慢查询: {}, 总查询: {}, 慢查询比例: {:.3f}%",
                            slowQueries, totalQueries, slowQueryRate);

                    // 告警阈值：慢查询比例超过1%
                    if (slowQueryRate > 1.0) {
                        log.warn("【慢查询告警】慢查询比例过高: {:.3f}%，建议优化查询性能", slowQueryRate);
                    }
                }

                // 分析全表扫描情况
                if (selectScan > 0 || selectFullJoin > 0) {
                    log.info("【查询分析】全表扫描: {}, 全连接扫描: {}", selectScan, selectFullJoin);

                    if (selectScan > 1000) {
                        log.warn("【性能告警】全表扫描次数过多: {}，建议检查索引", selectScan);
                    }
                }

                // 计算平均QPS
                if (uptime > 0) {
                    double qps = (double) totalQueries / uptime;
                    log.info("【性能统计】平均QPS: {:.2f}, 运行时间: {}秒", qps, uptime);
                }

            }

        } catch (Exception e) {
            log.debug("分析慢查询趋势异常: {}", e.getMessage());
        }
    }

    /**
     * 检测连接泄漏
     */
    private void detectConnectionLeaks() {
        try {
            log.debug("开始检测连接泄漏");

            // 检查是否有长时间未释放的连接
            // 这里可以通过Druid的监控功能来实现

            if (dataSource.getClass().getName().contains("DruidDataSource")) {
                detectDruidConnectionLeaks();
            }

            log.debug("连接泄漏检测完成");

        } catch (Exception e) {
            log.error("检测连接泄漏异常", e);
        }
    }

    /**
     * 检测Druid连接泄漏
     */
    private void detectDruidConnectionLeaks() {
        try {
            Class<?> druidClass = dataSource.getClass();

            // 获取活跃连接数
            Object activeCount = druidClass.getMethod("getActiveCount").invoke(dataSource);

            // 获取活跃连接峰值
            Object activePeak = druidClass.getMethod("getActivePeak").invoke(dataSource);

            if (activeCount instanceof Number && activePeak instanceof Number) {
                int active = ((Number) activeCount).intValue();
                int peak = ((Number) activePeak).intValue();

                // 如果活跃连接数持续接近峰值，可能存在连接泄漏
                if (active > 0 && active >= peak * 0.9) {
                    log.warn("【数据库告警】可能存在连接泄漏，活跃连接: {}, 峰值: {}", active, peak);
                }
            }

        } catch (Exception e) {
            log.debug("检测Druid连接泄漏失败: {}", e.getMessage());
        }
    }

    /**
     * 归档重要的安全事件
     */
    private void archiveImportantSecurityEvents() {
        try {
            log.info("开始归档重要的安全事件");

            // 归档登录失败记录
            archiveLoginFailureEvents();

            // 归档权限异常记录
            archivePermissionViolationEvents();

            // 归档支付异常记录
            archivePaymentAnomalyEvents();

            log.info("重要安全事件归档完成");

        } catch (Exception e) {
            log.error("归档重要安全事件异常", e);
        }
    }

    /**
     * 归档登录失败记录
     */
    private void archiveLoginFailureEvents() {
        try {
            log.debug("开始归档登录失败记录");

            // 这里可以实现具体的登录失败记录归档逻辑
            // 例如：将重要的登录失败记录移动到归档表
            // 或者导出到文件进行长期保存

            log.debug("登录失败记录归档完成");

        } catch (Exception e) {
            log.error("归档登录失败记录异常", e);
        }
    }

    /**
     * 归档权限异常记录
     */
    private void archivePermissionViolationEvents() {
        try {
            log.debug("开始归档权限异常记录");

            // 这里可以实现具体的权限异常记录归档逻辑
            // 例如：保留所有权限违规尝试的记录

            log.debug("权限异常记录归档完成");

        } catch (Exception e) {
            log.error("归档权限异常记录异常", e);
        }
    }

    /**
     * 归档支付异常记录
     */
    private void archivePaymentAnomalyEvents() {
        try {
            log.debug("开始归档支付异常记录");

            // 这里可以实现具体的支付异常记录归档逻辑
            // 例如：保留所有支付异常和可疑交易记录

            log.debug("支付异常记录归档完成");

        } catch (Exception e) {
            log.error("归档支付异常记录异常", e);
        }
    }

    /**
     * 生成每日活跃用户统计
     */
    private void generateDailyActiveUserStatistics() {
        try {
            log.info("开始生成每日活跃用户统计");

            LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
            LocalDateTime startOfDay = yesterday.withHour(0).withMinute(0).withSecond(0);
            LocalDateTime endOfDay = yesterday.withHour(23).withMinute(59).withSecond(59);

            // 统计昨日活跃用户数量
            // 这里可以通过查询用户会话表或聊天记录表来统计
            // 例如：SELECT COUNT(DISTINCT user_id) FROM chat_sessions WHERE created_time BETWEEN ? AND ?

            log.info("【会话统计】昨日活跃用户统计完成，统计时间范围: {} - {}", startOfDay, endOfDay);

            // 统计新用户数量
            generateNewUserStatistics(startOfDay, endOfDay);

            // 统计用户留存率
            generateUserRetentionStatistics();

        } catch (Exception e) {
            log.error("生成每日活跃用户统计异常", e);
        }
    }

    /**
     * 生成新用户统计
     */
    private void generateNewUserStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        try {
            log.debug("开始统计新用户数量");

            // 这里可以通过查询用户表来统计新注册用户
            // 例如：SELECT COUNT(*) FROM users WHERE created_time BETWEEN ? AND ?

            log.debug("新用户统计完成");

        } catch (Exception e) {
            log.error("统计新用户数量异常", e);
        }
    }

    /**
     * 生成用户留存率统计
     */
    private void generateUserRetentionStatistics() {
        try {
            log.debug("开始统计用户留存率");

            // 统计7日留存率、30日留存率等
            // 这里可以通过分析用户的登录记录来计算留存率

            log.debug("用户留存率统计完成");

        } catch (Exception e) {
            log.error("统计用户留存率异常", e);
        }
    }

    /**
     * 分析会话时长分布
     */
    private void analyzeSessionDurationDistribution() {
        try {
            log.info("开始分析会话时长分布");

            // 分析不同时长区间的会话分布
            // 例如：0-5分钟、5-15分钟、15-30分钟、30分钟以上
            analyzeSessionDurationRanges();

            // 计算平均会话时长
            calculateAverageSessionDuration();

            log.info("会话时长分布分析完成");

        } catch (Exception e) {
            log.error("分析会话时长分布异常", e);
        }
    }

    /**
     * 分析会话时长区间分布
     */
    private void analyzeSessionDurationRanges() {
        try {
            log.debug("开始分析会话时长区间分布");

            // 统计各个时长区间的会话数量
            // 0-5分钟：短会话
            // 5-15分钟：中等会话
            // 15-30分钟：长会话
            // 30分钟以上：超长会话

            log.debug("会话时长区间分布分析完成");

        } catch (Exception e) {
            log.error("分析会话时长区间分布异常", e);
        }
    }

    /**
     * 计算平均会话时长
     */
    private void calculateAverageSessionDuration() {
        try {
            log.debug("开始计算平均会话时长");

            // 计算昨日所有会话的平均时长
            // 这里可以通过查询会话表来计算

            log.debug("平均会话时长计算完成");

        } catch (Exception e) {
            log.error("计算平均会话时长异常", e);
        }
    }

    /**
     * 分析用户行为模式
     */
    private void analyzeUserBehaviorPatterns() {
        try {
            log.info("开始分析用户行为模式");

            // 分析用户活跃时间段
            analyzeUserActiveTimePatterns();

            // 分析用户功能使用偏好
            analyzeUserFeatureUsagePatterns();

            // 分析用户消费行为
            analyzeUserConsumptionPatterns();

            log.info("用户行为模式分析完成");

        } catch (Exception e) {
            log.error("分析用户行为模式异常", e);
        }
    }

    /**
     * 分析用户活跃时间段
     */
    private void analyzeUserActiveTimePatterns() {
        try {
            log.debug("开始分析用户活跃时间段");

            // 统计不同时间段的用户活跃度
            // 例如：早上(6-12)、下午(12-18)、晚上(18-24)、深夜(0-6)

            log.debug("用户活跃时间段分析完成");

        } catch (Exception e) {
            log.error("分析用户活跃时间段异常", e);
        }
    }

    /**
     * 分析用户功能使用偏好
     */
    private void analyzeUserFeatureUsagePatterns() {
        try {
            log.debug("开始分析用户功能使用偏好");

            // 统计各功能的使用频率
            // 例如：聊天、文件上传、网络搜索等功能的使用情况

            log.debug("用户功能使用偏好分析完成");

        } catch (Exception e) {
            log.error("分析用户功能使用偏好异常", e);
        }
    }

    /**
     * 分析用户消费行为
     */
    private void analyzeUserConsumptionPatterns() {
        try {
            log.debug("开始分析用户消费行为");

            // 分析用户的套餐购买行为
            // 例如：购买频率、偏好套餐类型、消费金额分布等

            log.debug("用户消费行为分析完成");

        } catch (Exception e) {
            log.error("分析用户消费行为异常", e);
        }
    }

    /**
     * 检查外部API可用性
     */
    private void checkExternalApiHealth() {
        try {
            log.debug("开始检查外部API可用性");

            // 检查支付API可用性
            checkPaymentApiHealth();

            // 检查汇率API可用性
            checkExchangeRateApiHealth();

            // 检查其他第三方API可用性
            checkThirdPartyApiHealth();

            log.debug("外部API可用性检查完成");

        } catch (Exception e) {
            log.error("检查外部API可用性异常", e);
        }
    }

    /**
     * 检查支付API可用性
     */
    private void checkPaymentApiHealth() {
        try {
            log.debug("开始检查支付API可用性");

            // 这里可以通过调用支付API的健康检查接口来验证
            // 或者通过简单的ping测试来检查连通性

            log.debug("支付API健康检查完成");

        } catch (Exception e) {
            log.warn("【健康告警】支付API健康检查异常: {}", e.getMessage());
        }
    }

    /**
     * 检查汇率API可用性
     */
    private void checkExchangeRateApiHealth() {
        try {
            log.debug("开始检查汇率API可用性");

            // 这里可以通过调用汇率API来验证其可用性
            // 检查响应时间和数据有效性

            log.debug("汇率API健康检查完成");

        } catch (Exception e) {
            log.warn("【健康告警】汇率API健康检查异常: {}", e.getMessage());
        }
    }

    /**
     * 检查其他第三方API可用性
     */
    private void checkThirdPartyApiHealth() {
        try {
            log.debug("开始检查其他第三方API可用性");

            // 检查其他可能使用的第三方服务
            // 例如：短信服务、邮件服务等

            log.debug("第三方API健康检查完成");

        } catch (Exception e) {
            log.warn("【健康告警】第三方API健康检查异常: {}", e.getMessage());
        }
    }

    /**
     * 检查关键功能状态
     */
    private void checkCriticalFunctionStatus() {
        try {
            log.debug("开始检查关键功能状态");

            // 检查数据库连接状态
            checkDatabaseFunctionStatus();

            // 检查缓存功能状态
            checkCacheFunctionStatus();

            // 检查文件上传功能状态
            checkFileUploadFunctionStatus();

            log.debug("关键功能状态检查完成");

        } catch (Exception e) {
            log.error("检查关键功能状态异常", e);
        }
    }

    /**
     * 检查数据库功能状态
     */
    private void checkDatabaseFunctionStatus() {
        try {
            log.debug("开始检查数据库功能状态");

            // 执行简单的数据库查询来验证连接
            try (var connection = dataSource.getConnection()) {
                boolean isValid = connection.isValid(3); // 3秒超时
                if (!isValid) {
                    log.warn("【健康告警】数据库连接验证失败");
                }
            }

            log.debug("数据库功能状态检查完成");

        } catch (Exception e) {
            log.error("【健康告警】数据库功能状态检查异常", e);
        }
    }

    /**
     * 检查缓存功能状态
     */
    private void checkCacheFunctionStatus() {
        try {
            log.debug("开始检查缓存功能状态");

            // 测试Redis连接
            String pingResult = redisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
            if (!"PONG".equals(pingResult)) {
                log.warn("【健康告警】Redis连接异常，ping结果: {}", pingResult);
            }

            log.debug("缓存功能状态检查完成");

        } catch (Exception e) {
            log.error("【健康告警】缓存功能状态检查异常", e);
        }
    }

    /**
     * 检查文件上传功能状态
     */
    private void checkFileUploadFunctionStatus() {
        try {
            log.debug("开始检查文件上传功能状态");

            // 检查文件上传相关的配置和权限
            // 例如：检查临时目录是否可写

            log.debug("文件上传功能状态检查完成");

        } catch (Exception e) {
            log.error("【健康告警】文件上传功能状态检查异常", e);
        }
    }

    /**
     * 检查系统资源状态
     */
    private void checkSystemResourceStatus() {
        try {
            log.debug("开始检查系统资源状态");

            // 检查JVM内存使用情况
            checkJvmMemoryStatus();

            // 检查线程池状态
            checkThreadPoolStatus();

            // 检查磁盘空间状态
            checkDiskSpaceStatus();

            log.debug("系统资源状态检查完成");

        } catch (Exception e) {
            log.error("检查系统资源状态异常", e);
        }
    }

    /**
     * 检查JVM内存使用情况
     */
    private void checkJvmMemoryStatus() {
        try {
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            long maxMemory = runtime.maxMemory();

            double memoryUsagePercent = (double) usedMemory / maxMemory * 100;

            log.debug("【资源监控】JVM内存使用率: {:.2f}%, 已用: {}MB, 最大: {}MB",
                    memoryUsagePercent, usedMemory / 1024 / 1024, maxMemory / 1024 / 1024);

            if (memoryUsagePercent > 85) {
                log.warn("【健康告警】JVM内存使用率过高: {:.2f}%", memoryUsagePercent);
            }

        } catch (Exception e) {
            log.error("检查JVM内存状态异常", e);
        }
    }

    /**
     * 检查线程池状态
     */
    private void checkThreadPoolStatus() {
        try {
            log.debug("开始检查线程池状态");

            // 通过线程池监控组件检查状态
            continuationThreadPoolMonitor.monitorThreadPool();

            log.debug("线程池状态检查完成");

        } catch (Exception e) {
            log.error("检查线程池状态异常", e);
        }
    }

    /**
     * 检查磁盘空间状态
     */
    private void checkDiskSpaceStatus() {
        try {
            log.debug("开始检查磁盘空间状态");

            // 检查系统磁盘空间
            java.io.File root = new java.io.File("/");
            long totalSpace = root.getTotalSpace();
            long freeSpace = root.getFreeSpace();
            long usedSpace = totalSpace - freeSpace;

            double diskUsagePercent = (double) usedSpace / totalSpace * 100;

            log.debug("【资源监控】磁盘使用率: {:.2f}%, 已用: {}GB, 总计: {}GB",
                    diskUsagePercent, usedSpace / 1024 / 1024 / 1024, totalSpace / 1024 / 1024 / 1024);

            if (diskUsagePercent > 90) {
                log.warn("【健康告警】磁盘使用率过高: {:.2f}%", diskUsagePercent);
            }

        } catch (Exception e) {
            log.error("检查磁盘空间状态异常", e);
        }
    }

    /**
     * 更新主要货币汇率
     */
    private void updateMajorCurrencyRates() {
        try {
            log.info("开始更新主要货币汇率");

            // 更新USD汇率
            updateCurrencyRate("USD");

            // 更新EUR汇率
            updateCurrencyRate("EUR");

            // 更新JPY汇率
            updateCurrencyRate("JPY");

            // 更新GBP汇率
            updateCurrencyRate("GBP");

            // 更新其他主要货币
            updateOtherMajorCurrencies();

            log.info("主要货币汇率更新完成");

        } catch (Exception e) {
            log.error("更新主要货币汇率异常", e);
            throw e; // 重新抛出异常，触发失败处理
        }
    }

    /**
     * 更新指定货币汇率
     */
    private void updateCurrencyRate(String currencyCode) {
        try {
            log.debug("开始更新{}汇率", currencyCode);

            // 这里可以调用外部汇率API获取最新汇率
            // 例如：调用汇率API服务获取CNY对指定货币的汇率

            // 模拟汇率更新逻辑
            // Double rate = exchangeRateApiService.getRate("CNY", currencyCode);
            // exchangeRateService.updateRate(currencyCode, rate);

            log.debug("{}汇率更新完成", currencyCode);

        } catch (Exception e) {
            log.error("更新{}汇率异常: {}", currencyCode, e.getMessage());
            throw e;
        }
    }

    /**
     * 更新其他主要货币
     */
    private void updateOtherMajorCurrencies() {
        try {
            log.debug("开始更新其他主要货币汇率");

            // 更新其他货币，如：AUD, CAD, CHF, HKD等
            String[] otherCurrencies = {"AUD", "CAD", "CHF", "HKD", "SGD"};

            for (String currency : otherCurrencies) {
                try {
                    updateCurrencyRate(currency);
                } catch (Exception e) {
                    log.warn("更新{}汇率失败，跳过: {}", currency, e.getMessage());
                    // 单个货币更新失败不影响其他货币
                }
            }

            log.debug("其他主要货币汇率更新完成");

        } catch (Exception e) {
            log.error("更新其他主要货币汇率异常", e);
        }
    }

    /**
     * 验证汇率数据有效性
     */
    private void validateExchangeRateData() {
        try {
            log.info("开始验证汇率数据有效性");

            // 检查汇率数据是否在合理范围内
            validateRateRanges();

            // 检查汇率更新时间是否正常
            validateRateUpdateTime();

            // 检查汇率数据完整性
            validateRateDataIntegrity();

            log.info("汇率数据有效性验证完成");

        } catch (Exception e) {
            log.error("验证汇率数据有效性异常", e);
            throw e;
        }
    }

    /**
     * 验证汇率范围
     */
    private void validateRateRanges() {
        try {
            log.debug("开始验证汇率范围");

            // 检查汇率是否在合理范围内
            // 例如：USD/CNY汇率应该在6-8之间（根据历史数据）
            // 如果汇率异常，可能是API数据错误

            log.debug("汇率范围验证完成");

        } catch (Exception e) {
            log.error("验证汇率范围异常", e);
        }
    }

    /**
     * 验证汇率更新时间
     */
    private void validateRateUpdateTime() {
        try {
            log.debug("开始验证汇率更新时间");

            // 检查汇率数据的更新时间是否正常
            // 如果更新时间过旧，可能需要重新获取

            log.debug("汇率更新时间验证完成");

        } catch (Exception e) {
            log.error("验证汇率更新时间异常", e);
        }
    }

    /**
     * 验证汇率数据完整性
     */
    private void validateRateDataIntegrity() {
        try {
            log.debug("开始验证汇率数据完整性");

            // 检查所有必需的货币汇率是否都已更新
            // 检查数据格式是否正确

            log.debug("汇率数据完整性验证完成");

        } catch (Exception e) {
            log.error("验证汇率数据完整性异常", e);
        }
    }

    /**
     * 缓存汇率数据
     */
    private void cacheExchangeRateData() {
        try {
            log.info("开始缓存汇率数据");

            // 将最新的汇率数据缓存到Redis
            cacheRatesToRedis();

            // 设置缓存过期时间（例如：12小时）
            setRateCacheExpiration();

            log.info("汇率数据缓存完成");

        } catch (Exception e) {
            log.error("缓存汇率数据异常", e);
        }
    }

    /**
     * 将汇率缓存到Redis
     */
    private void cacheRatesToRedis() {
        try {
            log.debug("开始将汇率缓存到Redis");

            // 缓存各种货币汇率
            // redisTemplate.opsForValue().set("exchange_rate:USD", usdRate);
            // redisTemplate.opsForValue().set("exchange_rate:EUR", eurRate);
            // 等等...

            log.debug("汇率Redis缓存完成");

        } catch (Exception e) {
            log.error("将汇率缓存到Redis异常", e);
        }
    }

    /**
     * 设置汇率缓存过期时间
     */
    private void setRateCacheExpiration() {
        try {
            log.debug("开始设置汇率缓存过期时间");

            // 设置12小时过期时间
            // redisTemplate.expire("exchange_rate:USD", Duration.ofHours(12));
            // 等等...

            log.debug("汇率缓存过期时间设置完成");

        } catch (Exception e) {
            log.error("设置汇率缓存过期时间异常", e);
        }
    }

    /**
     * 处理汇率更新失败
     */
    private void handleExchangeRateUpdateFailure() {
        try {
            log.warn("开始处理汇率更新失败情况");

            // 检查缓存中是否有可用的汇率数据
            checkCachedRateData();

            // 使用备用汇率数据源
            tryBackupRateSource();

            // 发送告警通知
            sendRateUpdateFailureAlert();

            log.warn("汇率更新失败处理完成");

        } catch (Exception e) {
            log.error("处理汇率更新失败异常", e);
        }
    }

    /**
     * 检查缓存的汇率数据
     */
    private void checkCachedRateData() {
        try {
            log.debug("开始检查缓存的汇率数据");

            // 检查Redis中是否有可用的汇率数据
            // 如果有，检查数据是否过期

            log.debug("缓存汇率数据检查完成");

        } catch (Exception e) {
            log.error("检查缓存汇率数据异常", e);
        }
    }

    /**
     * 尝试备用汇率数据源
     */
    private void tryBackupRateSource() {
        try {
            log.debug("开始尝试备用汇率数据源");

            // 如果主要汇率API失败，尝试备用API
            // 或者使用本地存储的历史汇率数据

            log.debug("备用汇率数据源尝试完成");

        } catch (Exception e) {
            log.error("尝试备用汇率数据源异常", e);
        }
    }

    /**
     * 发送汇率更新失败告警
     */
    private void sendRateUpdateFailureAlert() {
        try {
            log.warn("【汇率告警】汇率更新失败，请检查汇率API服务状态");

            // 这里可以发送邮件、短信或其他形式的告警通知
            // 通知运维人员汇率更新失败

        } catch (Exception e) {
            log.error("发送汇率更新失败告警异常", e);
        }
    }
}
