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
import java.util.concurrent.CompletableFuture;
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

    private final ObjectMapper objectMapper;
    private final ExchangeRateConfig exchangeRateConfig;

    // 专用的RestTemplate，配置了合理的超时时间
    private final RestTemplate exchangeRestTemplate;

    // 支持的货币列表 - 只保留美元相关的汇率
    private static final List<String> USD_RELATED_CURRENCIES = List.of(
        "CNY", "EUR", "GBP", "JPY", "KRW", "HKD", "SGD", "AUD", "CAD"
    );

    // 缓存汇率数据，避免频繁API调用
    private final Map<String, BigDecimal> rateCache = new ConcurrentHashMap<>();
    private volatile LocalDateTime lastUpdateTime;

    /**
     * 获取汇率（优先使用缓存，避免阻塞）
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

        // 优先从本地缓存获取（避免阻塞）
        BigDecimal cachedRate = rateCache.get(rateKey);
        if (cachedRate != null) {
            log.debug("从本地缓存获取汇率: {} = {}", rateKey, cachedRate);
            return cachedRate;
        }

        // 缓存未命中时，使用默认汇率并异步更新
        log.info("汇率缓存未命中，使用默认汇率并异步更新: {}", rateKey);

        // 异步更新汇率（不阻塞当前请求）
        asyncUpdateExchangeRate(fromCurrency, toCurrency);

        // 立即返回默认汇率，避免阻塞
        return getDefaultExchangeRate(fromCurrency, toCurrency);
    }

    /**
     * 异步更新单个汇率
     */
    private void asyncUpdateExchangeRate(String fromCurrency, String toCurrency) {
        CompletableFuture.runAsync(() -> {
            try {
                BigDecimal rate = fetchExchangeRateFromApi(fromCurrency, toCurrency);
                if (rate != null && rate.compareTo(BigDecimal.ZERO) > 0) {
                    String rateKey = fromCurrency.toUpperCase() + "_" + toCurrency.toUpperCase();
                    rateCache.put(rateKey, rate);
                    lastUpdateTime = LocalDateTime.now();
                    log.info("异步更新汇率成功: {} = {}", rateKey, rate);
                }
            } catch (Exception e) {
                log.warn("异步更新汇率失败: {} -> {}", fromCurrency, toCurrency, e);
            }
        });
    }

    /**
     * 从新API获取汇率（支持重试机制）
     * 使用 https://v2.xxapi.cn/api/allrates 一次获取所有汇率
     */
    private BigDecimal fetchExchangeRateFromApi(String fromCurrency, String toCurrency) {
        // 如果不是美元相关的汇率转换，直接返回null使用默认汇率
        if (!"USD".equals(fromCurrency.toUpperCase()) && !"USD".equals(toCurrency.toUpperCase())) {
            return null;
        }

        int retryCount = exchangeRateConfig.getApi().getRetryCount();

        for (int attempt = 1; attempt <= retryCount; attempt++) {
            try {
                // 使用新的API地址
                String url = "https://v2.xxapi.cn/api/allrates";
                log.debug("调用汇率API (第{}次尝试): {}", attempt, url);

                // 使用ResponseEntity获取更详细的响应信息
                ResponseEntity<String> responseEntity = exchangeRestTemplate.getForEntity(url, String.class);

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
                if (!jsonNode.has("code") || jsonNode.get("code").asInt() != 200) {
                    log.warn("汇率API返回失败状态: {}", jsonNode.has("msg") ? jsonNode.get("msg").asText() : "未知错误");
                    continue;
                }

                JsonNode dataNode = jsonNode.get("data");
                if (dataNode == null) {
                    log.warn("汇率API响应中缺少data字段");
                    continue;
                }

                JsonNode ratesNode = dataNode.get("rates");
                if (ratesNode == null) {
                    log.warn("汇率API响应中缺少rates字段");
                    continue;
                }

                // 根据货币对获取汇率
                BigDecimal rate = null;
                if ("USD".equals(fromCurrency.toUpperCase())) {
                    // USD转其他货币
                    JsonNode targetRateNode = ratesNode.get(toCurrency.toUpperCase());
                    if (targetRateNode != null && targetRateNode.has("rate")) {
                        rate = new BigDecimal(targetRateNode.get("rate").asText());
                    }
                } else if ("USD".equals(toCurrency.toUpperCase())) {
                    // 其他货币转USD，需要计算倒数
                    JsonNode sourceRateNode = ratesNode.get(fromCurrency.toUpperCase());
                    if (sourceRateNode != null && sourceRateNode.has("rate")) {
                        BigDecimal sourceRate = new BigDecimal(sourceRateNode.get("rate").asText());
                        if (sourceRate.compareTo(BigDecimal.ZERO) > 0) {
                            rate = BigDecimal.ONE.divide(sourceRate, 6, RoundingMode.HALF_UP);
                        }
                    }
                }

                if (rate == null) {
                    log.warn("汇率API响应中缺少目标货币: {} -> {}", fromCurrency, toCurrency);
                    continue;
                }

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
     * 批量更新汇率（异步执行，避免阻塞）
     * 只更新美元相关的汇率
     */
    public void updateAllRates() {
        log.info("开始批量更新美元相关汇率");

        if (!exchangeRateConfig.getApi().isEnabled()) {
            log.warn("汇率API未启用，跳过批量更新");
            return;
        }

        // 异步执行批量更新，避免阻塞定时任务线程
        CompletableFuture.runAsync(() -> {
            try {
                // 一次性获取所有汇率数据
                Map<String, BigDecimal> allRates = fetchAllRatesFromApi();
                if (allRates != null && !allRates.isEmpty()) {
                    // 更新缓存
                    rateCache.putAll(allRates);
                    lastUpdateTime = LocalDateTime.now();
                    log.info("批量更新汇率完成，成功更新{}个汇率对, 更新时间: {}", allRates.size(), lastUpdateTime);
                } else {
                    log.warn("批量更新汇率失败，未获取到有效数据");
                }
            } catch (Exception e) {
                log.error("批量更新汇率异常", e);
            }
        });
    }

    /**
     * 一次性获取所有美元相关汇率
     */
    private Map<String, BigDecimal> fetchAllRatesFromApi() {
        Map<String, BigDecimal> rates = new ConcurrentHashMap<>();
        int retryCount = exchangeRateConfig.getApi().getRetryCount();

        for (int attempt = 1; attempt <= retryCount; attempt++) {
            try {
                String url = "https://v2.xxapi.cn/api/allrates";
                log.debug("调用汇率API获取所有汇率 (第{}次尝试): {}", attempt, url);

                ResponseEntity<String> responseEntity = exchangeRestTemplate.getForEntity(url, String.class);

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
                if (!jsonNode.has("code") || jsonNode.get("code").asInt() != 200) {
                    log.warn("汇率API返回失败状态: {}", jsonNode.has("msg") ? jsonNode.get("msg").asText() : "未知错误");
                    continue;
                }

                JsonNode dataNode = jsonNode.get("data");
                if (dataNode == null) {
                    log.warn("汇率API响应中缺少data字段");
                    continue;
                }

                JsonNode ratesNode = dataNode.get("rates");
                if (ratesNode == null) {
                    log.warn("汇率API响应中缺少rates字段");
                    continue;
                }

                // 处理美元相关的汇率
                for (String currency : USD_RELATED_CURRENCIES) {
                    JsonNode currencyNode = ratesNode.get(currency);
                    if (currencyNode != null && currencyNode.has("rate")) {
                        try {
                            BigDecimal rate = new BigDecimal(currencyNode.get("rate").asText());
                            if (rate.compareTo(BigDecimal.ZERO) > 0 && rate.compareTo(new BigDecimal("1000")) <= 0) {
                                // USD -> 其他货币
                                rates.put("USD_" + currency, rate.setScale(6, RoundingMode.HALF_UP));
                                // 其他货币 -> USD (倒数)
                                BigDecimal reverseRate = BigDecimal.ONE.divide(rate, 6, RoundingMode.HALF_UP);
                                rates.put(currency + "_USD", reverseRate);
                            }
                        } catch (Exception e) {
                            log.warn("处理货币{}汇率时出错: {}", currency, e.getMessage());
                        }
                    }
                }

                log.info("成功获取{}个美元相关汇率对 (第{}次尝试)", rates.size(), attempt);
                return rates;

            } catch (RestClientException e) {
                log.warn("汇率API调用失败 (第{}次尝试): {}", attempt, e.getMessage());
                if (attempt < retryCount) {
                    try {
                        Thread.sleep(1000 * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception e) {
                log.error("汇率API调用异常 (第{}次尝试)", attempt, e);
                break;
            }
        }

        log.error("获取所有汇率失败，已重试{}次", retryCount);
        return null;
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
        String currencyCode = currency.toUpperCase();
        return "USD".equals(currencyCode) || USD_RELATED_CURRENCIES.contains(currencyCode);
    }

    /**
     * 获取缓存状态信息
     */
    public Map<String, Object> getCacheStatus() {
        Map<String, Object> status = new ConcurrentHashMap<>();
        status.put("cacheSize", rateCache.size());
        status.put("lastUpdateTime", lastUpdateTime);
        status.put("apiEnabled", exchangeRateConfig.getApi().isEnabled());
        status.put("supportedCurrencies", USD_RELATED_CURRENCIES);
        status.put("baseCurrency", "USD");
        return status;
    }
}
