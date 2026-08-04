package com.instser.chat.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManagementApiKeyServiceTest {

    private ManagementApiKeyService service;

    @BeforeEach
    void setUp() {
        service = new ManagementApiKeyService();
        ReflectionTestUtils.setField(service, "apiKeyEnabled", true);
        ReflectionTestUtils.setField(service, "masterApiKey", "master-secret-key-value");
        service.init();
    }

    @Test
    void extractApiKey_shouldOnlyAcceptHeader() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-API-Key")).thenReturn("header-key");
        when(request.getParameter("apiKey")).thenReturn("query-key");

        assertEquals("header-key", service.extractApiKey(request));
    }

    @Test
    void extractApiKey_shouldIgnoreQueryParameterOnly() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-API-Key")).thenReturn(null);
        when(request.getParameter("apiKey")).thenReturn("query-only-key");

        assertNull(service.extractApiKey(request));
    }

    @Test
    void validateRequest_shouldRejectQueryParameterOnlyAccess() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-API-Key")).thenReturn(null);
        when(request.getParameter("apiKey")).thenReturn("master-secret-key-value");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        assertFalse(service.validateRequest(request));
    }

    @Test
    void validateRequest_shouldAcceptValidHeaderKey() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-API-Key")).thenReturn("master-secret-key-value");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        assertTrue(service.validateRequest(request));
    }

    @Test
    void getMasterApiKey_shouldNoLongerReturnFullSecret() {
        assertNull(service.getMasterApiKey());
        String fingerprint = service.getMasterApiKeyFingerprint();
        assertTrue(fingerprint.contains("***"));
        assertFalse(fingerprint.contains("master-secret-key-value"));
    }
}
