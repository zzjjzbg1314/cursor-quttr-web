package com.example.cursorquitterweb.musicmv.dto;

import java.util.Map;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

/** Sanitized browser scene synchronized from the immutable template source. */
public class TemplateBrowserSceneRequest {
    @NotBlank private String schemaVersion;
    @NotBlank @Pattern(regexp = "(?i)^[0-9a-f]{64}$") private String manifestSha256;
    @NotNull private Map<String, Object> scene;

    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String value) { schemaVersion = value; }
    public String getManifestSha256() { return manifestSha256; }
    public void setManifestSha256(String value) { manifestSha256 = value; }
    public Map<String, Object> getScene() { return scene; }
    public void setScene(Map<String, Object> value) { scene = value; }
}
