package com.example.cursorquitterweb.util;

/**
 * 声网 RTM Token 生成工具类（AccessToken2 格式）
 * 基于声网官方实现
 * 参考: https://github.com/AgoraIO/Tools/tree/master/DynamicKey/AgoraDynamicKey
 */
public class RtmTokenBuilder2 {
    
    // RTM 登录权限
    private static final int PRIVILEGE_LOGIN = 1;
    
    /**
     * 生成 RTM Token（使用 AccessToken2 格式）
     * 
     * @param appId 声网 App ID
     * @param appCertificate 声网 App Certificate
     * @param userId 用户 ID（RTM Token 不需要 userId，但保留参数以兼容 API）
     * @param expirationInSeconds Token 过期时间（秒，从当前时间开始计算）
     * @return RTM Token 字符串（AccessToken2 格式，以 007eJx 开头）
     * @throws Exception 生成失败时抛出异常
     */
    public String buildToken(String appId, String appCertificate, 
                             String userId, int expirationInSeconds) throws Exception {
        if (appId == null || appId.isEmpty()) {
            throw new IllegalArgumentException("appId 不能为空");
        }
        if (appCertificate == null || appCertificate.isEmpty()) {
            throw new IllegalArgumentException("appCertificate 不能为空");
        }
        
        // 计算过期时间戳
        int currentTimestamp = (int) (System.currentTimeMillis() / 1000);
        int expireTimestamp = currentTimestamp + expirationInSeconds;
        
        // 使用 AccessToken2 生成 Token
        AccessToken2 token = new AccessToken2(appId, appCertificate, expirationInSeconds);
        token.addService((short) 1, PRIVILEGE_LOGIN, expireTimestamp);
        
        return token.build();
    }
}

