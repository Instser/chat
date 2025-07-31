package com.instser.chat.config;

import com.instser.chat.service.ChatInterruptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * 聊天中断功能配置类
 *
 * 注意：超时会话清理任务已迁移到 OptimizedScheduledTaskService 统一管理
 * 此配置类保留用于其他聊天中断相关配置
 *
 * @author instser
 * @since 2025-06-26
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduled.chat-interrupt.enabled", havingValue = "true", matchIfMissing = false)
public class ChatInterruptConfig {

    private final ChatInterruptService chatInterruptService;

    /**
     * 定时清理超时的会话（每5分钟执行一次）
     *
     * 已禁用：此功能已迁移到 OptimizedScheduledTaskService.lightweightTask() 中统一管理
     * 如需启用此独立任务，请设置 app.scheduled.chat-interrupt.enabled=true
     */
    // @Scheduled(fixedRate = 300000) // 5分钟 = 300000毫秒
    public void cleanupTimeoutSessions() {
        try {
            log.debug("开始执行超时会话清理任务（独立任务）");
            chatInterruptService.cleanupTimeoutSessions();
            log.debug("超时会话清理任务执行完成（独立任务）");
        } catch (Exception e) {
            log.error("超时会话清理任务执行失败（独立任务）", e);
        }
    }
}
