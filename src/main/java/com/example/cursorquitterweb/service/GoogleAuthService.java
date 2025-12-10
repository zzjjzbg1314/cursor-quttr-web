package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.dto.GoogleLoginRequest;
import com.example.cursorquitterweb.dto.GoogleLoginResponse;

/**
 * Google 登录服务接口
 */
public interface GoogleAuthService {
    
    /**
     * 处理 Google 登录
     * @param request Google 登录请求
     * @return 登录响应（包含用户信息）
     */
    GoogleLoginResponse login(GoogleLoginRequest request);
    
    /**
     * 验证 Google ID Token
     * @param idToken JWT token
     * @return Google User ID（sub），如果验证失败返回 null
     */
    String verifyIdToken(String idToken);
}

