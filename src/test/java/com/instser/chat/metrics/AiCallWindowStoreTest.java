package com.instser.chat.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiCallWindowStoreTest {

    @Test
    void recordsAndAggregatesByWindow() {
        AiCallWindowStore store = new AiCallWindowStore();
        store.recordSuccess("deepseek-v4-flash", 1000, 2000);
        store.recordSuccess("deepseek-v4-flash", 2000, 3000);
        store.recordFailure("deepseek-v4-flash");
        store.recordSuccess("qwen", 500, 800);

        AiCallWindowStore.Snapshot day = store.snapshot(24);
        assertEquals(3L, ((Number) day.totals.get("successCount")).longValue());
        assertEquals(1L, ((Number) day.totals.get("failedCount")).longValue());
        assertEquals(2, day.byModel.size());
        assertFalse(day.timeline.isEmpty());
        assertEquals("hour", day.meta.get("bucketUnit"));
    }
}
