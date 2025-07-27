package com.instser.chat.service.cache;

import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * Redis恢复事件
 * 
 * @author instser
 * @since 2025-07-24
 */
public class RedisRecoveryEvent extends ApplicationEvent {

    private final LocalDateTime eventTime;
    private final int successCount;

    public RedisRecoveryEvent(RedisCircuitBreaker source) {
        super(source);
        this.eventTime = LocalDateTime.now();
        this.successCount = source.getSuccessCount();
    }

    public LocalDateTime getEventTime() {
        return eventTime;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public RedisCircuitBreaker getCircuitBreaker() {
        return (RedisCircuitBreaker) getSource();
    }
}
