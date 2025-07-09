package com.instser.chat.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 汇率服务测试
 *
 * @author instser
 * @since 2025-07-08
 */
@SpringBootTest
public class ExchangeRateServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private ExchangeRateService exchangeRateService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        exchangeRateService = new ExchangeRateService(restTemplate);
    }

    @Test
    void testSameCurrencyReturnsOne() {
        // 测试相同货币返回1
        BigDecimal rate = exchangeRateService.getExchangeRate("CNY", "CNY");
        assertEquals(BigDecimal.ONE, rate);

        rate = exchangeRateService.getExchangeRate("USD", "USD");
        assertEquals(BigDecimal.ONE, rate);
    }

    @Test
    void testCnyToUsdConversion() {
        // 测试CNY到USD的转换
        BigDecimal rate = exchangeRateService.getExchangeRate("CNY", "USD");
        assertNotNull(rate);
        assertTrue(rate.compareTo(BigDecimal.ZERO) > 0);
        
        // 应该返回默认汇率0.14或API获取的汇率
        assertTrue(rate.compareTo(new BigDecimal("0.10")) > 0);
        assertTrue(rate.compareTo(new BigDecimal("0.20")) < 0);
    }

    @Test
    void testUsdToCnyConversion() {
        // 测试USD到CNY的转换
        BigDecimal rate = exchangeRateService.getExchangeRate("USD", "CNY");
        assertNotNull(rate);
        assertTrue(rate.compareTo(BigDecimal.ZERO) > 0);
        
        // 应该返回默认汇率7.14或API获取的汇率
        assertTrue(rate.compareTo(new BigDecimal("6.00")) > 0);
        assertTrue(rate.compareTo(new BigDecimal("8.00")) < 0);
    }

    @Test
    void testUnsupportedCurrencyReturnsOne() {
        // 测试不支持的货币返回1
        BigDecimal rate = exchangeRateService.getExchangeRate("CNY", "EUR");
        assertEquals(BigDecimal.ONE, rate);

        rate = exchangeRateService.getExchangeRate("JPY", "USD");
        assertEquals(BigDecimal.ONE, rate);
    }

    @Test
    void testNullCurrencyReturnsOne() {
        // 测试空货币返回1
        BigDecimal rate = exchangeRateService.getExchangeRate(null, "USD");
        assertEquals(BigDecimal.ONE, rate);

        rate = exchangeRateService.getExchangeRate("CNY", null);
        assertEquals(BigDecimal.ONE, rate);
    }

    @Test
    void testCurrencySupport() {
        // 测试货币支持检查
        assertTrue(exchangeRateService.isCurrencySupported("CNY"));
        assertTrue(exchangeRateService.isCurrencySupported("USD"));
        assertTrue(exchangeRateService.isCurrencySupported("cny"));
        assertTrue(exchangeRateService.isCurrencySupported("usd"));
        
        assertFalse(exchangeRateService.isCurrencySupported("EUR"));
        assertFalse(exchangeRateService.isCurrencySupported("JPY"));
        assertFalse(exchangeRateService.isCurrencySupported(null));
    }

    @Test
    void testPriceConversion() {
        // 测试价格转换
        BigDecimal cnyPrice = new BigDecimal("100.00");
        
        // CNY到CNY应该返回原价格
        BigDecimal convertedPrice = exchangeRateService.convertPrice(cnyPrice, "CNY", "CNY");
        assertEquals(cnyPrice, convertedPrice);
        
        // CNY到USD应该返回转换后的价格
        convertedPrice = exchangeRateService.convertPrice(cnyPrice, "CNY", "USD");
        assertNotNull(convertedPrice);
        assertTrue(convertedPrice.compareTo(BigDecimal.ZERO) > 0);
        assertTrue(convertedPrice.compareTo(cnyPrice) < 0); // USD价格应该小于CNY价格
        
        // 测试零价格
        convertedPrice = exchangeRateService.convertPrice(BigDecimal.ZERO, "CNY", "USD");
        assertEquals(BigDecimal.ZERO, convertedPrice);
        
        // 测试空价格
        convertedPrice = exchangeRateService.convertPrice(null, "CNY", "USD");
        assertEquals(BigDecimal.ZERO, convertedPrice);
    }

    @Test
    void testPriceConversionPrecision() {
        // 测试价格转换精度
        BigDecimal cnyPrice = new BigDecimal("69.99");
        BigDecimal convertedPrice = exchangeRateService.convertPrice(cnyPrice, "CNY", "USD");
        
        // 检查小数位数是否为2位
        assertEquals(2, convertedPrice.scale());
        
        // 检查转换结果是否合理（基于默认汇率0.14）
        BigDecimal expectedPrice = cnyPrice.multiply(new BigDecimal("0.14")).setScale(2, java.math.RoundingMode.HALF_UP);
        // 允许一定的误差范围（考虑到可能使用实时汇率）
        assertTrue(Math.abs(convertedPrice.doubleValue() - expectedPrice.doubleValue()) < 5.0);
    }
}
