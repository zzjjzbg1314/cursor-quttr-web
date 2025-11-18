package com.example.cursorquitterweb.dto;

import com.example.cursorquitterweb.entity.User;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 一键登录响应DTO
 */
public class OneClickLoginResponse {
    
    @JsonProperty("phone_number")
    private String phoneNumber;
    
    @JsonProperty("user")
    private User user;
    
    @JsonProperty("is_new_user")
    private Boolean isNewUser;

    public OneClickLoginResponse() {
    }

    public OneClickLoginResponse(String phoneNumber, User user, Boolean isNewUser) {
        this.phoneNumber = phoneNumber;
        this.user = user;
        this.isNewUser = isNewUser;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Boolean getIsNewUser() {
        return isNewUser;
    }

    public void setIsNewUser(Boolean isNewUser) {
        this.isNewUser = isNewUser;
    }

    @Override
    public String toString() {
        return "OneClickLoginResponse{" +
                "phoneNumber='" + phoneNumber + '\'' +
                ", user=" + user +
                ", isNewUser=" + isNewUser +
                '}';
    }
}

