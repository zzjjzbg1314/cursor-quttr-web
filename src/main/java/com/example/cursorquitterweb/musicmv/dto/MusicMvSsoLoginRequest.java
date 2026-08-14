package com.example.cursorquitterweb.musicmv.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class MusicMvSsoLoginRequest {
    @NotBlank
    @Size(max = 20)
    private String provider;

    @NotBlank
    @Size(max = 20000)
    private String idToken;

    @Size(max = 128)
    private String anonymousClientId;

    @Size(max = 120)
    private String displayName;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }

    public String getAnonymousClientId() {
        return anonymousClientId;
    }

    public void setAnonymousClientId(String anonymousClientId) {
        this.anonymousClientId = anonymousClientId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
