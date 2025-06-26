package com.instser.chat.config;

import com.instser.chat.service.ChatInterruptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 聊天中断功能配置类
 * 
 * @author instser
 * @since 2025-06-26
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class ChatInterruptConfig {
    
    private final ChatInterruptService chatInterruptService;
    
    /**
     * 定时清理超时的会话（每5分钟执行一次）
     */
    @Scheduled(fixedRate = 300000) // 5分钟 = 300000毫秒
    public void cleanupTimeoutSessions() {
        try {
            log.debug("开始执行超时会话清理任务");
            chatInterruptService.cleanupTimeoutSessions();
            log.debug("超时会话清理任务执行完成");
        } catch (Exception e) {
            log.error("超时会话清理任务执行失败", e);
        }
    }
}
