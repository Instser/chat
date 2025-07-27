package com.instser.chat.service.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存管理API密钥服务
 * 
 * @author instser
 * @since 2025-07-24
 */
@Slf4j
@Service
public class CacheApiKeyService {

    @Value("${cache.management.api-key.enabled:true}")
    private boolean apiKeyEnabled;

    @Value("${cache.management.api-key.master:}")
    private String masterApiKey;

    // API密钥存储（生产环境应该使用数据库）
    private final Map<String, ApiKeyInfo> apiKeys = new ConcurrentHashMap<>();
    
    // 访问记录（最近100次）
    private final Map<String, AccessRecord> accessRecords = new ConcurrentHashMap<>();
    private static final int MAX_ACCESS_RECORDS = 100;

    @PostConstruct
    public void init() {
        if (apiKeyEnabled) {
            // 如果没有配置主密钥，生成一个
            if (masterApiKey == null || masterApiKey.trim().isEmpty()) {
                masterApiKey = generateApiKey();
                log.warn("缓存管理API主密钥未配置，已生成临时密钥: {}", masterApiKey);
                log.warn("建议在配置文件中设置 cache.management.api-key.master 属性");
            }
            
            // 注册主密钥
            apiKeys.put(masterApiKey, ApiKeyInfo.builder()
                .name("Master Key")
                .description("主管理密钥")
                .createdAt(LocalDateTime.now())
                .enabled(true)
                .permissions("ALL")
                .build());
            
            log.info("缓存管理API密钥认证已启用，已注册 {} 个密钥", apiKeys.size());
        } else {
            log.info("缓存管理API密钥认证已禁用");
        }
    }

    /**
     * 验证API密钥
     */
    public boolean validateApiKey(String apiKey, String clientIp) {
        if (!apiKeyEnabled) {
            return true; // 如果禁用了API密钥认证，直接通过
        }

        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("缓存管理API访问缺少密钥 - IP: {}", clientIp);
            return false;
        }

        ApiKeyInfo keyInfo = apiKeys.get(apiKey);
        if (keyInfo == null) {
            log.warn("缓存管理API无效密钥访问 - IP: {}, Key: {}***", clientIp, 
                apiKey.length() > 8 ? apiKey.substring(0, 8) : apiKey);
            recordAccess(apiKey, clientIp, false, "Invalid API key");
            return false;
        }

        if (!keyInfo.isEnabled()) {
            log.warn("缓存管理API禁用密钥访问 - IP: {}, Key: {}", clientIp, keyInfo.getName());
            recordAccess(apiKey, clientIp, false, "API key disabled");
            return false;
        }

        // 记录成功访问
        recordAccess(apiKey, clientIp, true, "Success");
        log.debug("缓存管理API密钥验证成功 - IP: {}, Key: {}", clientIp, keyInfo.getName());
        return true;
    }

    /**
     * 生成新的API密钥
     */
    public String generateApiKey() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 创建新的API密钥
     */
    public String createApiKey(String name, String description, String permissions) {
        String apiKey = generateApiKey();
        
        ApiKeyInfo keyInfo = ApiKeyInfo.builder()
            .name(name)
            .description(description)
            .createdAt(LocalDateTime.now())
            .enabled(true)
            .permissions(permissions)
            .build();
        
        apiKeys.put(apiKey, keyInfo);
        log.info("创建新的缓存管理API密钥: {}", name);
        
        return apiKey;
    }

    /**
     * 禁用API密钥
     */
    public boolean disableApiKey(String apiKey) {
        ApiKeyInfo keyInfo = apiKeys.get(apiKey);
        if (keyInfo != null) {
            keyInfo.setEnabled(false);
            log.info("禁用缓存管理API密钥: {}", keyInfo.getName());
            return true;
        }
        return false;
    }

    /**
     * 获取所有API密钥信息（不包含实际密钥）
     */
    public Map<String, Object> getApiKeyStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        
        stats.put("enabled", apiKeyEnabled);
        stats.put("totalKeys", apiKeys.size());
        stats.put("enabledKeys", apiKeys.values().stream()
            .mapToInt(key -> key.isEnabled() ? 1 : 0)
            .sum());
        stats.put("recentAccesses", accessRecords.size());
        
        return stats;
    }

    /**
     * 记录访问
     */
    private void recordAccess(String apiKey, String clientIp, boolean success, String result) {
        String recordKey = clientIp + "_" + System.currentTimeMillis();
        
        AccessRecord record = AccessRecord.builder()
            .apiKey(apiKey.length() > 8 ? apiKey.substring(0, 8) + "***" : apiKey + "***")
            .clientIp(clientIp)
            .timestamp(LocalDateTime.now())
            .success(success)
            .result(result)
            .build();
        
        accessRecords.put(recordKey, record);
        
        // 保持记录数量在限制内
        if (accessRecords.size() > MAX_ACCESS_RECORDS) {
            String oldestKey = accessRecords.keySet().iterator().next();
            accessRecords.remove(oldestKey);
        }
    }

    /**
     * API密钥信息
     */
    @lombok.Data
    @lombok.Builder
    public static class ApiKeyInfo {
        private String name;
        private String description;
        private LocalDateTime createdAt;
        private boolean enabled;
        private String permissions;
    }

    /**
     * 访问记录
     */
    @lombok.Data
    @lombok.Builder
    public static class AccessRecord {
        private String apiKey;
        private String clientIp;
        private LocalDateTime timestamp;
        private boolean success;
        private String result;
    }

    /**
     * 检查是否启用了API密钥认证
     */
    public boolean isApiKeyEnabled() {
        return apiKeyEnabled;
    }

    /**
     * 获取主密钥（仅用于显示给管理员）
     */
    public String getMasterApiKey() {
        return masterApiKey;
    }
}
