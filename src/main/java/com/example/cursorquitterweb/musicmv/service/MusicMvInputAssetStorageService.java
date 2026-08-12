package com.example.cursorquitterweb.musicmv.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.example.cursorquitterweb.musicmv.support.IdUtils;

@Service
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class MusicMvInputAssetStorageService {
    private static final long MAX_MUSIC_BYTES = 500L * 1024L * 1024L;
    private static final long MAX_IMAGE_BYTES = 50L * 1024L * 1024L;
    private static final Duration DOWNLOAD_TTL = Duration.ofDays(7);

    private final R2StorageService r2;
    private final Path localRoot;
    private final String configuredPublicBaseUrl;

    public MusicMvInputAssetStorageService(
            R2StorageService r2,
            @Value("${music-mv.render.local-storage-dir:storage/music-mv-render}") String localDir,
            @Value("${music-mv.public-base-url:}") String configuredPublicBaseUrl
    ) {
        this.r2 = r2;
        this.localRoot = Paths.get(localDir).toAbsolutePath().normalize().resolve("inputs");
        this.configuredPublicBaseUrl = configuredPublicBaseUrl == null
                ? "" : configuredPublicBaseUrl.trim();
    }

    public StoredInputAsset store(String clientId, String kind, String fileName,
                                  String contentType, long contentLength, InputStream input,
                                  String requestBaseUrl) throws IOException {
        String normalizedKind = normalizeKind(kind);
        long maxBytes = "music".equals(normalizedKind) ? MAX_MUSIC_BYTES : MAX_IMAGE_BYTES;
        if (contentLength <= 0L || contentLength > maxBytes) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MV_INPUT_ASSET_SIZE_INVALID",
                    "Input asset size is invalid");
        }
        String normalizedContentType = normalizeContentType(normalizedKind, contentType, fileName);
        String safeFileName = IdUtils.safeFilename(fileName);
        String assetId = IdUtils.token("mva");
        Path stagingDir = localRoot.resolve(".staging").normalize();
        requireInside(stagingDir);
        Files.createDirectories(stagingDir);
        Path staging = Files.createTempFile(stagingDir, assetId + "-", ".upload");
        String sha256;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long copied = copyAndDigest(input, staging, digest, maxBytes);
            if (copied != contentLength) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "MV_INPUT_ASSET_TRUNCATED",
                        "Input asset upload was truncated");
            }
            sha256 = hex(digest.digest());
        } catch (ApiException exception) {
            Files.deleteIfExists(staging);
            throw exception;
        } catch (Exception exception) {
            Files.deleteIfExists(staging);
            throw new IOException("Store Music MV input asset failed", exception);
        }

        Instant expiresAt = Instant.now().plus(DOWNLOAD_TTL);
        String downloadUrl;
        String storage;
        try {
            if (r2.isConfigured()) {
                String objectKey = "music-mv-inputs/" + safeId(clientId) + "/" + assetId
                        + "/" + safeFileName;
                try (InputStream stagedInput = Files.newInputStream(staging)) {
                    r2.write(objectKey, stagedInput, contentLength, normalizedContentType);
                }
                downloadUrl = r2.presignedGetUrl(objectKey, DOWNLOAD_TTL);
                storage = "r2";
                Files.deleteIfExists(staging);
            } else {
                Path assetDir = localRoot.resolve(assetId).normalize();
                requireInside(assetDir);
                Files.createDirectories(assetDir);
                String extension = extension(safeFileName);
                Path source = assetDir.resolve("source" + extension).normalize();
                requireInside(source);
                Files.move(staging, source, StandardCopyOption.REPLACE_EXISTING);
                String accessToken = UUID.randomUUID().toString().replace("-", "")
                        + UUID.randomUUID().toString().replace("-", "");
                Properties metadata = new Properties();
                metadata.setProperty("accessToken", accessToken);
                metadata.setProperty("fileName", safeFileName);
                metadata.setProperty("contentType", normalizedContentType);
                metadata.setProperty("sizeBytes", Long.toString(contentLength));
                metadata.setProperty("sha256", sha256);
                metadata.setProperty("sourceFile", source.getFileName().toString());
                metadata.setProperty("expiresAt", expiresAt.toString());
                try (OutputStream metadataOutput = Files.newOutputStream(assetDir.resolve("asset.properties"))) {
                    metadata.store(metadataOutput, "Music MV input asset");
                }
                String baseUrl = !configuredPublicBaseUrl.isEmpty()
                        ? configuredPublicBaseUrl : requestBaseUrl;
                downloadUrl = UriComponentsBuilder.fromHttpUrl(trimTrailingSlash(baseUrl))
                        .path("/api/music-mv/v1/assets/").path(assetId)
                        .queryParam("access", accessToken).build().toUriString();
                storage = "local";
            }
        } catch (Exception exception) {
            Files.deleteIfExists(staging);
            if (exception instanceof ApiException) throw (ApiException) exception;
            throw new IOException("Publish Music MV input asset failed", exception);
        }
        return new StoredInputAsset(assetId, normalizedKind, downloadUrl, sha256,
                safeFileName, normalizedContentType, contentLength, expiresAt, storage);
    }

    public LocalAsset localAsset(String assetId, String accessToken) throws IOException {
        if (assetId == null || !assetId.matches("mva_[a-f0-9]{32}")) {
            throw notFound();
        }
        Path assetDir = localRoot.resolve(assetId).normalize();
        requireInside(assetDir);
        Path metadataPath = assetDir.resolve("asset.properties");
        if (!Files.isRegularFile(metadataPath)) throw notFound();
        Properties metadata = new Properties();
        try (InputStream metadataInput = Files.newInputStream(metadataPath)) {
            metadata.load(metadataInput);
        }
        String expected = metadata.getProperty("accessToken", "");
        if (!MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                (accessToken == null ? "" : accessToken)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw notFound();
        }
        Instant expiresAt = Instant.parse(metadata.getProperty("expiresAt"));
        if (Instant.now().isAfter(expiresAt)) {
            throw new ApiException(HttpStatus.GONE, "MV_INPUT_ASSET_EXPIRED",
                    "Input asset has expired");
        }
        Path source = assetDir.resolve(metadata.getProperty("sourceFile", "source")).normalize();
        requireInside(source);
        if (!Files.isRegularFile(source)) throw notFound();
        Resource resource = new InputStreamResource(Files.newInputStream(source));
        return new LocalAsset(resource, metadata.getProperty("fileName", "asset"),
                metadata.getProperty("contentType", "application/octet-stream"),
                Long.parseLong(metadata.getProperty("sizeBytes", "0")));
    }

    private long copyAndDigest(InputStream input, Path target, MessageDigest digest,
                               long maxBytes) throws IOException {
        long total = 0L;
        byte[] buffer = new byte[64 * 1024];
        try (OutputStream output = Files.newOutputStream(target)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "MV_INPUT_ASSET_SIZE_INVALID",
                            "Input asset size is invalid");
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }
        return total;
    }

    private String normalizeKind(String kind) {
        String value = kind == null ? "" : kind.trim().toLowerCase();
        if (!"music".equals(value) && !"image".equals(value)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MV_INPUT_ASSET_KIND_INVALID",
                    "Input asset kind must be music or image");
        }
        return value;
    }

    private String normalizeContentType(String kind, String contentType, String fileName) {
        String value = contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase();
        if ("music".equals(kind)) {
            if (value.startsWith("audio/")) return value;
            String lower = fileName == null ? "" : fileName.toLowerCase();
            if (lower.endsWith(".mp3")) return "audio/mpeg";
            if (lower.endsWith(".m4a")) return "audio/mp4";
            if (lower.endsWith(".wav")) return "audio/wav";
        } else if (value.startsWith("image/")) {
            return value;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "MV_INPUT_ASSET_TYPE_INVALID",
                "Input asset content type is invalid");
    }

    private void requireInside(Path path) {
        if (!path.toAbsolutePath().normalize().startsWith(localRoot.toAbsolutePath().normalize())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MV_INPUT_STORAGE_PATH_BLOCKED",
                    "Invalid input asset storage path");
        }
    }

    private String safeId(String value) {
        String result = value == null ? "" : value.trim();
        if (!result.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MV_RENDER_CLIENT_ID_INVALID",
                    "Identifier is invalid");
        }
        return result;
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 && dot >= fileName.length() - 10 ? fileName.substring(dot) : "";
    }

    private String trimTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        if (result.isEmpty()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "MV_INPUT_PUBLIC_URL_MISSING", "Music MV public base URL is unavailable");
        }
        return result;
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "MV_INPUT_ASSET_NOT_FOUND",
                "Input asset was not found");
    }

    private String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }

    public static final class StoredInputAsset {
        private final String assetId;
        private final String kind;
        private final String url;
        private final String sha256;
        private final String fileName;
        private final String contentType;
        private final long sizeBytes;
        private final Instant expiresAt;
        private final String storage;

        StoredInputAsset(String assetId, String kind, String url, String sha256,
                         String fileName, String contentType, long sizeBytes,
                         Instant expiresAt, String storage) {
            this.assetId = assetId;
            this.kind = kind;
            this.url = url;
            this.sha256 = sha256;
            this.fileName = fileName;
            this.contentType = contentType;
            this.sizeBytes = sizeBytes;
            this.expiresAt = expiresAt;
            this.storage = storage;
        }

        public String getAssetId() { return assetId; }
        public String getKind() { return kind; }
        public String getUrl() { return url; }
        public String getSha256() { return sha256; }
        public String getFileName() { return fileName; }
        public String getContentType() { return contentType; }
        public long getSizeBytes() { return sizeBytes; }
        public String getExpiresAt() { return expiresAt.toString(); }
        public String getStorage() { return storage; }
    }

    public static final class LocalAsset {
        private final Resource resource;
        private final String fileName;
        private final String contentType;
        private final long sizeBytes;

        LocalAsset(Resource resource, String fileName, String contentType, long sizeBytes) {
            this.resource = resource;
            this.fileName = fileName;
            this.contentType = contentType;
            this.sizeBytes = sizeBytes;
        }

        public Resource getResource() { return resource; }
        public String getFileName() { return fileName; }
        public String getContentType() { return contentType; }
        public long getSizeBytes() { return sizeBytes; }
    }
}
