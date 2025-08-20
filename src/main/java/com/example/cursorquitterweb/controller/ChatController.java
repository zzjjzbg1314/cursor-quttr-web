//package com.example.cursorquitterweb.controller;
//
//import com.example.cursorquitterweb.dto.ApiResponse;
//import com.example.cursorquitterweb.dto.ChatMessageRequest;
//import com.example.cursorquitterweb.entity.elasticsearch.ChatMessageDocument;
//import com.example.cursorquitterweb.service.ChatService;
//import com.example.cursorquitterweb.service.ChatMessageSearchService;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.data.domain.Pageable;
//import org.springframework.data.domain.Sort;
//import org.springframework.format.annotation.DateTimeFormat;
//import org.springframework.validation.annotation.Validated;
//import org.springframework.web.bind.annotation.*;
//
//import javax.servlet.http.HttpServletRequest;
//import javax.validation.Valid;
//import java.time.LocalDateTime;
//import java.util.List;
//import java.util.Collections;
//
///**
// * 聊天控制器
// */
//@RestController
//@RequestMapping("/api/chat")
//@Validated
//public class ChatController {
//
//    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
//
//    @Autowired
//    private ChatService chatService;
//
//    @Autowired
//    private ChatMessageSearchService chatMessageSearchService;
//
//    /**
//     * 发送聊天消息
//     * @param request 聊天消息请求
//     * @param httpRequest HTTP请求对象
//     * @return API响应
//     */
//    @PostMapping("/sendMsg")
//    public ApiResponse<String> sendMessage(@Valid @RequestBody ChatMessageRequest request,
//                                         HttpServletRequest httpRequest) {
//        try {
//            // 获取客户端IP地址
//            String ipAddress = getClientIpAddress(httpRequest);
//            logger.info("收到聊天消息请求: 用户={}, 阶段={}, 类型={}, 内容={}, IP={}",
//                       request.getNickName(), request.getUserStage(),
//                       request.getMsgType(), request.getContent(), ipAddress);
//
//            // 发送消息
//            boolean success = chatService.sendChatMessage(request);
//
//            if (success) {
//                return ApiResponse.success("消息发送成功");
//            } else {
//                return ApiResponse.error("消息发送失败");
//            }
//
//        } catch (Exception e) {
//            logger.error("发送聊天消息时发生错误", e);
//            return ApiResponse.error("发送消息失败: " + e.getMessage());
//        }
//    }
//
//    /**
//     * 获取客户端真实IP地址
//     */
//    private String getClientIpAddress(HttpServletRequest request) {
//        String xForwardedFor = request.getHeader("X-Forwarded-For");
//        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
//            return xForwardedFor.split(",")[0].trim();
//        }
//
//        String xRealIp = request.getHeader("X-Real-IP");
//        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
//            return xRealIp;
//        }
//
//        return request.getRemoteAddr();
//    }
//
//    /**
//     * 获取聊天功能信息
//     */
//    @GetMapping("/info")
//    public ApiResponse<Object> getChatInfo() {
//        return ApiResponse.success("群组聊天功能已启用，支持实时消息广播");
//    }
//
//    /**
//     * 发送聊天消息并存储到Elasticsearch索引
//     * @param request 聊天消息请求
//     * @return API响应
//     */
//    @PostMapping("/sendToIndex")
//    public ApiResponse<String> sendChatMessage(@Valid @RequestBody ChatMessageRequest request) {
//        try {
//            logger.info("收到聊天消息索引请求: 用户={}, 阶段={}, 类型={}, 内容={}",
//                       request.getNickName(), request.getUserStage(),
//                       request.getMsgType(), request.getContent());
//
//            // 发送消息到Elasticsearch索引
//            boolean success = chatService.sendChatMessage(request);
//
//            if (success) {
//                return ApiResponse.success("消息已成功存储到Elasticsearch索引");
//            } else {
//                return ApiResponse.error("消息存储到Elasticsearch索引失败");
//            }
//
//        } catch (Exception e) {
//            logger.error("存储聊天消息到Elasticsearch索引时发生错误", e);
//            return ApiResponse.error("存储消息失败: " + e.getMessage());
//        }
//    }
//
//    /**
//     * 分页查询所有聊天消息
//     * @param page 页码（从0开始）
//     * @param size 每页大小
//     * @param sort 排序字段（默认按创建时间倒序）
//     * @return 分页聊天消息数据
//     */
//    @GetMapping("/messages")
//    public ApiResponse<Page<ChatMessageDocument>> getChatMessages(
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(defaultValue = "20") int size,
//            @RequestParam(defaultValue = "createAt") String sort) {
//        try {
//            logger.info("分页查询聊天消息: page={}, size={}, sort={}", page, size, sort);
//
//            // 创建分页和排序对象
//            Sort sortObj = Sort.by(Sort.Direction.DESC, sort);
//            Pageable pageable = PageRequest.of(page, size, sortObj);
//
//            // 查询所有消息（这里需要实现一个查询所有消息的方法）
//            // 由于接口中没有查询所有消息的方法，我们可以使用时间范围查询来模拟
//            LocalDateTime endTime = LocalDateTime.now();
//            LocalDateTime startTime = endTime.minusDays(30); // 默认查询最近30天的消息
//
//            Page<ChatMessageDocument> messages = chatMessageSearchService.findMessagesByTimeRange(startTime, endTime, pageable);
//
//            return ApiResponse.success(messages);
//
//        } catch (Exception e) {
//            logger.error("分页查询聊天消息时发生错误", e);
//            return ApiResponse.error("查询失败: " + e.getMessage());
//        }
//    }
//
//    /**
//     * 根据消息类型分页查询聊天消息
//     * @param msgType 消息类型
//     * @param page 页码（从0开始）
//     * @param size 每页大小
//     * @param sort 排序字段（默认按创建时间倒序）
//     * @return 分页聊天消息数据
//     */
//    @GetMapping("/messages/type/{msgType}")
//    public ApiResponse<Page<ChatMessageDocument>> getChatMessagesByType(
//            @PathVariable String msgType,
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(defaultValue = "20") int size,
//            @RequestParam(defaultValue = "createAt") String sort) {
//        try {
//            logger.info("根据类型分页查询聊天消息: msgType={}, page={}, size={}, sort={}", msgType, page, size, sort);
//
//            // 创建分页和排序对象
//            Sort sortObj = Sort.by(Sort.Direction.DESC, sort);
//            Pageable pageable = PageRequest.of(page, size, sortObj);
//
//            Page<ChatMessageDocument> messages = chatMessageSearchService.findMessagesByType(msgType, pageable);
//
//            return ApiResponse.success(messages);
//
//        } catch (Exception e) {
//            logger.error("根据类型分页查询聊天消息时发生错误", e);
//            return ApiResponse.error("查询失败: " + e.getMessage());
//        }
//    }
//
//    /**
//     * 根据时间范围分页查询聊天消息
//     * @param startTime 开始时间
//     * @param endTime 结束时间
//     * @param page 页码（从0开始）
//     * @param size 每页大小
//     * @param sort 排序字段（默认按创建时间倒序）
//     * @return 分页聊天消息数据
//     */
//    @GetMapping("/messages/timeRange")
//    public ApiResponse<Page<ChatMessageDocument>> getChatMessagesByTimeRange(
//            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
//            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(defaultValue = "20") int size,
//            @RequestParam(defaultValue = "createAt") String sort) {
//        try {
//            logger.info("根据时间范围分页查询聊天消息: startTime={}, endTime={}, page={}, size={}, sort={}",
//                       startTime, endTime, page, size, sort);
//
//            // 创建分页和排序对象
//            Sort sortObj = Sort.by(Sort.Direction.DESC, sort);
//            Pageable pageable = PageRequest.of(page, size, sortObj);
//
//            Page<ChatMessageDocument> messages = chatMessageSearchService.findMessagesByTimeRange(startTime, endTime, pageable);
//
//            return ApiResponse.success(messages);
//
//        } catch (Exception e) {
//            logger.error("根据时间范围分页查询聊天消息时发生错误", e);
//            return ApiResponse.error("查询失败: " + e.getMessage());
//        }
//    }
//
//    /**
//     * 根据内容关键词分页搜索聊天消息
//     * @param content 搜索内容
//     * @param page 页码（从0开始）
//     * @param size 每页大小
//     * @param sort 排序字段（默认按创建时间倒序）
//     * @return 分页聊天消息数据
//     */
//    @GetMapping("/messages/search")
//    public ApiResponse<Page<ChatMessageDocument>> searchChatMessagesByContent(
//            @RequestParam String content,
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(defaultValue = "20") int size,
//            @RequestParam(defaultValue = "createAt") String sort) {
//        try {
//            logger.info("根据内容关键词分页搜索聊天消息: content={}, page={}, size={}, sort={}", content, page, size, sort);
//
//            // 创建分页和排序对象
//            Sort sortObj = Sort.by(Sort.Direction.DESC, sort);
//            Pageable pageable = PageRequest.of(page, size, sortObj);
//
//            Page<ChatMessageDocument> messages = chatMessageSearchService.searchMessagesByContent(content, pageable);
//
//            return ApiResponse.success(messages);
//
//        } catch (Exception e) {
//            logger.error("根据内容关键词分页搜索聊天消息时发生错误", e);
//            return ApiResponse.error("搜索失败: " + e.getMessage());
//        }
//    }
//
//    /**
//     * 复合条件分页查询聊天消息
//     * @param msgType 消息类型（可选）
//     * @param content 搜索内容（可选）
//     * @param startTime 开始时间（可选）
//     * @param endTime 结束时间（可选）
//     * @param page 页码（从0开始）
//     * @param size 每页大小
//     * @param sort 排序字段（默认按创建时间倒序）
//     * @return 分页聊天消息数据
//     */
//    @GetMapping("/messages/advanced")
//    public ApiResponse<Page<ChatMessageDocument>> advancedSearchChatMessages(
//            @RequestParam(required = false) String msgType,
//            @RequestParam(required = false) String content,
//            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
//            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(defaultValue = "20") int size,
//            @RequestParam(defaultValue = "createAt") String sort) {
//        try {
//            logger.info("复合条件分页查询聊天消息: msgType={}, content={}, startTime={}, endTime={}, page={}, size={}, sort={}",
//                       msgType, content, startTime, endTime, page, size, sort);
//
//            // 创建分页和排序对象
//            Sort sortObj = Sort.by(Sort.Direction.DESC, sort);
//            Pageable pageable = PageRequest.of(page, size, sortObj);
//
//            Page<ChatMessageDocument> messages;
//
//            // 根据提供的参数选择查询方法
//            if (msgType != null && content != null && startTime != null && endTime != null) {
//                // 所有条件都有
//                messages = chatMessageSearchService.searchMessages(msgType, content, startTime, endTime, pageable);
//            } else if (msgType != null && startTime != null && endTime != null) {
//                // 消息类型 + 时间范围
//                messages = chatMessageSearchService.findMessagesByTypeAndTimeRange(msgType, startTime, endTime, pageable);
//            } else if (msgType != null) {
//                // 只有消息类型
//                messages = chatMessageSearchService.findMessagesByType(msgType, pageable);
//            } else if (startTime != null && endTime != null) {
//                // 只有时间范围
//                messages = chatMessageSearchService.findMessagesByTimeRange(startTime, endTime, pageable);
//            } else if (content != null) {
//                // 只有内容搜索
//                messages = chatMessageSearchService.searchMessagesByContent(content, pageable);
//            } else {
//                // 默认查询最近30天的消息
//                LocalDateTime defaultEndTime = LocalDateTime.now();
//                LocalDateTime defaultStartTime = defaultEndTime.minusDays(30);
//                messages = chatMessageSearchService.findMessagesByTimeRange(defaultStartTime, defaultEndTime, pageable);
//            }
//
//            return ApiResponse.success(messages);
//
//        } catch (Exception e) {
//            logger.error("复合条件分页查询聊天消息时发生错误", e);
//            return ApiResponse.error("查询失败: " + e.getMessage());
//        }
//    }
//
//    /**
//     * 获取最新聊天消息
//     * @param limit 限制数量（默认20条）
//     * @return 最新聊天消息列表
//     */
//    @GetMapping("/messages/latest")
//    public ApiResponse<List<ChatMessageDocument>> getLatestChatMessages(
//            @RequestParam(defaultValue = "50") int limit) {
//        try {
//            logger.info("获取最新聊天消息: limit={}", limit);
//
//            List<ChatMessageDocument> messages = chatMessageSearchService.getLatestMessages(limit);
//
//            return ApiResponse.success(messages);
//
//        } catch (Exception e) {
//            logger.error("获取最新聊天消息时发生错误", e);
//            return ApiResponse.error("查询失败: " + e.getMessage());
//        }
//    }
//
//    /**
//     * 根据消息类型获取最新聊天消息
//     * @param msgType 消息类型
//     * @param limit 限制数量（默认20条）
//     * @return 最新聊天消息列表
//     */
//    @GetMapping("/messages/latest/{msgType}")
//    public ApiResponse<List<ChatMessageDocument>> getLatestChatMessagesByType(
//            @PathVariable String msgType,
//            @RequestParam(defaultValue = "20") int limit) {
//        try {
//            logger.info("根据类型获取最新聊天消息: msgType={}, limit={}", msgType, limit);
//
//            List<ChatMessageDocument> messages = chatMessageSearchService.getLatestMessagesByType(msgType, limit);
//
//            return ApiResponse.success(messages);
//
//        } catch (Exception e) {
//            logger.error("根据类型获取最新聊天消息时发生错误", e);
//            return ApiResponse.error("查询失败: " + e.getMessage());
//        }
//    }
//
//    /**
//     * 根据昵称分页查询聊天消息
//     * @param nickName 昵称
//     * @param page 页码（从0开始）
//     * @param size 每页大小
//     * @return 分页聊天消息数据
//     */
//    @GetMapping("/messages/nickname/{nickName}")
//    public ApiResponse<List<ChatMessageDocument>> getChatMessagesByNickName(
//            @PathVariable String nickName,
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(defaultValue = "20") int size) {
//        try {
//            logger.info("根据昵称查询聊天消息: nickName={}, page={}, size={}", nickName, page, size);
//
//            List<ChatMessageDocument> messages = chatMessageSearchService.findMessagesByNickName(nickName);
//
//            // 手动分页处理（因为接口没有提供分页方法）
//            int start = page * size;
//            int end = Math.min(start + size, messages.size());
//
//            if (start >= messages.size()) {
//                return ApiResponse.success(Collections.emptyList());
//            }
//
//            List<ChatMessageDocument> pagedMessages = messages.subList(start, end);
//
//            return ApiResponse.success(pagedMessages);
//
//        } catch (Exception e) {
//            logger.error("根据昵称查询聊天消息时发生错误", e);
//            return ApiResponse.error("查询失败: " + e.getMessage());
//        }
//    }
//
//    /**
//     * 根据用户阶段分页查询聊天消息
//     * @param userStage 用户阶段
//     * @param page 页码（从0开始）
//     * @param size 每页大小
//     * @return 分页聊天消息数据
//     */
//    @GetMapping("/messages/stage/{userStage}")
//    public ApiResponse<List<ChatMessageDocument>> getChatMessagesByUserStage(
//            @PathVariable String userStage,
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(defaultValue = "20") int size) {
//        try {
//            logger.info("根据用户阶段查询聊天消息: userStage={}, page={}, size={}", userStage, page, size);
//
//            List<ChatMessageDocument> messages = chatMessageSearchService.findMessagesByUserStage(userStage);
//
//            // 手动分页处理（因为接口没有提供分页方法）
//            int start = page * size;
//            int end = Math.min(start + size, messages.size());
//
//            if (start >= messages.size()) {
//                return ApiResponse.success(Collections.emptyList());
//            }
//
//            List<ChatMessageDocument> pagedMessages = messages.subList(start, end);
//
//            return ApiResponse.success(pagedMessages);
//
//        } catch (Exception e) {
//            logger.error("根据用户阶段查询聊天消息时发生错误", e);
//            return ApiResponse.error("查询失败: " + e.getMessage());
//        }
//    }
//}
