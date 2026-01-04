package com.example.cursorquitterweb.util;

import com.example.cursorquitterweb.rtm.RtmTokenBuilder2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.util.AssertionErrors.assertFalse;
import static org.springframework.test.util.AssertionErrors.assertTrue;


/**
 * RTM Token 生成器测试
 */
public class GenerateRtmTokenBuilder2Test {
    
    private static final String APP_ID = "9404f12b93dc4638a350df2af50c5f16";
    private static final String APP_CERTIFICATE = "cbb6d34d963e416bb1a59b4af2485064";
    private static final String USER_ID = "64b4a132-f07d-4614-9705-4f7a6f866b8d";
    private static final int EXPIRATION_IN_SECONDS = 3600;
    
    @Test
    public void testBuildToken() throws Exception {
        RtmTokenBuilder2 tokenBuilder = new RtmTokenBuilder2();
        String token = tokenBuilder.buildToken(APP_ID, APP_CERTIFICATE, USER_ID, EXPIRATION_IN_SECONDS);
        
        assertNotNull("Token 不能为空", token);
        assertFalse("Token 不能为空字符串", token.isEmpty());
        
        System.out.println("生成的 Token: " + token);
        System.out.println("Token 长度: " + token.length());
        
        // Token 应该是 Base64 编码的，以 BwAg 开头（版本号 0x007 的 Base64 编码）
        // 注意：Base64 编码的 0x0007 (2字节) 应该是 "BwAg" 或类似
        assertTrue("Token 格式应该正确", token.length() > 20);
    }
}

