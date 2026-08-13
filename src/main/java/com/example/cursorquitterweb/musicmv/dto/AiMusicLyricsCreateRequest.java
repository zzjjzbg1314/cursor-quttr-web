package com.example.cursorquitterweb.musicmv.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class AiMusicLyricsCreateRequest {
    @NotBlank
    @Size(max = 200)
    private String prompt;

    public String getPrompt() { return prompt; }
    public void setPrompt(String value) { prompt = value; }
}
