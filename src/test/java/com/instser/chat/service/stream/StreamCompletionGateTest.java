package com.instser.chat.service.stream;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamCompletionGateTest {

    @Test
    void tryEnterSucceedsOnlyOnce() {
        StreamCompletionGate gate = new StreamCompletionGate();
        assertTrue(gate.tryEnter());
        assertFalse(gate.tryEnter());
        assertTrue(gate.isCompleted());
    }

    @Test
    void concurrentTryEnterOnlyOneWinner() throws Exception {
        StreamCompletionGate gate = new StreamCompletionGate();
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger winners = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    if (gate.tryEnter()) {
                        winners.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        pool.shutdownNow();
        assertEquals(1, winners.get());
        assertTrue(gate.isCompleted());
    }
}
