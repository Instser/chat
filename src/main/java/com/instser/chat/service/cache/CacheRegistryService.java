package com.instser.chat.service.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存注册服务
 * 自动发现和注册系统中的所有缓存实例
 * 
 * @author instser
 * @since 2025-07-24
 */
@Slf4j
@Service
public class CacheRegistryService {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private GlobalCacheManager globalCacheManager;

    @Autowired
    private CacheManager springCacheManager;

    // 自动发现的缓存实例
    private final Map<String, DiscoveredCache> discoveredCaches = new ConcurrentHashMap<>();

    // 缓存使用统计
    private final Map<String, CacheUsageStats> usageStats = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 自动发现系统中的缓存
        discoverCaches();
        log.info("缓存注册服务初始化完成，发现{}个缓存实例", discoveredCaches.size());
    }

    /**
     * 自动发现系统中的缓存
     */
    private void discoverCaches() {
        // 1. 发现Spring Cache实例
        discoverSpringCaches();
        
        // 2. 发现Caffeine本地缓存
        discoverCaffeineCaches();
        
        // 3. 发现Redis缓存使用
        discoverRedisCaches();
        
        // 4. 发现混合缓存使用
        discoverHybridCaches();
    }

    /**
     * 发现Spring Cache实例
     */
    private void discoverSpringCaches() {
        Collection<String> cacheNames = springCacheManager.getCacheNames();
        for (String cacheName : cacheNames) {
            org.springframework.cache.Cache cache = springCacheManager.getCache(cacheName);
            if (cache != null) {
                DiscoveredCache discoveredCache = DiscoveredCache.builder()
                        .name(cacheName)
                        .type(GlobalCacheManager.CacheType.SPRING_CACHE)
                        .module(inferModuleFromCacheName(cacheName))
                        .description("Spring Cache实例: " + cacheName)
                        .nativeCache(cache.getNativeCache())
                        .discoveredAt(System.currentTimeMillis())
                        .build();

                discoveredCaches.put(cacheName, discoveredCache);
                
                // 注册到全局缓存管理器
                globalCacheManager.registerCache(
                    discoveredCache.getModule(),
                    cacheName,
                    GlobalCacheManager.CacheType.SPRING_CACHE,
                    discoveredCache.getDescription(),
                    Duration.ofMinutes(30) // 默认TTL
                );
                
                log.debug("发现Spring Cache: {}", cacheName);
            }
        }
    }

    /**
     * 发现Caffeine本地缓存
     */
    private void discoverCaffeineCaches() {
        // 通过反射扫描所有Bean中的Caffeine Cache字段
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        
        for (String beanName : beanNames) {
            try {
                // 跳过非单例Bean，避免重复创建
                if (!applicationContext.isSingleton(beanName)) {
                    continue;
                }

                // 安全获取Bean，避免循环依赖
                Object bean;
                try {
                    bean = applicationContext.getBean(beanName);
                } catch (Exception e) {
                    // 如果获取Bean失败（可能是循环依赖），跳过
                    log.debug("跳过Bean {}: {}", beanName, e.getMessage());
                    continue;
                }

                Class<?> beanClass = bean.getClass();
                
                // 扫描字段
                Field[] fields = beanClass.getDeclaredFields();
                for (Field field : fields) {
                    if (isCaffeineCache(field.getType())) {
                        try {
                            field.setAccessible(true);
                            Object cacheInstance = field.get(bean);

                            if (cacheInstance != null) {
                                String cacheName = beanClass.getSimpleName() + "." + field.getName();
                                String module = inferModuleFromClassName(beanClass.getSimpleName());

                                DiscoveredCache discoveredCache = DiscoveredCache.builder()
                                        .name(cacheName)
                                        .type(GlobalCacheManager.CacheType.LOCAL)
                                        .module(module)
                                        .description("Caffeine本地缓存: " + cacheName)
                                        .nativeCache(cacheInstance)
                                        .discoveredAt(System.currentTimeMillis())
                                        .build();

                                discoveredCaches.put(cacheName, discoveredCache);

                                // 注册到全局缓存管理器
                                globalCacheManager.registerCache(
                                    module,
                                    cacheName,
                                    GlobalCacheManager.CacheType.LOCAL,
                                    discoveredCache.getDescription(),
                                    Duration.ofMinutes(5) // 默认TTL
                                );

                                log.debug("发现Caffeine缓存: {} in {}", cacheName, beanName);
                            }
                        } catch (IllegalAccessException | SecurityException e) {
                            log.debug("访问字段{}时出错: {}", field.getName(), e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("扫描Bean缓存时出错: {}, 错误: {}", beanName, e.getMessage());
            }
        }
    }

    /**
     * 发现Redis缓存使用
     */
    private void discoverRedisCaches() {
        // 扫描使用RedisTemplate的服务
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        
        for (String beanName : beanNames) {
            try {
                // 跳过非单例Bean，避免重复创建
                if (!applicationContext.isSingleton(beanName)) {
                    continue;
                }

                // 安全获取Bean，避免循环依赖
                Object bean;
                try {
                    bean = applicationContext.getBean(beanName);
                } catch (Exception e) {
                    // 如果获取Bean失败（可能是循环依赖），跳过
                    log.debug("跳过Bean {}: {}", beanName, e.getMessage());
                    continue;
                }

                Class<?> beanClass = bean.getClass();
                
                // 检查是否使用RedisTemplate
                if (hasRedisTemplateField(beanClass) || hasRedisTemplateMethods(beanClass)) {
                    String module = inferModuleFromClassName(beanClass.getSimpleName());
                    String cacheName = beanClass.getSimpleName() + ".redis";
                    
                    DiscoveredCache discoveredCache = DiscoveredCache.builder()
                            .name(cacheName)
                            .type(GlobalCacheManager.CacheType.REDIS)
                            .module(module)
                            .description("Redis缓存使用: " + beanClass.getSimpleName())
                            .nativeCache(bean)
                            .discoveredAt(System.currentTimeMillis())
                            .build();

                    discoveredCaches.put(cacheName, discoveredCache);
                    
                    log.debug("发现Redis缓存使用: {} in {}", cacheName, beanName);
                }
            } catch (Exception e) {
                log.debug("扫描Redis缓存使用时出错: {}, 错误: {}", beanName, e.getMessage());
            }
        }
    }

    /**
     * 发现混合缓存使用
     */
    private void discoverHybridCaches() {
        // 扫描使用HybridCacheManager的服务
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        
        for (String beanName : beanNames) {
            try {
                // 跳过非单例Bean，避免重复创建
                if (!applicationContext.isSingleton(beanName)) {
                    continue;
                }

                // 安全获取Bean，避免循环依赖
                Object bean;
                try {
                    bean = applicationContext.getBean(beanName);
                } catch (Exception e) {
                    // 如果获取Bean失败（可能是循环依赖），跳过
                    log.debug("跳过Bean {}: {}", beanName, e.getMessage());
                    continue;
                }

                Class<?> beanClass = bean.getClass();
                
                // 检查是否使用HybridCacheManager
                if (hasHybridCacheManagerField(beanClass)) {
                    String module = inferModuleFromClassName(beanClass.getSimpleName());
                    String cacheName = beanClass.getSimpleName() + ".hybrid";
                    
                    DiscoveredCache discoveredCache = DiscoveredCache.builder()
                            .name(cacheName)
                            .type(GlobalCacheManager.CacheType.HYBRID)
                            .module(module)
                            .description("混合缓存使用: " + beanClass.getSimpleName())
                            .nativeCache(bean)
                            .discoveredAt(System.currentTimeMillis())
                            .build();

                    discoveredCaches.put(cacheName, discoveredCache);
                    
                    log.debug("发现混合缓存使用: {} in {}", cacheName, beanName);
                }
            } catch (Exception e) {
                log.debug("扫描混合缓存使用时出错: {}, 错误: {}", beanName, e.getMessage());
            }
        }
    }

    /**
     * 手动注册缓存实例
     */
    public void registerCache(String name, GlobalCacheManager.CacheType type, String module, 
                             String description, Object nativeCache) {
        DiscoveredCache discoveredCache = DiscoveredCache.builder()
                .name(name)
                .type(type)
                .module(module)
                .description(description)
                .nativeCache(nativeCache)
                .discoveredAt(System.currentTimeMillis())
                .build();

        discoveredCaches.put(name, discoveredCache);
        
        // 注册到全局缓存管理器
        globalCacheManager.registerCache(module, name, type, description, Duration.ofMinutes(30));
        
        log.info("手动注册缓存: {}, 类型: {}, 模块: {}", name, type, module);
    }

    /**
     * 记录缓存使用统计
     */
    public void recordCacheUsage(String cacheName, boolean hit, long responseTime) {
        CacheUsageStats stats = usageStats.computeIfAbsent(cacheName, k -> new CacheUsageStats());
        stats.recordUsage(hit, responseTime);
    }

    /**
     * 获取所有发现的缓存
     */
    public Map<String, DiscoveredCache> getDiscoveredCaches() {
        return new HashMap<>(discoveredCaches);
    }

    /**
     * 获取缓存使用统计
     */
    public Map<String, CacheUsageStats> getUsageStats() {
        return new HashMap<>(usageStats);
    }

    /**
     * 按模块获取发现的缓存
     */
    public Map<String, DiscoveredCache> getDiscoveredCachesByModule(String module) {
        Map<String, DiscoveredCache> result = new HashMap<>();
        for (Map.Entry<String, DiscoveredCache> entry : discoveredCaches.entrySet()) {
            if (module.equals(entry.getValue().getModule())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    /**
     * 按类型获取发现的缓存
     */
    public Map<String, DiscoveredCache> getDiscoveredCachesByType(GlobalCacheManager.CacheType type) {
        Map<String, DiscoveredCache> result = new HashMap<>();
        for (Map.Entry<String, DiscoveredCache> entry : discoveredCaches.entrySet()) {
            if (type.equals(entry.getValue().getType())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    // ========== 私有辅助方法 ==========

    /**
     * 从缓存名称推断模块名
     */
    private String inferModuleFromCacheName(String cacheName) {
        if (cacheName.contains("exchange")) return "exchange";
        if (cacheName.contains("payment")) return "payment";
        if (cacheName.contains("user")) return "user";
        if (cacheName.contains("chat")) return "chat";
        if (cacheName.contains("ai")) return "ai";
        if (cacheName.contains("package")) return "package";
        if (cacheName.contains("websearch")) return "websearch";
        return "unknown";
    }

    /**
     * 从类名推断模块名
     */
    private String inferModuleFromClassName(String className) {
        String lowerName = className.toLowerCase();
        if (lowerName.contains("exchange")) return "exchange";
        if (lowerName.contains("payment")) return "payment";
        if (lowerName.contains("user")) return "user";
        if (lowerName.contains("chat")) return "chat";
        if (lowerName.contains("ai") || lowerName.contains("gemini")) return "ai";
        if (lowerName.contains("package")) return "package";
        if (lowerName.contains("websearch")) return "websearch";
        if (lowerName.contains("oauth")) return "auth";
        return "unknown";
    }

    /**
     * 检查是否为Caffeine缓存类型
     */
    private boolean isCaffeineCache(Class<?> type) {
        return type.getName().contains("com.github.benmanes.caffeine.cache.Cache");
    }

    /**
     * 检查类是否有RedisTemplate字段
     */
    private boolean hasRedisTemplateField(Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            if (field.getType().getName().contains("RedisTemplate")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查类是否有RedisTemplate相关方法
     */
    private boolean hasRedisTemplateMethods(Class<?> clazz) {
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            if (method.getName().contains("redis") || method.getName().contains("Redis")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查类是否有HybridCacheManager字段
     */
    private boolean hasHybridCacheManagerField(Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            if (field.getType().getName().contains("HybridCacheManager")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 发现的缓存实例信息
     */
    public static class DiscoveredCache {
        private final String name;
        private final GlobalCacheManager.CacheType type;
        private final String module;
        private final String description;
        private final Object nativeCache;
        private final long discoveredAt;

        private DiscoveredCache(Builder builder) {
            this.name = builder.name;
            this.type = builder.type;
            this.module = builder.module;
            this.description = builder.description;
            this.nativeCache = builder.nativeCache;
            this.discoveredAt = builder.discoveredAt;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public String getName() { return name; }
        public GlobalCacheManager.CacheType getType() { return type; }
        public String getModule() { return module; }
        public String getDescription() { return description; }
        public Object getNativeCache() { return nativeCache; }
        public long getDiscoveredAt() { return discoveredAt; }

        public static class Builder {
            private String name;
            private GlobalCacheManager.CacheType type;
            private String module;
            private String description;
            private Object nativeCache;
            private long discoveredAt;

            public Builder name(String name) { this.name = name; return this; }
            public Builder type(GlobalCacheManager.CacheType type) { this.type = type; return this; }
            public Builder module(String module) { this.module = module; return this; }
            public Builder description(String description) { this.description = description; return this; }
            public Builder nativeCache(Object nativeCache) { this.nativeCache = nativeCache; return this; }
            public Builder discoveredAt(long discoveredAt) { this.discoveredAt = discoveredAt; return this; }

            public DiscoveredCache build() { return new DiscoveredCache(this); }
        }
    }

    /**
     * 缓存使用统计
     */
    public static class CacheUsageStats {
        private long totalRequests = 0;
        private long hits = 0;
        private long misses = 0;
        private long totalResponseTime = 0;
        private long maxResponseTime = 0;
        private long minResponseTime = Long.MAX_VALUE;

        public synchronized void recordUsage(boolean hit, long responseTime) {
            totalRequests++;
            if (hit) {
                hits++;
            } else {
                misses++;
            }

            totalResponseTime += responseTime;
            maxResponseTime = Math.max(maxResponseTime, responseTime);
            minResponseTime = Math.min(minResponseTime, responseTime);
        }

        // Getters
        public long getTotalRequests() { return totalRequests; }
        public long getHits() { return hits; }
        public long getMisses() { return misses; }
        public double getHitRate() {
            return totalRequests > 0 ? (double) hits / totalRequests : 0.0;
        }
        public double getAverageResponseTime() {
            return totalRequests > 0 ? (double) totalResponseTime / totalRequests : 0.0;
        }
        public long getMaxResponseTime() { return maxResponseTime; }
        public long getMinResponseTime() { return minResponseTime == Long.MAX_VALUE ? 0 : minResponseTime; }
    }
}
