package com.example.cursorquitterweb.musicmv.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@Service
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class R2StorageService {
    private final String publicBaseUrl;
    private final String accountId;
    private final String bucket;
    private final String accessKeyId;
    private final String secretAccessKey;
    private S3Client s3Client;
    private S3Presigner s3Presigner;

    public R2StorageService(@Value("${music-mv.r2.public-base-url:}") String publicBaseUrl,
                            @Value("${music-mv.r2.account-id:}") String accountId,
                            @Value("${music-mv.r2.bucket:}") String bucket,
                            @Value("${music-mv.r2.access-key-id:}") String accessKeyId,
                            @Value("${music-mv.r2.secret-access-key:}") String secretAccessKey) {
        this.publicBaseUrl = publicBaseUrl;
        this.accountId = accountId;
        this.bucket = bucket;
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
    }

    public void write(String objectKey, byte[] bytes) {
        write(objectKey, bytes, contentType(objectKey));
    }

    public void write(String objectKey, byte[] bytes, String contentType) {
        String key = normalizeObjectKey(objectKey);
        client().putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType == null ? contentType(key) : contentType)
                        .build(),
                RequestBody.fromBytes(bytes)
        );
    }

    public void write(String objectKey, InputStream input, long contentLength,
                      String contentType) {
        String key = normalizeObjectKey(objectKey);
        client().putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType == null ? contentType(key) : contentType)
                        .build(),
                RequestBody.fromInputStream(input, contentLength)
        );
    }

    public byte[] read(String objectKey) {
        String key = normalizeObjectKey(objectKey);
        try (ResponseInputStream<GetObjectResponse> inputStream = client().getObject(
                GetObjectRequest.builder().bucket(bucket).key(key).build()
        )) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        } catch (NoSuchKeyException e) {
            throw new IllegalStateException("R2 object not found: " + key, e);
        } catch (IOException e) {
            throw new IllegalStateException("Read R2 object failed: " + key, e);
        }
    }

    public boolean exists(String objectKey) {
        try {
            client().headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(normalizeObjectKey(objectKey))
                    .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public long size(String objectKey) {
        try {
            return client().headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(normalizeObjectKey(objectKey))
                    .build()).contentLength();
        } catch (Exception e) {
            return 0L;
        }
    }

    public String publicUrl(String objectKey) {
        String clean = normalizeObjectKey(objectKey);
        if (publicBaseUrl != null && !publicBaseUrl.trim().isEmpty()) {
            return trimTrailingSlash(publicBaseUrl) + "/" + clean;
        }
        return "/api/v1/files/" + clean;
    }

    public String presignedPutUrl(String objectKey, String contentType, Duration expiresIn) {
        String key = normalizeObjectKey(objectKey);
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType == null ? contentType(key) : contentType)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(expiresIn)
                .putObjectRequest(putObjectRequest)
                .build();
        PresignedPutObjectRequest presignedRequest = presigner().presignPutObject(presignRequest);
        return presignedRequest.url().toString();
    }

    public String presignedGetUrl(String objectKey, Duration expiresIn) {
        String key = normalizeObjectKey(objectKey);
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiresIn)
                .getObjectRequest(getObjectRequest)
                .build();
        PresignedGetObjectRequest presignedRequest = presigner().presignGetObject(presignRequest);
        return presignedRequest.url().toString();
    }

    public boolean isConfigured() {
        return !isBlank(accountId) && !isBlank(bucket)
                && !isBlank(accessKeyId) && !isBlank(secretAccessKey);
    }

    public void deletePrefix(String objectPrefix) {
        String prefix = normalizeObjectKey(objectPrefix);
        String continuationToken = null;
        do {
            ListObjectsV2Response response = client().listObjectsV2(
                    ListObjectsV2Request.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .continuationToken(continuationToken)
                            .build()
            );
            for (S3Object object : response.contents()) {
                client().deleteObject(DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(object.key())
                        .build());
            }
            continuationToken = response.nextContinuationToken();
        } while (continuationToken != null && !continuationToken.trim().isEmpty());
    }

    public List<String> listKeys(String objectPrefix) {
        String prefix = normalizeObjectKey(objectPrefix);
        List<String> keys = new ArrayList<String>();
        String continuationToken = null;
        do {
            ListObjectsV2Response response = client().listObjectsV2(
                    ListObjectsV2Request.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .continuationToken(continuationToken)
                            .build()
            );
            for (S3Object object : response.contents()) keys.add(object.key());
            continuationToken = response.nextContinuationToken();
        } while (continuationToken != null && !continuationToken.trim().isEmpty());
        return keys;
    }

    public void delete(String objectKey) {
        client().deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(normalizeObjectKey(objectKey))
                .build());
    }

    public String normalizeObjectKey(String objectKey) {
        if (objectKey == null || objectKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Object key is required");
        }
        String clean = objectKey.replace("\\", "/");
        while (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        if (clean.contains("..")) {
            throw new IllegalArgumentException("Invalid object key");
        }
        return clean;
    }

    public String contentType(String objectKey) {
        String lower = objectKey == null ? "" : objectKey.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".heic")) {
            return "image/heic";
        }
        if (lower.endsWith(".zip")) {
            return "application/zip";
        }
        if (lower.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (lower.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (lower.endsWith(".m4a")) {
            return "audio/mp4";
        }
        if (lower.endsWith(".wav")) {
            return "audio/wav";
        }
        if (lower.endsWith(".txt")) {
            return "text/plain; charset=utf-8";
        }
        return "image/jpeg";
    }

    private S3Client client() {
        if (s3Client == null) {
            ensureConfigured();
            s3Client = S3Client.builder()
                    .endpointOverride(endpoint())
                    .credentialsProvider(credentials())
                    .region(Region.of("auto"))
                    .build();
        }
        return s3Client;
    }

    private S3Presigner presigner() {
        if (s3Presigner == null) {
            ensureConfigured();
            s3Presigner = S3Presigner.builder()
                    .endpointOverride(endpoint())
                    .credentialsProvider(credentials())
                    .region(Region.of("auto"))
                    .build();
        }
        return s3Presigner;
    }

    private StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey));
    }

    private URI endpoint() {
        return URI.create("https://" + accountId + ".r2.cloudflarestorage.com");
    }

    private void ensureConfigured() {
        if (isBlank(accountId) || isBlank(bucket) || isBlank(accessKeyId) || isBlank(secretAccessKey)) {
            throw new IllegalStateException("R2 storage is required. Configure CLOUDFLARE_ACCOUNT_ID, "
                    + "CLOUDFLARE_R2_BUCKET, CLOUDFLARE_R2_ACCESS_KEY_ID and CLOUDFLARE_R2_SECRET_ACCESS_KEY.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimTrailingSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
