package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * SSL证书管理控制器
 */
@RestController
@RequestMapping("/api/ssl")
public class SslController {
    
    private static final Logger logger = LogUtil.getLogger(SslController.class);
    
    @Value("${server.ssl.key-store:}")
    private String keyStorePath;
    
    @Value("${server.ssl.key-store-password:}")
    private String keyStorePassword;
    
    @Value("${server.ssl.key-alias:}")
    private String keyAlias;
    
    @Value("${server.ssl.enabled:false}")
    private boolean sslEnabled;
    
    /**
     * 获取SSL证书信息
     */
    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> getSslInfo() {
        LogUtil.logDebug(logger, "获取SSL证书信息");
        
        Map<String, Object> sslInfo = new HashMap<>();
        sslInfo.put("enabled", sslEnabled);
        sslInfo.put("keyStorePath", keyStorePath);
        sslInfo.put("keyAlias", keyAlias);
        
        if (sslEnabled && keyStorePath != null && !keyStorePath.isEmpty()) {
            try {
                ClassPathResource resource = new ClassPathResource(keyStorePath);
                if (resource.exists()) {
                    KeyStore keyStore = KeyStore.getInstance("JKS");
                    keyStore.load(resource.getInputStream(), keyStorePassword.toCharArray());
                    
                    sslInfo.put("keyStoreExists", true);
                    sslInfo.put("keyStoreSize", keyStore.size());
                    
                    if (keyAlias != null && !keyAlias.isEmpty() && keyStore.containsAlias(keyAlias)) {
                        X509Certificate cert = (X509Certificate) keyStore.getCertificate(keyAlias);
                        if (cert != null) {
                            Map<String, Object> certInfo = new HashMap<>();
                            certInfo.put("subject", cert.getSubjectDN().getName());
                            certInfo.put("issuer", cert.getIssuerDN().getName());
                            certInfo.put("serialNumber", cert.getSerialNumber().toString());
                            certInfo.put("notBefore", LocalDateTime.ofInstant(
                                cert.getNotBefore().toInstant(), ZoneId.systemDefault()));
                            certInfo.put("notAfter", LocalDateTime.ofInstant(
                                cert.getNotAfter().toInstant(), ZoneId.systemDefault()));
                            
                            sslInfo.put("certificate", certInfo);
                        }
                    }
                } else {
                    sslInfo.put("keyStoreExists", false);
                    sslInfo.put("error", "KeyStore文件不存在");
                }
            } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
                LogUtil.logError(logger, "获取SSL证书信息失败", e);
                sslInfo.put("error", "获取证书信息失败: " + e.getMessage());
            }
        } else {
            sslInfo.put("keyStoreExists", false);
            sslInfo.put("error", "SSL未启用或配置不完整");
        }
        
        return ApiResponse.success(sslInfo);
    }
    
    /**
     * 检查SSL状态
     */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> getSslStatus() {
        LogUtil.logDebug(logger, "检查SSL状态");
        
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", sslEnabled);
        status.put("secure", sslEnabled);
        status.put("protocol", sslEnabled ? "HTTPS" : "HTTP");
        
        return ApiResponse.success(status);
    }
} 