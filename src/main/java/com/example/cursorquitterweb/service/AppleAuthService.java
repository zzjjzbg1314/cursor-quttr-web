package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.dto.AppleLoginRequest;
import com.example.cursorquitterweb.dto.AppleLoginResponse;

/**
 * Apple 登录服务接口
 */
public interface AppleAuthService {
    
    /**
     * 处理 Apple 登录
     * @param request Apple 登录请求
     * @return 登录响应（包含用户信息）
     */
    AppleLoginResponse login(AppleLoginRequest request);
    
    /**
     * 验证 Apple Identity Token
     * @param identityToken JWT token
     * @return Apple User ID（sub），如果验证失败返回 null
     */
    String verifyIdentityToken(String identityToken);
}

