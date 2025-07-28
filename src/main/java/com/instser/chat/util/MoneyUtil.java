package com.instser.chat.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金额处理工具类
 *
 * @author instser
 * @since 2024-01-01
 */
public class MoneyUtil {

    /**
     * 美元精度（2位小数）
     */
    public static final int USD_SCALE = 2;

    /**
     * 人民币精度（2位小数）
     */
    public static final int CNY_SCALE = 2;

    /**
     * 默认舍入模式（四舍五入）
     */
    public static final RoundingMode DEFAULT_ROUNDING_MODE = RoundingMode.HALF_UP;

    /**
     * 格式化金额（根据货币类型）
     *
     * @param amount   金额
     * @param currency 货币类型
     * @return 格式化后的金额
     */
    public static BigDecimal formatAmount(BigDecimal amount, String currency) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }

        int scale = getScaleByCurrency(currency);
        return amount.setScale(scale, DEFAULT_ROUNDING_MODE);
    }



    /**
     * 检查金额是否为零或正数
     *
     * @param amount 金额
     * @return 是否为零或正数
     */
    public static boolean isZeroOrPositive(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) >= 0;
    }

    /**
     * 检查金额是否为零
     *
     * @param amount 金额
     * @return 是否为零
     */
    public static boolean isZero(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * 根据货币类型获取精度
     *
     * @param currency 货币类型
     * @return 精度位数
     */
    private static int getScaleByCurrency(String currency) {
        if (currency == null) {
            return USD_SCALE;
        }

        switch (currency.toUpperCase()) {
            case "USD":
                return USD_SCALE;
            case "CNY":
                return CNY_SCALE;
            default:
                return USD_SCALE; // 默认使用美元精度
        }
    }



    /**
     * 格式化金额用于支付宝API
     *
     * @param amount 金额
     * @return 格式化后的金额字符串
     */
    public static String formatAmountForAlipay(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return amount.setScale(2, DEFAULT_ROUNDING_MODE).toPlainString();
    }

    /**
     * 格式化金额用于易支付API
     *
     * @param amount 金额
     * @return 格式化后的金额字符串
     */
    public static String formatAmountForYiPay(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return amount.setScale(2, DEFAULT_ROUNDING_MODE).toPlainString();
    }

    /**
     * 检查是否为易支付方式
     *
     * @param paymentMethod 支付方式
     * @return 是否为易支付
     */
    public static boolean isYiPayMethod(String paymentMethod) {
        if (paymentMethod == null) {
            return false;
        }
        String method = paymentMethod.toLowerCase();
        return "alipay".equals(method) || "wechatpay".equals(method);
    }

    /**
     * 检查是否需要汇率转换
     * 易支付方式或目标货币为CNY时需要转换
     *
     * @param paymentMethod 支付方式
     * @param targetCurrency 目标货币
     * @return 是否需要转换
     */
    public static boolean needsCurrencyConversion(String paymentMethod, String targetCurrency) {
        // 如果目标货币为CNY，则需要转换
        if ("CNY".equalsIgnoreCase(targetCurrency)) {
            return true;
        }
        // 如果是易支付方式，也需要转换为CNY
        return isYiPayMethod(paymentMethod);
    }

    /**
     * 获取支付方式对应的默认货币
     *
     * @param paymentMethod 支付方式
     * @return 默认货币
     */
    public static String getDefaultCurrencyForPaymentMethod(String paymentMethod) {
        if (isYiPayMethod(paymentMethod)) {
            return "CNY"; // 易支付使用人民币
        }
        return "USD"; // 其他支付方式使用美元
    }
}
