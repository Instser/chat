package com.instser.chat.service;

import com.instser.chat.config.GeminiApiKeyConfig;
import com.instser.chat.entity.ApiKeyStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Gemini API密钥轮询管理器
 * 负责管理多个API密钥的轮询使用、健康检查和故障恢复
 *
 * @author instser
 * @since 2025-07-16
 */
@Component
@Slf4j
public class GeminiApiKeyManager {
    
    private final GeminiApiKeyConfig config;
    private final Map<String, ApiKeyStatus> keyStatusMap = new ConcurrentHashMap<>();
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    private final Random random = new Random();
    
    @Autowired
    public GeminiApiKeyManager(GeminiApiKeyConfig config) {
        this.config = config;
        initializeKeys();
        startHealthCheckScheduler();
    }
    
    /**
     * 初始化密钥状态
     */
    private void initializeKeys() {
        for (String key : config.getKeys()) {
            keyStatusMap.put(key, new ApiKeyStatus(key, true, 0, null, null, null, 0, 0, 1));
        }
        log.info("【密钥管理】初始化{}个API密钥", config.getKeys().size());
    }
    
    /**
     * 获取下一个可用的API密钥
     */
    public String getNextApiKey() {
        List<String> availableKeys = getAvailableKeys();
        
        if (availableKeys.isEmpty()) {
            log.error("【密钥管理】没有可用的API密钥");
            throw new RuntimeException("没有可用的API密钥");
        }
        
        String selectedKey = selectKeyByStrategy(availableKeys);
        updateKeyUsage(selectedKey);
        
        log.debug("【密钥管理】选择API密钥：{}...", maskApiKey(selectedKey));
        return selectedKey;
    }
    
    /**
     * 根据策略选择密钥
     */
    private String selectKeyByStrategy(List<String> availableKeys) {
        switch (config.getStrategy().toUpperCase()) {
            case "RANDOM":
                return availableKeys.get(random.nextInt(availableKeys.size()));
                
            case "WEIGHTED":
                return selectWeightedKey(availableKeys);
                
            case "ROUND_ROBIN":
            default:
                int index = currentIndex.getAndIncrement() % availableKeys.size();
                return availableKeys.get(index);
        }
    }
    
    /**
     * 加权选择密钥
     */
    private String selectWeightedKey(List<String> availableKeys) {
        int totalWeight = availableKeys.stream()
            .mapToInt(key -> keyStatusMap.get(key).getWeight())
            .sum();
            
        int randomWeight = random.nextInt(totalWeight);
        int currentWeight = 0;
        
        for (String key : availableKeys) {
            currentWeight += keyStatusMap.get(key).getWeight();
            if (randomWeight < currentWeight) {
                return key;
            }
        }
        
        return availableKeys.get(0); // fallback
    }
    
