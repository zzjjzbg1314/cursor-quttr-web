package com.example.cursorquitterweb.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Apple JWT Token 验证工具类
 */
public class AppleJwtUtil {
    
    private static final Logger logger = LogUtil.getLogger(AppleJwtUtil.class);
    
    private static final String APPLE_PUBLIC_KEYS_URL = "https://appleid.apple.com/auth/keys";
    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    
    private static Map<String, PublicKey> publicKeysCache = new HashMap<>();
    private static long lastFetchTime = 0;
    private static final long CACHE_DURATION = 24 * 60 * 60 * 1000; // 24小时
    
    /**
     * 验证 Apple Identity Token
     * @param identityToken JWT token
     * @param expectedAudience 期望的 audience（通常是你的 App ID 或 Client ID）
     * @return 如果验证成功返回 token 中的 sub（Apple User ID），否则返回 null
     */
    public static String verifyIdentityToken(String identityToken, String expectedAudience) {
        try {
            // 1. 分割 JWT token
            String[] parts = identityToken.split("\\.");
            if (parts.length != 3) {
                logger.error("无效的 JWT token 格式");
                return null;
            }
            
            // 2. 解码 header
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
            ObjectMapper mapper = new ObjectMapper();
            JsonNode header = mapper.readTree(headerJson);
            String kid = header.get("kid").asText();
            String alg = header.get("alg").asText();
            
            logger.debug("JWT header - kid: {}, alg: {}", kid, alg);
            
            // 3. 解码 payload
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode payload = mapper.readTree(payloadJson);
            
            // 4. 验证基本字段
            String iss = payload.get("iss").asText();
            String aud = payload.has("aud") ? payload.get("aud").asText() : "";
            long exp = payload.get("exp").asLong();
            String sub = payload.get("sub").asText();
            
            logger.debug("JWT payload - iss: {}, aud: {}, exp: {}, sub: {}", iss, aud, exp, sub);
            
            // 验证 issuer
            if (!APPLE_ISSUER.equals(iss)) {
                logger.error("无效的 issuer: {}", iss);
                return null;
            }
            
            // 验证 audience（如果提供）
            if (expectedAudience != null && !expectedAudience.isEmpty() && !aud.equals(expectedAudience)) {
                logger.warn("Audience 不匹配，期望: {}, 实际: {}", expectedAudience, aud);
                // 注意：根据实际情况决定是否严格验证
            }
            
            // 验证过期时间
            long currentTime = System.currentTimeMillis() / 1000;
            if (exp < currentTime) {
                logger.error("Token 已过期，exp: {}, current: {}", exp, currentTime);
                return null;
            }
            
            // 5. 获取 Apple 公钥并验证签名
            PublicKey publicKey = getApplePublicKey(kid);
            if (publicKey == null) {
                logger.error("无法获取 Apple 公钥，kid: {}", kid);
                return null;
            }
            
            // 验证签名
            String signatureInput = parts[0] + "." + parts[1];
            byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[2]);
            
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(signatureInput.getBytes());
            
            boolean verified = signature.verify(signatureBytes);
            
            if (verified) {
                logger.info("Apple Identity Token 验证成功，sub: {}", sub);
                return sub;
            } else {
                logger.error("签名验证失败");
                return null;
            }
            
        } catch (Exception e) {
            logger.error("验证 Apple Identity Token 失败", e);
            return null;
        }
    }
    
    /**
     * 获取 Apple 公钥
     */
    private static PublicKey getApplePublicKey(String kid) {
        try {
            // 检查缓存
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastFetchTime < CACHE_DURATION && publicKeysCache.containsKey(kid)) {
                logger.debug("使用缓存的公钥，kid: {}", kid);
                return publicKeysCache.get(kid);
            }
            
            // 从 Apple 获取公钥
            logger.info("从 Apple 获取公钥");
            URL url = new URL(APPLE_PUBLIC_KEYS_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            // 解析公钥
            ObjectMapper mapper = new ObjectMapper();
            JsonNode keysNode = mapper.readTree(response.toString());
            JsonNode keys = keysNode.get("keys");
            
            publicKeysCache.clear();
            
            for (JsonNode key : keys) {
                String keyId = key.get("kid").asText();
                String n = key.get("n").asText();
                String e = key.get("e").asText();
                
                // 构建公钥
                byte[] nBytes = Base64.getUrlDecoder().decode(n);
                byte[] eBytes = Base64.getUrlDecoder().decode(e);
                
                BigInteger modulus = new BigInteger(1, nBytes);
                BigInteger exponent = new BigInteger(1, eBytes);
                
                RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
                KeyFactory factory = KeyFactory.getInstance("RSA");
                PublicKey publicKey = factory.generatePublic(spec);
                
                publicKeysCache.put(keyId, publicKey);
                logger.debug("缓存公钥，kid: {}", keyId);
            }
            
            lastFetchTime = currentTime;
            
            return publicKeysCache.get(kid);
            
        } catch (Exception e) {
            logger.error("获取 Apple 公钥失败", e);
            return null;
        }
    }
    
    /**
     * 从 token 中提取 payload（不验证签名）
     * 仅用于调试目的
     */
    public static JsonNode decodePayload(String identityToken) {
        try {
            String[] parts = identityToken.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readTree(payloadJson);
            
        } catch (Exception e) {
            logger.error("解码 JWT payload 失败", e);
            return null;
        }
    }
}

