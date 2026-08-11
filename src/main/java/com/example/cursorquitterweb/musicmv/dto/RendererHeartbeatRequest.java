package com.example.cursorquitterweb.musicmv.dto;

import javax.validation.constraints.NotBlank;

public class RendererHeartbeatRequest {
    @NotBlank
    private String nodeId;
    @NotBlank
    private String name;
    @NotBlank
    private String status;
    private String runtimeVersion;
    private String runtimeSha256;
    private String lastError;

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRuntimeVersion() {
        return runtimeVersion;
    }

    public void setRuntimeVersion(String runtimeVersion) {
        this.runtimeVersion = runtimeVersion;
    }

    public String getRuntimeSha256() {
        return runtimeSha256;
    }

    public void setRuntimeSha256(String runtimeSha256) {
        this.runtimeSha256 = runtimeSha256;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
