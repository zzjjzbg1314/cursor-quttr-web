package com.example.cursorquitterweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CursorQuitterWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(CursorQuitterWebApplication.class, args);
    }

} 