package com.instser.chat.service.cache;

import com.instser.chat.service.UserDailyUsageService;
import com.instser.chat.common.util.LogUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Random;

/**
 * 缓存次数服务 - 使用Redis缓存和原子操作管理用户模型使用次数
 *
 * @author instser
 * @since 2025-09-04
 */
@Service
@Slf4j
public class CachedUsageService {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private UserDailyUsageService userDailyUsageService;
    
    @Autowired
    private HybridCacheManager hybridCacheManager;
    
    private static final String REMAINING_CACHE_PREFIX = "user:remaining:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30); // 优化：减少到30分钟
    private static final Duration SHORT_TTL = Duration.ofMinutes(10); // 低剩余次数时的短TTL
    private static final int SYNC_THRESHOLD = 5; // 剩余5次以下时同步数据库
    private static final int SHORT_TTL_THRESHOLD = 3; // 剩余3次以下时使用短TTL
    private final Random random = new Random();
    
    /**
     * 检查用户是否可以使用指定模型
     * 
     * @param userId 用户ID
     * @param modelId 模型ID
     * @param packageId 套餐ID
     * @return true-可以使用，false-不能使用
     */
    public boolean canUseModel(Long userId, String modelId, Long packageId) {
        String cacheKey = REMAINING_CACHE_PREFIX + userId + ":" + modelId;
        
        try {
            // 1. 尝试从缓存获取剩余次数
            Object cachedValue = redisTemplate.opsForValue().get(cacheKey);
            if (cachedValue != null) {
                int remaining = Integer.parseInt(cachedValue.toString());
                LogUtils.logUserDebug(log, userId, "缓存次数检查", "模型{}缓存剩余次数: {}", modelId, remaining);
                // -1 表示无限制，可以使用；> 0 表示有剩余次数，可以使用
                return remaining == -1 || remaining > 0;
            }
            
            // 2. 缓存未命中，从数据库获取并缓存
            int dbRemaining = userDailyUsageService.getRemainingModelChats(userId, packageId, modelId);
            
            // 尝试缓存结果，失败也不影响功能
            try {
                cacheRemainingCount(cacheKey, dbRemaining);
                LogUtils.logUserDebug(log, userId, "数据库次数查询", "模型{}数据库剩余次数: {}，已缓存", modelId, dbRemaining);
            } catch (Exception cacheEx) {
                LogUtils.logUserWarning(log, userId, "缓存写入失败", "模型{}缓存写入失败，但不影响功能", modelId);
            }
            
            // -1 表示无限制，可以使用；> 0 表示有剩余次数，可以使用
            return dbRemaining == -1 || dbRemaining > 0;
            
        } catch (Exception e) {
            LogUtils.logUserError(log, userId, "缓存次数检查", "检查模型{}使用次数异常，降级到数据库查询", e, modelId);
            // 异常时降级到数据库查询
            return userDailyUsageService.canChatWithModel(userId, packageId, modelId);
        }
    }
    
