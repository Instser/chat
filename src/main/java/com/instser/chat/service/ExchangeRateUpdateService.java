package com.instser.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 汇率定时更新服务
 *
 * @author instser
 * @since 2025-06-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "exchange.api.enabled", havingValue = "true")
public class ExchangeRateUpdateService {

    private final ExchangeRateService exchangeRateService;

    /**
     * 每小时更新一次汇率（工作时间）
     * 周一到周五，上午9点到下午6点，每小时执行一次
     */
    @Scheduled(cron = "0 0 9-18 * * MON-FRI")
    public void updateExchangeRatesWorkingHours() {
        log.info("开始工作时间汇率更新任务");
        try {
            exchangeRateService.updateAllRates();
            log.info("工作时间汇率更新任务完成");
        } catch (Exception e) {
            log.error("工作时间汇率更新任务失败", e);
        }
    }

    /**
     * 每天早上8点更新一次汇率
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void updateExchangeRatesDaily() {
        log.info("开始每日汇率更新任务");
        try {
            exchangeRateService.updateAllRates();
            log.info("每日汇率更新任务完成");
        } catch (Exception e) {
            log.error("每日汇率更新任务失败", e);
        }
    }

    /**
     * 每周清理一次缓存（周日凌晨2点）
     */
    @Scheduled(cron = "0 0 2 * * SUN")
    public void clearExchangeRateCache() {
        log.info("开始清理汇率缓存任务");
        try {
            exchangeRateService.clearCache();
            log.info("汇率缓存清理任务完成");
        } catch (Exception e) {
            log.error("汇率缓存清理任务失败", e);
        }
    }
}
