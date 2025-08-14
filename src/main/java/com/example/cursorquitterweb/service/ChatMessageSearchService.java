package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.entity.elasticsearch.ChatMessageDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 群聊消息搜索服务接口
 */
public interface ChatMessageSearchService {

    /**
     * 索引单条消息
     */
    ChatMessageDocument indexMessage(ChatMessageDocument message);

    /**
     * 保存单条消息（只返回成功/失败状态）
     */
    boolean saveMessage(ChatMessageDocument message);

    /**
     * 批量索引消息
     */
    Iterable<ChatMessageDocument> indexMessages(List<ChatMessageDocument> messages);

    /**
     * 批量保存消息（只返回成功/失败状态）
     */
    boolean saveMessages(List<ChatMessageDocument> messages);

    /**
     * 根据ID查找消息
     */
    ChatMessageDocument findMessageById(String id);

    /**
     * 根据消息类型查找消息
     */
    List<ChatMessageDocument> findMessagesByType(String msgType);

    /**
     * 根据消息类型分页查找消息
     */
    Page<ChatMessageDocument> findMessagesByType(String msgType, Pageable pageable);

    /**
     * 根据时间范围查找消息
     */
    List<ChatMessageDocument> findMessagesByTimeRange(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 根据时间范围分页查找消息
     */
    Page<ChatMessageDocument> findMessagesByTimeRange(LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    /**
     * 根据消息类型和时间范围查找消息
     */
    List<ChatMessageDocument> findMessagesByTypeAndTimeRange(String msgType, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 根据消息类型和时间范围分页查找消息
     */
    Page<ChatMessageDocument> findMessagesByTypeAndTimeRange(String msgType, LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    /**
     * 根据内容关键词搜索消息
     */
    List<ChatMessageDocument> searchMessagesByContent(String content);

    /**
     * 根据内容关键词分页搜索消息
     */
    Page<ChatMessageDocument> searchMessagesByContent(String content, Pageable pageable);

    /**
     * 根据昵称查找消息
     */
    List<ChatMessageDocument> findMessagesByNickName(String nickName);

    /**
     * 根据用户阶段查找消息
     */
    List<ChatMessageDocument> findMessagesByUserStage(String userStage);

    /**
     * 复合搜索：消息类型 + 内容关键词 + 时间范围
     */
    List<ChatMessageDocument> searchMessages(String msgType, String content, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 复合搜索：消息类型 + 内容关键词 + 时间范围（分页）
     */
    Page<ChatMessageDocument> searchMessages(String msgType, String content, LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    /**
     * 获取最新消息
     */
    List<ChatMessageDocument> getLatestMessages(int limit);

    /**
     * 根据消息类型获取最新消息
     */
    List<ChatMessageDocument> getLatestMessagesByType(String msgType, int limit);

    /**
     * 更新消息
     */
    ChatMessageDocument updateMessage(ChatMessageDocument message);

    /**
     * 删除消息
     */
    void deleteMessage(String id);

    /**
     * 根据消息类型删除消息
     */
    void deleteMessagesByType(String msgType);

    /**
     * 根据时间范围删除消息
     */
    void deleteMessagesByTimeRange(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 检查索引是否存在
     */
    boolean indexExists();

    /**
     * 创建索引
     */
    boolean createIndex();

    /**
     * 删除索引
     */
    boolean deleteIndex();
}
