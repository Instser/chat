package com.instser.chat.service.stream;

import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamSubscriptionRegistryTest {

    @Test
    void cancelDisposesRegisteredSubscription() {
        StreamSubscriptionRegistry registry = new StreamSubscriptionRegistry();
        AtomicInteger disposed = new AtomicInteger();
        Disposable disposable = () -> disposed.incrementAndGet();

        registry.register("s1", disposable);
        assertTrue(registry.hasSubscription("s1"));
        assertTrue(registry.cancel("s1"));
        assertEquals(1, disposed.get());
        assertFalse(registry.hasSubscription("s1"));
        assertFalse(registry.cancel("s1"));
    }

    @Test
    void registerReplacesAndDisposesPrevious() {
        StreamSubscriptionRegistry registry = new StreamSubscriptionRegistry();
        AtomicInteger firstDisposed = new AtomicInteger();
        AtomicInteger secondDisposed = new AtomicInteger();
        Disposable first = createDisposable(firstDisposed);
        Disposable second = createDisposable(secondDisposed);

        registry.register("s1", first);
        registry.register("s1", second);

        assertEquals(1, firstDisposed.get());
        assertEquals(0, secondDisposed.get());
        assertTrue(registry.hasSubscription("s1"));

        registry.cancel("s1");
        assertEquals(1, secondDisposed.get());
    }

    @Test
    void removeDoesNotDispose() {
        StreamSubscriptionRegistry registry = new StreamSubscriptionRegistry();
        AtomicInteger disposed = new AtomicInteger();
        Disposable disposable = createDisposable(disposed);

        registry.register("s1", disposable);
        registry.remove("s1");
        assertEquals(0, disposed.get());
        assertFalse(registry.hasSubscription("s1"));
    }

    private static Disposable createDisposable(AtomicInteger counter) {
        return new Disposable() {
            private volatile boolean disposed;

            @Override
            public void dispose() {
                if (!disposed) {
                    disposed = true;
                    counter.incrementAndGet();
                }
            }

            @Override
            public boolean isDisposed() {
                return disposed;
            }
        };
    }
}
