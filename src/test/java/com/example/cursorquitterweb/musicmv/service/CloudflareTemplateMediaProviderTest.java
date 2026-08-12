package com.example.cursorquitterweb.musicmv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import java.net.URI;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.example.cursorquitterweb.musicmv.dto.TemplateMediaUploadSessionRequest;
import com.example.cursorquitterweb.musicmv.service.CloudflareTemplateMediaProvider.MediaState;
import com.example.cursorquitterweb.musicmv.service.CloudflareTemplateMediaProvider.UploadSession;
import com.fasterxml.jackson.databind.ObjectMapper;

class CloudflareTemplateMediaProviderTest {
    @Test
    void createsImagesDirectUploadWithoutExposingApiToken() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo("https://api.cloudflare.test/accounts/account/images/v2/direct_upload"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer images-secret"))
                .andRespond(withSuccess("{\"success\":true,\"result\":{\"id\":\"img-1\","
                        + "\"uploadURL\":\"https://upload.imagedelivery.net/direct\"}}",
                        MediaType.APPLICATION_JSON));
        CloudflareTemplateMediaProvider provider = provider(restTemplate);

        UploadSession session = provider.createImageUpload("img-1", imageRequest());

        assertEquals("img-1", session.getProviderAssetId());
        assertEquals("https://upload.imagedelivery.net/direct", session.getUploadUrl());
        assertEquals("https://imagedelivery.net/account-hash/img-1/public",
                session.getProviderDetails().get("deliveryUrl"));
        server.verify();
    }

    @Test
    void createsResumableStreamTusSession() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo("https://api.cloudflare.test/accounts/account/stream?direct_user=true"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Tus-Resumable", "1.0.0"))
                .andExpect(header("Upload-Length", "100"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .location(URI.create("https://upload.cloudflarestream.com/tus"))
                        .headers(streamHeaders()));
        CloudflareTemplateMediaProvider provider = provider(restTemplate);
        TemplateMediaUploadSessionRequest request = imageRequest();
        request.setRole("full_mv"); request.setFilename("mv.mp4"); request.setDurationSeconds(180d);

        UploadSession session = provider.createStreamUpload("placeholder", request);

        assertEquals("stream-1", session.getProviderAssetId());
        assertEquals("https://upload.cloudflarestream.com/tus", session.getUploadUrl());
        assertTrue(String.valueOf(session.getProviderDetails().get("playbackUrl"))
                .endsWith("/stream-1/manifest/video.m3u8"));
        server.verify();
    }

    @Test
    void validatesExpectedCloudflareDeliveryBaseUrls() {
        CloudflareTemplateMediaProvider provider = provider(new RestTemplate());
        assertTrue(provider.imagesDeliveryValid());
        assertTrue(provider.streamDeliveryValid());
    }

    @Test
    void treatsUploadedImageWithVariantsAsReadyWhenDraftFieldIsOmitted() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo("https://api.cloudflare.test/accounts/account/images/v1/img-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"success\":true,\"result\":{\"id\":\"img-1\","
                        + "\"variants\":[\"https://imagedelivery.net/account-hash/img-1/public\"]}}",
                        MediaType.APPLICATION_JSON));

        MediaState state = provider(restTemplate).imageState("img-1");

        assertEquals("ready", state.getStatus());
        assertEquals(Boolean.FALSE, state.getProviderDetails().get("draft"));
        server.verify();
    }

    private CloudflareTemplateMediaProvider provider(RestTemplate restTemplate) {
        return new CloudflareTemplateMediaProvider(new ObjectMapper(), restTemplate,
                "https://api.cloudflare.test", "account", "images-secret",
                "https://imagedelivery.net/account-hash", "account", "stream-secret",
                "https://customer-code.cloudflarestream.com");
    }

    private TemplateMediaUploadSessionRequest imageRequest() {
        TemplateMediaUploadSessionRequest request = new TemplateMediaUploadSessionRequest();
        request.setRole("cover");
        request.setSourceSha256("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        request.setSourceSizeBytes(100L);
        request.setWidth(1080); request.setHeight(1920);
        return request;
    }

    private HttpHeaders streamHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("stream-media-id", "stream-1");
        return headers;
    }
}
