package com.example.cursorquitterweb.musicmv.aimusic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.cursorquitterweb.musicmv.aimusic.AiMusicProvider.GenerateSongCommand;
import com.example.cursorquitterweb.musicmv.aimusic.AiMusicProvider.LyricsSnapshot;
import com.example.cursorquitterweb.musicmv.aimusic.AiMusicProvider.Submission;
import com.example.cursorquitterweb.musicmv.aimusic.AiMusicProvider.TaskSnapshot;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;

class SunoApiAiMusicProviderTest {
    @Test
    void submitsNeutralCommandUsingSunoApiContractAndProtectedCallback() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo("https://api.sunoapi.test/api/v1/generate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer suno-secret"))
                .andExpect(jsonPath("$.callBackUrl").value(
                        org.hamcrest.Matchers.startsWith(
                                "https://app.test/api/music-mv/v1/provider-webhooks/sunoapi/music?")))
                .andExpect(jsonPath("$.model").value("V5_5"))
                .andExpect(jsonPath("$.styleWeight").value(0.72d))
                .andExpect(jsonPath("$.weirdnessConstraint").value(0.41d))
                .andExpect(jsonPath("$.negativeTags").value("heavy metal"))
                .andExpect(jsonPath("$.vocalGender").value("f"))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"taskId\":\"task-1\"}}",
                        MediaType.APPLICATION_JSON));
        SunoApiAiMusicProvider provider = provider(restTemplate);
        GenerateSongCommand command = new GenerateSongCommand();
        command.setPrompt("A birthday song");
        command.setStyle("Pop");
        command.setTitle("Happy Day");
        command.setModel("V5_5");
        command.setCustomMode(true);
        command.setNegativeTags("heavy metal");
        command.setVocalGender("f");
        command.setStyleWeight(Double.valueOf(0.72d));
        command.setWeirdnessConstraint(Double.valueOf(0.41d));
        command.setCallbackUrl(provider.callbackUrl("https://app.test", "aimusic_123"));

        Submission submission = provider.submit(command);

        assertThat(submission.getProviderTaskId()).isEqualTo("task-1");
        URI callback = URI.create(command.getCallbackUrl());
        Map<String, String> parameters = queryParameters(callback);
        assertThat(parameters.get("jobId")).isEqualTo("aimusic_123");
        provider.verifyCallbackToken("aimusic_123", parameters.get("token"));
        assertThatThrownBy(() -> provider.verifyCallbackToken("aimusic_other", parameters.get("token")))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("SUNOAPI_WEBHOOK_TOKEN_INVALID"));
        server.verify();
    }

    @Test
    void queriesTaskWithRequiredTaskIdAndNormalizesCandidates() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo(
                        "https://api.sunoapi.test/api/v1/generate/record-info?taskId=task-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"taskId\":\"task-1\","
                        + "\"status\":\"SUCCESS\",\"response\":{\"sunoData\":[{"
                        + "\"id\":\"song-1\",\"audioUrl\":\"https://audio.test/song.mp3\","
                        + "\"streamAudioUrl\":\"https://audio.test/stream\","
                        + "\"imageUrl\":\"https://audio.test/cover.jpg\","
                        + "\"prompt\":\"lyrics\",\"title\":\"Happy Day\","
                        + "\"tags\":\"pop\",\"duration\":180.5}]}}}",
                        MediaType.APPLICATION_JSON));

        TaskSnapshot snapshot = provider(restTemplate).query("task-1");

        assertThat(snapshot.getStatus()).isEqualTo("completed");
        assertThat(snapshot.getCandidates()).hasSize(1);
        assertThat(snapshot.getCandidates().get(0).getProviderAudioId()).isEqualTo("song-1");
        assertThat(snapshot.getCandidates().get(0).getAudioUrl())
                .isEqualTo("https://audio.test/song.mp3");
        server.verify();
    }

    @Test
    void parsesCompleteCallbackIntoStableContract() {
        SunoApiAiMusicProvider provider = provider(new RestTemplate());
        Map<String, Object> song = new LinkedHashMap<String, Object>();
        song.put("id", "song-1");
        song.put("audio_url", "https://audio.test/song.mp3");
        song.put("title", "Happy Day");
        song.put("duration", Double.valueOf(181.0d));
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("callbackType", "complete");
        data.put("task_id", "task-1");
        data.put("data", Collections.singletonList(song));
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("code", Integer.valueOf(200));
        payload.put("msg", "success");
        payload.put("data", data);

        TaskSnapshot snapshot = provider.parseCallback(payload);

        assertThat(snapshot.getProviderTaskId()).isEqualTo("task-1");
        assertThat(snapshot.getStatus()).isEqualTo("completed");
        assertThat(snapshot.getCandidates()).hasSize(1);
        assertThat(snapshot.getCandidates().get(0).getDurationSeconds()).isEqualTo(181.0d);
    }

    @Test
    void submitsAndQueriesStandaloneLyrics() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo("https://api.sunoapi.test/api/v1/lyrics"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer suno-secret"))
                .andExpect(jsonPath("$.prompt").value("A song about growing up together"))
                .andExpect(jsonPath("$.callBackUrl").value("https://app.test/lyrics-callback"))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"taskId\":\"lyrics-1\"}}",
                        MediaType.APPLICATION_JSON));
        SunoApiAiMusicProvider provider = provider(restTemplate);

        Submission submission = provider.submitLyrics("A song about growing up together",
                "https://app.test/lyrics-callback");

        assertThat(submission.getProviderTaskId()).isEqualTo("lyrics-1");
        server.verify();

        server.reset();
        server.expect(once(), requestTo(
                        "https://api.sunoapi.test/api/v1/lyrics/record-info?taskId=lyrics-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"taskId\":\"lyrics-1\","
                        + "\"status\":\"SUCCESS\",\"response\":{\"data\":[{"
                        + "\"title\":\"Growing Up\",\"text\":\"[Verse]\\nSide by side\","
                        + "\"status\":\"complete\",\"errorMessage\":\"\"}]}}}",
                        MediaType.APPLICATION_JSON));

        LyricsSnapshot snapshot = provider.queryLyrics("lyrics-1");

        assertThat(snapshot.getStatus()).isEqualTo("completed");
        assertThat(snapshot.getCandidates()).hasSize(1);
        assertThat(snapshot.getCandidates().get(0).getTitle()).isEqualTo("Growing Up");
        assertThat(snapshot.getCandidates().get(0).getText()).contains("[Verse]");
        server.verify();
    }

    private SunoApiAiMusicProvider provider(RestTemplate restTemplate) {
        return new SunoApiAiMusicProvider(restTemplate, new ObjectMapper(),
                "https://api.sunoapi.test", "suno-secret", "callback-secret", "V5_5");
    }

    private Map<String, String> queryParameters(URI uri) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        UriComponentsBuilder.fromUri(uri).build().getQueryParams()
                .forEach((key, values) -> result.put(key, values.get(0)));
        return result;
    }
}
