package com.instser.chat.service.cache;

import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * Redis熔断器事件
 * 
 * @author instser
 * @since 2025-07-24
 */
public class RedisCircuitBreakerEvent extends ApplicationEvent {

    private final boolean isFailedOver;
    private final LocalDateTime eventTime;
    private final RedisCircuitBreaker.State state;
    private final int failureCount;

    public RedisCircuitBreakerEvent(RedisCircuitBreaker source, boolean isFailedOver) {
        super(source);
        this.isFailedOver = isFailedOver;
        this.eventTime = LocalDateTime.now();
        this.state = source.getState();
        this.failureCount = source.getFailureCount();
    }

    public boolean isFailedOver() {
        return isFailedOver;
    }

    public LocalDateTime getEventTime() {
        return eventTime;
    }

    public RedisCircuitBreaker.State getState() {
        return state;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public RedisCircuitBreaker getCircuitBreaker() {
        return (RedisCircuitBreaker) getSource();
    }
}
