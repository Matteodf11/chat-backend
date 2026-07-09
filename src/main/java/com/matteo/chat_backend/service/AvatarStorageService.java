package com.matteo.chat_backend.service;

import com.matteo.chat_backend.entity.User;
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

@Service
@RequiredArgsConstructor
public class AvatarStorageService {

    private static final long MAX_AVATAR_BYTES = 2L * 1024L * 1024L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${supabase.url:}")
    private String supabaseUrl;

    @Value("${supabase.service-role-key:}")
    private String serviceRoleKey;

    @Value("${supabase.storage.avatar-bucket:avatars}")
    private String avatarBucket;

    public String uploadAvatar(User user, MultipartFile file) {
        validateFile(file);
        validateConfiguration();

        try {
            String extension = extensionFor(file.getContentType());
            String objectPath = "users/" + user.getId() + "/avatar." + extension;
            String encodedPath = encodeObjectPath(objectPath);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizedSupabaseUrl() + "/storage/v1/object/" + avatarBucket + "/" + encodedPath))
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .header("apikey", serviceRoleKey)
                    .header("Content-Type", file.getContentType())
                    .header("x-upsert", "true")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(file.getBytes()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("Supabase no acepto el avatar: " + response.body());
            }

            return normalizedSupabaseUrl() + "/storage/v1/object/public/" + avatarBucket + "/" + encodedPath;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo subir el avatar", e);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Selecciona una imagen");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new RuntimeException("El avatar no puede superar 2 MB");
        }
        if (file.getContentType() == null || !ALLOWED_CONTENT_TYPES.contains(file.getContentType().toLowerCase(Locale.ROOT))) {
            throw new RuntimeException("Formato de avatar no permitido");
        }
    }

    private void validateConfiguration() {
        if (supabaseUrl == null || supabaseUrl.isBlank()) {
            throw new RuntimeException("Falta SUPABASE_URL");
        }
        if (serviceRoleKey == null || serviceRoleKey.isBlank()) {
            throw new RuntimeException("Falta SUPABASE_SERVICE_ROLE_KEY");
        }
    }

    private String extensionFor(String contentType) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }

    private String encodeObjectPath(String objectPath) {
        return URLEncoder.encode(objectPath, StandardCharsets.UTF_8).replace("+", "%20").replace("%2F", "/");
    }

    private String normalizedSupabaseUrl() {
        return supabaseUrl.endsWith("/") ? supabaseUrl.substring(0, supabaseUrl.length() - 1) : supabaseUrl;
    }
}
