package com.instser.chat.component;

import com.instser.chat.service.cache.CachedUsageService;
import com.instser.chat.service.cache.HybridCacheManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatLimitCacheManagerTest {

    @Mock
    private HybridCacheManager hybridCacheManager;

    @Mock
    private CachedUsageService cachedUsageService;

    @InjectMocks
    private ChatLimitCacheManager chatLimitCacheManager;

    @Test
    void shouldClearAllUserPackageCaches() throws Exception {
        Method method;
        try {
            method = ChatLimitCacheManager.class.getMethod("clearAllUserPackageCaches");
        } catch (NoSuchMethodException ex) {
            fail("缺少 clearAllUserPackageCaches() 管理方法");
            return;
        }

        assertDoesNotThrow(() -> method.invoke(chatLimitCacheManager));

        verify(hybridCacheManager).evictPattern("user:account:*");
        verify(hybridCacheManager).evictPattern("package:config:*");
        verify(hybridCacheManager).evictPattern("model:permission:*");
        verify(cachedUsageService).evictAllUserModelCaches();
    }
}
