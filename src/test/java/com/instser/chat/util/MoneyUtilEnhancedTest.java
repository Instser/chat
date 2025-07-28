package com.instser.chat.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MoneyUtil 增强功能测试
 *
 * @author instser
 * @since 2025-07-28
 */
class MoneyUtilEnhancedTest {

    @Test
    void testIsYiPayMethod() {
        // 测试易支付方式识别
        assertTrue(MoneyUtil.isYiPayMethod("alipay"));
        assertTrue(MoneyUtil.isYiPayMethod("wechatpay"));
        assertTrue(MoneyUtil.isYiPayMethod("ALIPAY"));
        assertTrue(MoneyUtil.isYiPayMethod("WECHATPAY"));
        
        assertFalse(MoneyUtil.isYiPayMethod("overseas_alipay"));
        assertFalse(MoneyUtil.isYiPayMethod("paypal"));
        assertFalse(MoneyUtil.isYiPayMethod("stripe"));
        assertFalse(MoneyUtil.isYiPayMethod(null));
        assertFalse(MoneyUtil.isYiPayMethod(""));
    }

    @Test
    void testNeedsCurrencyConversion() {
        // 易支付 + CNY = 需要转换
        assertTrue(MoneyUtil.needsCurrencyConversion("alipay", "CNY"));
        assertTrue(MoneyUtil.needsCurrencyConversion("wechatpay", "CNY"));
        assertTrue(MoneyUtil.needsCurrencyConversion("ALIPAY", "cny"));
        
        // 易支付 + USD = 不需要转换
        assertFalse(MoneyUtil.needsCurrencyConversion("alipay", "USD"));
        assertFalse(MoneyUtil.needsCurrencyConversion("wechatpay", "USD"));
        
        // 非易支付 + CNY = 不需要转换
        assertFalse(MoneyUtil.needsCurrencyConversion("overseas_alipay", "CNY"));
        assertFalse(MoneyUtil.needsCurrencyConversion("paypal", "CNY"));
        
        // 边界情况
        assertFalse(MoneyUtil.needsCurrencyConversion(null, "CNY"));
        assertFalse(MoneyUtil.needsCurrencyConversion("alipay", null));
        assertFalse(MoneyUtil.needsCurrencyConversion(null, null));
    }

    @Test
    void testGetDefaultCurrencyForPaymentMethod() {
        // 易支付方式应该返回CNY
        assertEquals("CNY", MoneyUtil.getDefaultCurrencyForPaymentMethod("alipay"));
        assertEquals("CNY", MoneyUtil.getDefaultCurrencyForPaymentMethod("wechatpay"));
        assertEquals("CNY", MoneyUtil.getDefaultCurrencyForPaymentMethod("ALIPAY"));
        
        // 其他支付方式应该返回USD
        assertEquals("USD", MoneyUtil.getDefaultCurrencyForPaymentMethod("overseas_alipay"));
        assertEquals("USD", MoneyUtil.getDefaultCurrencyForPaymentMethod("paypal"));
        assertEquals("USD", MoneyUtil.getDefaultCurrencyForPaymentMethod("stripe"));
        assertEquals("USD", MoneyUtil.getDefaultCurrencyForPaymentMethod(null));
        assertEquals("USD", MoneyUtil.getDefaultCurrencyForPaymentMethod(""));
    }

    @Test
    void testFormatAmountForYiPay() {
        // 测试易支付金额格式化
        assertEquals("9.99", MoneyUtil.formatAmountForYiPay(new BigDecimal("9.99")));
        assertEquals("10.00", MoneyUtil.formatAmountForYiPay(new BigDecimal("10")));
        assertEquals("0.01", MoneyUtil.formatAmountForYiPay(new BigDecimal("0.01")));
        assertEquals("999.99", MoneyUtil.formatAmountForYiPay(new BigDecimal("999.99")));
        
        // 测试精度处理
        assertEquals("9.99", MoneyUtil.formatAmountForYiPay(new BigDecimal("9.994")));
        assertEquals("10.00", MoneyUtil.formatAmountForYiPay(new BigDecimal("9.995")));
        
        // 测试null值
        assertEquals("0.00", MoneyUtil.formatAmountForYiPay(null));
    }

    @Test
    void testFormatAmountForAlipay() {
        // 测试支付宝金额格式化
        assertEquals("9.99", MoneyUtil.formatAmountForAlipay(new BigDecimal("9.99")));
        assertEquals("10.00", MoneyUtil.formatAmountForAlipay(new BigDecimal("10")));
        assertEquals("0.01", MoneyUtil.formatAmountForAlipay(new BigDecimal("0.01")));
        assertEquals("999.99", MoneyUtil.formatAmountForAlipay(new BigDecimal("999.99")));
        
        // 测试精度处理
        assertEquals("9.99", MoneyUtil.formatAmountForAlipay(new BigDecimal("9.994")));
        assertEquals("10.00", MoneyUtil.formatAmountForAlipay(new BigDecimal("9.995")));
        
        // 测试null值
        assertEquals("0.00", MoneyUtil.formatAmountForAlipay(null));
    }

