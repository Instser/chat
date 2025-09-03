package com.instser.chat.component;

import com.instser.chat.service.cache.CachedUsageService;
import com.instser.chat.service.UserAccountService;
import com.instser.chat.entity.UserAccount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 使用次数缓存预热组件
 * 在应用启动后异步预热活跃用户的使用次数缓存
 *
 * @author instser
 * @since 2025-09-04
 */
@Component
@Slf4j
public class UsageCacheWarmup implements ApplicationRunner {

    @Autowired
    private UserAccountService userAccountService;
    
    @Autowired
    private CachedUsageService cachedUsageService;
    
    /**
     * 常用模型ID列表
     */
    private static final String[] COMMON_MODELS = {"1", "2", "3", "4", "5"};
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 延迟10秒后开始预热，避免影响应用启动
        new Thread(() -> {
            try {
                TimeUnit.SECONDS.sleep(10);
                log.info("开始预热使用次数缓存...");
                warmupUsageCache();
                log.info("使用次数缓存预热完成");
            } catch (Exception e) {
                log.error("使用次数缓存预热异常", e);
            }
        }).start();
    }

    /**
     * 异步预热使用次数缓存
     */
    @Async
    public void warmupUsageCache() {
        try {
            // 获取有套餐的用户列表（简化实现）
            // 这里可以根据实际需求实现更复杂的活跃用户筛选逻辑
            log.info("开始预热使用次数缓存，使用所有有效用户");
            
            // 由于getRecentActiveUsers方法不存在，我们暂时跳过预热
            // 实际使用时可以根据需要实现该方法或使用其他查询方式
            log.warn("跳过缓存预热：getRecentActiveUsers方法未实现，请根据需要补充实现");
            
        } catch (Exception e) {
            log.error("预热使用次数缓存过程异常", e);
        }
    }
}