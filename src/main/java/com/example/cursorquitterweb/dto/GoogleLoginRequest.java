package com.example.cursorquitterweb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotBlank;

/**
 * Google 登录请求DTO
 */
public class GoogleLoginRequest {
    
    @NotBlank(message = "Google User ID 不能为空")
    @JsonProperty("google_user_id")
    private String googleUserId;
    
    @JsonProperty("id_token")
    private String idToken;
    
    @JsonProperty("email")
    private String email;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("given_name")
    private String givenName;
    
    @JsonProperty("family_name")
    private String familyName;
    
    @JsonProperty("picture")
    private String picture;

    @JsonProperty("language")
    private String language;
    
    public GoogleLoginRequest() {
    }
    
    public String getGoogleUserId() {
        return googleUserId;
    }
    
    public void setGoogleUserId(String googleUserId) {
        this.googleUserId = googleUserId;
    }
    
    public String getIdToken() {
        return idToken;
    }
    
    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getGivenName() {
        return givenName;
    }
    
    public void setGivenName(String givenName) {
        this.givenName = givenName;
    }
    
    public String getFamilyName() {
        return familyName;
    }
    
    public void setFamilyName(String familyName) {
        this.familyName = familyName;
    }
    
    public String getPicture() {
        return picture;
    }
    
    public void setPicture(String picture) {
        this.picture = picture;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
    
    @Override
    public String toString() {
        return "GoogleLoginRequest{" +
                "googleUserId='" + googleUserId + '\'' +
                ", email='" + email + '\'' +
                ", name='" + name + '\'' +
                ", givenName='" + givenName + '\'' +
                ", familyName='" + familyName + '\'' +
                ", language='" + language + '\'' +
                '}';
    }
}
