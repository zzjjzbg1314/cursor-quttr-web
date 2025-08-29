//package com.example.cursorquitterweb.util;
//
//import org.junit.jupiter.api.Test;
//import org.slf4j.Logger;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//class LogUtilTest {
//
//    @Test
//    void testGetLogger() {
//        Logger logger = LogUtil.getLogger(LogUtilTest.class);
//        assertNotNull(logger);
//
//        Logger loggerByName = LogUtil.getLogger("test.logger");
//        assertNotNull(loggerByName);
//    }
//
//    @Test
//    void testLogLevels() {
//        Logger logger = LogUtil.getLogger(LogUtilTest.class);
//
//        // 测试各种日志级别方法
//        assertDoesNotThrow(() -> {
//            LogUtil.logDebug(logger, "Debug message");
//            LogUtil.logInfo(logger, "Info message");
//            LogUtil.logWarn(logger, "Warn message");
//            LogUtil.logError(logger, "Error message");
//        });
//    }
//
//    @Test
//    void testLogWithParameters() {
//        Logger logger = LogUtil.getLogger(LogUtilTest.class);
//
//        assertDoesNotThrow(() -> {
//            LogUtil.logInfo(logger, "User {} logged in at {}", "testuser", "2024-01-01");
//            LogUtil.logError(logger, "Error occurred: {}", "test error");
//        });
//    }
//}