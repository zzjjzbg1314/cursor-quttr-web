package com.example.cursorquitterweb.config;

import org.hibernate.dialect.PostgreSQLDialect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.sql.Types;

/**
 * 数据库配置类
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.example.cursorquitterweb.repository")
@EnableTransactionManagement
public class DatabaseConfig {
    
    /**
     * 自定义PostgreSQL方言，支持UUID类型
     */
    @Bean
    public PostgreSQLDialect postgreSQLDialect() {
        return new PostgreSQLDialect() {
            @Override
            protected void registerColumnType(int code, String name) {
                super.registerColumnType(code, name);
                // 注册UUID类型
                registerColumnType(Types.OTHER, "uuid");
            }
        };
    }
}
