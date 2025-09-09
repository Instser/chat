package com.instser.chat.service.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 全局缓存管理器
 * 统一管理系统中所有缓存实例和策略
 * 
 * @author instser
 * @since 2025-07-24
 */
@Slf4j
@Component
public class GlobalCacheManager {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public RedisCircuitBreaker circuitBreaker;

    @Autowired
    private CacheManager springCacheManager;

    @Autowired
    public HybridCacheManager hybridCacheManager;

    // 缓存注册表：存储所有已注册的缓存实例
    private final Map<String, CacheInstance> cacheRegistry = new ConcurrentHashMap<>();

    // 业务模块缓存映射
    private final Map<String, Set<String>> moduleToCache = new ConcurrentHashMap<>();

    // 缓存类型统计
    private final Map<CacheType, Set<String>> typeToCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 注册系统内置缓存
        registerBuiltinCaches();
        log.info("全局缓存管理器初始化完成，已注册{}个缓存实例", cacheRegistry.size());
    }

    /**
     * 注册系统内置缓存
     */
    private void registerBuiltinCaches() {
        // 1. 支付模块缓存
        registerCache("payment", "payment:freq", CacheType.HYBRID, "支付频率限制缓存", Duration.ofMinutes(10));
        registerCache("payment", "payment:callback", CacheType.HYBRID, "支付回调防重放缓存", Duration.ofMinutes(60));
        registerCache("payment", "payment:session", CacheType.HYBRID, "支付会话管理缓存", Duration.ofMinutes(5));
        registerCache("payment", "payment:suspicious", CacheType.HYBRID, "可疑行为记录缓存", Duration.ofDays(7));

        // 2. 汇率服务缓存
        registerCache("exchange", "exchangeRates", CacheType.SPRING_CACHE, "汇率数据缓存", Duration.ofMinutes(30));

        // 3. 网络搜索缓存
        registerCache("websearch", "websearch:permission", CacheType.LOCAL, "网络搜索权限缓存", Duration.ofMinutes(5));

        // 4. 用户账户缓存（潜在优化点）
        registerCache("user", "user:account", CacheType.POTENTIAL, "用户账户信息缓存", Duration.ofMinutes(3));
        registerCache("user", "user:package", CacheType.POTENTIAL, "用户套餐信息缓存", Duration.ofMinutes(3));

        // 5. 聊天相关缓存（潜在优化点）
        registerCache("chat", "chat:session", CacheType.POTENTIAL, "聊天会话缓存", Duration.ofMinutes(30));
        registerCache("chat", "chat:messages", CacheType.POTENTIAL, "聊天消息缓存", Duration.ofMinutes(15));
        registerCache("chat", "chat:ratelimit", CacheType.POTENTIAL, "聊天频率限制缓存", Duration.ofMinutes(1));

        // 6. AI模型缓存（潜在优化点）
        registerCache("ai", "ai:models", CacheType.POTENTIAL, "AI模型配置缓存", Duration.ofHours(1));
        registerCache("ai", "ai:permissions", CacheType.POTENTIAL, "AI模型权限缓存", Duration.ofMinutes(3));

        // 7. 套餐管理缓存（潜在优化点）
        registerCache("package", "package:config", CacheType.POTENTIAL, "套餐配置缓存", Duration.ofHours(1));
        registerCache("package", "package:permissions", CacheType.POTENTIAL, "套餐权限缓存", Duration.ofMinutes(3));

        // 8. 订单管理缓存
        registerCache("order", "order:recent_pending", CacheType.HYBRID, "近期未支付订单缓存", Duration.ofMinutes(5));
    }

    /**
     * 注册缓存实例
     */
    public void registerCache(String module, String cacheName, CacheType type, String description, Duration ttl) {
        CacheInstance instance = CacheInstance.builder()
                .module(module)
                .name(cacheName)
                .type(type)
                .description(description)
                .ttl(ttl)
                .registeredAt(System.currentTimeMillis())
                .build();

        cacheRegistry.put(cacheName, instance);
        
        // 更新模块映射
        moduleToCache.computeIfAbsent(module, k -> new HashSet<>()).add(cacheName);
        
        // 更新类型映射
        typeToCache.computeIfAbsent(type, k -> new HashSet<>()).add(cacheName);

        log.debug("注册缓存实例: module={}, name={}, type={}", module, cacheName, type);
    }

    /**
     * 获取缓存值（统一接口）
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String cacheName, String key, Supplier<T> fallbackSupplier) {
        CacheInstance instance = cacheRegistry.get(cacheName);
        if (instance == null) {
            log.warn("未找到缓存实例: {}", cacheName);
            return fallbackSupplier.get();
        }

        try {
            switch (instance.getType()) {
                case HYBRID:
                    return getFromHybridCache(key, fallbackSupplier);
                case SPRING_CACHE:
                    return getFromSpringCache(cacheName, key, fallbackSupplier);
                case LOCAL:
                    return getFromLocalCache(cacheName, key, fallbackSupplier);
                case REDIS:
                    return getFromRedisCache(key, fallbackSupplier);
                case POTENTIAL:
                    // 潜在缓存，直接调用fallback
                    return fallbackSupplier.get();
                default:
                    return fallbackSupplier.get();
            }
        } catch (Exception e) {
            log.error("缓存获取异常，cacheName: {}, key: {}", cacheName, key, e);
            return fallbackSupplier.get();
        }
    }

    /**
     * 设置缓存值（统一接口）
     */
    public void put(String cacheName, String key, Object value) {
        CacheInstance instance = cacheRegistry.get(cacheName);
        if (instance == null) {
            log.warn("未找到缓存实例: {}", cacheName);
            return;
        }

        try {
            switch (instance.getType()) {
                case HYBRID:
                    putToHybridCache(key, value);
                    break;
                case SPRING_CACHE:
                    putToSpringCache(cacheName, key, value);
                    break;
                case LOCAL:
                    putToLocalCache(cacheName, key, value);
                    break;
                case REDIS:
                    putToRedisCache(key, value, instance.getTtl());
                    break;
                case POTENTIAL:
                    // 潜在缓存，暂不处理
                    break;
            }
        } catch (Exception e) {
            log.error("缓存设置异常，cacheName: {}, key: {}", cacheName, key, e);
        }
    }

    /**
     * 删除缓存（统一接口）
     */
    public void evict(String cacheName, String key) {
        CacheInstance instance = cacheRegistry.get(cacheName);
        if (instance == null) {
            log.warn("未找到缓存实例: {}", cacheName);
            return;
        }

        try {
            switch (instance.getType()) {
                case HYBRID:
                    evictFromHybridCache(key);
                    break;
                case SPRING_CACHE:
                    evictFromSpringCache(cacheName, key);
                    break;
                case LOCAL:
                    evictFromLocalCache(cacheName, key);
                    break;
                case REDIS:
                    evictFromRedisCache(key);
                    break;
                case POTENTIAL:
                    // 潜在缓存，暂不处理
                    break;
            }
        } catch (Exception e) {
            log.error("缓存删除异常，cacheName: {}, key: {}", cacheName, key, e);
        }
    }

    /**
     * 清空指定模块的所有缓存
     */
    public void clearModule(String module) {
        Set<String> cacheNames = moduleToCache.get(module);
        if (cacheNames == null || cacheNames.isEmpty()) {
            log.warn("模块{}没有注册的缓存", module);
            return;
        }

        for (String cacheName : cacheNames) {
            clearCache(cacheName);
        }
        log.info("已清空模块{}的所有缓存", module);
    }

    /**
     * 清空指定缓存的所有数据
     */
    public void clearCache(String cacheName) {
        CacheInstance instance = cacheRegistry.get(cacheName);
        if (instance == null) {
            log.warn("未找到缓存实例: {}", cacheName);
            return;
        }

        try {
            switch (instance.getType()) {
                case HYBRID:
                    clearHybridCache(cacheName);
                    break;
                case SPRING_CACHE:
                    clearSpringCache(cacheName);
                    break;
                case LOCAL:
                    clearLocalCache(cacheName);
                    break;
                case REDIS:
                    clearRedisCache(cacheName);
                    break;
                case POTENTIAL:
                    // 潜在缓存，暂不处理
                    break;
            }
            log.info("已清空缓存: {}", cacheName);
        } catch (Exception e) {
            log.error("清空缓存异常，cacheName: {}", cacheName, e);
        }
    }

    /**
     * 获取所有缓存实例信息
     */
    public Map<String, CacheInstance> getAllCaches() {
        return new HashMap<>(cacheRegistry);
    }

    /**
     * 按模块获取缓存实例
     */
    public Map<String, CacheInstance> getCachesByModule(String module) {
        Set<String> cacheNames = moduleToCache.get(module);
        if (cacheNames == null) {
            return Collections.emptyMap();
        }

        Map<String, CacheInstance> result = new HashMap<>();
        for (String cacheName : cacheNames) {
            CacheInstance instance = cacheRegistry.get(cacheName);
            if (instance != null) {
                result.put(cacheName, instance);
            }
        }
        return result;
    }

    /**
     * 按类型获取缓存实例
     */
    public Map<String, CacheInstance> getCachesByType(CacheType type) {
        Set<String> cacheNames = typeToCache.get(type);
        if (cacheNames == null) {
            return Collections.emptyMap();
        }

        Map<String, CacheInstance> result = new HashMap<>();
        for (String cacheName : cacheNames) {
            CacheInstance instance = cacheRegistry.get(cacheName);
            if (instance != null) {
                result.put(cacheName, instance);
            }
        }
        return result;
    }

    /**
     * 获取缓存统计信息
     */
    public GlobalCacheStats getGlobalStats() {
        Map<CacheType, Integer> typeCounts = new HashMap<>();
        Map<String, Integer> moduleCounts = new HashMap<>();

        for (CacheInstance instance : cacheRegistry.values()) {
            typeCounts.merge(instance.getType(), 1, Integer::sum);
            moduleCounts.merge(instance.getModule(), 1, Integer::sum);
        }

        return GlobalCacheStats.builder()
                .totalCaches(cacheRegistry.size())
                .typeDistribution(typeCounts)
                .moduleDistribution(moduleCounts)
                .redisAvailable(circuitBreaker.isAvailable())
                .circuitBreakerState(circuitBreaker.getState().name())
                .build();
    }

    // ========== 私有方法：具体缓存操作实现 ==========

    // 本地缓存实例映射
    private final Map<String, Cache<String, Object>> localCaches = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    private <T> T getFromHybridCache(String key, Supplier<T> fallbackSupplier) {
        return hybridCacheManager.get(key, fallbackSupplier);
    }

    private void putToHybridCache(String key, Object value) {
        hybridCacheManager.put(key, value);
    }

    private void evictFromHybridCache(String key) {
        hybridCacheManager.evict(key);
    }

    private void clearHybridCache(String cacheName) {
        // 清空混合缓存中以cacheName为前缀的所有键
        hybridCacheManager.clear();
    }

    @SuppressWarnings("unchecked")
    private <T> T getFromSpringCache(String cacheName, String key, Supplier<T> fallbackSupplier) {
        org.springframework.cache.Cache cache = springCacheManager.getCache(cacheName);
        if (cache != null) {
            org.springframework.cache.Cache.ValueWrapper wrapper = cache.get(key);
            if (wrapper != null) {
                return (T) wrapper.get();
            }
        }
        return fallbackSupplier.get();
    }

    private void putToSpringCache(String cacheName, String key, Object value) {
        org.springframework.cache.Cache cache = springCacheManager.getCache(cacheName);
        if (cache != null) {
            cache.put(key, value);
        }
    }

    private void evictFromSpringCache(String cacheName, String key) {
        org.springframework.cache.Cache cache = springCacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }

    private void clearSpringCache(String cacheName) {
        org.springframework.cache.Cache cache = springCacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T getFromLocalCache(String cacheName, String key, Supplier<T> fallbackSupplier) {
        Cache<String, Object> cache = getOrCreateLocalCache(cacheName);
        Object value = cache.getIfPresent(key);
        if (value != null) {
            return (T) value;
        }
        return fallbackSupplier.get();
    }

    private void putToLocalCache(String cacheName, String key, Object value) {
        Cache<String, Object> cache = getOrCreateLocalCache(cacheName);
        cache.put(key, value);
    }

    private void evictFromLocalCache(String cacheName, String key) {
        Cache<String, Object> cache = localCaches.get(cacheName);
        if (cache != null) {
            cache.invalidate(key);
        }
    }

    private void clearLocalCache(String cacheName) {
        Cache<String, Object> cache = localCaches.get(cacheName);
        if (cache != null) {
            cache.invalidateAll();
        }
    }

    private Cache<String, Object> getOrCreateLocalCache(String cacheName) {
        return localCaches.computeIfAbsent(cacheName, name -> 
            Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .recordStats()
                .build()
        );
    }

    @SuppressWarnings("unchecked")
    private <T> T getFromRedisCache(String key, Supplier<T> fallbackSupplier) {
        if (!circuitBreaker.isAvailable()) {
            return fallbackSupplier.get();
        }

        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                circuitBreaker.recordSuccess();
                return (T) value;
            }
        } catch (Exception e) {
            circuitBreaker.recordFailure();
            log.warn("Redis缓存获取失败，key: {}", key, e);
        }
        return fallbackSupplier.get();
    }

    private void putToRedisCache(String key, Object value, Duration ttl) {
        if (!circuitBreaker.isAvailable()) {
            return;
        }

        try {
            redisTemplate.opsForValue().set(key, value, ttl);
            circuitBreaker.recordSuccess();
        } catch (Exception e) {
            circuitBreaker.recordFailure();
            log.warn("Redis缓存设置失败，key: {}", key, e);
        }
    }

    private void evictFromRedisCache(String key) {
        if (!circuitBreaker.isAvailable()) {
            return;
        }

        try {
            redisTemplate.delete(key);
            circuitBreaker.recordSuccess();
        } catch (Exception e) {
            circuitBreaker.recordFailure();
            log.warn("Redis缓存删除失败，key: {}", key, e);
        }
    }

    private void clearRedisCache(String cacheName) {
        if (!circuitBreaker.isAvailable()) {
            return;
        }

        try {
            Set<String> keys = redisTemplate.keys(cacheName + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            circuitBreaker.recordSuccess();
        } catch (Exception e) {
            circuitBreaker.recordFailure();
            log.warn("Redis缓存清空失败，cacheName: {}", cacheName, e);
        }
    }

    /**
     * 缓存类型枚举
     */
    public enum CacheType {
        HYBRID("混合缓存", "L1(内存)+L2(Redis)+L3(数据库)"),
        SPRING_CACHE("Spring缓存", "基于Spring Cache注解"),
        LOCAL("本地缓存", "Caffeine本地内存缓存"),
        REDIS("Redis缓存", "纯Redis分布式缓存"),
        POTENTIAL("潜在缓存", "可优化为缓存的场景");

        private final String displayName;
        private final String description;

        CacheType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    /**
     * 缓存实例信息
     */
    public static class CacheInstance {
        private final String module;
        private final String name;
        private final CacheType type;
        private final String description;
        private final Duration ttl;
        private final long registeredAt;

        private CacheInstance(Builder builder) {
            this.module = builder.module;
            this.name = builder.name;
            this.type = builder.type;
            this.description = builder.description;
            this.ttl = builder.ttl;
            this.registeredAt = builder.registeredAt;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public String getModule() { return module; }
        public String getName() { return name; }
        public CacheType getType() { return type; }
        public String getDescription() { return description; }
        public Duration getTtl() { return ttl; }
        public long getRegisteredAt() { return registeredAt; }

        public static class Builder {
            private String module;
            private String name;
            private CacheType type;
            private String description;
            private Duration ttl;
            private long registeredAt;

            public Builder module(String module) { this.module = module; return this; }
            public Builder name(String name) { this.name = name; return this; }
            public Builder type(CacheType type) { this.type = type; return this; }
            public Builder description(String description) { this.description = description; return this; }
            public Builder ttl(Duration ttl) { this.ttl = ttl; return this; }
            public Builder registeredAt(long registeredAt) { this.registeredAt = registeredAt; return this; }

            public CacheInstance build() { return new CacheInstance(this); }
        }
    }

    /**
     * 全局缓存统计信息
     */
    public static class GlobalCacheStats {
        private final int totalCaches;
        private final Map<CacheType, Integer> typeDistribution;
        private final Map<String, Integer> moduleDistribution;
        private final boolean redisAvailable;
        private final String circuitBreakerState;

        private GlobalCacheStats(Builder builder) {
            this.totalCaches = builder.totalCaches;
            this.typeDistribution = builder.typeDistribution;
            this.moduleDistribution = builder.moduleDistribution;
            this.redisAvailable = builder.redisAvailable;
            this.circuitBreakerState = builder.circuitBreakerState;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public int getTotalCaches() { return totalCaches; }
        public Map<CacheType, Integer> getTypeDistribution() { return typeDistribution; }
        public Map<String, Integer> getModuleDistribution() { return moduleDistribution; }
        public boolean isRedisAvailable() { return redisAvailable; }
        public String getCircuitBreakerState() { return circuitBreakerState; }

        public static class Builder {
            private int totalCaches;
            private Map<CacheType, Integer> typeDistribution;
            private Map<String, Integer> moduleDistribution;
            private boolean redisAvailable;
            private String circuitBreakerState;

            public Builder totalCaches(int totalCaches) { this.totalCaches = totalCaches; return this; }
            public Builder typeDistribution(Map<CacheType, Integer> typeDistribution) { this.typeDistribution = typeDistribution; return this; }
            public Builder moduleDistribution(Map<String, Integer> moduleDistribution) { this.moduleDistribution = moduleDistribution; return this; }
            public Builder redisAvailable(boolean redisAvailable) { this.redisAvailable = redisAvailable; return this; }
            public Builder circuitBreakerState(String circuitBreakerState) { this.circuitBreakerState = circuitBreakerState; return this; }

            public GlobalCacheStats build() { return new GlobalCacheStats(this); }
        }
    }
}
