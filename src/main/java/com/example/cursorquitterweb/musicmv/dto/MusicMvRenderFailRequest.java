package com.example.cursorquitterweb.musicmv.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class MusicMvRenderFailRequest {
    @NotBlank
    private String nodeId;
    @NotBlank
    private String leaseToken;
    @NotBlank
    @Size(max = 128)
    private String errorCode;
    @NotBlank
    @Size(max = 2000)
    private String errorMessage;
    private boolean retryable;

    public String getNodeId() { return nodeId; }
    public void setNodeId(String value) { nodeId = value; }
    public String getLeaseToken() { return leaseToken; }
    public void setLeaseToken(String value) { leaseToken = value; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String value) { errorCode = value; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String value) { errorMessage = value; }
    public boolean isRetryable() { return retryable; }
    public void setRetryable(boolean value) { retryable = value; }
}
