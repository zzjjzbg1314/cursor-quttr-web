package com.example.cursorquitterweb.musicmv.aimusic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.example.cursorquitterweb.musicmv.aimusic.AiMusicProvider.GenerateSongCommand;
import com.example.cursorquitterweb.musicmv.aimusic.AiMusicProvider.Submission;
import com.example.cursorquitterweb.musicmv.aimusic.AiMusicProvider.TaskSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;

class KieAiMusicProviderTest {
    @Test
    void submitsProviderNeutralCommandUsingKieGenerateContract() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(once(), requestTo("https://api.kie.test/api/v1/generate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer kie-secret"))
                .andExpect(jsonPath("$.callBackUrl").value("https://app.test/callback"))
                .andExpect(jsonPath("$.model").value("V5_5"))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"taskId\":\"task-1\"}}",
                        MediaType.APPLICATION_JSON));
        KieAiMusicProvider provider = new KieAiMusicProvider(restTemplate,
                new ObjectMapper(), "https://api.kie.test", "kie-secret", "V5_5");
        GenerateSongCommand command = new GenerateSongCommand();
        command.setPrompt("A birthday song");
        command.setStyle("Pop");
        command.setTitle("Happy Day");
        command.setModel("V5_5");
        command.setCustomMode(true);
        command.setCallbackUrl("https://app.test/callback");

        Submission submission = provider.submit(command);

        assertThat(submission.getProviderTaskId()).isEqualTo("task-1");
        server.verify();
    }

    @Test
    void parsesCompleteCallbackIntoStableCandidateContract() {
        KieAiMusicProvider provider = new KieAiMusicProvider(new RestTemplate(),
                new ObjectMapper(), "https://api.kie.test", "kie-secret", "V5_5");
        Map<String, Object> song = new LinkedHashMap<String, Object>();
        song.put("id", "song-1");
        song.put("audio_url", "https://audio.test/song.mp3");
        song.put("stream_audio_url", "https://audio.test/stream");
        song.put("image_url", "https://audio.test/cover.jpg");
        song.put("prompt", "lyrics");
        song.put("title", "Happy Day");
        song.put("tags", "pop");
        song.put("duration", Double.valueOf(180.5d));
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("callbackType", "complete");
        data.put("task_id", "task-1");
        data.put("data", java.util.Collections.singletonList(song));
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("code", Integer.valueOf(200));
        payload.put("msg", "All generated successfully.");
        payload.put("data", data);

        TaskSnapshot snapshot = provider.parseCallback(payload);

        assertThat(snapshot.getStatus()).isEqualTo("completed");
        assertThat(snapshot.getProviderTaskId()).isEqualTo("task-1");
        assertThat(snapshot.getErrorCode()).isNull();
        assertThat(snapshot.getCandidates()).hasSize(1);
        assertThat(snapshot.getCandidates().get(0).getProviderAudioId()).isEqualTo("song-1");
        assertThat(snapshot.getCandidates().get(0).getDurationSeconds()).isEqualTo(180.5d);
    }
}
