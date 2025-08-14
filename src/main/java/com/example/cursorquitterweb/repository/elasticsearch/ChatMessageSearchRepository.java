package com.example.cursorquitterweb.repository.elasticsearch;

import com.example.cursorquitterweb.entity.elasticsearch.ChatMessageDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 群聊消息的Elasticsearch搜索仓库
 */
@Repository
public interface ChatMessageSearchRepository extends ElasticsearchRepository<ChatMessageDocument, String> {

    /**
     * 根据消息类型查找消息
     */
    List<ChatMessageDocument> findByMsgType(String msgType);

    /**
     * 根据消息类型分页查找消息
     */
    Page<ChatMessageDocument> findByMsgType(String msgType, Pageable pageable);

    /**
     * 根据创建时间范围查找消息
     */
    List<ChatMessageDocument> findByCreateAtBetween(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 根据创建时间范围分页查找消息
     */
    Page<ChatMessageDocument> findByCreateAtBetween(LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    /**
     * 根据消息类型和创建时间范围查找消息
     */
    List<ChatMessageDocument> findByMsgTypeAndCreateAtBetween(String msgType, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 根据消息类型和创建时间范围分页查找消息
     */
    Page<ChatMessageDocument> findByMsgTypeAndCreateAtBetween(String msgType, LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    /**
     * 根据创建时间排序查找消息（最新消息在前）
     */
    List<ChatMessageDocument> findByOrderByCreateAtDesc();

    /**
     * 根据消息类型和创建时间排序查找消息（最新消息在前）
     */
    List<ChatMessageDocument> findByMsgTypeOrderByCreateAtDesc(String msgType);

    /**
     * 根据昵称查找消息
     */
    List<ChatMessageDocument> findByNickNameContaining(String nickName);

    /**
     * 根据用户阶段查找消息
     */
    List<ChatMessageDocument> findByUserStage(String userStage);

    /**
     * 根据内容关键词查找消息
     */
    List<ChatMessageDocument> findByContentContaining(String content);

    /**
     * 根据内容关键词分页查找消息
     */
    Page<ChatMessageDocument> findByContentContaining(String content, Pageable pageable);

    /**
     * 根据消息类型、内容关键词和创建时间范围查找消息
     */
    List<ChatMessageDocument> findByMsgTypeAndContentContainingAndCreateAtBetween(
        String msgType, String content, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 根据消息类型、内容关键词和创建时间范围分页查找消息
     */
    Page<ChatMessageDocument> findByMsgTypeAndContentContainingAndCreateAtBetween(
        String msgType, String content, LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);
}
