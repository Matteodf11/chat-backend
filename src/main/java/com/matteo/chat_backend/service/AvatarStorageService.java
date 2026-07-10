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
import java.util.UUID;

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
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Selecciona una imagen");
        }

        try {
            return uploadAvatar(user, file.getBytes(), file.getContentType());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo leer el avatar seleccionado: " + e.getMessage(), e);
        }
    }

    public String uploadAvatar(User user, byte[] bytes, String contentType) {
        return uploadProfileImage(user, bytes, contentType, "avatar");
    }

    public String uploadBanner(User user, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Selecciona una imagen");
        }

        try {
            return uploadBanner(user, file.getBytes(), file.getContentType());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo leer el banner seleccionado: " + e.getMessage(), e);
        }
    }

    public String uploadBanner(User user, byte[] bytes, String contentType) {
        return uploadProfileImage(user, bytes, contentType, "banner");
    }

    private String uploadProfileImage(User user, byte[] bytes, String contentType, String imageName) {
        validateFile(bytes, contentType);
        validateConfiguration();

        try {
            String extension = extensionFor(contentType);
            String objectPath = "users/" + user.getId() + "/" + imageName + "." + extension;
            String encodedPath = encodeObjectPath(objectPath);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizedSupabaseUrl() + "/storage/v1/object/" + avatarBucket + "/" + encodedPath))
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .header("apikey", serviceRoleKey)
                    .header("Content-Type", contentType)
                    .header("x-upsert", "true")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("Supabase no acepto la imagen: " + response.body());
            }

            return "/api/users/" + user.getId() + "/" + imageName + "/" + imageName + "." + extension + "?v=" + System.currentTimeMillis();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo subir la imagen: " + e.getMessage(), e);
        }
    }

    public AvatarFile downloadAvatar(UUID userId, String fileName) {
        return downloadProfileImage(userId, fileName, "avatar");
    }

    public AvatarFile downloadBanner(UUID userId, String fileName) {
        return downloadProfileImage(userId, fileName, "banner");
    }

    private AvatarFile downloadProfileImage(UUID userId, String fileName, String imageName) {
        validateConfiguration();

        try {
            String extension = extensionFromFileName(fileName);
            String objectPath = "users/" + userId + "/" + imageName + "." + extension;
            String encodedPath = encodeObjectPath(objectPath);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizedSupabaseUrl() + "/storage/v1/object/" + avatarBucket + "/" + encodedPath))
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .header("apikey", serviceRoleKey)
                    .GET()
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("Imagen no encontrada");
            }

            return new AvatarFile(response.body(), contentTypeFor(extension));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo leer la imagen: " + e.getMessage(), e);
        }
    }

    private void validateFile(byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            throw new RuntimeException("Selecciona una imagen");
        }
        if (bytes.length > MAX_AVATAR_BYTES) {
            throw new RuntimeException("El avatar no puede superar 2 MB");
        }
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
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

    private String extensionFromFileName(String fileName) {
        if (fileName == null) {
            throw new RuntimeException("Avatar no encontrado");
        }
        String lowerFileName = fileName.toLowerCase(Locale.ROOT);
        if (lowerFileName.endsWith(".png")) {
            return "png";
        }
        if (lowerFileName.endsWith(".webp")) {
            return "webp";
        }
        if (lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg")) {
            return "jpg";
        }
        throw new RuntimeException("Formato de avatar no permitido");
    }

    private String contentTypeFor(String extension) {
        return switch (extension) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }

    private String encodeObjectPath(String objectPath) {
        return URLEncoder.encode(objectPath, StandardCharsets.UTF_8).replace("+", "%20").replace("%2F", "/");
    }

    private String normalizedSupabaseUrl() {
        return supabaseUrl.endsWith("/") ? supabaseUrl.substring(0, supabaseUrl.length() - 1) : supabaseUrl;
    }

    public record AvatarFile(byte[] bytes, String contentType) {
    }
}
