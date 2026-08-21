package com.example.cursorquitterweb.musicmv.aimusic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import com.example.cursorquitterweb.musicmv.repository.AiMusicJobRepository;
import com.example.cursorquitterweb.musicmv.dto.MusicMvRenderJobCreateRequest;
import com.example.cursorquitterweb.musicmv.service.MusicMvInputAssetStorageService;
import com.example.cursorquitterweb.musicmv.service.MusicMvInputAssetStorageService.StoredInputAsset;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.example.cursorquitterweb.musicmv.support.RowUtils;

/** Copies a selected provider result into our own asset store before rendering. */
@Service
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class AiMusicCandidateStorageService {
    private static final long MAX_PROVIDER_AUDIO_BYTES = 100L * 1024L * 1024L;

    private final AiMusicJobRepository repository;
    private final MusicMvInputAssetStorageService storage;
    private final RestTemplate restTemplate;

    @Autowired
    public AiMusicCandidateStorageService(AiMusicJobRepository repository,
                                          MusicMvInputAssetStorageService storage) {
        this(repository, storage, downloadClient());
    }

    AiMusicCandidateStorageService(AiMusicJobRepository repository,
                                   MusicMvInputAssetStorageService storage,
                                   RestTemplate restTemplate) {
        this.repository = repository;
        this.storage = storage;
        this.restTemplate = restTemplate;
    }

    public void materialize(String clientId, Map<String, Object> candidate,
                            String requestBaseUrl) {
        if (hasStoredAsset(candidate)) {
            try {
                storage.requireOwnedAsset(clientId, storedAsset(candidate), "music");
                return;
            } catch (ApiException exception) {
                if (!refreshableStorageFailure(exception.getCode())) throw exception;
            }
        }
        String sourceUrl = RowUtils.str(candidate, "provider_audio_url");
        requireSafeProviderUrl(sourceUrl);
        DownloadedAudio audio;
        try {
            audio = restTemplate.execute(sourceUrl, HttpMethod.GET, null, response -> {
                MediaType mediaType = response.getHeaders().getContentType();
                String contentType = mediaType == null ? "audio/mpeg" : mediaType.toString();
                if (!contentType.toLowerCase().startsWith("audio/")) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY,
                            "AI_MUSIC_AUDIO_TYPE_INVALID", "Generated song is not an audio file");
                }
                return new DownloadedAudio(readLimited(response.getBody()), contentType);
            });
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_MUSIC_AUDIO_DOWNLOAD_FAILED",
                    "Unable to download the selected generated song", true, null);
        }
        if (audio == null || audio.bytes.length == 0) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_MUSIC_AUDIO_EMPTY",
                    "Selected generated song is empty", true, null);
        }
        String fileName = safeTitle(RowUtils.str(candidate, "title"))
                + extension(audio.contentType);
        try {
            StoredInputAsset stored = storage.store(clientId, "music", fileName,
                    audio.contentType, audio.bytes.length,
                    new ByteArrayInputStream(audio.bytes), requestBaseUrl);
            repository.markCandidateStored(RowUtils.str(candidate, "candidate_id"),
                    stored.getAssetId(), stored.getUrl(), stored.getSha256(),
                    stored.getSizeBytes(), stored.getFileName(), stored.getContentType());
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_MUSIC_AUDIO_PERSIST_FAILED",
                    "Unable to persist the selected generated song", true, null);
        }
    }

    private byte[] readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        long total = 0L;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_PROVIDER_AUDIO_BYTES) {
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "AI_MUSIC_AUDIO_TOO_LARGE", "Generated song exceeds the allowed size");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private void requireSafeProviderUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("unsafe provider URL");
            }
            InetAddress address = InetAddress.getByName(uri.getHost());
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
                throw new IllegalArgumentException("private provider URL");
            }
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_MUSIC_AUDIO_URL_BLOCKED",
                    "Generated song URL is not a safe public HTTPS address");
        }
    }

    private String safeTitle(String value) {
        String result = value == null ? "generated-song" : value.trim();
        result = result.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        return result.isEmpty() ? "generated-song"
                : result.substring(0, Math.min(80, result.length()));
    }

    private String extension(String contentType) {
        String value = contentType == null ? "" : contentType.toLowerCase();
        if (value.startsWith("audio/wav") || value.startsWith("audio/x-wav")) return ".wav";
        if (value.startsWith("audio/mp4") || value.startsWith("audio/x-m4a")) return ".m4a";
        return ".mp3";
    }

    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }

    private boolean hasStoredAsset(Map<String, Object> candidate) {
        return !blank(RowUtils.str(candidate, "storage_url"))
                && !blank(RowUtils.str(candidate, "storage_sha256"))
                && RowUtils.lng(candidate, "storage_size_bytes") != null
                && !blank(RowUtils.str(candidate, "storage_file_name"))
                && !blank(RowUtils.str(candidate, "storage_content_type"));
    }

    private MusicMvRenderJobCreateRequest.Asset storedAsset(Map<String, Object> candidate) {
        MusicMvRenderJobCreateRequest.Asset asset = new MusicMvRenderJobCreateRequest.Asset();
        asset.setUrl(RowUtils.str(candidate, "storage_url"));
        asset.setSha256(RowUtils.str(candidate, "storage_sha256"));
        asset.setSizeBytes(RowUtils.lng(candidate, "storage_size_bytes"));
        asset.setFileName(RowUtils.str(candidate, "storage_file_name"));
        asset.setContentType(RowUtils.str(candidate, "storage_content_type"));
        return asset;
    }

    private boolean refreshableStorageFailure(String code) {
        return "MV_INPUT_ASSET_EXPIRED".equals(code)
                || "MV_INPUT_ASSET_NOT_FOUND".equals(code)
                || "MV_INPUT_ASSET_URL_INVALID".equals(code);
    }

    private static RestTemplate downloadClient() {
        RequestConfig config = RequestConfig.custom().setConnectTimeout(15000)
                .setConnectionRequestTimeout(15000).setSocketTimeout(120000).build();
        CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(config)
                .disableAutomaticRetries().disableRedirectHandling().disableCookieManagement().build();
        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(client));
    }

    private static final class DownloadedAudio {
        private final byte[] bytes;
        private final String contentType;

        private DownloadedAudio(byte[] bytes, String contentType) {
            this.bytes = bytes;
            this.contentType = contentType;
        }
    }
}
