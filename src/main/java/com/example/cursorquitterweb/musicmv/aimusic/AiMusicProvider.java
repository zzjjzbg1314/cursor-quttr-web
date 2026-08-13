package com.example.cursorquitterweb.musicmv.aimusic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface AiMusicProvider {
    String providerCode();

    String defaultModel();

    String webhookPath();

    /**
     * Lets each provider protect and shape its callback URL without leaking
     * provider-specific fields into the public songwriting contract.
     */
    default String callbackUrl(String publicBaseUrl, String jobId) {
        return publicBaseUrl + webhookPath();
    }

    Submission submit(GenerateSongCommand command);

    TaskSnapshot query(String providerTaskId);

    default boolean supportsLyrics() { return false; }

    default String lyricsWebhookPath() {
        return "/api/music-mv/v1/provider-webhooks/" + providerCode() + "/lyrics";
    }

    default Submission submitLyrics(String prompt, String callbackUrl) {
        throw new UnsupportedOperationException("Lyrics generation is not supported");
    }

    default LyricsSnapshot queryLyrics(String providerTaskId) {
        throw new UnsupportedOperationException("Lyrics generation is not supported");
    }

    final class GenerateSongCommand {
        private String prompt;
        private String style;
        private String title;
        private String model;
        private boolean customMode;
        private boolean instrumental;
        private String callbackUrl;
        private String negativeTags;
        private String vocalGender;
        private Double styleWeight;
        private Double weirdnessConstraint;

        public String getPrompt() { return prompt; }
        public void setPrompt(String value) { prompt = value; }
        public String getStyle() { return style; }
        public void setStyle(String value) { style = value; }
        public String getTitle() { return title; }
        public void setTitle(String value) { title = value; }
        public String getModel() { return model; }
        public void setModel(String value) { model = value; }
        public boolean isCustomMode() { return customMode; }
        public void setCustomMode(boolean value) { customMode = value; }
        public boolean isInstrumental() { return instrumental; }
        public void setInstrumental(boolean value) { instrumental = value; }
        public String getCallbackUrl() { return callbackUrl; }
        public void setCallbackUrl(String value) { callbackUrl = value; }
        public String getNegativeTags() { return negativeTags; }
        public void setNegativeTags(String value) { negativeTags = value; }
        public String getVocalGender() { return vocalGender; }
        public void setVocalGender(String value) { vocalGender = value; }
        public Double getStyleWeight() { return styleWeight; }
        public void setStyleWeight(Double value) { styleWeight = value; }
        public Double getWeirdnessConstraint() { return weirdnessConstraint; }
        public void setWeirdnessConstraint(Double value) { weirdnessConstraint = value; }
    }

    final class Submission {
        private final String providerTaskId;
        private final Map<String, Object> raw;

        public Submission(String providerTaskId, Map<String, Object> raw) {
            this.providerTaskId = providerTaskId;
            this.raw = raw;
        }

        public String getProviderTaskId() { return providerTaskId; }
        public Map<String, Object> getRaw() { return raw; }
    }

    final class TaskSnapshot {
        private String providerTaskId;
        private String status;
        private String errorCode;
        private String errorMessage;
        private boolean retryable;
        private List<Candidate> candidates = new ArrayList<Candidate>();
        private Map<String, Object> raw;

        public String getProviderTaskId() { return providerTaskId; }
        public void setProviderTaskId(String value) { providerTaskId = value; }
        public String getStatus() { return status; }
        public void setStatus(String value) { status = value; }
        public String getErrorCode() { return errorCode; }
        public void setErrorCode(String value) { errorCode = value; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String value) { errorMessage = value; }
        public boolean isRetryable() { return retryable; }
        public void setRetryable(boolean value) { retryable = value; }
        public List<Candidate> getCandidates() { return candidates; }
        public void setCandidates(List<Candidate> value) {
            candidates = value == null ? new ArrayList<Candidate>() : value;
        }
        public Map<String, Object> getRaw() { return raw; }
        public void setRaw(Map<String, Object> value) { raw = value; }
    }

    final class Candidate {
        private String providerAudioId;
        private String title;
        private String lyrics;
        private String style;
        private Double durationSeconds;
        private String audioUrl;
        private String streamUrl;
        private String imageUrl;
        private Map<String, Object> raw;

        public String getProviderAudioId() { return providerAudioId; }
        public void setProviderAudioId(String value) { providerAudioId = value; }
        public String getTitle() { return title; }
        public void setTitle(String value) { title = value; }
        public String getLyrics() { return lyrics; }
        public void setLyrics(String value) { lyrics = value; }
        public String getStyle() { return style; }
        public void setStyle(String value) { style = value; }
        public Double getDurationSeconds() { return durationSeconds; }
        public void setDurationSeconds(Double value) { durationSeconds = value; }
        public String getAudioUrl() { return audioUrl; }
        public void setAudioUrl(String value) { audioUrl = value; }
        public String getStreamUrl() { return streamUrl; }
        public void setStreamUrl(String value) { streamUrl = value; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String value) { imageUrl = value; }
        public Map<String, Object> getRaw() { return raw; }
        public void setRaw(Map<String, Object> value) { raw = value; }
    }

    final class LyricsSnapshot {
        private String providerTaskId;
        private String status;
        private String errorCode;
        private String errorMessage;
        private boolean retryable;
        private List<LyricsCandidate> candidates = new ArrayList<LyricsCandidate>();
        private Map<String, Object> raw;

        public String getProviderTaskId() { return providerTaskId; }
        public void setProviderTaskId(String value) { providerTaskId = value; }
        public String getStatus() { return status; }
        public void setStatus(String value) { status = value; }
        public String getErrorCode() { return errorCode; }
        public void setErrorCode(String value) { errorCode = value; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String value) { errorMessage = value; }
        public boolean isRetryable() { return retryable; }
        public void setRetryable(boolean value) { retryable = value; }
        public List<LyricsCandidate> getCandidates() { return candidates; }
        public void setCandidates(List<LyricsCandidate> value) {
            candidates = value == null ? new ArrayList<LyricsCandidate>() : value;
        }
        public Map<String, Object> getRaw() { return raw; }
        public void setRaw(Map<String, Object> value) { raw = value; }
    }

    final class LyricsCandidate {
        private String title;
        private String text;
        private String status;
        private String errorMessage;

        public String getTitle() { return title; }
        public void setTitle(String value) { title = value; }
        public String getText() { return text; }
        public void setText(String value) { text = value; }
        public String getStatus() { return status; }
        public void setStatus(String value) { status = value; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String value) { errorMessage = value; }
    }
}
