package com.instser.chat.util;

import com.instser.chat.common.exception.PaymentException;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * Stripe支付工具类
 * 提供Stripe支付相关的工具方法
 *
 * @author instser
 * @since 2025-08-29
 */
@Slf4j
public class StripeUtil {

    /**
     * 将金额转换为Stripe要求的最小货币单位（分、厘等）
     * 
     * @param amount 金额
     * @param currency 货币类型
     * @return 最小货币单位金额
     */
    public static long convertToStripeAmount(BigDecimal amount, String currency) {
        if (amount == null) {
            throw new PaymentException("金额不能为空");
        }
        
        if (currency == null || currency.trim().isEmpty()) {
            throw new PaymentException("货币类型不能为空");
        }
        
        String currencyCode = currency.toLowerCase();
        
        // 零小数位货币（不需要转换）
        if (isZeroDecimalCurrency(currencyCode)) {
            return amount.setScale(0, RoundingMode.HALF_UP).longValue();
        }
        
        // 三小数位货币
        if (isThreeDecimalCurrency(currencyCode)) {
            return amount.multiply(new BigDecimal("1000"))
                         .setScale(0, RoundingMode.HALF_UP)
                         .longValue();
        }
        
        // 默认两小数位货币（大部分货币）
        return amount.multiply(new BigDecimal("100"))
                     .setScale(0, RoundingMode.HALF_UP)
                     .longValue();
    }

