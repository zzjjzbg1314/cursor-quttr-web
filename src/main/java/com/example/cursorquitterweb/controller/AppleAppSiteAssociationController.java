package com.example.cursorquitterweb.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Apple App Site Association 控制器
 * 用于提供 Universal Links 配置文件
 */
@RestController
public class AppleAppSiteAssociationController {

    /**
     * 提供 apple-app-site-association 文件
     * iOS 系统会通过此路径验证 Universal Links 配置
     * 
     * @return apple-app-site-association 文件内容
     */
    @GetMapping("/.well-known/apple-app-site-association")
    public ResponseEntity<String> getAppleAppSiteAssociation() {
        try {
            // 从 classpath 读取文件
            ClassPathResource resource = new ClassPathResource(".well-known/apple-app-site-association");
            
            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }
            
            // 读取文件内容
            String content = StreamUtils.copyToString(
                resource.getInputStream(), 
                StandardCharsets.UTF_8
            );
            
            // 设置响应头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // iOS 要求不缓存此文件
            headers.setCacheControl("no-cache, no-store, must-revalidate");
            headers.setPragma("no-cache");
            headers.setExpires(0);
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(content);
                    
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}

