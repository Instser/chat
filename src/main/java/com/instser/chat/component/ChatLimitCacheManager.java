package com.instser.chat.component;

import com.instser.chat.event.UserPackageChangeEvent;
import com.instser.chat.service.cache.HybridCacheManager;
import com.instser.chat.service.cache.CachedUsageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 聊天限制缓存管理器
 * 负责处理用户套餐变更时的缓存更新
 *
 * @author instser
 * @since 2025-09-03
 */
@Component
@Slf4j
public class ChatLimitCacheManager {
    
    @Autowired
    private HybridCacheManager hybridCacheManager;
    
    @Autowired
    private CachedUsageService cachedUsageService;
    
    private static final String USER_ACCOUNT_CACHE_PREFIX = "user:account:";
    private static final String PACKAGE_CONFIG_CACHE_PREFIX = "package:config:";
    private static final String MODEL_PERMISSION_CACHE_PREFIX = "model:permission:";
    
    /**
     * 用户套餐变更时清除相关缓存
     */
    @EventListener
    @Async
    public void handlePackageChange(UserPackageChangeEvent event) {
        Long userId = event.getUserId();
        
        try {
            // 清除用户账户缓存
            hybridCacheManager.evict(USER_ACCOUNT_CACHE_PREFIX + userId);
            
            // 清除模型权限缓存（通配符模式，需要实现evictPattern方法）
            evictModelPermissionCacheForUser(userId);
            
            // 清除用户的缓存次数（新增）
            evictUserModelUsageCacheForUser(userId);
            
            log.info("用户{}套餐变更（{}），已清除所有相关缓存", userId, event.getChangeType());
        } catch (Exception e) {
            log.error("处理用户{}套餐变更事件异常", userId, e);
        }
    }
    
    /**
     * 清除指定用户的所有模型权限缓存
     */
    private void evictModelPermissionCacheForUser(Long userId) {
        // 常见模型ID列表，实际应该从配置或数据库获取
        String[] commonModelIds = {"1", "2", "3", "4", "5"}; // STANDARD, ADVANCED, PRO, ULTRA, PREMIUM
        
        for (String modelId : commonModelIds) {
            String cacheKey = MODEL_PERMISSION_CACHE_PREFIX + userId + ":" + modelId;
            hybridCacheManager.evict(cacheKey);
        }
    }
    
    /**
     * 清除指定用户的所有模型使用次数缓存（新增）
     */
    private void evictUserModelUsageCacheForUser(Long userId) {
        // 常见模型ID列表
        String[] commonModelIds = {"1", "2", "3", "4", "5"};
        
        for (String modelId : commonModelIds) {
            cachedUsageService.evictUserModelCache(userId, modelId);
        }
    }
    
    /**
     * 手动清除所有Package配置缓存（用于系统升级时清理旧格式缓存）
     */
    public void clearAllPackageConfigCaches() {
        try {
            // 这里应该使用Redis模式匹配删除，但HybridCacheManager没有提供批量删除方法
            // 暂时通过常见套餐ID清除，实际使用中可以根据需要扩展
            Long[] commonPackageIds = {1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L};
            
            for (Long packageId : commonPackageIds) {
                String cacheKey = PACKAGE_CONFIG_CACHE_PREFIX + packageId;
                hybridCacheManager.evict(cacheKey);
            }
            
            log.info("已清除常见套餐配置缓存，如需清理更多请手动处理或重启Redis");
        } catch (Exception e) {
            log.error("清除套餐配置缓存异常", e);
        }
    }
}