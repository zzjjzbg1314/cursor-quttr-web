package com.example.cursorquitterweb.musicmv.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class AiMusicLyricsCreateRequest {
    @NotBlank
    @javax.validation.constraints.Pattern(regexp = "^[A-Za-z0-9_-]{8,128}$")
    private String requestId;
    public String getRequestId() { return requestId; }
    public void setRequestId(String value) { requestId = value; }

    @NotBlank
    @Size(max = 200)
    private String prompt;

    public String getPrompt() { return prompt; }
    public void setPrompt(String value) { prompt = value; }
}
