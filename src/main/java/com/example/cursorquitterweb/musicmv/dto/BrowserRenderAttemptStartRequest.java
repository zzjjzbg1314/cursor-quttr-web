package com.example.cursorquitterweb.musicmv.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/** Identifies the browser tab asking to own the next render attempt. */
public class BrowserRenderAttemptStartRequest {
    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = "^[A-Za-z0-9_-]+$")
    private String sessionId;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String value) { sessionId = value; }
}
