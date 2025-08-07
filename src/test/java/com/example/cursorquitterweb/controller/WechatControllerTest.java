//package com.example.cursorquitterweb.controller;
//
//import com.example.cursorquitterweb.dto.WechatLoginRequest;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.http.MediaType;
//import org.springframework.test.web.servlet.MockMvc;
//
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//
//@SpringBootTest
//@AutoConfigureWebMvc
//class WechatControllerTest {
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    @Test
//    void testWechatLoginWithValidCode() throws Exception {
//        WechatLoginRequest request = new WechatLoginRequest("test_code");
//
//        mockMvc.perform(post("/api/wechat/login")
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().isOk());
//    }
//
//    @Test
//    void testWechatLoginWithEmptyCode() throws Exception {
//        WechatLoginRequest request = new WechatLoginRequest("");
//
//        mockMvc.perform(post("/api/wechat/login")
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().isBadRequest());
//    }
//
//    @Test
//    void testWechatLoginWithNullCode() throws Exception {
//        WechatLoginRequest request = new WechatLoginRequest(null);
//
//        mockMvc.perform(post("/api/wechat/login")
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(objectMapper.writeValueAsString(request)))
//                .andExpect(status().isBadRequest());
//    }
//}