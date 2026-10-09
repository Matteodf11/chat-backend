package com.matteo.chat_backend.service;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.HttpRequest;
import static org.junit.jupiter.api.Assertions.*;

class SupabaseRequestHeadersTest {
    @Test
    void legacyJwtRetainsBearerAndApiKeyHeaders() {
        var request = SupabaseRequestHeaders.authorize(builder(), "test.legacy.jwt").build();
        assertEquals("Bearer test.legacy.jwt", request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("test.legacy.jwt", request.headers().firstValue("apikey").orElseThrow());
    }

    @Test
    void newSecretKeyIsNotSentAsJwt() {
        var request = SupabaseRequestHeaders.authorize(builder(), "sb_secret_test_only").build();
        assertTrue(request.headers().firstValue("Authorization").isEmpty());
        assertEquals("sb_secret_test_only", request.headers().firstValue("apikey").orElseThrow());
    }

    @Test
    void missingKeyFailsWithoutMakingARequest() {
        assertThrows(IllegalStateException.class, () -> SupabaseRequestHeaders.authorize(builder(), " "));
        assertThrows(IllegalStateException.class, () -> SupabaseRequestHeaders.authorize(builder(), null));
    }

    private HttpRequest.Builder builder() {
        return HttpRequest.newBuilder(URI.create("https://example.invalid/storage/v1/object"));
    }
}
