package com.instser.chat.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 系统启动时的缓存清理组件
 * 用于清理可能不兼容的旧版本缓存数据
 *
 * @author instser
 * @since 2025-09-04
 */
@Component
@Order(1) // 优先级高，在其他组件之前执行
@Slf4j
public class CacheCleanupRunner implements ApplicationRunner {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("开始系统启动缓存清理...");
        
        try {
            // 清理旧的Package配置缓存（完整Package对象格式）
            clearOldPackageConfigCaches();
            
            log.info("系统启动缓存清理完成");
        } catch (Exception e) {
            log.error("系统启动缓存清理异常，但不影响系统启动", e);
        }
    }

    /**
     * 清理旧的Package配置缓存
     * 因为我们改成了使用PackageCacheDTO，旧的完整Package对象会导致反序列化错误
     */
    private void clearOldPackageConfigCaches() {
        try {
            String pattern = "package:config:*";
            Set<String> keys = redisTemplate.keys(pattern);
            
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("清理旧格式Package配置缓存{}个", keys.size());
            } else {
                log.debug("未发现需要清理的Package配置缓存");
            }
        } catch (Exception e) {
            log.warn("清理旧Package配置缓存失败，但不影响系统功能", e);
        }
    }
}