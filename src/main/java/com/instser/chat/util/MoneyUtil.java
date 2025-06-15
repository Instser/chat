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
     * 格式化美元金额
     *
     * @param amount 金额
     * @return 格式化后的金额
     */
    public static BigDecimal formatUSD(BigDecimal amount) {
        return formatAmount(amount, "USD");
    }

    /**
     * 格式化人民币金额
     *
     * @param amount 金额
     * @return 格式化后的金额
     */
    public static BigDecimal formatCNY(BigDecimal amount) {
        return formatAmount(amount, "CNY");
    }

    /**
     * 金额相加
     *
     * @param amount1  金额1
     * @param amount2  金额2
     * @param currency 货币类型
     * @return 相加结果
     */
    public static BigDecimal add(BigDecimal amount1, BigDecimal amount2, String currency) {
        if (amount1 == null) amount1 = BigDecimal.ZERO;
        if (amount2 == null) amount2 = BigDecimal.ZERO;

        BigDecimal result = amount1.add(amount2);
        return formatAmount(result, currency);
    }

    /**
     * 金额相减
     *
     * @param amount1  被减数
     * @param amount2  减数
     * @param currency 货币类型
     * @return 相减结果
     */
    public static BigDecimal subtract(BigDecimal amount1, BigDecimal amount2, String currency) {
        if (amount1 == null) amount1 = BigDecimal.ZERO;
        if (amount2 == null) amount2 = BigDecimal.ZERO;

        BigDecimal result = amount1.subtract(amount2);
        return formatAmount(result, currency);
    }

    /**
     * 金额相乘
     *
     * @param amount     金额
     * @param multiplier 乘数
     * @param currency   货币类型
     * @return 相乘结果
     */
    public static BigDecimal multiply(BigDecimal amount, BigDecimal multiplier, String currency) {
        if (amount == null) amount = BigDecimal.ZERO;
        if (multiplier == null) multiplier = BigDecimal.ONE;

        BigDecimal result = amount.multiply(multiplier);
        return formatAmount(result, currency);
    }

    /**
     * 金额相除
     *
     * @param amount   被除数
     * @param divisor  除数
     * @param currency 货币类型
     * @return 相除结果
     */
    public static BigDecimal divide(BigDecimal amount, BigDecimal divisor, String currency) {
        if (amount == null) amount = BigDecimal.ZERO;
        if (divisor == null || divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("除数不能为空或零");
        }

        int scale = getScaleByCurrency(currency);
        BigDecimal result = amount.divide(divisor, scale + 2, DEFAULT_ROUNDING_MODE);
        return formatAmount(result, currency);
    }

    /**
     * 比较金额大小
     *
     * @param amount1 金额1
     * @param amount2 金额2
     * @return 比较结果（-1: amount1 < amount2, 0: amount1 = amount2, 1: amount1 > amount2）
     */
    public static int compare(BigDecimal amount1, BigDecimal amount2) {
        if (amount1 == null) amount1 = BigDecimal.ZERO;
        if (amount2 == null) amount2 = BigDecimal.ZERO;

        return amount1.compareTo(amount2);
    }

    /**
     * 检查金额是否为正数
     *
     * @param amount 金额
     * @return 是否为正数
     */
    public static boolean isPositive(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
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
     * 将金额转换为字符串（用于显示）
     *
     * @param amount   金额
     * @param currency 货币类型
     * @return 格式化的金额字符串
     */
    public static String toString(BigDecimal amount, String currency) {
        BigDecimal formattedAmount = formatAmount(amount, currency);
        return formattedAmount.toPlainString();
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
}
