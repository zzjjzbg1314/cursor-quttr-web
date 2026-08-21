package com.example.cursorquitterweb.musicmv.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Properties;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.example.cursorquitterweb.musicmv.support.IdUtils;
import com.example.cursorquitterweb.musicmv.dto.MusicMvRenderJobCreateRequest;

@Service
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class MusicMvInputAssetStorageService {
    private static final long MAX_MUSIC_BYTES = 500L * 1024L * 1024L;
    private static final long MAX_IMAGE_BYTES = 50L * 1024L * 1024L;
    private static final Duration MUSIC_RETENTION = Duration.ofDays(3650);
    private static final Duration R2_DOWNLOAD_TTL = Duration.ofMinutes(15);
    private static final String MUSIC_R2_PREFIX = "music-mv-inputs/music/";
    private static final String USER_IMAGE_R2_PREFIX = "images/user/";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final R2StorageService r2;
    private final Path localRoot;
    private final String configuredPublicBaseUrl;
    private final boolean requireCloudInputAssets;
    private final Duration imageRetention;

    public MusicMvInputAssetStorageService(
            R2StorageService r2,
            @Value("${music-mv.render.local-storage-dir:storage/music-mv-render}") String localDir,
            @Value("${music-mv.public-base-url:}") String configuredPublicBaseUrl,
            @Value("${music-mv.render.require-cloud-input-assets:true}") boolean requireCloudInputAssets,
            @Value("${music-mv.render.image-retention-days:3650}") long imageRetentionDays
    ) {
        this.r2 = r2;
        this.localRoot = Paths.get(localDir).toAbsolutePath().normalize().resolve("inputs");
        this.configuredPublicBaseUrl = configuredPublicBaseUrl == null
                ? "" : configuredPublicBaseUrl.trim();
        this.requireCloudInputAssets = requireCloudInputAssets;
        this.imageRetention = Duration.ofDays(Math.max(1L, Math.min(imageRetentionDays, 3650L)));
    }

    public boolean isCloudStorageConfigured() {
        return r2.isConfigured();
    }

    public boolean isCloudStorageRequired() {
        return requireCloudInputAssets;
    }

    public StoredInputAsset store(String clientId, String kind, String fileName,
                                  String contentType, long contentLength, InputStream input,
                                  String requestBaseUrl) throws IOException {
        String normalizedKind = normalizeKind(kind);
        String normalizedOwnerId = normalizeOwnerId(clientId);
        if (requireCloudInputAssets && !r2.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "MV_INPUT_CLOUD_STORAGE_UNAVAILABLE",
                    "Cloud photo storage is temporarily unavailable", true, null);
        }
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

        Instant expiresAt = Instant.now().plus("music".equals(normalizedKind)
                ? MUSIC_RETENTION : imageRetention);
        String accessToken = accessToken(expiresAt);
        String baseUrl = !configuredPublicBaseUrl.isEmpty()
                ? configuredPublicBaseUrl : requestBaseUrl;
        String downloadUrl = capabilityUrl(baseUrl, assetId, accessToken);
        String storage;
        try {
            if (r2.isConfigured()) {
                String objectKey = r2ObjectKey(normalizedKind, assetId, accessToken);
                try (InputStream stagedInput = Files.newInputStream(staging)) {
                    r2.write(objectKey, stagedInput, contentLength, normalizedContentType);
                }
                Properties metadata = metadata(normalizedOwnerId, normalizedKind, accessToken,
                        safeFileName, normalizedContentType, contentLength, sha256, expiresAt);
                try {
                    r2.write(r2MetadataKey(normalizedKind, assetId, accessToken),
                            propertiesBytes(metadata), "application/octet-stream");
                } catch (RuntimeException exception) {
                    r2.delete(objectKey);
                    throw exception;
                }
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
                Properties metadata = metadata(normalizedOwnerId, normalizedKind, accessToken,
                        safeFileName, normalizedContentType, contentLength, sha256, expiresAt);
                metadata.setProperty("sourceFile", source.getFileName().toString());
                try (OutputStream metadataOutput = Files.newOutputStream(assetDir.resolve("asset.properties"))) {
                    metadata.store(metadataOutput, "Music MV input asset");
                }
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

    public InputAssetAccess access(String assetId, String accessToken) throws IOException {
        if (assetId == null || !assetId.matches("mva_[a-f0-9]{32}")) {
            throw notFound();
        }
        if (r2.isConfigured() && validR2Capability(accessToken)) {
            for (String kind : new String[] {"music", "image"}) {
                String objectKey = r2ObjectKey(kind, assetId, accessToken);
                if (r2.exists(objectKey)) {
                    requireNotExpired(r2Metadata(kind, assetId, accessToken));
                    return InputAssetAccess.redirect(r2.presignedGetUrl(objectKey,
                            R2_DOWNLOAD_TTL));
                }
            }
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
        return InputAssetAccess.local(resource, metadata.getProperty("fileName", "asset"),
                metadata.getProperty("contentType", "application/octet-stream"),
                Long.parseLong(metadata.getProperty("sizeBytes", "0")));
    }

    /** Kept for local-storage tests and callers that explicitly need a resource. */
    public LocalAsset localAsset(String assetId, String accessToken) throws IOException {
        InputAssetAccess result = access(assetId, accessToken);
        if (result.getLocalAsset() == null) throw notFound();
        return result.getLocalAsset();
    }

    /**
     * Resolves a browser-submitted photo back to canonical server-owned cloud metadata.
     * The render contract never trusts URL, hash, size or type values supplied by the browser.
     */
    public void requireOwnedCloudAsset(String ownerId,
                                       MusicMvRenderJobCreateRequest.Asset asset,
                                       String expectedKind) {
        if (!r2.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "MV_INPUT_CLOUD_STORAGE_UNAVAILABLE",
                    "Cloud photo storage is temporarily unavailable", true, null);
        }
        AssetCapability capability = parseCapability(asset == null ? null : asset.getUrl());
        Properties metadata = r2Metadata(expectedKind, capability.assetId,
                capability.accessToken);
        requireNotExpired(metadata);
        if (!normalizeOwnerId(ownerId).equals(metadata.getProperty("ownerId"))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MV_INPUT_ASSET_NOT_FOUND",
                    "Input asset was not found");
        }
        requireMetadataEquals(asset, metadata);
    }

    /** Verifies that a trusted stored asset is still present, owned and readable. */
    public void requireOwnedAsset(String ownerId,
                                  MusicMvRenderJobCreateRequest.Asset asset,
                                  String expectedKind) {
        AssetCapability capability = parseCapability(asset == null ? null : asset.getUrl());
        String normalizedOwnerId = normalizeOwnerId(ownerId);
        String normalizedKind = normalizeKind(expectedKind);
        if (r2.isConfigured()) {
            Properties metadata = r2Metadata(normalizedKind, capability.assetId,
                    capability.accessToken);
            requireNotExpired(metadata);
            if (!normalizedOwnerId.equals(metadata.getProperty("ownerId"))) throw notFound();
            requireMetadataEquals(asset, metadata);
            if (!r2.exists(r2ObjectKey(normalizedKind, capability.assetId,
                    capability.accessToken))) throw notFound();
            return;
        }
        Path assetDir = localRoot.resolve(capability.assetId).normalize();
        requireInside(assetDir);
        Path metadataPath = assetDir.resolve("asset.properties");
        if (!Files.isRegularFile(metadataPath)) throw notFound();
        Properties metadata = new Properties();
        try (InputStream input = Files.newInputStream(metadataPath)) {
            metadata.load(input);
        } catch (IOException exception) {
            throw notFound();
        }
        if (!MessageDigest.isEqual(metadata.getProperty("accessToken", "")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                capability.accessToken.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw notFound();
        }
        requireNotExpired(metadata);
        if (!normalizedOwnerId.equals(metadata.getProperty("ownerId"))
                || !normalizedKind.equals(metadata.getProperty("kind"))) throw notFound();
        requireMetadataEquals(asset, metadata);
        Path source = assetDir.resolve(metadata.getProperty("sourceFile", "source")).normalize();
        requireInside(source);
        if (!Files.isRegularFile(source)) throw notFound();
    }

    /** Removes an uploaded object after ownership has been verified. */
    public void deleteOwnedAsset(String ownerId, StoredInputAsset asset) throws IOException {
        if (asset == null) throw notFound();
        AssetCapability capability = parseCapability(asset.getUrl());
        if (!asset.getAssetId().equals(capability.assetId)) throw notFound();
        if (r2.isConfigured()) {
            Properties metadata = r2Metadata(asset.getKind(), capability.assetId,
                    capability.accessToken);
            if (!normalizeOwnerId(ownerId).equals(metadata.getProperty("ownerId"))) {
                throw notFound();
            }
            deleteRegisteredAsset(asset);
            return;
        }
        Path assetDir = localRoot.resolve(capability.assetId).normalize();
        requireInside(assetDir);
        Path metadataPath = assetDir.resolve("asset.properties");
        if (!Files.isRegularFile(metadataPath)) throw notFound();
        Properties metadata = new Properties();
        try (InputStream input = Files.newInputStream(metadataPath)) {
            metadata.load(input);
        }
        if (!normalizeOwnerId(ownerId).equals(metadata.getProperty("ownerId"))) {
            throw notFound();
        }
        deleteTree(assetDir);
    }

    /**
     * Idempotently removes an asset that has already been authorized through its trusted D1 row.
     * This is intentionally separate from the public ownership check so a partially completed
     * delete can be reconciled even when its private metadata object was removed already.
     */
    public void deleteRegisteredAsset(StoredInputAsset asset) throws IOException {
        if (asset == null) throw notFound();
        AssetCapability capability = parseCapability(asset.getUrl());
        if (!asset.getAssetId().equals(capability.assetId)) throw notFound();
        if (r2.isConfigured()) {
            r2.delete(r2ObjectKey(asset.getKind(), capability.assetId, capability.accessToken));
            r2.delete(r2MetadataKey(asset.getKind(), capability.assetId, capability.accessToken));
            return;
        }
        Path assetDir = localRoot.resolve(capability.assetId).normalize();
        requireInside(assetDir);
        if (Files.exists(assetDir)) deleteTree(assetDir);
    }

    @Scheduled(cron = "${music-mv.render.input-cleanup-cron:0 25 3 * * ?}")
    public void cleanupExpiredAssets() {
        cleanupExpiredLocalAssets();
        if (!r2.isConfigured()) return;
        cleanupExpiredR2Objects(MUSIC_R2_PREFIX,
                "music-mv-inputs/music/mva_[a-f0-9]{32}/[a-f0-9]{64}(\\.properties)?");
        cleanupExpiredR2Objects(USER_IMAGE_R2_PREFIX,
                "images/user/mva_[a-f0-9]{32}/[a-f0-9]{64}(\\.properties)?");
    }

    private void cleanupExpiredR2Objects(String prefix, String keyPattern) {
        for (String key : r2.listKeys(prefix)) {
            if (!key.matches(keyPattern)) {
                continue;
            }
            String token = key.substring(key.lastIndexOf('/') + 1).replace(".properties", "");
            if (!validR2Capability(token)) r2.delete(key);
        }
    }

    private void cleanupExpiredLocalAssets() {
        if (!Files.isDirectory(localRoot)) return;
        try (Stream<Path> children = Files.list(localRoot)) {
            children.filter(Files::isDirectory)
                    .filter(path -> !".staging".equals(path.getFileName().toString()))
                    .forEach(path -> {
                        Path metadataPath = path.resolve("asset.properties");
                        Properties metadata = new Properties();
                        try (InputStream input = Files.newInputStream(metadataPath)) {
                            metadata.load(input);
                            Instant expiresAt = Instant.parse(metadata.getProperty("expiresAt"));
                            if (Instant.now().isAfter(expiresAt)) deleteTree(path);
                        } catch (Exception ignored) {
                            // Preserve unreadable directories for manual inspection.
                        }
                    });
        } catch (IOException ignored) {
            // Best-effort maintenance must never affect request processing.
        }
    }

    private void deleteTree(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
                Files.deleteIfExists(path);
            }
        }
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

    private String capabilityUrl(String baseUrl, String assetId, String accessToken) {
        return UriComponentsBuilder.fromHttpUrl(trimTrailingSlash(baseUrl))
                .path("/api/music-mv/v1/assets/").path(assetId)
                .queryParam("access", accessToken).build().toUriString();
    }

    private String r2ObjectKey(String kind, String assetId, String accessToken) {
        String prefix = "image".equals(kind) ? USER_IMAGE_R2_PREFIX : MUSIC_R2_PREFIX;
        return prefix + assetId + "/" + accessToken;
    }

    private String r2MetadataKey(String kind, String assetId, String accessToken) {
        return r2ObjectKey(kind, assetId, accessToken) + ".properties";
    }

    private Properties metadata(String ownerId, String kind, String accessToken,
                                String fileName, String contentType, long sizeBytes,
                                String sha256, Instant expiresAt) {
        Properties metadata = new Properties();
        metadata.setProperty("ownerId", ownerId);
        metadata.setProperty("kind", kind);
        metadata.setProperty("accessToken", accessToken);
        metadata.setProperty("fileName", fileName);
        metadata.setProperty("contentType", contentType);
        metadata.setProperty("sizeBytes", Long.toString(sizeBytes));
        metadata.setProperty("sha256", sha256);
        metadata.setProperty("expiresAt", expiresAt.toString());
        return metadata;
    }

    private byte[] propertiesBytes(Properties properties) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            properties.store(output, "Music MV cloud input asset");
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Serialize input asset metadata failed", exception);
        }
    }

    private Properties r2Metadata(String kind, String assetId, String accessToken) {
        String key = r2MetadataKey(kind, assetId, accessToken);
        if (!r2.exists(key)) {
            throw notFound();
        }
        Properties metadata = new Properties();
        try (InputStream input = new ByteArrayInputStream(r2.read(key))) {
            metadata.load(input);
            return metadata;
        } catch (IOException | RuntimeException exception) {
            throw notFound();
        }
    }

    private void requireNotExpired(Properties metadata) {
        try {
            if (Instant.now().isAfter(Instant.parse(metadata.getProperty("expiresAt")))) {
                throw new ApiException(HttpStatus.GONE, "MV_INPUT_ASSET_EXPIRED",
                        "Input asset has expired");
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw notFound();
        }
    }

    private void requireMetadataEquals(MusicMvRenderJobCreateRequest.Asset asset,
                                       Properties metadata) {
        boolean exact = asset != null
                && metadata.getProperty("sha256", "").equalsIgnoreCase(asset.getSha256())
                && metadata.getProperty("fileName", "").equals(asset.getFileName())
                && metadata.getProperty("contentType", "").equalsIgnoreCase(asset.getContentType())
                && Long.toString(asset.getSizeBytes() == null ? -1L : asset.getSizeBytes())
                        .equals(metadata.getProperty("sizeBytes", ""));
        if (!exact) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "MV_INPUT_ASSET_METADATA_MISMATCH",
                    "Uploaded photo metadata does not match cloud storage");
        }
    }

    private AssetCapability parseCapability(String value) {
        try {
            java.net.URI uri = java.net.URI.create(value);
            String path = uri.getPath();
            java.util.List<String> access = UriComponentsBuilder.fromUri(uri).build()
                    .getQueryParams().get("access");
            if (path == null || !path.matches(".*/api/music-mv/v1/assets/mva_[a-f0-9]{32}")
                    || access == null || access.size() != 1 || !validR2Capability(access.get(0))) {
                throw new IllegalArgumentException("invalid capability");
            }
            return new AssetCapability(path.substring(path.lastIndexOf('/') + 1), access.get(0));
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MV_INPUT_ASSET_URL_INVALID",
                    "Input asset must be uploaded before creating a video");
        }
    }

    private String normalizeOwnerId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MV_INPUT_OWNER_INVALID",
                    "Input asset owner is invalid");
        }
        return normalized;
    }

    private static final class AssetCapability {
        private final String assetId;
        private final String accessToken;

        private AssetCapability(String assetId, String accessToken) {
            this.assetId = assetId;
            this.accessToken = accessToken;
        }
    }

    private String accessToken(Instant expiresAt) {
        byte[] random = new byte[24];
        SECURE_RANDOM.nextBytes(random);
        return String.format("%016x", expiresAt.getEpochSecond()) + hex(random);
    }

    private boolean validR2Capability(String value) {
        if (value == null || !value.matches("[a-f0-9]{64}")) return false;
        try {
            long epochSeconds = Long.parseUnsignedLong(value.substring(0, 16), 16);
            return !Instant.now().isAfter(Instant.ofEpochSecond(epochSeconds));
        } catch (RuntimeException exception) {
            return false;
        }
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

        public StoredInputAsset(String assetId, String kind, String url, String sha256,
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

    public static final class InputAssetAccess {
        private final String redirectUrl;
        private final LocalAsset localAsset;

        private InputAssetAccess(String redirectUrl, LocalAsset localAsset) {
            this.redirectUrl = redirectUrl;
            this.localAsset = localAsset;
        }

        static InputAssetAccess redirect(String url) {
            return new InputAssetAccess(url, null);
        }

        static InputAssetAccess local(Resource resource, String fileName,
                                      String contentType, long sizeBytes) {
            return new InputAssetAccess(null,
                    new LocalAsset(resource, fileName, contentType, sizeBytes));
        }

        public String getRedirectUrl() { return redirectUrl; }
        public LocalAsset getLocalAsset() { return localAsset; }
    }
}
