package com.matteo.chat_backend.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttachmentStorageService {

    public static final long MAX_ATTACHMENT_BYTES = 25L * 1024L * 1024L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif",
            "application/pdf", "text/plain", "text/csv", "application/zip",
            "application/x-zip-compressed", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${supabase.url:}")
    private String supabaseUrl;

    @Value("${supabase.service-role-key:}")
    private String serviceRoleKey;

    @Value("${supabase.storage.attachment-bucket:attachments}")
    private String attachmentBucket;

    public StoredAttachment upload(MultipartFile file) {
        validateConfiguration();
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Selecciona un archivo");
        }
        String contentType = normalizeContentType(file.getContentType());
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Este tipo de archivo no esta permitido");
        }
        if (file.getSize() > MAX_ATTACHMENT_BYTES) {
            throw new IllegalArgumentException("El archivo no puede superar 25 MB");
        }

        try {
            byte[] bytes = file.getBytes();
            validateSignature(bytes, contentType);
            String originalName = safeFileName(file.getOriginalFilename());
            String objectKey = "messages/" + UUID.randomUUID() + "/" + originalName;
            HttpRequest request = authorizedRequest("/storage/v1/object/" + attachmentBucket + "/" + encodePath(objectKey))
                    .header("Content-Type", contentType)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            requireSuccess(response, "Supabase no acepto el archivo");
            return new StoredAttachment(objectKey, originalName, contentType, bytes.length);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RuntimeException("No se pudo subir el archivo: " + exception.getMessage(), exception);
        }
    }

    public String createSignedUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) return null;
        validateConfiguration();
        try {
            HttpRequest request = authorizedRequest("/storage/v1/object/sign/" + attachmentBucket + "/" + encodePath(objectKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"expiresIn\":3600}"))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            requireSuccess(response, "No se pudo crear el enlace del archivo");
            JsonNode json = objectMapper.readTree(response.body());
            String signedPath = json.path("signedURL").asText();
            if (signedPath.isBlank()) return null;
            return signedPath.startsWith("http") ? signedPath : normalizedUrl() + "/storage/v1" + signedPath;
        } catch (Exception exception) {
            return null;
        }
    }

    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) return;
        try {
            String body = objectMapper.writeValueAsString(java.util.Map.of("prefixes", java.util.List.of(objectKey)));
            HttpRequest request = authorizedRequest("/storage/v1/object/" + attachmentBucket)
                    .header("Content-Type", "application/json")
                    .method("DELETE", HttpRequest.BodyPublishers.ofString(body))
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // The message must still be removable if storage is temporarily unavailable.
        }
    }

    private HttpRequest.Builder authorizedRequest(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(normalizedUrl() + path))
                .header("Authorization", "Bearer " + serviceRoleKey)
                .header("apikey", serviceRoleKey);
    }

    private void validateSignature(byte[] bytes, String contentType) {
        if (bytes.length == 0) throw new IllegalArgumentException("El archivo esta vacio");
        boolean valid = switch (contentType) {
            case "image/png" -> startsWith(bytes, 0x89, 0x50, 0x4E, 0x47);
            case "image/jpeg", "image/jpg" -> startsWith(bytes, 0xFF, 0xD8, 0xFF);
            case "image/gif" -> startsWith(bytes, 0x47, 0x49, 0x46, 0x38);
            case "image/webp" -> startsWith(bytes, 0x52, 0x49, 0x46, 0x46) && bytes.length > 11
                    && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50;
            case "application/pdf" -> startsWith(bytes, 0x25, 0x50, 0x44, 0x46);
            case "application/zip", "application/x-zip-compressed",
                 "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                 "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                 "application/vnd.openxmlformats-officedocument.presentationml.presentation" ->
                    startsWith(bytes, 0x50, 0x4B);
            default -> true;
        };
        if (!valid) throw new IllegalArgumentException("El contenido del archivo no coincide con su formato");
    }

    private boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xFF) != prefix[i]) return false;
        }
        return true;
    }

    private String safeFileName(String value) {
        String name = value == null ? "archivo" : value.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[^a-zA-Z0-9._ -]", "_").trim();
        if (name.isBlank()) name = "archivo";
        if (name.length() > 180) name = name.substring(name.length() - 180);
        return name;
    }

    private String normalizeContentType(String value) {
        return value == null ? "application/octet-stream" : value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private void validateConfiguration() {
        if (supabaseUrl == null || supabaseUrl.isBlank()) throw new RuntimeException("Falta SUPABASE_URL");
        if (serviceRoleKey == null || serviceRoleKey.isBlank()) throw new RuntimeException("Falta SUPABASE_SERVICE_ROLE_KEY");
    }

    private String normalizedUrl() {
        return supabaseUrl.endsWith("/") ? supabaseUrl.substring(0, supabaseUrl.length() - 1) : supabaseUrl;
    }

    private String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20").replace("%2F", "/");
    }

    private void requireSuccess(HttpResponse<String> response, String message) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException(message + ": " + response.body());
        }
    }

    public record StoredAttachment(String key, String name, String contentType, long size) {}
}
