package com.gamemasterx.server.asset.controller;

import com.gamemasterx.server.filter.AuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * REST controller for uploading local map assets.
 *
 * <p>Uploads are authorized, size-bounded, limited to allowed MIME types,
 * stored with a safe generated name, and protected against path traversal.</p>
 */
@RestController
@RequestMapping("/api/assets/maps")
public class MapAssetController {

    private final long maxBytes;
    private final Path uploadDir;
    private final Set<String> allowedTypes;

    public MapAssetController(
            @Value("${game.master.x.map.asset.max-bytes:10485760}") long maxBytes,
            @Value("${game.master.x.map.asset.upload-dir:./uploads/maps}") String uploadDirPath,
            @Value("${game.master.x.map.asset.allowed-types:image/png,image/jpeg,image/webp,image/gif}") String allowedTypesCsv) {
        this.maxBytes = maxBytes;
        this.uploadDir = Paths.get(uploadDirPath).toAbsolutePath().normalize();
        this.allowedTypes = Set.of(allowedTypesCsv.split(","));
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create upload directory: " + uploadDir, e);
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<MapAssetUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        // Authorization: require authenticated user
        Object userAttr = request.getAttribute(AuthFilter.AUTH_USER_ATTR);
        if (!(userAttr instanceof String userId) || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(new MapAssetUploadResponse(null, "File is empty"));
        }

        long size = file.getSize();
        if (size > maxBytes) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(new MapAssetUploadResponse(null, "File exceeds maximum allowed size"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !allowedTypes.contains(contentType.toLowerCase())) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body(new MapAssetUploadResponse(null, "Content type not allowed"));
        }

        String extension = extensionForContentType(contentType);
        String safeName = UUID.randomUUID().toString() + extension;
        // Path traversal protection: use generated name only, never original filename
        Path target = uploadDir.resolve(safeName).normalize();
        // Ensure target stays within uploadDir
        if (!target.startsWith(uploadDir)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new MapAssetUploadResponse(null, "Invalid target path"));
        }

        try {
            Files.createDirectories(uploadDir);
            file.transferTo(target.toFile());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MapAssetUploadResponse(null, "Failed to store file"));
        }

        String relativePath = uploadDir.relativize(target).toString();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MapAssetUploadResponse(relativePath, null));
    }

    private String extensionForContentType(String contentType) {
        return switch (contentType.toLowerCase()) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".bin";
        };
    }

    public static class MapAssetUploadResponse {
        private final String path;
        private final String error;

        public MapAssetUploadResponse(String path, String error) {
            this.path = path;
            this.error = error;
        }

        public String getPath() {
            return path;
        }

        public String getError() {
            return error;
        }
    }
}
