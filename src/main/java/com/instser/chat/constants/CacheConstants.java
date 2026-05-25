package com.instser.chat.constants;

/**
 * Redis Key 命名规范常量
 * 格式：{项目}:{模块}:{类型}:{标识}
 * 示例：chat:user:account:12345
 */
public final class CacheConstants {

    public static final String PROJECT = "chat";

    // ===== 用户相关 =====
    public static final String USER_ACCOUNT = PROJECT + ":user:account:";
    public static final String USER_PACKAGE = PROJECT + ":user:package:";
    public static final String USER_DAILY_USAGE = PROJECT + ":user:daily_usage:";
    public static final String USER_REMAINING = PROJECT + ":user:remaining:";
    public static final String USER_PAYMENT_LOCK = PROJECT + ":user:payment:lock:";

    // ===== 会话相关 =====
    public static final String SESSION_MESSAGES = PROJECT + ":session:messages:";
    public static final String CHAT_SESSION = PROJECT + ":chat:session:";
    public static final String CHAT_MESSAGES = PROJECT + ":chat:messages:";
    public static final String CHAT_RATE_LIMIT = PROJECT + ":chat:rate_limit:";

    // ===== 分布式锁 =====
    public static final String LOCK_ORDER = PROJECT + ":lock:order:";
    public static final String LOCK_USAGE = PROJECT + ":lock:usage:";

    // ===== AI 模型相关 =====
    public static final String MODEL_PERMISSION = PROJECT + ":model:permission:";
    public static final String AI_MODELS = PROJECT + ":ai:models:";
    public static final String AI_PERMISSIONS = PROJECT + ":ai:permissions:";

    // ===== 套餐相关 =====
    public static final String PACKAGE_CONFIG = PROJECT + ":package:config:";
    public static final String PACKAGE_PERMISSIONS = PROJECT + ":package:permissions:";
    public static final String DAILY_USAGE = PROJECT + ":daily:usage:";

    // ===== 支付相关 =====
    public static final String PAYMENT_FREQ = PROJECT + ":payment:freq:";
    public static final String PAYMENT_CALLBACK = PROJECT + ":payment:callback:";
    public static final String PAYMENT_SESSION = PROJECT + ":payment:session:";
    public static final String PAYMENT_SUSPICIOUS = PROJECT + ":payment:suspicious:";
    public static final String PAYMENT_RATE_LIMIT = PROJECT + ":payment:rate_limit:";
    public static final String PAYMENT_DAILY_LIMIT = PROJECT + ":payment:daily_limit:";
    public static final String PAYMENT_DAILY_AMOUNT = PROJECT + ":payment:daily_amount:";
    public static final String PAYPAL_ACCESS_TOKEN = PROJECT + ":paypal:access_token:";
    public static final String PAYPAL_TOKEN_LOCK = PROJECT + ":paypal:token_lock:";
    public static final String ORDER_RECENT_PENDING = PROJECT + ":order:recent_pending:";

    // ===== 联网搜索相关 =====
    public static final String WEBSEARCH_PERMISSION = PROJECT + ":websearch:permission:";
    public static final String WEBSEARCH_RATE_LIMIT = PROJECT + ":websearch:rate_limit:";

    // ===== 缓存标记 =====
    public static final String CACHE_PREFIX = PROJECT + ":cache:";
    public static final String EXCHANGE_RATES = "exchangeRates";

    // ===== 熔断器状态 =====
    public static final String CIRCUIT_BREAKER = PROJECT + ":circuit:";

    private CacheConstants() {}

    // 构造 key 的工厂方法
    public static String userAccount(Long userId) {
        return USER_ACCOUNT + userId;
    }

    public static String orderLock(String orderNo) {
        return LOCK_ORDER + orderNo;
    }

    public static String userPaymentLock(Long userId) {
        return USER_PAYMENT_LOCK + userId;
    }
}
