//package com.example.cursorquitterweb.controller;
//
//import com.example.cursorquitterweb.entity.User;
//import com.example.cursorquitterweb.service.UserService;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
//import org.springframework.boot.test.mock.mockito.MockBean;
//import org.springframework.http.MediaType;
//import org.springframework.test.web.servlet.MockMvc;
//
//import java.time.OffsetDateTime;
//import java.util.Optional;
//import java.util.UUID;
//
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.eq;
//import static org.mockito.Mockito.when;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
//
//@WebMvcTest(UserController.class)
//public class UserControllerTest {
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    @MockBean
//    private UserService userService;
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    @Test
//    public void testUpdateChallengeStartTime() throws Exception {
//        String userId = UUID.randomUUID().toString();
//        OffsetDateTime newStartTime = OffsetDateTime.now();
//
//        User mockUser = new User();
//        mockUser.setId(UUID.fromString(userId));
//        mockUser.setChallengeResetTime(newStartTime);
//
//        when(userService.updateChallengeStartTime(eq(userId), any(OffsetDateTime.class)))
//            .thenReturn(mockUser);
//
//        String requestBody = "{\"newStartTime\":\"" + newStartTime.toString() + "\"}";
//
//        mockMvc.perform(put("/api/users/" + userId + "/challenge-start-time")
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(requestBody))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.success").value(true))
//                .andExpect(jsonPath("$.message").value("挑战开始时间更新成功"));
//    }
//
//    @Test
//    public void testGetChallengeStartTime() throws Exception {
//        UUID userId = UUID.randomUUID();
//        OffsetDateTime challengeTime = OffsetDateTime.now();
//
//        User mockUser = new User();
//        mockUser.setId(userId);
//        mockUser.setChallengeResetTime(challengeTime);
//
//        when(userService.findById(userId)).thenReturn(Optional.of(mockUser));
//
//        mockMvc.perform(get("/api/users/" + userId + "/challenge-start-time"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.success").value(true))
//                .andExpect(jsonPath("$.message").value("获取挑战开始时间成功"));
//    }
//
//    @Test
//    public void testUpdateChallengeStartTimeUserNotFound() throws Exception {
//        String userId = UUID.randomUUID().toString();
//        OffsetDateTime newStartTime = OffsetDateTime.now();
//
//        when(userService.updateChallengeStartTime(eq(userId), any(OffsetDateTime.class)))
//            .thenThrow(new RuntimeException("用户不存在"));
//
//        String requestBody = "{\"newStartTime\":\"" + newStartTime.toString() + "\"}";
//
//        mockMvc.perform(put("/api/users/" + userId + "/challenge-start-time")
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(requestBody))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.success").value(false))
//                .andExpect(jsonPath("$.message").value("更新失败: 用户不存在"));
//    }
//
//    @Test
//    public void testGetChallengeStartTimeUserNotFound() throws Exception {
//        UUID userId = UUID.randomUUID();
//
//        when(userService.findById(userId)).thenReturn(Optional.empty());
//
//        mockMvc.perform(get("/api/users/" + userId + "/challenge-start-time"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.success").value(false))
//                .andExpect(jsonPath("$.message").value("用户不存在"));
//    }
//
//    @Test
//    public void testUpdateChallengeStartTimeInvalidTimeFormat() throws Exception {
//        String userId = UUID.randomUUID().toString();
//        String invalidTimeFormat = "invalid-time-format";
//
//        String requestBody = "{\"newStartTime\":\"" + invalidTimeFormat + "\"}";
//
//        mockMvc.perform(put("/api/users/" + userId + "/challenge-start-time")
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(requestBody))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.success").value(false))
//                .andExpect(jsonPath("$.message").value("时间格式无效，请使用ISO 8601格式（如：2024-01-15T10:00:00+08:00）"));
//    }
//}
