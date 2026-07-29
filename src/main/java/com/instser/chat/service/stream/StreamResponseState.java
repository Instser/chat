package com.instser.chat.service.stream;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 流式响应过程状态，替代 ChatServiceImpl 中多个 final boolean[] 状态数组。
 *
 * <p>仅描述一次 AI 流式调用过程中的游标与互斥标记，不承载业务决策。</p>
 * <p>完成闸门字段使用原子变量，便于非阻塞回调并发下只收尾一次。</p>
 */
public final class StreamResponseState {

    private boolean hasContentSent;
    private boolean realAiResponse;
    private boolean interrupted;
    private boolean interruptSaved;
    private final AtomicBoolean completionHandled = new AtomicBoolean(false);
    private boolean firstTokenMarked;
    private boolean networkResultsSent;
    private final StreamCompletionGate completionGate = new StreamCompletionGate();

    public boolean hasContentSent() {
        return hasContentSent;
    }

    public boolean isRealAiResponse() {
        return realAiResponse;
    }

    public boolean isInterrupted() {
        return interrupted;
    }

    public boolean isInterruptSaved() {
        return interruptSaved;
    }

    public boolean isCompletionHandled() {
        return completionHandled.get();
    }

    /**
     * 线程安全完成闸门：与 {@link #canHandleCompletion()}/{@link #markCompletionHandled()} 等价的 CAS 入口。
     */
    public boolean tryHandleCompletion() {
        boolean entered = completionHandled.compareAndSet(false, true);
        if (entered) {
            completionGate.tryEnter();
        }
        return entered;
    }

    public StreamCompletionGate completionGate() {
        return completionGate;
    }

    public boolean isFirstTokenMarked() {
        return firstTokenMarked;
    }

    public boolean isNetworkResultsSent() {
        return networkResultsSent;
    }

    public void markContentSent() {
        this.hasContentSent = true;
    }

    public void markRealAiResponse() {
        this.realAiResponse = true;
        this.hasContentSent = true;
    }

    public void markInterrupted() {
        this.interrupted = true;
    }

    public void markInterruptSaved() {
        this.interruptSaved = true;
    }

    public void markCompletionHandled() {
        this.completionHandled.set(true);
        this.completionGate.tryEnter();
    }

    public void markFirstToken() {
        this.firstTokenMarked = true;
        this.hasContentSent = true;
    }

    public void markNetworkResultsSent() {
        this.networkResultsSent = true;
    }

    /**
     * 是否允许保存中断部分响应：已中断且尚未保存。
     */
    public boolean canSaveInterrupt() {
        return interrupted && !interruptSaved;
    }

    /**
     * 是否允许执行完成处理：尚未处理完成逻辑。
     * 异步路径优先使用 {@link #tryHandleCompletion()}。
     */
    public boolean canHandleCompletion() {
        return !completionHandled.get();
    }

    /**
     * 兼容旧逻辑：标记中断检测发生（可能尚未写出中断完成帧）。
     */
    public void markInterruptDetected() {
        this.interrupted = true;
        this.interruptSaved = true;
    }
}
