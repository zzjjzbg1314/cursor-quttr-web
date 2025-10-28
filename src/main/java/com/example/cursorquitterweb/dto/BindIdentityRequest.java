package com.example.cursorquitterweb.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 绑定用户身份请求DTO
 */
public class BindIdentityRequest {
    
    @NotBlank(message = "身份类型不能为空")
    @Size(max = 20, message = "身份类型长度不能超过20个字符")
    private String identityType;
    
    @NotBlank(message = "身份ID不能为空")
    @Size(max = 128, message = "身份ID长度不能超过128个字符")
    private String identityId;
    
    /**
     * 身份相关数据（JSON格式）
     */
    private String identityData;
    
    public BindIdentityRequest() {
    }
    
    public BindIdentityRequest(String identityType, String identityId, String identityData) {
        this.identityType = identityType;
        this.identityId = identityId;
        this.identityData = identityData;
    }
    
    public String getIdentityType() {
        return identityType;
    }
    
    public void setIdentityType(String identityType) {
        this.identityType = identityType;
    }
    
    public String getIdentityId() {
        return identityId;
    }
    
    public void setIdentityId(String identityId) {
        this.identityId = identityId;
    }
    
    public String getIdentityData() {
        return identityData;
    }
    
    public void setIdentityData(String identityData) {
        this.identityData = identityData;
    }
    
    @Override
    public String toString() {
        return "BindIdentityRequest{" +
                "identityType='" + identityType + '\'' +
                ", identityId='" + identityId + '\'' +
                ", identityData='" + identityData + '\'' +
                '}';
    }
}

