package com.example.cursorquitterweb.util;

import com.example.cursorquitterweb.rtm.RtmTokenBuilder2;

/**
 * RTM Token 生成测试程序
 */
public class TestRtmToken {
    
    private static final String APP_ID = "9404f12b93dc4638a350df2af50c5f16";
    private static final String APP_CERTIFICATE = "cbb6d34d963e416bb1a59b4af2485064";
    private static final String USER_ID = "64b4a132-f07d-4614-9705-4f7a6f866b8d";
    private static final int EXPIRATION_IN_SECONDS = 3600;
    
    public static void main(String[] args) {
        try {
            RtmTokenBuilder2 tokenBuilder = new RtmTokenBuilder2();
            String token = tokenBuilder.buildToken(APP_ID, APP_CERTIFICATE, USER_ID, EXPIRATION_IN_SECONDS);
            
            System.out.println("========================================");
            System.out.println("RTM Token 生成测试");
            System.out.println("========================================");
            System.out.println("App ID: " + APP_ID);
            System.out.println("User ID: " + USER_ID);
            System.out.println("过期时间: " + EXPIRATION_IN_SECONDS + " 秒");
            System.out.println("----------------------------------------");
            System.out.println("生成的 Token:");
            System.out.println(token);
            System.out.println("----------------------------------------");
            System.out.println("Token 长度: " + token.length() + " 字符");
            System.out.println("Token 前缀: " + (token.length() > 4 ? token.substring(0, Math.min(20, token.length())) : token));
            System.out.println("========================================");
            
            // 期望的 Token 格式（参考）
            String expectedTokenPrefix = "BwAgADk0MDRmMTJiOTNkYzQ2MzhhMzUwZGYyYWY1MGM1ZjE2";
            System.out.println("期望的 Token 前缀: " + expectedTokenPrefix);
            System.out.println("实际 Token 前缀: " + (token.length() > expectedTokenPrefix.length() ? token.substring(0, expectedTokenPrefix.length()) : token));
            
        } catch (Exception e) {
            System.err.println("生成 Token 失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