    /**
     * 将Stripe金额转换为标准金额
     * 
     * @param stripeAmount Stripe最小货币单位金额
     * @param currency 货币类型
     * @return 标准金额
     */
    public static BigDecimal convertFromStripeAmount(long stripeAmount, String currency) {
        if (currency == null || currency.trim().isEmpty()) {
            throw new PaymentException("货币类型不能为空");
        }
        
        String currencyCode = currency.toLowerCase();
        
        // 零小数位货币
        if (isZeroDecimalCurrency(currencyCode)) {
            return new BigDecimal(stripeAmount);
        }
        
        // 三小数位货币
        if (isThreeDecimalCurrency(currencyCode)) {
            return new BigDecimal(stripeAmount).divide(new BigDecimal("1000"), 3, RoundingMode.HALF_UP);
        }
        
        // 默认两小数位货币
        return new BigDecimal(stripeAmount).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    /**
     * 判断是否为零小数位货币
     */
    private static boolean isZeroDecimalCurrency(String currency) {
        return ZERO_DECIMAL_CURRENCIES.contains(currency);
    }

    /**
     * 判断是否为三小数位货币
     */
    private static boolean isThreeDecimalCurrency(String currency) {
        return THREE_DECIMAL_CURRENCIES.contains(currency);
    }

    /**
     * 零小数位货币列表
     */
    private static final java.util.Set<String> ZERO_DECIMAL_CURRENCIES = java.util.Set.of(
        "jpy", // 日元
        "krw", // 韩元
        "pyg", // 巴拉圭瓜拉尼
        "rwf", // 卢旺达法郎
        "ugx", // 乌干达先令
        "vnd", // 越南盾
        "vuv", // 瓦努阿图瓦图
        "xaf", // 中非法郎
        "xof", // 西非法郎
        "xpf"  // 太平洋法郎
    );

    /**
     * 三小数位货币列表
     */
    private static final java.util.Set<String> THREE_DECIMAL_CURRENCIES = java.util.Set.of(
        "bhd", // 巴林第纳尔
        "jod", // 约旦第纳尔
        "kwd", // 科威特第纳尔
        "omr", // 阿曼里亚尔
        "tnd"  // 突尼斯第纳尔
    );

    /**
     * 获取Stripe测试卡号映射
     */
    public static Map<String, String> getTestCards() {
        Map<String, String> testCards = new HashMap<>();
        
        // 成功测试卡
        testCards.put("visa_success", "4242424242424242");
        testCards.put("visa_debit", "4000056655665556");
        testCards.put("mastercard", "5555555555554444");
        testCards.put("amex", "378282246310005");
        testCards.put("discover", "6011111111111117");
        
        // 失败测试卡
        testCards.put("declined_card", "4000000000000002");
        testCards.put("insufficient_funds", "4000000000009995");
        testCards.put("expired_card", "4000000000000069");
        testCards.put("incorrect_cvc", "4000000000000127");
        testCards.put("processing_error", "4000000000000119");
        
        // 3D Secure测试卡
        testCards.put("3ds_required", "4000002500003155");
        testCards.put("3ds_optional", "4000002760003184");
        
        return testCards;
    }

    /**
     * 获取货币显示名称
     */
    public static String getCurrencyDisplayName(String currency) {
        if (currency == null) {
            return "未知货币";
        }
        
        Map<String, String> currencyNames = new HashMap<>();
        currencyNames.put("usd", "美元");
        currencyNames.put("eur", "欧元");
        currencyNames.put("gbp", "英镑");
        currencyNames.put("jpy", "日元");
        currencyNames.put("cad", "加元");
        currencyNames.put("aud", "澳元");
        currencyNames.put("chf", "瑞士法郎");
        currencyNames.put("sek", "瑞典克朗");
        currencyNames.put("nok", "挪威克朗");
        currencyNames.put("dkk", "丹麦克朗");
        
        return currencyNames.getOrDefault(currency.toLowerCase(), currency.toUpperCase());
    }

    /**
     * 格式化金额显示
     */
    public static String formatAmount(BigDecimal amount, String currency) {
        if (amount == null || currency == null) {
            return "0.00";
        }
        
        String currencyCode = currency.toLowerCase();
        
        // 零小数位货币
        if (isZeroDecimalCurrency(currencyCode)) {
            return String.format("%.0f %s", amount, currency.toUpperCase());
        }
        
        // 三小数位货币
        if (isThreeDecimalCurrency(currencyCode)) {
            return String.format("%.3f %s", amount, currency.toUpperCase());
        }
        
        // 默认两小数位货币
        return String.format("%.2f %s", amount, currency.toUpperCase());
    }

    /**
     * 验证Stripe事件ID格式
     */
    public static boolean isValidStripeEventId(String eventId) {
        if (eventId == null || eventId.trim().isEmpty()) {
            return false;
        }
        
        // Stripe事件ID格式：evt_xxxxxxxxxxxxxxxx
        return eventId.matches("^evt_[a-zA-Z0-9]{16,}$");
    }

    /**
     * 验证PaymentIntent ID格式
     */
    public static boolean isValidPaymentIntentId(String paymentIntentId) {
        if (paymentIntentId == null || paymentIntentId.trim().isEmpty()) {
            return false;
        }
        
        // PaymentIntent ID格式：pi_xxxxxxxxxxxxxxxx
        return paymentIntentId.matches("^pi_[a-zA-Z0-9]{16,}$");
    }

    /**
     * 生成Stripe客户端参考标识
     */
    public static String generateClientReferenceId(Long userId, String orderNo) {
        if (userId == null || orderNo == null) {
            throw new PaymentException("用户ID和订单号不能为空");
        }
        
        return String.format("user_%d_order_%s", userId, orderNo);
    }

    /**
     * 解析Stripe客户端参考标识
     */
    public static Map<String, String> parseClientReferenceId(String clientReferenceId) {
        Map<String, String> result = new HashMap<>();
        
        if (clientReferenceId == null || !clientReferenceId.contains("_")) {
            return result;
        }
        
        try {
            // 格式：user_123_order_ORD_xxx
            String[] parts = clientReferenceId.split("_");
            if (parts.length >= 4 && "user".equals(parts[0]) && "order".equals(parts[2])) {
                result.put("userId", parts[1]);
                result.put("orderNo", String.join("_", java.util.Arrays.copyOfRange(parts, 3, parts.length)));
            }
        } catch (Exception e) {
            log.warn("解析Stripe客户端参考标识失败: {}", clientReferenceId, e);
        }
        
        return result;
    }
}