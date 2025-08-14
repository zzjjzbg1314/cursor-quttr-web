package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.entity.elasticsearch.ChatMessageDocument;
import com.example.cursorquitterweb.service.ChatMessageSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 群聊消息控制器
 */
@RestController
@RequestMapping("/api/chat-messages")
public class ChatMessageController {

    @Autowired
    private ChatMessageSearchService chatMessageSearchService;

    /**
     * 索引单条消息
     */
    @PostMapping
    public ApiResponse<ChatMessageDocument> indexMessage(@RequestBody ChatMessageDocument message) {
        try {
            ChatMessageDocument indexedMessage = chatMessageSearchService.indexMessage(message);
            return ApiResponse.success(indexedMessage);
        } catch (Exception e) {
            return ApiResponse.error("索引消息失败: " + e.getMessage());
        }
    }

    /**
     * 批量索引消息
     */
    @PostMapping("/batch")
    public ApiResponse<Iterable<ChatMessageDocument>> indexMessages(@RequestBody List<ChatMessageDocument> messages) {
        try {
            Iterable<ChatMessageDocument> indexedMessages = chatMessageSearchService.indexMessages(messages);
            return ApiResponse.success(indexedMessages);
        } catch (Exception e) {
            return ApiResponse.error("批量索引消息失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID查找消息
     */
    @GetMapping("/{id}")
    public ApiResponse<ChatMessageDocument> findMessageById(@PathVariable String id) {
        try {
            ChatMessageDocument message = chatMessageSearchService.findMessageById(id);
            if (message != null) {
                return ApiResponse.success(message);
            } else {
                return ApiResponse.error("消息不存在");
            }
        } catch (Exception e) {
            return ApiResponse.error("查找消息失败: " + e.getMessage());
        }
    }

    /**
     * 根据消息类型查找消息
     */
    @GetMapping("/type/{msgType}")
    public ApiResponse<List<ChatMessageDocument>> findMessagesByType(@PathVariable String msgType) {
        try {
            List<ChatMessageDocument> messages = chatMessageSearchService.findMessagesByType(msgType);
            return ApiResponse.success(messages);
        } catch (Exception e) {
            return ApiResponse.error("根据类型查找消息失败: " + e.getMessage());
        }
    }

    /**
     * 根据消息类型分页查找消息
     */
    @GetMapping("/type/{msgType}/page")
    public ApiResponse<Page<ChatMessageDocument>> findMessagesByTypePage(
            @PathVariable String msgType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<ChatMessageDocument> messages = chatMessageSearchService.findMessagesByType(msgType, pageable);
            return ApiResponse.success(messages);
        } catch (Exception e) {
            return ApiResponse.error("分页查找消息失败: " + e.getMessage());
        }
    }

    /**
     * 根据时间范围查找消息
     */
    @GetMapping("/time-range")
    public ApiResponse<List<ChatMessageDocument>> findMessagesByTimeRange(
            @RequestParam String startTime,
            @RequestParam String endTime) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime start = LocalDateTime.parse(startTime, formatter);
            LocalDateTime end = LocalDateTime.parse(endTime, formatter);
            
            List<ChatMessageDocument> messages = chatMessageSearchService.findMessagesByTimeRange(start, end);
            return ApiResponse.success(messages);
        } catch (Exception e) {
            return ApiResponse.error("根据时间范围查找消息失败: " + e.getMessage());
        }
    }

    /**
     * 根据消息类型和时间范围查找消息
     */
    @GetMapping("/type/{msgType}/time-range")
    public ApiResponse<List<ChatMessageDocument>> findMessagesByTypeAndTimeRange(
            @PathVariable String msgType,
            @RequestParam String startTime,
            @RequestParam String endTime) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime start = LocalDateTime.parse(startTime, formatter);
            LocalDateTime end = LocalDateTime.parse(endTime, formatter);
            
            List<ChatMessageDocument> messages = chatMessageSearchService.findMessagesByTypeAndTimeRange(msgType, start, end);
            return ApiResponse.success(messages);
        } catch (Exception e) {
            return ApiResponse.error("根据类型和时间范围查找消息失败: " + e.getMessage());
        }
    }

    /**
     * 根据内容关键词搜索消息
     */
    @GetMapping("/search")
    public ApiResponse<List<ChatMessageDocument>> searchMessagesByContent(@RequestParam String content) {
        try {
            List<ChatMessageDocument> messages = chatMessageSearchService.searchMessagesByContent(content);
            return ApiResponse.success(messages);
        } catch (Exception e) {
            return ApiResponse.error("搜索消息失败: " + e.getMessage());
        }
    }

    /**
     * 复合搜索：消息类型 + 内容关键词 + 时间范围
     */
    @GetMapping("/search/advanced")
    public ApiResponse<List<ChatMessageDocument>> advancedSearch(
            @RequestParam(required = false) String msgType,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        try {
            LocalDateTime start = null;
            LocalDateTime end = null;
            
            if (startTime != null && endTime != null) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                start = LocalDateTime.parse(startTime, formatter);
                end = LocalDateTime.parse(endTime, formatter);
            }
            
            List<ChatMessageDocument> messages = chatMessageSearchService.searchMessages(msgType, content, start, end);
            return ApiResponse.success(messages);
        } catch (Exception e) {
            return ApiResponse.error("高级搜索失败: " + e.getMessage());
        }
    }

    /**
     * 获取最新消息
     */
    @GetMapping("/latest")
    public ApiResponse<List<ChatMessageDocument>> getLatestMessages(@RequestParam(defaultValue = "10") int limit) {
        try {
            List<ChatMessageDocument> messages = chatMessageSearchService.getLatestMessages(limit);
            return ApiResponse.success(messages);
        } catch (Exception e) {
            return ApiResponse.error("获取最新消息失败: " + e.getMessage());
        }
    }

    /**
     * 根据消息类型获取最新消息
     */
    @GetMapping("/latest/type/{msgType}")
    public ApiResponse<List<ChatMessageDocument>> getLatestMessagesByType(
            @PathVariable String msgType,
            @RequestParam(defaultValue = "10") int limit) {
        try {
            List<ChatMessageDocument> messages = chatMessageSearchService.getLatestMessagesByType(msgType, limit);
            return ApiResponse.success(messages);
        } catch (Exception e) {
            return ApiResponse.error("获取最新消息失败: " + e.getMessage());
        }
    }

    /**
     * 检查索引是否存在
     */
    @GetMapping("/index/exists")
    public ApiResponse<Boolean> checkIndexExists() {
        try {
            boolean exists = chatMessageSearchService.indexExists();
            return ApiResponse.success(exists);
        } catch (Exception e) {
            return ApiResponse.error("检查索引状态失败: " + e.getMessage());
        }
    }

    /**
     * 创建索引
     */
    @PostMapping("/index/create")
    public ApiResponse<Boolean> createIndex() {
        try {
            boolean created = chatMessageSearchService.createIndex();
            return ApiResponse.success(created);
        } catch (Exception e) {
            return ApiResponse.error("创建索引失败: " + e.getMessage());
        }
    }
}