    /**
     * 获取可用密钥列表
     */
    private List<String> getAvailableKeys() {
        LocalDateTime now = LocalDateTime.now();
        
        return keyStatusMap.entrySet().stream()
            .filter(entry -> {
                ApiKeyStatus status = entry.getValue();
                // 检查是否在禁用期内
                if (status.getDisabledUntil() != null && now.isBefore(status.getDisabledUntil())) {
                    return false;
                }
                // 重新启用之前被禁用的密钥
                if (status.getDisabledUntil() != null && now.isAfter(status.getDisabledUntil())) {
                    status.setAvailable(true);
                    status.setDisabledUntil(null);
                    status.setFailureCount(0);
                    log.info("【密钥管理】密钥重新启用：{}...", maskApiKey(entry.getKey()));
                }
                return status.isAvailable();
            })
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    /**
     * 更新密钥使用统计
     */
    private void updateKeyUsage(String apiKey) {
        ApiKeyStatus status = keyStatusMap.get(apiKey);
        if (status != null) {
            status.setLastUsed(LocalDateTime.now());
            status.setTotalRequests(status.getTotalRequests() + 1);
        }
    }
    
    /**
     * 报告密钥使用成功
     */
    public void reportSuccess(String apiKey) {
        ApiKeyStatus status = keyStatusMap.get(apiKey);
        if (status != null) {
            status.setSuccessRequests(status.getSuccessRequests() + 1);
            status.setFailureCount(0); // 重置失败计数
            log.debug("【密钥管理】密钥使用成功：{}...", maskApiKey(apiKey));
        }
    }
    
    /**
     * 报告密钥使用失败
     */
    public void reportFailure(String apiKey, Exception exception) {
        ApiKeyStatus status = keyStatusMap.get(apiKey);
        if (status != null) {
            status.setFailureCount(status.getFailureCount() + 1);
            status.setLastFailure(LocalDateTime.now());
            
            log.warn("【密钥管理】密钥使用失败：{}...，失败次数：{}，错误：{}", 
                    maskApiKey(apiKey), status.getFailureCount(), exception.getMessage());
            
            // 达到最大失败次数时禁用密钥
            if (status.getFailureCount() >= config.getMaxRetries()) {
                status.setAvailable(false);
                status.setDisabledUntil(LocalDateTime.now().plusSeconds(config.getDisableTimeout()));
                log.error("【密钥管理】密钥已禁用：{}...，禁用时间：{}秒", 
                        maskApiKey(apiKey), config.getDisableTimeout());
            }
        }
    }
    
    /**
     * 动态更新密钥列表
     */
    public void updateApiKeys(List<String> newKeys) {
        log.info("【密钥管理】更新API密钥列表，新密钥数量：{}", newKeys.size());
        
        // 移除不在新列表中的密钥
        keyStatusMap.entrySet().removeIf(entry -> {
            if (!newKeys.contains(entry.getKey())) {
                log.info("【密钥管理】移除密钥：{}...", maskApiKey(entry.getKey()));
                return true;
            }
            return false;
        });
        
        // 添加新密钥
        for (String key : newKeys) {
            if (!keyStatusMap.containsKey(key)) {
                keyStatusMap.put(key, new ApiKeyStatus(key, true, 0, null, null, null, 0, 0, 1));
                log.info("【密钥管理】添加新密钥：{}...", maskApiKey(key));
            }
        }
        
        // 更新配置
        config.setKeys(new ArrayList<>(newKeys));
        
        // 重置轮询索引
        currentIndex.set(0);
    }
    
    /**
     * 获取密钥状态统计
     */
    public Map<String, Object> getKeyStatistics() {
        Map<String, Object> stats = new HashMap<>();

        List<Map<String, Object>> keyStats = keyStatusMap.entrySet().stream()
            .map(entry -> {
                String key = entry.getKey();
                ApiKeyStatus status = entry.getValue();

                Map<String, Object> keyInfo = new HashMap<>();
                keyInfo.put("apiKey", key); // 显示完整的API密钥
                keyInfo.put("available", status.isAvailable());
                keyInfo.put("totalRequests", status.getTotalRequests());
                keyInfo.put("successRequests", status.getSuccessRequests());
                keyInfo.put("failureCount", status.getFailureCount());
                keyInfo.put("successRate", status.getTotalRequests() > 0 ?
                    (double) status.getSuccessRequests() / status.getTotalRequests() : 0.0);
                keyInfo.put("lastUsed", status.getLastUsed());
                keyInfo.put("weight", status.getWeight());

                return keyInfo;
            })
            .collect(Collectors.toList());

        stats.put("keys", keyStats);
        stats.put("totalKeys", keyStatusMap.size());
        stats.put("availableKeys", getAvailableKeys().size());
        stats.put("strategy", config.getStrategy());

        return stats;
    }
    
    /**
     * 启动健康检查调度器
     */
    private void startHealthCheckScheduler() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::performHealthCheck, 
            config.getHealthCheckInterval(), 
            config.getHealthCheckInterval(), 
            TimeUnit.SECONDS);
    }
    
    /**
     * 执行健康检查
     */
    private void performHealthCheck() {
        log.debug("【密钥管理】开始执行健康检查");
        
        for (Map.Entry<String, ApiKeyStatus> entry : keyStatusMap.entrySet()) {
            String apiKey = entry.getKey();
            ApiKeyStatus status = entry.getValue();
            
            // 检查长时间未使用的密钥
            if (status.getLastUsed() != null && 
                status.getLastUsed().isBefore(LocalDateTime.now().minusHours(1))) {
                // 可以在这里执行简单的API测试
                log.debug("【密钥管理】密钥长时间未使用：{}...", maskApiKey(apiKey));
            }
        }
    }
    
    /**
     * 掩码API密钥用于日志
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 10) {
            return "***";
        }
        return apiKey.substring(0, 10) + "...";
    }
}
