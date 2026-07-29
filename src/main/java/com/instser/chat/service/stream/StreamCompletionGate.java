package com.instser.chat.service.stream;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 同一条流的完成/收尾只进入一次的闸门（线程安全）。
 *
 * <p>可与 {@link StreamResponseState} 配合：状态对象描述流过程，本闸门专管“收尾互斥”。</p>
 */
public final class StreamCompletionGate {

    private final AtomicBoolean completed = new AtomicBoolean(false);

    /**
     * @return true 表示当前调用方获得收尾权；false 表示已有人完成过
     */
    public boolean tryEnter() {
        return completed.compareAndSet(false, true);
    }

    public boolean isCompleted() {
        return completed.get();
    }

    public void resetForTests() {
        completed.set(false);
    }
}
