package com.example.cursorquitterweb.config;

import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

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

    @Value("${server.ssl.key-store-type:JKS}")
    private String keyStoreType;

    @Value("${server.ssl.key-password:}")
    private String keyPassword;
    
    @Value("${server.ssl.enabled:false}")
    private boolean sslEnabled;
    
    @Value("${server.port:443}")
    private int httpsPort;
    
    @Value("${server.http.port:8080}")
    private int httpPort;
    
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
            LogUtil.logInfo(logger, "配置HTTP到HTTPS重定向，HTTP端口: {}, HTTPS端口: {}", httpPort, httpsPort);
        } else if (sslEnabled) {
            throw new IllegalStateException("SSL证书配置失败，拒绝启动；请检查证书路径、格式、密码和别名");
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
            
            // 保留旧的类路径证书，同时允许每个节点使用独立的外部证书。
            String resourcePath = keyStorePath.trim();
            if (!resourcePath.startsWith("classpath:") && !resourcePath.startsWith("file:")) {
                LogUtil.logError(logger, "证书路径必须使用classpath:或file:前缀");
                return false;
            }
            Resource resource = new DefaultResourceLoader().getResource(resourcePath);
            if (!resource.exists()) {
                LogUtil.logError(logger, "KeyStore文件不存在: {}", keyStorePath);
                return false;
            }
            
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
            char[] password = keyStorePassword == null ? new char[0] : keyStorePassword.toCharArray();
            try (InputStream input = resource.getInputStream()) {
                keyStore.load(input, password);
            }
            String selectedAlias = keyAlias;
            if (selectedAlias == null || selectedAlias.isEmpty()) {
                Enumeration<String> aliases = keyStore.aliases();
                while (aliases.hasMoreElements()) {
                    String candidate = aliases.nextElement();
                    if (keyStore.isKeyEntry(candidate)) {
                        if (selectedAlias != null && !selectedAlias.isEmpty()) {
                            throw new GeneralSecurityException("多个私钥条目需要明确配置别名");
                        }
                        selectedAlias = candidate;
                    }
                }
            }
            if (selectedAlias == null || !keyStore.isKeyEntry(selectedAlias)) {
                throw new GeneralSecurityException("未找到指定的私钥条目");
            }
            char[] privatePassword = keyPassword == null || keyPassword.isEmpty()
                    ? password : keyPassword.toCharArray();
            if (!(keyStore.getKey(selectedAlias, privatePassword) instanceof PrivateKey)) {
                throw new GeneralSecurityException("证书条目不包含可用私钥");
            }
            Certificate[] chain = keyStore.getCertificateChain(selectedAlias);
            if (chain == null || chain.length == 0) {
                throw new GeneralSecurityException("证书链为空");
            }
            for (Certificate certificate : chain) {
                ((X509Certificate) certificate).checkValidity();
            }
            
            LogUtil.logInfo(logger, "KeyStore验证成功，包含 {} 个条目", keyStore.size());
            return true;
            
        } catch (GeneralSecurityException | IOException e) {
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
        connector.setPort(httpPort);
        connector.setSecure(false);
        connector.setRedirectPort(httpsPort);
        
        return connector;
    }
}
