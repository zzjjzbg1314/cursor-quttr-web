package com.example.cursorquitterweb.dto;

/**
 * 绑定手机号码请求DTO
 */
public class BindPhoneRequest {
    private String userId;
    private String phoneNumber;

    // 默认构造函数
    public BindPhoneRequest() {}

    // 带参数构造函数
    public BindPhoneRequest(String userId, String phoneNumber) {
        this.userId = userId;
        this.phoneNumber = phoneNumber;
    }

    // Getters and Setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    @Override
    public String toString() {
        return "BindPhoneRequest{" +
                "userId='" + userId + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                '}';
    }
}
