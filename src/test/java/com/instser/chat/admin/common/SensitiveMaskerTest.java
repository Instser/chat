package com.instser.chat.admin.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveMaskerTest {

    @Test
    void maskSecret_shouldHideMiddle() {
        String masked = SensitiveMasker.maskSecret("sk-abcdefghijklmn");
        assertTrue(masked.contains("***"));
        assertFalse(masked.contains("sk-abcdefghijklmn"));
    }

    @Test
    void maskEmail_shouldKeepDomain() {
        assertEquals("a***e@example.com", SensitiveMasker.maskEmail("alice@example.com"));
    }

    @Test
    void hasSecret_shouldDetectBlank() {
        assertFalse(SensitiveMasker.hasSecret(" "));
        assertTrue(SensitiveMasker.hasSecret("token"));
    }
}
