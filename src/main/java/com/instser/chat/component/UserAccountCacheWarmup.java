package com.instser.chat.component;

import com.instser.chat.service.UserAccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 用户账户缓存预热组件
 *
 * @author instser
 * @since 2025-09-03
 */
@Component
@Slf4j
public class UserAccountCacheWarmup {
    
    @Autowired
    private UserAccountService userAccountService;
    
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void warmupUserAccountCache() {
        log.info("开始预热用户账户缓存...");
        
        try {
            // 预热活跃用户的账户信息
            List<Long> activeUserIds = userAccountService.getActiveUserIds(1000);
            
            if (activeUserIds.isEmpty()) {
                log.info("没有找到活跃用户，跳过缓存预热");
                return;
            }
            
            // 使用并行流预热缓存，提高效率
            activeUserIds.parallelStream()
                .forEach(userId -> {
                    try {
                        userAccountService.getUserAccount(userId);
                    } catch (Exception e) {
                        log.warn("预热用户{}账户缓存失败: {}", userId, e.getMessage());
                    }
                });
                
            log.info("用户账户缓存预热完成，预热用户数: {}", activeUserIds.size());
        } catch (Exception e) {
            log.error("用户账户缓存预热异常", e);
        }
    }
}