    /**
     * 记录模型使用，确保数据一致性的双阶段提交
     * 
     * @param userId 用户ID
     * @param modelId 模型ID
     * @param packageId 套餐ID
     * @return true-记录成功，false-次数不足或记录失败
     */
    public boolean recordModelUsage(Long userId, String modelId, Long packageId) {
        String cacheKey = REMAINING_CACHE_PREFIX + userId + ":" + modelId;
        String lockKey = "lock:usage:" + userId + ":" + modelId;
        
        try {
            // 1. 获取分布式锁，防止并发问题
            Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(5));
            if (!Boolean.TRUE.equals(lockAcquired)) {
                LogUtils.logUserWarning(log, userId, "获取分布式锁失败", "模型{}正在被其他请求处理", modelId);
                return false;
            }
            
            try {
                // 2. 先检查缓存中是否有剩余次数
                Object cachedValue = redisTemplate.opsForValue().get(cacheKey);
                if (cachedValue != null) {
                    int remaining = Integer.parseInt(cachedValue.toString());
                    
                    // -1 表示无限制，不需要递减，直接记录使用
                    if (remaining == -1) {
                        boolean dbSuccess = userDailyUsageService.recordModelChatUsage(userId, packageId, modelId);
                        LogUtils.logUserDebug(log, userId, "无限制套餐", "模型{}无限制套餐，直接记录使用: {}", modelId, dbSuccess);
                        return dbSuccess;
                    }
                    
                    if (remaining <= 0) {
                        LogUtils.logUserWarning(log, userId, "缓存次数不足", "模型{}缓存显示次数不足: {}", modelId, remaining);
                        return false;
                    }
                    
                    // 3. 改进的双阶段提交：先数据库，再Redis
                    // 阶段1：尝试数据库写入（带预检查）
                    boolean dbSuccess = userDailyUsageService.recordModelChatUsage(userId, packageId, modelId);
                    if (!dbSuccess) {
                        LogUtils.logUserWarning(log, userId, "数据库记录失败", "模型{}数据库记录失败，可能次数不足", modelId);
                        return false;
                    }
                    
                    // 阶段2：数据库成功后，尝试递减Redis
                    try {
                        Long newRemaining = redisTemplate.opsForValue().decrement(cacheKey);
                        if (newRemaining == null) {
                            // Redis递减失败，但数据库已成功，直接清除缓存降级到数据库模式
                            LogUtils.logUserWarning(log, userId, "Redis递减失败", "模型{}Redis操作失败，清除缓存降级到数据库模式", modelId);
                            redisTemplate.delete(cacheKey); // 清除可能不一致的缓存
                            return true; // 数据库已成功，返回成功
                        }
                        
                        LogUtils.logUserDebug(log, userId, "双阶段提交成功", "模型{}使用成功，剩余次数: {}", modelId, newRemaining);
                        
                        // 检查是否需要同步数据库
                        if (newRemaining <= SYNC_THRESHOLD) {
                            if (newRemaining <= 0) {
                                // 次数用完，立即同步确保准确性
                                syncWithDatabaseImmediately(userId, modelId, packageId, cacheKey);
                            } else {
                                // 接近限制，异步同步数据库数据
                                asyncSyncWithDatabase(userId, modelId, packageId, cacheKey);
                            }
                        }
                        
                        return true;
                        
                    } catch (Exception redisEx) {
                        // Redis完全失败，清除缓存，降级到数据库模式
                        LogUtils.logUserError(log, userId, "Redis完全失败", "模型{}Redis异常，清除缓存降级到数据库模式", redisEx, modelId);
                        try {
                            redisTemplate.delete(cacheKey); // 清除可能不一致的缓存
                        } catch (Exception cleanEx) {
                            LogUtils.logUserWarning(log, userId, "缓存清理失败", "模型{}缓存清理失败，但不影响功能", modelId);
                        }
                        return true; // 数据库已成功，返回成功
                    }
                    
                } else {
                    // 缓存不存在，降级到数据库操作
                    LogUtils.logUserDebug(log, userId, "缓存未命中降级", "模型{}缓存不存在，使用数据库操作", modelId);
                    return handleCacheMiss(userId, modelId, packageId, cacheKey);
                }
                
            } finally {
                // 释放分布式锁
                redisTemplate.delete(lockKey);
            }
            
        } catch (Exception e) {
            LogUtils.logUserError(log, userId, "缓存次数递减", "模型{}使用记录异常，降级到数据库操作", e, modelId);
            // 释放锁
            redisTemplate.delete(lockKey);
            // 异常时降级到原有的数据库操作
            return userDailyUsageService.recordModelChatUsage(userId, packageId, modelId);
        }
    }
    
    /**
     * 处理缓存未命中情况
     */
    private boolean handleCacheMiss(Long userId, String modelId, Long packageId, String cacheKey) {
        try {
            // 从数据库获取当前剩余次数
            int dbRemaining = userDailyUsageService.getRemainingModelChats(userId, packageId, modelId);
            
            // -1 表示无限制，直接记录使用
            if (dbRemaining == -1) {
                boolean dbSuccess = userDailyUsageService.recordModelChatUsage(userId, packageId, modelId);
                if (dbSuccess) {
                    cacheRemainingCount(cacheKey, -1); // 缓存无限制状态
                    LogUtils.logUserDebug(log, userId, "缓存次数处理", "模型{}无限制套餐，记录使用成功", modelId);
                }
                return dbSuccess;
            }
            
            if (dbRemaining <= 0) {
                LogUtils.logUserWarning(log, userId, "缓存次数处理", "模型{}数据库显示无剩余次数", modelId);
                cacheRemainingCount(cacheKey, 0);
                return false;
            }
            
            // 先记录使用到数据库
            boolean dbSuccess = userDailyUsageService.recordModelChatUsage(userId, packageId, modelId);
            if (dbSuccess) {
                // 记录成功，缓存新的剩余次数
                cacheRemainingCount(cacheKey, dbRemaining - 1);
                LogUtils.logUserDebug(log, userId, "缓存次数处理", "模型{}数据库操作成功，缓存剩余次数: {}", modelId, dbRemaining - 1);
            }
            
            return dbSuccess;
            
        } catch (Exception e) {
            LogUtils.logUserError(log, userId, "缓存次数处理", "处理模型{}缓存未命中异常", e, modelId);
            return false;
        }
    }
    
    /**
     * 立即同步数据库数据（同步方式）
     */
    private void syncWithDatabaseImmediately(Long userId, String modelId, Long packageId, String cacheKey) {
        try {
            int dbRemaining = userDailyUsageService.getRemainingModelChats(userId, packageId, modelId);
            redisTemplate.opsForValue().set(cacheKey, dbRemaining, getCacheExpireTime());
            
            LogUtils.logUserDebug(log, userId, "立即同步数据库", "模型{}数据库实际剩余: {}，已更新缓存", modelId, dbRemaining);
            
        } catch (Exception e) {
            LogUtils.logUserError(log, userId, "立即同步数据库", "同步模型{}数据异常", e, modelId);
        }
    }
    
    /**
     * 异步同步数据库数据 - 优化版本
     */
    @Async
    public void asyncSyncWithDatabase(Long userId, String modelId, Long packageId, String cacheKey) {
        try {
            // 智能延迟：根据剩余次数调整延迟时间
            Object cachedValue = redisTemplate.opsForValue().get(cacheKey);
            int currentRemaining = cachedValue != null ? Integer.parseInt(cachedValue.toString()) : 0;
            
            int delayMs = calculateSyncDelay(currentRemaining);
            Thread.sleep(delayMs);
            
            // 再次检查缓存是否还存在（可能已被清除或过期）
            Object latestCachedValue = redisTemplate.opsForValue().get(cacheKey);
            if (latestCachedValue == null) {
                LogUtils.logUserDebug(log, userId, "异步同步跳过", "模型{}缓存已不存在，跳过同步", modelId);
                return;
            }
            
            int dbRemaining = userDailyUsageService.getRemainingModelChats(userId, packageId, modelId);
            
            // 只有数据库和缓存差异较大时才更新
            int cacheRemaining = Integer.parseInt(latestCachedValue.toString());
            int difference = Math.abs(dbRemaining - cacheRemaining);
            
            if (difference > 1) { // 差异超过1次才同步
                Duration ttl = getCacheExpireTime(dbRemaining);
                redisTemplate.opsForValue().set(cacheKey, dbRemaining, ttl);
                LogUtils.logUserDebug(log, userId, "异步同步完成", "模型{}缓存已更新: 缓存{}次 → 数据库{}次", modelId, cacheRemaining, dbRemaining);
            } else {
                LogUtils.logUserDebug(log, userId, "异步同步跳过", "模型{}数据一致，差异{}次，无需更新", modelId, difference);
            }
            
        } catch (Exception e) {
            LogUtils.logUserError(log, userId, "异步同步数据库", "异步同步模型{}数据异常", e, modelId);
        }
    }
    
    /**
     * 根据剩余次数计算同步延迟时间
     */
    private int calculateSyncDelay(int remaining) {
        if (remaining <= 1) {
            return 50; // 最后1次，快速同步
        } else if (remaining <= 3) {
            return 100; // 最后3次，较快同步
        } else {
            return 200; // 正常情况，标准延迟
        }
    }
    
    /**
     * 缓存剩余次数，带智能TTL防止缓存雪崩和内存泄漏
     */
    private void cacheRemainingCount(String cacheKey, int remaining) {
        try {
            Duration intelligentTtl = getCacheExpireTime(remaining);
            redisTemplate.opsForValue().set(cacheKey, remaining, intelligentTtl);
            
        } catch (Exception e) {
            log.warn("缓存剩余次数失败，key: {}, remaining: {}", cacheKey, remaining, e);
        }
    }
    
    /**
     * 获取智能化的缓存过期时间，防止缓存雪崩和内存泄漏
     * 
     * @param remaining 剩余次数，用于决定TTL长度。注意：-1表示无限制
     */
    private Duration getCacheExpireTime(Integer remaining) {
        Duration baseTtl;
        int randomMinutes;
        
        // -1 表示无限制，应该使用标准TTL
        if (remaining != null && remaining > 0 && remaining <= SHORT_TTL_THRESHOLD) {
            // 剩余次数很少时使用短TTL，减少内存占用
            baseTtl = SHORT_TTL;
            randomMinutes = random.nextInt(5); // 0-5分钟随机
            log.debug("智能TTL: 剩余次数较少({}次)，使用短TTL: {}分钟 + {}分钟随机", remaining, baseTtl.toMinutes(), randomMinutes);
        } else {
            // 正常情况使用标准TTL (包括无限制的-1情况)
            baseTtl = CACHE_TTL;  
            randomMinutes = random.nextInt(10); // 0-10分钟随机
            if (remaining != null && remaining == -1) {
                log.debug("智能TTL: 无限制套餐(-1次)，使用标准TTL: {}分钟 + {}分钟随机", baseTtl.toMinutes(), randomMinutes);
            }
        }
        
        return baseTtl.plusMinutes(randomMinutes);
    }
    
    /**
     * 获取随机化的缓存过期时间，防止缓存雪崩（向后兼容方法）
     */
    private Duration getCacheExpireTime() {
        return getCacheExpireTime(null);
    }
    
    /**
     * 手动刷新用户模型的缓存次数（管理接口用）
     */
    public void refreshUserModelCache(Long userId, String modelId, Long packageId) {
        String cacheKey = REMAINING_CACHE_PREFIX + userId + ":" + modelId;
        
        try {
            int dbRemaining = userDailyUsageService.getRemainingModelChats(userId, packageId, modelId);
            redisTemplate.opsForValue().set(cacheKey, dbRemaining, getCacheExpireTime());
            
            LogUtils.logUserOperation(log, userId, "手动刷新缓存", "模型{}缓存已刷新，剩余次数: {}", modelId, dbRemaining);
            
        } catch (Exception e) {
            LogUtils.logUserError(log, userId, "手动刷新缓存", "刷新模型{}缓存异常", e, modelId);
        }
    }
    
    /**
     * 清除用户模型的缓存次数
     */
    public void evictUserModelCache(Long userId, String modelId) {
        String cacheKey = REMAINING_CACHE_PREFIX + userId + ":" + modelId;
        
        try {
            redisTemplate.delete(cacheKey);
            LogUtils.logUserDebug(log, userId, "清除缓存次数", "模型{}缓存已清除", modelId);
            
        } catch (Exception e) {
            LogUtils.logUserError(log, userId, "清除缓存次数", "清除模型{}缓存异常", e, modelId);
        }
    }
}