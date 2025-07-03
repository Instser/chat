package com.instser.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.config.ExchangeRateConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 汇率服务测试类
 * 测试新的汇率API集成
 *
 * @author instser
 * @since 2025-07-03
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
public class ExchangeRateServiceTest {

    @Autowired
    private ExchangeRateService exchangeRateService;

    @Test
    public void testGetUsdToCnyRate() {
        log.info("测试获取USD到CNY汇率");
        
        BigDecimal rate = exchangeRateService.getExchangeRate("USD", "CNY");
        assertNotNull(rate, "汇率不应为null");
        assertTrue(rate.compareTo(BigDecimal.ZERO) > 0, "汇率应大于0");
        
        log.info("USD到CNY汇率: {}", rate);
    }

    @Test
    public void testGetCnyToUsdRate() {
        log.info("测试获取CNY到USD汇率");
        
        BigDecimal rate = exchangeRateService.getExchangeRate("CNY", "USD");
        assertNotNull(rate, "汇率不应为null");
        assertTrue(rate.compareTo(BigDecimal.ZERO) > 0, "汇率应大于0");
        
        log.info("CNY到USD汇率: {}", rate);
    }

    @Test
    public void testGetUsdToEurRate() {
        log.info("测试获取USD到EUR汇率");
        
        BigDecimal rate = exchangeRateService.getExchangeRate("USD", "EUR");
        assertNotNull(rate, "汇率不应为null");
        assertTrue(rate.compareTo(BigDecimal.ZERO) > 0, "汇率应大于0");
        
        log.info("USD到EUR汇率: {}", rate);
    }

    @Test
    public void testSameCurrencyRate() {
        log.info("测试相同货币汇率");
        
        BigDecimal rate = exchangeRateService.getExchangeRate("USD", "USD");
        assertEquals(BigDecimal.ONE, rate, "相同货币汇率应为1");
        
        log.info("USD到USD汇率: {}", rate);
    }

    @Test
    public void testUnsupportedCurrencyPair() {
        log.info("测试不支持的货币对");
        
        // 测试非美元相关的货币对，应该返回默认汇率
        BigDecimal rate = exchangeRateService.getExchangeRate("EUR", "GBP");
        assertNotNull(rate, "汇率不应为null");
        
        log.info("EUR到GBP汇率: {}", rate);
    }

    @Test
    public void testCurrencySupport() {
        log.info("测试货币支持检查");
        
        assertTrue(exchangeRateService.isCurrencySupported("USD"), "应支持USD");
        assertTrue(exchangeRateService.isCurrencySupported("CNY"), "应支持CNY");
        assertTrue(exchangeRateService.isCurrencySupported("EUR"), "应支持EUR");
        assertFalse(exchangeRateService.isCurrencySupported("XYZ"), "不应支持XYZ");
        
        log.info("货币支持检查通过");
    }

    @Test
    public void testUpdateAllRates() {
        log.info("测试批量更新汇率");
        
        // 获取更新前的缓存状态
        Map<String, Object> statusBefore = exchangeRateService.getCacheStatus();
        log.info("更新前缓存状态: {}", statusBefore);
        
        // 执行批量更新
        exchangeRateService.updateAllRates();
        
        // 等待异步更新完成
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 获取更新后的缓存状态
        Map<String, Object> statusAfter = exchangeRateService.getCacheStatus();
        log.info("更新后缓存状态: {}", statusAfter);
        
        // 验证缓存大小增加
        Integer cacheSizeBefore = (Integer) statusBefore.get("cacheSize");
        Integer cacheSizeAfter = (Integer) statusAfter.get("cacheSize");
        
        log.info("缓存大小变化: {} -> {}", cacheSizeBefore, cacheSizeAfter);
    }

    @Test
    public void testCacheStatus() {
        log.info("测试缓存状态");
        
        Map<String, Object> status = exchangeRateService.getCacheStatus();
        assertNotNull(status, "缓存状态不应为null");
        assertTrue(status.containsKey("cacheSize"), "应包含缓存大小");
        assertTrue(status.containsKey("apiEnabled"), "应包含API启用状态");
        assertTrue(status.containsKey("supportedCurrencies"), "应包含支持的货币");
        assertTrue(status.containsKey("baseCurrency"), "应包含基础货币");
        
        log.info("缓存状态: {}", status);
    }

    @Test
    public void testClearCache() {
        log.info("测试清除缓存");
        
        // 先获取一些汇率以填充缓存
        exchangeRateService.getExchangeRate("USD", "CNY");
        exchangeRateService.getExchangeRate("USD", "EUR");
        
        Map<String, Object> statusBefore = exchangeRateService.getCacheStatus();
        log.info("清除前缓存状态: {}", statusBefore);
        
        // 清除缓存
        exchangeRateService.clearCache();
        
        Map<String, Object> statusAfter = exchangeRateService.getCacheStatus();
        log.info("清除后缓存状态: {}", statusAfter);
        
        // 验证缓存已清除
        assertEquals(0, statusAfter.get("cacheSize"), "缓存应已清空");
    }
}
