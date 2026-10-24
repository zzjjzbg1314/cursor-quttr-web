package com.example.cursorquitterweb.musicmv.dto;

import javax.validation.constraints.Size;

public class MusicMvAuthMethodRequest {
    @Size(max=254) public String email;
    @Size(max=128) public String challengeId;
    @Size(max=256) public String code;
    @Size(max=20000) public String idToken;
    @Size(max=120) public String displayName;
    @Size(max=128) public String anonymousClientId;
    @Size(max=10) public String locale;
    public boolean link;
}
