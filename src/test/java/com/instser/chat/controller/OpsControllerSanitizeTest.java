package com.instser.chat.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsControllerSanitizeTest {

    @Test
    void sanitizeForAi_shouldRedactApiKeyAndBearerToken() {
        String input = "api_key=sk-abc123 password: SuperSecret Bearer your-jwt-token user@example.com";
        String sanitized = OpsController.sanitizeForAi(input);

        assertTrue(sanitized.contains("***REDACTED***") || sanitized.contains("***JWT_REDACTED***"));
        assertFalse(sanitized.contains("sk-abc123"));
        assertFalse(sanitized.contains("SuperSecret"));
        assertFalse(sanitized.contains("user@example.com"));
        assertTrue(sanitized.contains("***EMAIL_REDACTED***"));
    }

    @Test
    void sanitizeForAi_shouldKeepNormalStackTrace() {
        String input = "NullPointerException at ChatServiceImpl.java:1602";
        assertEquals(input, OpsController.sanitizeForAi(input));
    }

    @Test
    void sanitizeForAi_shouldHandleNullAndEmpty() {
        assertEquals(null, OpsController.sanitizeForAi(null));
        assertEquals("", OpsController.sanitizeForAi(""));
    }
}
