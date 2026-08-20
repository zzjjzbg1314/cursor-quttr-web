package com.example.cursorquitterweb.musicmv.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.example.cursorquitterweb.musicmv.support.ApiException;

@Service
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class MusicMvRenderArtifactStorageService {
    private static final long DEFAULT_MAX_OUTPUT_BYTES = 2L * 1024L * 1024L * 1024L;
    private final R2StorageService r2;
    private final Path localRoot;
    private final long maxOutputBytes;

    public MusicMvRenderArtifactStorageService(
            R2StorageService r2,
            @Value("${music-mv.render.local-storage-dir:storage/music-mv-render}") String localDir,
            @Value("${music-mv.render.max-output-bytes:2147483648}") long maxOutputBytes
    ) {
        this.r2 = r2;
        this.localRoot = Paths.get(localDir).toAbsolutePath().normalize();
        this.maxOutputBytes = maxOutputBytes <= 0L
                ? DEFAULT_MAX_OUTPUT_BYTES : maxOutputBytes;
    }

    public StoredArtifact storeOutput(String jobId, InputStream input, long contentLength,
                                      String contentType, String expectedSha256) throws IOException {
        if (contentLength <= 0L || contentLength > maxOutputBytes) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MV_RENDER_OUTPUT_SIZE_INVALID",
                    "Rendered MV output size is invalid");
        }
        String expected = normalizeSha256(expectedSha256);
        MessageDigest digest = sha256Digest();
        DigestInputStream digestInput = new DigestInputStream(input, digest);
        String objectKey = "music-mv-renders/" + safeId(jobId) + "/result.mp4";
        String storageKey;
        if (r2.isConfigured()) {
            r2.write(objectKey, digestInput, contentLength, contentType);
            storageKey = "r2:" + objectKey;
        } else {
            Path target = localRoot.resolve("outputs").resolve(safeId(jobId))
                    .resolve("result.mp4").normalize();
            requireInside(target, localRoot);
            Files.createDirectories(target.getParent());
            Files.copy(digestInput, target, StandardCopyOption.REPLACE_EXISTING);
            if (Files.size(target) != contentLength) {
                Files.deleteIfExists(target);
                throw new ApiException(HttpStatus.BAD_REQUEST, "MV_RENDER_OUTPUT_TRUNCATED",
                        "Rendered MV upload was truncated", true, null);
            }
            storageKey = "local:" + localRoot.relativize(target).toString().replace('\\', '/');
        }
        String actual = hex(digest.digest());
        if (!MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            deleteQuietly(storageKey);
            throw new ApiException(HttpStatus.BAD_REQUEST, "MV_RENDER_OUTPUT_HASH_MISMATCH",
                    "Rendered MV SHA-256 does not match the upload");
        }
        return new StoredArtifact(storageKey, contentLength, actual,
                contentType == null ? "video/mp4" : contentType);
    }

    public BrowserUploadSession createBrowserUploadSession(String jobId, long contentLength,
                                                            String contentType, String sha256) {
        requireBrowserOutput(contentLength, contentType, sha256);
        if (!r2.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "MV_BROWSER_OUTPUT_STORAGE_UNAVAILABLE",
                    "Direct browser video upload is temporarily unavailable", true, null);
        }
        String objectKey = browserObjectKey(jobId);
        Map<String, String> metadata = new LinkedHashMap<String, String>();
        metadata.put("sha256", normalizeSha256(sha256));
        metadata.put("render-job-id", safeId(jobId));
        String uploadUrl = r2.presignedPutUrl(objectKey, "video/mp4", contentLength,
                metadata, Duration.ofMinutes(30));
        return new BrowserUploadSession(uploadUrl, objectKey, contentLength,
                normalizeSha256(sha256), "video/mp4");
    }

    public StoredArtifact verifyBrowserUpload(String jobId, long expectedSize,
                                               String contentType, String expectedSha256) {
        requireBrowserOutput(expectedSize, contentType, expectedSha256);
        String objectKey = browserObjectKey(jobId);
        R2StorageService.ObjectInfo info;
        try {
            info = r2.objectInfo(objectKey);
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "MV_BROWSER_OUTPUT_NOT_UPLOADED",
                    "Browser video upload has not completed", true, null);
        }
        String storedSha256 = info.getMetadata() == null ? null
                : info.getMetadata().get("sha256");
        if (info.getSizeBytes() != expectedSize
                || !normalizeSha256(expectedSha256).equalsIgnoreCase(storedSha256)
                || info.getContentType() == null
                || !info.getContentType().toLowerCase().startsWith("video/mp4")) {
            deleteQuietly("r2:" + objectKey);
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "MV_BROWSER_OUTPUT_INTEGRITY_MISMATCH",
                    "Browser video upload does not match the signed artifact metadata");
        }
        return new StoredArtifact("r2:" + objectKey, expectedSize,
                normalizeSha256(expectedSha256), "video/mp4");
    }

    private void requireBrowserOutput(long contentLength, String contentType, String sha256) {
        if (contentLength <= 0L || contentLength > maxOutputBytes) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MV_RENDER_OUTPUT_SIZE_INVALID",
                    "Rendered MV output size is invalid");
        }
        if (contentType == null || !contentType.toLowerCase().startsWith("video/mp4")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MV_RENDER_OUTPUT_TYPE_INVALID",
                    "Browser rendering requires MP4 output");
        }
        normalizeSha256(sha256);
    }

    private String browserObjectKey(String jobId) {
        return "music-mv-renders/" + safeId(jobId) + "/result.mp4";
    }

    public boolean exists(String storageKey) {
        if (storageKey == null) return false;
        if (storageKey.startsWith("r2:")) return r2.isConfigured() && r2.exists(storageKey.substring(3));
        if (storageKey.startsWith("local:")) return Files.isRegularFile(localPath(storageKey));
        return false;
    }

    public long size(String storageKey) {
        try {
            if (storageKey.startsWith("r2:")) return r2.size(storageKey.substring(3));
            if (storageKey.startsWith("local:")) return Files.size(localPath(storageKey));
        } catch (Exception ignored) {
            return 0L;
        }
        return 0L;
    }

    public String temporaryDownloadUrl(String storageKey) {
        if (storageKey != null && storageKey.startsWith("r2:") && r2.isConfigured()) {
            return r2.presignedGetUrl(storageKey.substring(3), Duration.ofMinutes(15));
        }
        return null;
    }

    public Resource localResource(String storageKey) throws IOException {
        if (storageKey == null || !storageKey.startsWith("local:")) {
            return null;
        }
        Path path = localPath(storageKey);
        if (!Files.isRegularFile(path)) return null;
        return new FileSystemResource(path.toFile());
    }

    public void delete(String storageKey) {
        deleteQuietly(storageKey);
    }

    private Path localPath(String storageKey) {
        Path path = localRoot.resolve(storageKey.substring("local:".length())).normalize();
        requireInside(path, localRoot);
        return path;
    }

    private void deleteQuietly(String storageKey) {
        try {
            if (storageKey.startsWith("r2:")) r2.delete(storageKey.substring(3));
            else if (storageKey.startsWith("local:")) Files.deleteIfExists(localPath(storageKey));
        } catch (Exception ignored) {
            // A failed integrity check is already surfaced to the caller.
        }
    }

    private void requireInside(Path path, Path root) {
        if (!path.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MV_RENDER_STORAGE_PATH_BLOCKED",
                    "Invalid rendered MV storage path");
        }
    }

    private String safeId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MV_RENDER_JOB_ID_INVALID",
                    "Invalid render job id");
        }
        return value;
    }

    private String normalizeSha256(String value) {
        String result = value == null ? "" : value.trim().toLowerCase();
        if (!result.matches("[0-9a-f]{64}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MV_RENDER_OUTPUT_HASH_REQUIRED",
                    "Rendered MV SHA-256 is required");
        }
        return result;
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }

    public static final class StoredArtifact {
        private final String storageKey;
        private final long sizeBytes;
        private final String sha256;
        private final String contentType;

        StoredArtifact(String storageKey, long sizeBytes, String sha256, String contentType) {
            this.storageKey = storageKey;
            this.sizeBytes = sizeBytes;
            this.sha256 = sha256;
            this.contentType = contentType;
        }

        public String getStorageKey() { return storageKey; }
        public long getSizeBytes() { return sizeBytes; }
        public String getSha256() { return sha256; }
        public String getContentType() { return contentType; }
    }

    public static final class BrowserUploadSession {
        private final String uploadUrl;
        private final String objectKey;
        private final long sizeBytes;
        private final String sha256;
        private final String contentType;

        BrowserUploadSession(String uploadUrl, String objectKey, long sizeBytes,
                             String sha256, String contentType) {
            this.uploadUrl = uploadUrl;
            this.objectKey = objectKey;
            this.sizeBytes = sizeBytes;
            this.sha256 = sha256;
            this.contentType = contentType;
        }

        public String getUploadUrl() { return uploadUrl; }
        public String getObjectKey() { return objectKey; }
        public long getSizeBytes() { return sizeBytes; }
        public String getSha256() { return sha256; }
        public String getContentType() { return contentType; }
    }
}
