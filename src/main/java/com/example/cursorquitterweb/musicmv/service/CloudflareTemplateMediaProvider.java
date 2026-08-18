package com.example.cursorquitterweb.musicmv.service;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.example.cursorquitterweb.musicmv.dto.TemplateMediaUploadSessionRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Cloudflare Images and Stream adapter; no browser receives API credentials. */
@Service
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class CloudflareTemplateMediaProvider {
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final String apiBaseUrl;
    private final String imagesAccountId;
    private final String imagesApiToken;
    private final String imagesDeliveryBaseUrl;
    private final String streamAccountId;
    private final String streamApiToken;
    private final String streamDeliveryBaseUrl;

    @Autowired
    public CloudflareTemplateMediaProvider(
            ObjectMapper objectMapper,
            @Value("${music-mv.media.cloudflare-api-base-url:https://api.cloudflare.com/client/v4}") String apiBaseUrl,
            @Value("${music-mv.media.images.account-id:}") String imagesAccountId,
            @Value("${music-mv.media.images.api-token:}") String imagesApiToken,
            @Value("${music-mv.media.images.delivery-base-url:}") String imagesDeliveryBaseUrl,
            @Value("${music-mv.media.stream.account-id:}") String streamAccountId,
            @Value("${music-mv.media.stream.api-token:}") String streamApiToken,
            @Value("${music-mv.media.stream.delivery-base-url:}") String streamDeliveryBaseUrl) {
        this(objectMapper, CloudflareRestTemplateFactory.create(), apiBaseUrl,
                imagesAccountId, imagesApiToken, imagesDeliveryBaseUrl,
                streamAccountId, streamApiToken, streamDeliveryBaseUrl);
    }

    CloudflareTemplateMediaProvider(ObjectMapper objectMapper, RestTemplate restTemplate,
                                    String apiBaseUrl, String imagesAccountId,
                                    String imagesApiToken, String imagesDeliveryBaseUrl,
                                    String streamAccountId, String streamApiToken,
                                    String streamDeliveryBaseUrl) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.apiBaseUrl = trimSlash(apiBaseUrl);
        this.imagesAccountId = trim(imagesAccountId);
        this.imagesApiToken = trim(imagesApiToken);
        this.imagesDeliveryBaseUrl = trimSlash(imagesDeliveryBaseUrl);
        this.streamAccountId = trim(streamAccountId);
        this.streamApiToken = trim(streamApiToken);
        this.streamDeliveryBaseUrl = trimSlash(streamDeliveryBaseUrl);
    }

    public boolean imagesConfigured() {
        return configured(imagesAccountId, imagesApiToken, imagesDeliveryBaseUrl);
    }

    public boolean streamConfigured() {
        return configured(streamAccountId, streamApiToken, streamDeliveryBaseUrl);
    }

    public boolean imagesDeliveryConfigured() { return !imagesDeliveryBaseUrl.isEmpty(); }
    public boolean streamDeliveryConfigured() { return !streamDeliveryBaseUrl.isEmpty(); }

    /**
     * Builds public delivery fields from the stable provider asset id at response time.
     * D1 intentionally stores the provider and asset id as the source of truth; generated
     * URLs are deployment configuration and therefore must not be required in persisted JSON.
     */
    public Map<String, Object> resolveDeliveryDetails(String provider, String providerAssetId,
                                                       Map<String, Object> persistedDetails) {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        if (persistedDetails != null) details.putAll(persistedDetails);
        String assetId = trim(providerAssetId);
        if (assetId.isEmpty()) return details;

        if ("cloudflare_images".equals(provider) && imagesDeliveryConfigured()) {
            putIfBlank(details, "deliveryUrl",
                    imagesDeliveryBaseUrl + "/" + assetId + "/public");
        } else if ("cloudflare_stream".equals(provider) && streamDeliveryConfigured()) {
            Map<String, Object> generated = streamDeliveryDetails(assetId);
            putIfBlank(details, "playbackUrl", generated.get("playbackUrl"));
            putIfBlank(details, "thumbnailUrl", generated.get("thumbnailUrl"));
        }
        return details;
    }

    public boolean imagesDeliveryValid() {
        try {
            URI uri = URI.create(imagesDeliveryBaseUrl);
            String host = uri.getHost();
            String path = uri.getPath();
            return "https".equalsIgnoreCase(uri.getScheme()) && host != null
                    && (host.equals("imagedelivery.net") || host.endsWith(".imagedelivery.net"))
                    && path != null && path.matches("/[^/]+");
        } catch (RuntimeException ignored) {
            return false;
        }
    }
    public boolean streamDeliveryValid() {
        return validHttpsHost(streamDeliveryBaseUrl, "cloudflarestream.com");
    }

    public UploadSession createImageUpload(String providerAssetId,
                                           TemplateMediaUploadSessionRequest request) {
        requireImagesConfigured();
        HttpHeaders headers = bearer(imagesApiToken);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<String, Object>();
        body.add("id", providerAssetId);
        body.add("requireSignedURLs", "false");
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("sourceSha256", request.getSourceSha256().toLowerCase());
        metadata.put("role", request.getRole());
        body.add("metadata", json(metadata));
        JsonNode result = jsonExchange(apiBaseUrl + "/accounts/" + imagesAccountId
                + "/images/v2/direct_upload", HttpMethod.POST,
                new HttpEntity<MultiValueMap<String, Object>>(body, headers)).path("result");
        String id = required(result.path("id").asText(null), "Cloudflare Images response is missing id");
        String uploadUrl = required(result.path("uploadURL").asText(null),
                "Cloudflare Images response is missing uploadURL");
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put("deliveryUrl", imagesDeliveryBaseUrl + "/" + id + "/public");
        return new UploadSession("cloudflare_images", id, uploadUrl, "awaiting_upload", details);
    }

    public UploadSession createStreamUpload(String providerAssetId,
                                            TemplateMediaUploadSessionRequest request) {
        requireStreamConfigured();
        HttpHeaders headers = bearer(streamApiToken);
        headers.set("Tus-Resumable", "1.0.0");
        headers.set("Upload-Length", String.valueOf(request.getSourceSizeBytes()));
        String filename = trim(request.getFilename()).isEmpty() ? providerAssetId + ".mp4"
                : request.getFilename();
        headers.set("Upload-Metadata", "name " + base64(filename)
                + ",sourceSha256 " + base64(request.getSourceSha256().toLowerCase())
                + ",role " + base64(request.getRole()));
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    apiBaseUrl + "/accounts/" + streamAccountId + "/stream?direct_user=true",
                    HttpMethod.POST, new HttpEntity<Void>(headers), String.class);
            String uploadUrl = required(response.getHeaders().getFirst(HttpHeaders.LOCATION),
                    "Cloudflare Stream response is missing Location");
            String uid = response.getHeaders().getFirst("stream-media-id");
            if (uid == null || uid.trim().isEmpty()) uid = providerAssetId;
            Map<String, Object> details = streamDeliveryDetails(uid);
            return new UploadSession("cloudflare_stream", uid, uploadUrl,
                    "awaiting_upload", details);
        } catch (HttpStatusCodeException exception) {
            throw providerFailure("Cloudflare Stream TUS session failed", exception);
        }
    }

    public MediaState imageState(String providerAssetId) {
        requireImagesConfigured();
        JsonNode result = jsonExchange(apiBaseUrl + "/accounts/" + imagesAccountId
                + "/images/v1/" + providerAssetId, HttpMethod.GET,
                new HttpEntity<Void>(bearer(imagesApiToken))).path("result");
        JsonNode draft = result.get("draft");
        boolean ready = draft != null && !draft.isNull()
                ? !draft.asBoolean(true)
                : result.path("variants").isArray() && !result.path("variants").isEmpty();
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put("deliveryUrl", imagesDeliveryBaseUrl + "/" + providerAssetId + "/public");
        details.put("draft", Boolean.valueOf(!ready));
        return new MediaState(ready ? "ready" : "processing", details);
    }

    public boolean isReusableReadyAsset(String provider, String providerAssetId) {
        try {
            MediaState state;
            if ("cloudflare_images".equals(provider)) {
                state = imageState(providerAssetId);
            } else if ("cloudflare_stream".equals(provider)) {
                state = streamState(providerAssetId);
            } else {
                return false;
            }
            return "ready".equals(state.getStatus());
        } catch (IllegalStateException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof HttpStatusCodeException
                    && ((HttpStatusCodeException) cause).getRawStatusCode() == 404) {
                return false;
            }
            throw exception;
        }
    }

    public MediaState streamState(String providerAssetId) {
        requireStreamConfigured();
        JsonNode result = jsonExchange(apiBaseUrl + "/accounts/" + streamAccountId
                + "/stream/" + providerAssetId, HttpMethod.GET,
                new HttpEntity<Void>(bearer(streamApiToken))).path("result");
        boolean ready = result.path("readyToStream").asBoolean(false)
                || "ready".equalsIgnoreCase(result.path("status").path("state").asText(""));
        Map<String, Object> details = streamDeliveryDetails(providerAssetId);
        details.put("readyToStream", Boolean.valueOf(ready));
        details.put("state", result.path("status").path("state").asText("unknown"));
        details.put("pctComplete", result.path("status").path("pctComplete").asText(null));
        return new MediaState(ready ? "ready" : "processing", details);
    }

    /** Permanently removes a template showcase asset from its Cloudflare product. */
    public void deleteAsset(String provider, String providerAssetId) {
        String assetId = required(trim(providerAssetId), "Cloudflare asset id is required");
        if ("cloudflare_images".equals(provider)) {
            requireImagesConfigured();
            deleteIgnoringMissing(apiBaseUrl + "/accounts/" + imagesAccountId
                    + "/images/v1/" + assetId, imagesApiToken);
            return;
        }
        if ("cloudflare_stream".equals(provider)) {
            requireStreamConfigured();
            deleteIgnoringMissing(apiBaseUrl + "/accounts/" + streamAccountId
                    + "/stream/" + assetId, streamApiToken);
            return;
        }
        throw new IllegalStateException("Unsupported template media provider: " + provider);
    }

    private void deleteIgnoringMissing(String url, String token) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.DELETE,
                    new HttpEntity<Void>(bearer(token)), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            if (!root.path("success").asBoolean(false)) {
                throw new IllegalStateException("Cloudflare media delete failed: "
                        + root.path("errors"));
            }
        } catch (HttpStatusCodeException exception) {
            if (exception.getRawStatusCode() == 404) return;
            throw providerFailure("Cloudflare media delete failed", exception);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Parse Cloudflare media delete response failed", exception);
        }
    }

    private JsonNode jsonExchange(String url, HttpMethod method, HttpEntity<?> entity) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            if (!root.path("success").asBoolean(false)) {
                throw new IllegalStateException("Cloudflare media request failed: " + root.path("errors"));
            }
            return root;
        } catch (HttpStatusCodeException exception) {
            throw providerFailure("Cloudflare media request failed", exception);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Parse Cloudflare media response failed", exception);
        }
    }

    private IllegalStateException providerFailure(String message, HttpStatusCodeException exception) {
        return new IllegalStateException(message + ": HTTP " + exception.getRawStatusCode()
                + " " + exception.getResponseBodyAsString(), exception);
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private Map<String, Object> streamDeliveryDetails(String uid) {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put("playbackUrl", streamDeliveryBaseUrl + "/" + uid + "/manifest/video.m3u8");
        details.put("thumbnailUrl", streamDeliveryBaseUrl + "/" + uid + "/thumbnails/thumbnail.jpg");
        return details;
    }

    private static void putIfBlank(Map<String, Object> target, String key, Object value) {
        Object existing = target.get(key);
        if (existing == null || String.valueOf(existing).trim().isEmpty()) {
            target.put(key, value);
        }
    }

    private void requireImagesConfigured() {
        if (!imagesConfigured()) throw new IllegalStateException(
                "Cloudflare Images is not configured for the isolated Music MV module");
    }

    private void requireStreamConfigured() {
        if (!streamConfigured()) throw new IllegalStateException(
                "Cloudflare Stream is not configured for the isolated Music MV module");
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Serialize media metadata failed", exception); }
    }

    private static boolean configured(String... values) {
        for (String value : values) if (trim(value).isEmpty()) return false;
        return true;
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String required(String value, String message) {
        if (value == null || value.trim().isEmpty()) throw new IllegalStateException(message);
        return value;
    }

    private static String trim(String value) { return value == null ? "" : value.trim(); }
    private static String trimSlash(String value) { return trim(value).replaceAll("/+$", ""); }

    private static boolean validHttpsHost(String value, String suffix) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && host != null
                    && (host.equals(suffix) || host.endsWith("." + suffix))
                    && (uri.getPath() == null || uri.getPath().isEmpty());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static class UploadSession {
        private final String provider;
        private final String providerAssetId;
        private final String uploadUrl;
        private final String status;
        private final Map<String, Object> providerDetails;

        public UploadSession(String provider, String providerAssetId, String uploadUrl,
                             String status, Map<String, Object> providerDetails) {
            this.provider = provider;
            this.providerAssetId = providerAssetId;
            this.uploadUrl = uploadUrl;
            this.status = status;
            this.providerDetails = providerDetails;
        }
        public String getProvider() { return provider; }
        public String getProviderAssetId() { return providerAssetId; }
        public String getUploadUrl() { return uploadUrl; }
        public String getStatus() { return status; }
        public Map<String, Object> getProviderDetails() { return providerDetails; }
    }

    public static class MediaState {
        private final String status;
        private final Map<String, Object> providerDetails;
        public MediaState(String status, Map<String, Object> providerDetails) {
            this.status = status;
            this.providerDetails = providerDetails;
        }
        public String getStatus() { return status; }
        public Map<String, Object> getProviderDetails() { return providerDetails; }
    }
}
