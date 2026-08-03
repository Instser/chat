package com.instser.chat.service.stream;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一次 AI 流式调用的过程状态：flags + content/reasoning 累计缓冲。
 * 完成落库只读本对象，不再外部平行传递多个 StringBuilder。
 */
public final class StreamResponseState {

    private boolean hasContentSent;
    private boolean hasReasoningSent;
    private boolean realAiResponse;
    private boolean interrupted;
    private boolean interruptSaved;
    private final AtomicBoolean completionHandled = new AtomicBoolean(false);
    private boolean firstTokenMarked;
    private boolean networkResultsSent;
    /** 命中系统提示泄露特征后，抑制后续 reasoning 写出与落库。 */
    private boolean reasoningSuppressed;
    private final StreamCompletionGate completionGate = new StreamCompletionGate();

    private final StringBuilder contentBuffer = new StringBuilder();
    private final StringBuilder reasoningBuffer = new StringBuilder();

    public boolean hasContentSent() {
        return hasContentSent;
    }

    public boolean hasReasoningSent() {
        return hasReasoningSent;
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

    public boolean isReasoningSuppressed() {
        return reasoningSuppressed;
    }

    public void markContentSent() {
        this.hasContentSent = true;
    }

    public void markReasoningSent() {
        this.hasReasoningSent = true;
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

    public boolean canSaveInterrupt() {
        return interrupted && !interruptSaved;
    }

    public boolean canHandleCompletion() {
        return !completionHandled.get();
    }

    public void markInterruptDetected() {
        this.interrupted = true;
        this.interruptSaved = true;
    }

    public void appendContent(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        contentBuffer.append(text);
        hasContentSent = true;
        realAiResponse = true;
    }

    public void appendReasoning(String text) {
        if (reasoningSuppressed) {
            return;
        }
        if (text == null || text.isEmpty()) {
            return;
        }
        reasoningBuffer.append(text);
        hasReasoningSent = true;
        realAiResponse = true;
    }

    /**
     * 判定 reasoning 含系统提示泄露特征：清空缓冲、标记抑制，避免落库/历史回放。
     * 已发出的 SSE 分片无法撤回，后续分片不再写出。
     */
    public void suppressReasoningLeak() {
        this.reasoningSuppressed = true;
        this.reasoningBuffer.setLength(0);
        this.hasReasoningSent = false;
    }

    public void clearReasoning() {
        this.reasoningBuffer.setLength(0);
        this.hasReasoningSent = false;
    }

    public String content() {
        return contentBuffer.toString();
    }

    public String reasoning() {
        return reasoningBuffer.toString();
    }

    public StringBuilder contentBuffer() {
        return contentBuffer;
    }

    public AssistantOutput toAssistantOutput() {
        return AssistantOutput.of(content(), reasoning());
    }

    public boolean hasPersistableOutput() {
        return toAssistantOutput().hasPersistableOutput();
    }

    /**
     * 是否已产生任何可观察输出（正文或思考）。
     */
    public boolean hasAnyOutput() {
        return hasContentSent || hasReasoningSent || contentBuffer.length() > 0 || reasoningBuffer.length() > 0;
    }
}
