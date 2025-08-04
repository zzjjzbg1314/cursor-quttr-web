package com.example.cursorquitterweb.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HelloController {

    @GetMapping("/hello")
    public String hello() {
        return "Hello, Spring Boot! 欢迎使用Java 8版本的Spring Boot项目";
    }

    @GetMapping("/health")
    public String health() {
        return "应用运行正常";
    }
} 