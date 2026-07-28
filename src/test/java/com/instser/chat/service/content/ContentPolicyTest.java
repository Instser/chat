package com.instser.chat.service.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentPolicyTest {

    private final ContentPolicy policy = new ContentPolicy();

    @Test
    void detectShouldPassThroughNormalContent() {
        ContentPolicy.ContentDetectionResult result = policy.detectContentAndGetResult("hello world");
        assertEquals("hello world", result.getCleanedContent());
        assertFalse(result.wasFiltered());
        assertFalse(result.needsRetry());
    }

    @Test
    void detectShouldFilterAugmentProxyAds() {
        ContentPolicy.ContentDetectionResult result = policy.detectContentAndGetResult("欢迎使用 Augment Proxy 官网");
        assertTrue(result.wasFiltered());
        assertEquals("", result.getCleanedContent());
    }

    @Test
    void detectShouldFlagRetryableBlockedMessage() {
        ContentPolicy.ContentDetectionResult result = policy.detectContentAndGetResult(
                "Request blocked. Please reach out to support@example.com if you think this was a mistake.");
        assertTrue(result.needsRetry());
        assertFalse(result.wasFiltered());
    }
}
