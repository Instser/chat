package com.instser.chat.service.stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话维度的 Reactor 订阅取消表（非阻塞改造预重构）。
 *
 * <p>与 {@link com.instser.chat.service.ChatInterruptService} 分工：
 * 中断服务管业务中断标志/活跃会话；本表管上游订阅 dispose。</p>
 */
@Slf4j
@Component
public class StreamSubscriptionRegistry {

    private final ConcurrentHashMap<String, Disposable> subscriptions = new ConcurrentHashMap<>();

    /**
     * 注册或替换某会话的当前订阅。若已有旧订阅且未 dispose，先取消旧订阅。
     */
    public void register(String sessionId, Disposable disposable) {
        if (sessionId == null || sessionId.isBlank() || disposable == null) {
            return;
        }
        Disposable previous = subscriptions.put(sessionId, disposable);
        if (previous != null && previous != disposable && !previous.isDisposed()) {
            log.info("会话存在旧订阅，先取消。sessionId={}", sessionId);
            safeDispose(previous, sessionId, "replace");
        }
        log.debug("订阅已注册 sessionId={}", sessionId);
    }

    /**
     * 取消并移除会话订阅。
     *
     * @return true 表示找到了订阅并尝试取消
     */
    public boolean cancel(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        Disposable disposable = subscriptions.remove(sessionId);
        if (disposable == null) {
            log.debug("取消订阅时未找到注册项 sessionId={}", sessionId);
            return false;
        }
        safeDispose(disposable, sessionId, "cancel");
        return true;
    }

    /**
     * 仅移除注册项，不 dispose（流自然结束时由调用方已结束订阅时使用）。
     */
    public void remove(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        Disposable removed = subscriptions.remove(sessionId);
        if (removed != null) {
            log.debug("订阅注册项已移除 sessionId={}, disposed={}", sessionId, removed.isDisposed());
        }
    }

    public boolean hasSubscription(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        Disposable disposable = subscriptions.get(sessionId);
        return disposable != null && !disposable.isDisposed();
    }

    public int size() {
        return subscriptions.size();
    }

    /**
     * 统一收敛：按终态原因取消订阅（日志可检索 STREAM_TERMINAL）。
     */
    public boolean cancel(String sessionId, StreamTerminalReason reason) {
        log.info("STREAM_TERMINAL sessionId={} reason={} action=cancel_subscription",
                sessionId, reason != null ? reason : StreamTerminalReason.CANCELLED);
        return cancel(sessionId);
    }

    private void safeDispose(Disposable disposable, String sessionId, String reason) {
        try {
            if (!disposable.isDisposed()) {
                disposable.dispose();
                log.info("已 dispose 上游订阅 sessionId={}, reason={}", sessionId, reason);
            }
        } catch (Exception e) {
            log.warn("dispose 订阅失败 sessionId={}, reason={}, err={}", sessionId, reason, e.getMessage());
        }
    }
}
