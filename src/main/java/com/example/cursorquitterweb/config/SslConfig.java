package com.example.cursorquitterweb.config;

import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

/**
 * SSL配置类，包含SSL证书验证和HTTP到HTTPS重定向功能
 */
@Configuration
public class SslConfig {
    
    private static final Logger logger = LogUtil.getLogger(SslConfig.class);
    
    @Value("${server.ssl.key-store-password:}")
    private String keyStorePassword;
    
    @Value("${server.ssl.key-alias:}")
    private String keyAlias;
    
    @Value("${server.ssl.key-store:}")
    private String keyStorePath;
    
    @Value("${server.ssl.enabled:false}")
    private boolean sslEnabled;
    
    @Value("${server.port:443}")
    private int httpsPort;
    
    /**
     * 配置Tomcat服务器工厂，支持HTTPS和HTTP重定向
     */
    @Bean
    public ServletWebServerFactory servletContainer() {
        TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory();
        
        // 验证证书文件是否存在
        if (sslEnabled && validateKeyStore()) {
            LogUtil.logInfo(logger, "SSL证书配置成功，证书路径: {}", keyStorePath);
            
            // 添加HTTP连接器，重定向到HTTPS
            tomcat.addAdditionalTomcatConnectors(createHttpConnector());
            LogUtil.logInfo(logger, "配置HTTP到HTTPS重定向，HTTPS端口: {}", httpsPort);
        } else if (sslEnabled) {
            LogUtil.logWarn(logger, "SSL证书配置失败，将使用HTTP模式");
        } else {
            LogUtil.logInfo(logger, "SSL未启用，使用HTTP模式");
        }
        
        return tomcat;
    }
    
    /**
     * 验证KeyStore文件
     */
    private boolean validateKeyStore() {
        try {
            if (keyStorePath == null || keyStorePath.isEmpty()) {
                LogUtil.logWarn(logger, "KeyStore路径未配置");
                return false;
            }
            
            // 去掉classpath:前缀，因为ClassPathResource不需要这个前缀
            String resourcePath = keyStorePath;
            if (resourcePath.startsWith("classpath:")) {
                resourcePath = resourcePath.substring("classpath:".length());
            }
            
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (!resource.exists()) {
                LogUtil.logError(logger, "KeyStore文件不存在: {}", keyStorePath);
                return false;
            }
            
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(resource.getInputStream(), keyStorePassword.toCharArray());
            
            if (keyAlias != null && !keyAlias.isEmpty()) {
                if (!keyStore.containsAlias(keyAlias)) {
                    LogUtil.logError(logger, "KeyStore中不存在别名: {}", keyAlias);
                    return false;
                }
            }
            
            LogUtil.logInfo(logger, "KeyStore验证成功，包含 {} 个条目", keyStore.size());
            return true;
            
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
            LogUtil.logError(logger, "KeyStore验证失败", e);
            return false;
        }
    }
    
    /**
     * 创建HTTP连接器
     */
    private org.apache.catalina.connector.Connector createHttpConnector() {
        org.apache.catalina.connector.Connector connector = new org.apache.catalina.connector.Connector("org.apache.coyote.http11.Http11NioProtocol");
        connector.setScheme("http");
        connector.setPort(80);
        connector.setSecure(false);
        connector.setRedirectPort(httpsPort);
        
        return connector;
    }
} 