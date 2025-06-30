package com.instser.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instser.chat.config.ExchangeRateConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 汇率服务
 * 提供实时汇率查询和缓存功能
 *
 * @author instser
 * @since 2025-06-29
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ExchangeRateConfig exchangeRateConfig;

    // 支持的货币列表
    private static final List<String> SUPPORTED_CURRENCIES = List.of(
        "USD", "CNY", "EUR", "GBP", "JPY", "KRW", "HKD", "SGD", "AUD", "CAD"
    );

    // 缓存汇率数据，避免频繁API调用
    private final Map<String, BigDecimal> rateCache = new ConcurrentHashMap<>();
    private volatile LocalDateTime lastUpdateTime;

    /**
     * 获取汇率
     * 支持缓存，缓存时间30分钟
     *
     * @param fromCurrency 源货币
     * @param toCurrency   目标货币
     * @return 汇率
     */
    @Cacheable(value = "exchangeRates", key = "#fromCurrency + '_' + #toCurrency", unless = "#result == null")
    public BigDecimal getExchangeRate(String fromCurrency, String toCurrency) {
        if (fromCurrency == null || toCurrency == null) {
            log.warn("货币类型不能为空，fromCurrency: {}, toCurrency: {}", fromCurrency, toCurrency);
            return BigDecimal.ONE;
        }

        // 相同货币直接返回1
        if (fromCurrency.equalsIgnoreCase(toCurrency)) {
            return BigDecimal.ONE;
        }

        String rateKey = fromCurrency.toUpperCase() + "_" + toCurrency.toUpperCase();

        // 如果API未启用，直接使用默认汇率
        if (!exchangeRateConfig.getApi().isEnabled()) {
            log.debug("汇率API未启用，使用默认汇率: {}", rateKey);
            return getDefaultExchangeRate(fromCurrency, toCurrency);
        }

        try {
            // 调用汇率API
            BigDecimal rate = fetchExchangeRateFromApi(fromCurrency, toCurrency);
            if (rate != null && rate.compareTo(BigDecimal.ZERO) > 0) {
                log.debug("成功获取汇率: {} = {}", rateKey, rate);
                // 缓存到本地
                rateCache.put(rateKey, rate);
                lastUpdateTime = LocalDateTime.now();
                return rate;
            }
        } catch (Exception e) {
            log.error("获取汇率失败，使用默认汇率: {}", rateKey, e);
        }

        // API失败时使用默认汇率
        return getDefaultExchangeRate(fromCurrency, toCurrency);
    }

    /**
     * 从API获取汇率（支持重试机制）
     */
    private BigDecimal fetchExchangeRateFromApi(String fromCurrency, String toCurrency) {
        int retryCount = exchangeRateConfig.getApi().getRetryCount();

        for (int attempt = 1; attempt <= retryCount; attempt++) {
            try {
                String url = exchangeRateConfig.getApi().getUrl() + fromCurrency.toUpperCase();
                log.debug("调用汇率API (第{}次尝试): {}", attempt, url);

                // 使用ResponseEntity获取更详细的响应信息
                ResponseEntity<String> responseEntity = restTemplate.getForEntity(url, String.class);

                if (responseEntity.getStatusCode() != HttpStatus.OK) {
                    log.warn("汇率API返回非200状态码: {}", responseEntity.getStatusCode());
                    continue;
                }

                String response = responseEntity.getBody();
                if (response == null || response.trim().isEmpty()) {
                    log.warn("汇率API返回空响应");
                    continue;
                }

                JsonNode jsonNode = objectMapper.readTree(response);

                // 检查API响应是否成功
                if (jsonNode.has("success") && !jsonNode.get("success").asBoolean()) {
                    log.warn("汇率API返回失败状态: {}", jsonNode.get("error-type"));
                    continue;
                }

                JsonNode ratesNode = jsonNode.get("rates");
                if (ratesNode == null) {
                    log.warn("汇率API响应中缺少rates字段");
                    continue;
                }

                JsonNode targetRateNode = ratesNode.get(toCurrency.toUpperCase());
                if (targetRateNode == null) {
                    log.warn("汇率API响应中缺少目标货币: {}", toCurrency);
                    continue;
                }

                BigDecimal rate = new BigDecimal(targetRateNode.asText());

                // 验证汇率合理性（避免异常数据）
                if (rate.compareTo(BigDecimal.ZERO) <= 0 || rate.compareTo(new BigDecimal("1000")) > 0) {
                    log.warn("汇率数据异常: {} = {}", fromCurrency + "_" + toCurrency, rate);
                    continue;
                }

                log.info("成功获取汇率: {} = {} (第{}次尝试)", fromCurrency + "_" + toCurrency, rate, attempt);
                return rate.setScale(6, RoundingMode.HALF_UP);

            } catch (RestClientException e) {
                log.warn("汇率API调用失败 (第{}次尝试): {}", attempt, e.getMessage());
                if (attempt < retryCount) {
                    try {
                        Thread.sleep(1000 * attempt); // 递增延迟重试
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception e) {
                log.error("汇率API调用异常 (第{}次尝试)", attempt, e);
                break; // 非网络异常，直接退出重试
            }
        }

        log.error("汇率API调用失败，已重试{}次", retryCount);
        return null;
    }

    /**
     * 获取默认汇率
     */
    private BigDecimal getDefaultExchangeRate(String fromCurrency, String toCurrency) {
        String rateKey = fromCurrency.toUpperCase() + "_" + toCurrency.toUpperCase();

        // 从配置中获取默认汇率
        Map<String, BigDecimal> defaultRates = exchangeRateConfig.getDefaultRates();
        if (defaultRates != null && defaultRates.containsKey(rateKey)) {
            BigDecimal rate = defaultRates.get(rateKey);
            log.debug("使用配置的默认汇率: {} = {}", rateKey, rate);
            return rate;
        }

        // 尝试计算反向汇率
        String reverseKey = toCurrency.toUpperCase() + "_" + fromCurrency.toUpperCase();
        if (defaultRates != null && defaultRates.containsKey(reverseKey)) {
            BigDecimal reverseRate = defaultRates.get(reverseKey);
            if (reverseRate.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal rate = BigDecimal.ONE.divide(reverseRate, 6, RoundingMode.HALF_UP);
                log.debug("使用反向汇率计算: {} = {} (来自 {} = {})", rateKey, rate, reverseKey, reverseRate);
                return rate;
            }
        }

        // 如果没有配置默认汇率，返回1
        log.warn("未找到默认汇率配置: {}，返回1", rateKey);
        return BigDecimal.ONE;
    }

    /**
     * 清除汇率缓存
     */
    @CacheEvict(value = "exchangeRates", allEntries = true)
    public void clearCache() {
        rateCache.clear();
        lastUpdateTime = null;
        log.info("汇率缓存已清除");
    }

    /**
     * 批量更新汇率
     */
    public void updateAllRates() {
        log.info("开始批量更新汇率");

        if (!exchangeRateConfig.getApi().isEnabled()) {
            log.warn("汇率API未启用，跳过批量更新");
            return;
        }

        for (String baseCurrency : SUPPORTED_CURRENCIES) {
            for (String targetCurrency : SUPPORTED_CURRENCIES) {
                if (!baseCurrency.equals(targetCurrency)) {
                    try {
                        BigDecimal rate = fetchExchangeRateFromApi(baseCurrency, targetCurrency);
                        if (rate != null) {
                            String rateKey = baseCurrency + "_" + targetCurrency;
                            rateCache.put(rateKey, rate);
                        }
                        // 避免API调用过于频繁
                        Thread.sleep(100);
                    } catch (Exception e) {
                        log.warn("更新汇率失败: {} -> {}", baseCurrency, targetCurrency, e);
                    }
                }
            }
        }

        lastUpdateTime = LocalDateTime.now();
        log.info("批量更新汇率完成，更新时间: {}", lastUpdateTime);
    }

    /**
     * 获取所有支持的货币对
     */
    public Map<String, BigDecimal> getSupportedRates() {
        Map<String, BigDecimal> defaultRates = exchangeRateConfig.getDefaultRates();
        return defaultRates != null ? Map.copyOf(defaultRates) : Map.of();
    }

    /**
     * 检查货币是否支持
     */
    public boolean isCurrencySupported(String currency) {
        return SUPPORTED_CURRENCIES.contains(currency.toUpperCase());
    }

    /**
     * 获取缓存状态信息
     */
    public Map<String, Object> getCacheStatus() {
        Map<String, Object> status = new ConcurrentHashMap<>();
        status.put("cacheSize", rateCache.size());
        status.put("lastUpdateTime", lastUpdateTime);
        status.put("apiEnabled", exchangeRateConfig.getApi().isEnabled());
        status.put("supportedCurrencies", SUPPORTED_CURRENCIES);
        return status;
    }
}
