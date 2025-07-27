package com.instser.chat.service.cache;

import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * Redis熔断器重置事件
 * 
 * @author instser
 * @since 2025-07-24
 */
public class RedisCircuitBreakerResetEvent extends ApplicationEvent {

    private final LocalDateTime eventTime;

    public RedisCircuitBreakerResetEvent(RedisCircuitBreaker source) {
        super(source);
        this.eventTime = LocalDateTime.now();
    }

    public LocalDateTime getEventTime() {
        return eventTime;
    }

    public RedisCircuitBreaker getCircuitBreaker() {
        return (RedisCircuitBreaker) getSource();
    }
}