    @Test
    void testFormatAmount() {
        // 测试USD格式化
        BigDecimal amount = new BigDecimal("9.999");
        assertEquals(new BigDecimal("10.00"), MoneyUtil.formatAmount(amount, "USD"));
        
        // 测试CNY格式化
        assertEquals(new BigDecimal("10.00"), MoneyUtil.formatAmount(amount, "CNY"));
        
        // 测试null货币类型（默认USD）
        assertEquals(new BigDecimal("10.00"), MoneyUtil.formatAmount(amount, null));
        
        // 测试null金额
        assertEquals(BigDecimal.ZERO, MoneyUtil.formatAmount(null, "USD"));
    }

    @Test
    void testIsZeroOrPositive() {
        // 测试正数
        assertTrue(MoneyUtil.isZeroOrPositive(new BigDecimal("10.00")));
        assertTrue(MoneyUtil.isZeroOrPositive(new BigDecimal("0.01")));
        
        // 测试零
        assertTrue(MoneyUtil.isZeroOrPositive(BigDecimal.ZERO));
        assertTrue(MoneyUtil.isZeroOrPositive(new BigDecimal("0.00")));
        
        // 测试负数
        assertFalse(MoneyUtil.isZeroOrPositive(new BigDecimal("-0.01")));
        assertFalse(MoneyUtil.isZeroOrPositive(new BigDecimal("-10.00")));
        
        // 测试null
        assertFalse(MoneyUtil.isZeroOrPositive(null));
    }

    @Test
    void testIsZero() {
        // 测试零值
        assertTrue(MoneyUtil.isZero(BigDecimal.ZERO));
        assertTrue(MoneyUtil.isZero(new BigDecimal("0.00")));
        assertTrue(MoneyUtil.isZero(new BigDecimal("0")));
        
        // 测试非零值
        assertFalse(MoneyUtil.isZero(new BigDecimal("0.01")));
        assertFalse(MoneyUtil.isZero(new BigDecimal("-0.01")));
        assertFalse(MoneyUtil.isZero(new BigDecimal("10.00")));
        
        // 测试null
        assertFalse(MoneyUtil.isZero(null));
    }

    @Test
    void testCurrencyScaling() {
        // 测试不同货币的精度处理
        BigDecimal amount = new BigDecimal("9.999");
        
        // USD应该是2位小数
        BigDecimal usdAmount = MoneyUtil.formatAmount(amount, "USD");
        assertEquals(2, usdAmount.scale());
        assertEquals(new BigDecimal("10.00"), usdAmount);
        
        // CNY应该是2位小数
        BigDecimal cnyAmount = MoneyUtil.formatAmount(amount, "CNY");
        assertEquals(2, cnyAmount.scale());
        assertEquals(new BigDecimal("10.00"), cnyAmount);
        
        // 未知货币应该使用USD精度
        BigDecimal unknownAmount = MoneyUtil.formatAmount(amount, "EUR");
        assertEquals(2, unknownAmount.scale());
        assertEquals(new BigDecimal("10.00"), unknownAmount);
    }

    @Test
    void testPaymentMethodCaseInsensitive() {
        // 测试支付方式大小写不敏感
        assertTrue(MoneyUtil.isYiPayMethod("alipay"));
        assertTrue(MoneyUtil.isYiPayMethod("ALIPAY"));
        assertTrue(MoneyUtil.isYiPayMethod("Alipay"));
        assertTrue(MoneyUtil.isYiPayMethod("AliPay"));
        
        assertTrue(MoneyUtil.isYiPayMethod("wechatpay"));
        assertTrue(MoneyUtil.isYiPayMethod("WECHATPAY"));
        assertTrue(MoneyUtil.isYiPayMethod("WechatPay"));
        assertTrue(MoneyUtil.isYiPayMethod("WeChatPay"));
    }

    @Test
    void testCurrencyConversionCaseInsensitive() {
        // 测试货币大小写不敏感
        assertTrue(MoneyUtil.needsCurrencyConversion("alipay", "cny"));
        assertTrue(MoneyUtil.needsCurrencyConversion("alipay", "CNY"));
        assertTrue(MoneyUtil.needsCurrencyConversion("alipay", "Cny"));
        
        assertFalse(MoneyUtil.needsCurrencyConversion("alipay", "usd"));
        assertFalse(MoneyUtil.needsCurrencyConversion("alipay", "USD"));
        assertFalse(MoneyUtil.needsCurrencyConversion("alipay", "Usd"));
    }
}
