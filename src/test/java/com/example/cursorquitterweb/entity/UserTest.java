//package com.example.cursorquitterweb.entity;
//
//import org.junit.jupiter.api.Test;
//import static org.junit.jupiter.api.Assertions.*;
//
///**
// * User实体类测试
// */
//public class UserTest {
//
//    @Test
//    public void testPhoneNumberField() {
//        User user = new User("测试用户");
//
//        // 测试手机号字段的默认值
//        assertNull(user.getPhoneNumber());
//
//        // 测试设置手机号
//        user.setPhoneNumber("13800138000");
//        assertEquals("13800138000", user.getPhoneNumber());
//
//        // 测试清空手机号
//        user.setPhoneNumber(null);
//        assertNull(user.getPhoneNumber());
//    }
//
//    @Test
//    public void testQuitReasonField() {
//        User user = new User("测试用户");
//
//        // 测试戒色原因字段的默认值
//        assertNull(user.getQuitReason());
//
//        // 测试设置戒色原因
//        user.setQuitReason("为了健康生活");
//        assertEquals("为了健康生活", user.getQuitReason());
//
//        // 测试清空戒色原因
//        user.setQuitReason(null);
//        assertNull(user.getQuitReason());
//    }
//
//    @Test
//    public void testInitUser() {
//        User user = User.initUser();
//
//        // 验证基础字段
//        assertEquals("新用户", user.getNickname());
//        assertEquals("https://example.com/default-avatar.jpg", user.getAvatarUrl());
//        assertEquals((short) 0, user.getGender());
//        assertEquals("zh_CN", user.getLanguage());
//        assertEquals(1, user.getBestRecord());
//        assertNull(user.getQuitReason());
//
//        // 验证手机号字段不初始化
//        assertNull(user.getPhoneNumber());
//    }
//
//    @Test
//    public void testToStringWithPhoneNumber() {
//        User user = new User("测试用户");
//        user.setPhoneNumber("13800138000");
//
//        String userString = user.toString();
//
//        // 验证toString包含手机号字段
//        assertTrue(userString.contains("phoneNumber='13800138000'"));
//    }
//
//    @Test
//    public void testToStringWithQuitReason() {
//        User user = new User("测试用户");
//        user.setQuitReason("为了健康生活");
//
//        String userString = user.toString();
//
//        // 验证toString包含戒色原因字段
//        assertTrue(userString.contains("quitReason='为了健康生活'"));
//    }
//}
