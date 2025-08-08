package com.example.cursorquitterweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableScheduling
@EnableTransactionManagement // 确保有这个注解
public class CursorQuitterWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(CursorQuitterWebApplication.class, args);
    }

} 