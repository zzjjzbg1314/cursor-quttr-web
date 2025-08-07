package com.example.cursorquitterweb.config;

import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * HTTP到HTTPS重定向配置
 */
@Configuration
public class HttpToHttpsRedirectConfig {
    
    private static final Logger logger = LogUtil.getLogger(HttpToHttpsRedirectConfig.class);
    
    @Value("${server.port:443}")
    private int httpsPort;
    
    @Value("${server.ssl.enabled:false}")
    private boolean sslEnabled;
    
    /**
     * 配置Tomcat服务器，支持HTTP到HTTPS重定向
     */
    @Bean
    public ServletWebServerFactory servletContainer() {
        TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory();
        
        // 只有在SSL启用时才添加HTTP重定向
        if (sslEnabled) {
            // 添加HTTP连接器，重定向到HTTPS
            tomcat.addAdditionalTomcatConnectors(createHttpConnector());
            LogUtil.logInfo(logger, "配置HTTP到HTTPS重定向，HTTPS端口: {}", httpsPort);
        } else {
            LogUtil.logInfo(logger, "SSL未启用，跳过HTTP重定向配置");
        }
        
        return tomcat;
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