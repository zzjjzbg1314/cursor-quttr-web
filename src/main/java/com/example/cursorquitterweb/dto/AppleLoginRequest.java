package com.example.cursorquitterweb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotBlank;

/**
 * Apple 登录请求DTO
 */
public class AppleLoginRequest {
    
    @NotBlank(message = "Apple User ID 不能为空")
    @JsonProperty("apple_user_id")
    private String appleUserId;
    
    @NotBlank(message = "Identity Token 不能为空")
    @JsonProperty("identity_token")
    private String identityToken;
    
    @JsonProperty("authorization_code")
    private String authorizationCode;
    
    @JsonProperty("email")
    private String email;
    
    @JsonProperty("given_name")
    private String givenName;
    
    @JsonProperty("family_name")
    private String familyName;
    
    public AppleLoginRequest() {
    }
    
    public String getAppleUserId() {
        return appleUserId;
    }
    
    public void setAppleUserId(String appleUserId) {
        this.appleUserId = appleUserId;
    }
    
    public String getIdentityToken() {
        return identityToken;
    }
    
    public void setIdentityToken(String identityToken) {
        this.identityToken = identityToken;
    }
    
    public String getAuthorizationCode() {
        return authorizationCode;
    }
    
    public void setAuthorizationCode(String authorizationCode) {
        this.authorizationCode = authorizationCode;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
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
    
    @Override
    public String toString() {
        return "AppleLoginRequest{" +
                "appleUserId='" + appleUserId + '\'' +
                ", email='" + email + '\'' +
                ", givenName='" + givenName + '\'' +
                ", familyName='" + familyName + '\'' +
                '}';
    }
}

