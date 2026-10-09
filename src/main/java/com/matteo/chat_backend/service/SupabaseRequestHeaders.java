package com.matteo.chat_backend.service;

import java.net.http.HttpRequest;

/** Shared authentication for legacy service_role JWTs and rotatable secret API keys. */
final class SupabaseRequestHeaders {
    private SupabaseRequestHeaders() {}

    static HttpRequest.Builder authorize(HttpRequest.Builder builder, String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Falta SUPABASE_SERVICE_ROLE_KEY");
        }
        builder.setHeader("apikey", key);
        // Secret API keys are not JWTs; sending them as Bearer causes Invalid JWT.
        if (!key.startsWith("sb_secret_")) {
            builder.setHeader("Authorization", "Bearer " + key);
        }
        return builder;
    }
}
