package com.example.cursorquitterweb.musicmv.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/** Lease-bound interruption report from a browser render attempt. */
public class BrowserRenderFailureRequest {
    @NotBlank @Size(max = 128) @Pattern(regexp = "^[A-Za-z0-9_-]+$")
    private String attemptId;
    @NotBlank @Size(max = 128) @Pattern(regexp = "^[A-Za-z0-9_-]+$")
    private String leaseToken;
    @Size(max = 500)
    private String message;

    public String getAttemptId() { return attemptId; }
    public void setAttemptId(String value) { attemptId = value; }
    public String getLeaseToken() { return leaseToken; }
    public void setLeaseToken(String value) { leaseToken = value; }
    public String getMessage() { return message; }
    public void setMessage(String value) { message = value; }
}
