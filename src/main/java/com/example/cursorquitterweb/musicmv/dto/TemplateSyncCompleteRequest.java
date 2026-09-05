package com.example.cursorquitterweb.musicmv.dto;

import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class TemplateSyncCompleteRequest {
    @NotBlank @Pattern(regexp = "(?i)^[0-9a-f]{64}$") private String manifestSha256;
    @NotEmpty @Size(max = 5000) private List<String> mediaRoles;
    public String getManifestSha256() { return manifestSha256; }
    public void setManifestSha256(String value) { manifestSha256 = value; }
    public List<String> getMediaRoles() { return mediaRoles; }
    public void setMediaRoles(List<String> value) { mediaRoles = value; }
}
