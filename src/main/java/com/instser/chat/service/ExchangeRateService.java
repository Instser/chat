package com.instser.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 汇率服务 - 简化版本，只支持CNY和USD转换
 *
 * @author instser
 * @since 2025-07-08
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final RestTemplate restTemplate;

    // 默认汇率：1 CNY = 0.14 USD (1 USD = 7.14 CNY)
    private static final BigDecimal DEFAULT_CNY_TO_USD = new BigDecimal("0.14");
    private static final BigDecimal DEFAULT_USD_TO_CNY = new BigDecimal("7.14");

    /**
     * 获取汇率
     * 支持CNY和USD互转，其他货币返回1
     *
     * @param fromCurrency 源货币
     * @param toCurrency   目标货币
     * @return 汇率
     */
    @Cacheable(value = "exchangeRates", key = "#fromCurrency + '_' + #toCurrency")
    public BigDecimal getExchangeRate(String fromCurrency, String toCurrency) {
        if (fromCurrency == null || toCurrency == null) {
            log.warn("货币类型不能为空，fromCurrency: {}, toCurrency: {}", fromCurrency, toCurrency);
            return BigDecimal.ONE;
        }

        // 相同货币直接返回1
        if (fromCurrency.equalsIgnoreCase(toCurrency)) {
            return BigDecimal.ONE;
        }

        String from = fromCurrency.toUpperCase();
        String to = toCurrency.toUpperCase();

        // 只支持CNY和USD互转
        if ("CNY".equals(from) && "USD".equals(to)) {
            return getCnyToUsdRate();
        } else if ("USD".equals(from) && "CNY".equals(to)) {
            return getUsdToCnyRate();
        } else {
            log.warn("不支持的货币转换: {} -> {}, 返回默认汇率1", from, to);
            return BigDecimal.ONE;
        }
    }

    /**
     * 获取CNY到USD的汇率
     */
    private BigDecimal getCnyToUsdRate() {
        try {
            // 尝试从API获取实时汇率
            BigDecimal rate = fetchRateFromApi("CNY", "USD");
            if (rate != null && rate.compareTo(BigDecimal.ZERO) > 0) {
                log.debug("获取实时汇率成功: 1 CNY = {} USD", rate);
                return rate;
            }
        } catch (Exception e) {
            log.warn("获取实时汇率失败，使用默认汇率: {}", e.getMessage());
        }

        log.debug("使用默认汇率: 1 CNY = {} USD", DEFAULT_CNY_TO_USD);
        return DEFAULT_CNY_TO_USD;
    }

    /**
     * 获取USD到CNY的汇率
     */
    private BigDecimal getUsdToCnyRate() {
        try {
            // 尝试从API获取实时汇率
            BigDecimal rate = fetchRateFromApi("USD", "CNY");
            if (rate != null && rate.compareTo(BigDecimal.ZERO) > 0) {
                log.debug("获取实时汇率成功: 1 USD = {} CNY", rate);
                return rate;
            }
        } catch (Exception e) {
            log.warn("获取实时汇率失败，使用默认汇率: {}", e.getMessage());
        }

        log.debug("使用默认汇率: 1 USD = {} CNY", DEFAULT_USD_TO_CNY);
        return DEFAULT_USD_TO_CNY;
    }

    /**
     * 从API获取汇率（简化实现）
     */
    private BigDecimal fetchRateFromApi(String from, String to) {
        try {
            // 使用免费的汇率API
            String url = "https://api.exchangerate-api.com/v4/latest/" + from;
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // 简单的JSON解析（实际项目中应该使用ObjectMapper）
                String body = response.getBody();
                String searchKey = "\"" + to + "\":";
                int startIndex = body.indexOf(searchKey);
                if (startIndex != -1) {
                    startIndex += searchKey.length();
                    int endIndex = body.indexOf(",", startIndex);
                    if (endIndex == -1) {
                        endIndex = body.indexOf("}", startIndex);
                    }
                    if (endIndex != -1) {
                        String rateStr = body.substring(startIndex, endIndex).trim();
                        return new BigDecimal(rateStr).setScale(4, RoundingMode.HALF_UP);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("API获取汇率失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 检查货币是否支持
     */
    public boolean isCurrencySupported(String currency) {
        if (currency == null) {
            return false;
        }
        String curr = currency.toUpperCase();
        return "CNY".equals(curr) || "USD".equals(curr);
    }

    /**
     * 转换价格
     */
    public BigDecimal convertPrice(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal rate = getExchangeRate(fromCurrency, toCurrency);
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }
}
