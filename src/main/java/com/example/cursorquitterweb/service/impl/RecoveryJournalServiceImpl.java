package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.entity.RecoveryJournal;
import com.example.cursorquitterweb.entity.User;
import com.example.cursorquitterweb.dto.RecoveryJournalDto;
import com.example.cursorquitterweb.repository.RecoveryJournalRepository;
import com.example.cursorquitterweb.repository.UserRepository;
import com.example.cursorquitterweb.service.RecoveryJournalService;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 康复日记服务实现类
 */
@Service
public class RecoveryJournalServiceImpl implements RecoveryJournalService {
    
    private static final Logger logger = LogUtil.getLogger(RecoveryJournalServiceImpl.class);
    
    @Autowired
    private RecoveryJournalRepository recoveryJournalRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Override
    @Transactional(readOnly = true)
    public Optional<RecoveryJournal> findById(UUID id) {
        logger.debug("查找康复日记，ID: {}", id);
        return recoveryJournalRepository.findById(id);
    }
    
    @Override
    @Transactional
    public RecoveryJournal save(RecoveryJournal recoveryJournal) {
        logger.debug("保存康复日记: {}", recoveryJournal);
        return recoveryJournalRepository.save(recoveryJournal);
    }
    
    @Override
    @Transactional
    public RecoveryJournal createJournal(UUID userId, String title, String content) {
        logger.info("创建康复日记，用户ID: {}, 标题: {}", userId, title);
        
        // 验证用户是否存在
        Optional<User> userOpt = userRepository.findById(userId);
        if (!userOpt.isPresent()) {
            throw new IllegalArgumentException("用户不存在，ID: " + userId);
        }
        
        RecoveryJournal journal = new RecoveryJournal(userId, title, content);
        // 不设置ID，让数据库自动生成
        RecoveryJournal savedJournal = recoveryJournalRepository.save(journal);
        
        logger.info("康复日记创建成功，ID: {}", savedJournal.getId());
        return savedJournal;
    }
    
    @Override
    @Transactional
    public RecoveryJournal updateJournal(UUID id, String title, String content) {
        logger.info("更新康复日记，ID: {}", id);
        
        Optional<RecoveryJournal> journalOpt = recoveryJournalRepository.findById(id);
        if (!journalOpt.isPresent()) {
            throw new IllegalArgumentException("康复日记不存在，ID: " + id);
        }
        
        RecoveryJournal journal = journalOpt.get();
        journal.setTitle(title);
        journal.setContent(content);
        journal.preUpdate(); // 自动更新updatedAt时间
        
        RecoveryJournal updatedJournal = recoveryJournalRepository.save(journal);
        logger.info("康复日记更新成功，ID: {}", id);
        return updatedJournal;
    }
    
    @Override
    @Transactional
    public void deleteJournal(UUID id) {
        logger.info("删除康复日记，ID: {}", id);
        
        if (!recoveryJournalRepository.existsById(id)) {
            throw new IllegalArgumentException("康复日记不存在，ID: " + id);
        }
        
        recoveryJournalRepository.deleteById(id);
        logger.info("康复日记删除成功，ID: {}", id);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<RecoveryJournalDto> findByUserId(UUID userId) {
        logger.debug("查询用户康复日记列表，用户ID: {}", userId);
        
        List<RecoveryJournal> journals = recoveryJournalRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return convertToDtoList(journals);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Page<RecoveryJournalDto> findByUserId(UUID userId, Pageable pageable) {
        logger.debug("分页查询用户康复日记，用户ID: {}, 分页: {}", userId, pageable);
        
        Page<RecoveryJournal> journalPage = recoveryJournalRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return journalPage.map(this::convertToDto);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<RecoveryJournalDto> findByUserIdAndDateRange(UUID userId, OffsetDateTime startTime, OffsetDateTime endTime) {
        logger.debug("查询用户康复日记时间范围，用户ID: {}, 开始时间: {}, 结束时间: {}", userId, startTime, endTime);
        
        List<RecoveryJournal> journals = recoveryJournalRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(userId, startTime, endTime);
        return convertToDtoList(journals);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<RecoveryJournalDto> searchByKeyword(UUID userId, String keyword) {
        logger.debug("关键词搜索康复日记，用户ID: {}, 关键词: {}", userId, keyword);
        
        if (keyword == null || keyword.trim().isEmpty()) {
            return findByUserId(userId);
        }
        
        List<RecoveryJournal> journals = recoveryJournalRepository.findByUserIdAndKeywordSearch(userId, keyword.trim());
        return convertToDtoList(journals);
    }
    
    @Override
    @Transactional(readOnly = true)
    public long countByUserId(UUID userId) {
        logger.debug("统计用户康复日记数量，用户ID: {}", userId);
        return recoveryJournalRepository.countByUserId(userId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public long countByUserIdAndDateRange(UUID userId, OffsetDateTime startTime, OffsetDateTime endTime) {
        logger.debug("统计用户康复日记时间范围数量，用户ID: {}, 开始时间: {}, 结束时间: {}", userId, startTime, endTime);
        return recoveryJournalRepository.countByUserIdAndCreatedAtBetween(userId, startTime, endTime);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<RecoveryJournalDto> getLatestJournal(UUID userId) {
        logger.debug("获取用户最新康复日记，用户ID: {}", userId);
        
        RecoveryJournal latestJournal = recoveryJournalRepository.findFirstByUserIdOrderByCreatedAtDesc(userId);
        if (latestJournal != null) {
            return Optional.of(convertToDto(latestJournal));
        }
        return Optional.empty();
    }
    
    @Override
    @Transactional(readOnly = true)
    public RecoveryJournalTimeRange getJournalTimeRange(UUID userId) {
        logger.debug("获取用户康复日记时间范围，用户ID: {}", userId);
        
        OffsetDateTime earliestDate = recoveryJournalRepository.findEarliestCreatedAtByUserId(userId);
        OffsetDateTime latestDate = recoveryJournalRepository.findLatestCreatedAtByUserId(userId);
        long totalCount = recoveryJournalRepository.countByUserId(userId);
        
        return new RecoveryJournalTimeRange(earliestDate, latestDate, totalCount);
    }
    
    /**
     * 将实体转换为DTO
     */
    private RecoveryJournalDto convertToDto(RecoveryJournal journal) {
        // 获取用户信息
        Optional<User> userOpt = userRepository.findById(journal.getUserId());
        String nickname = userOpt.map(User::getNickname).orElse("未知用户");
        String avatarUrl = userOpt.map(User::getAvatarUrl).orElse("");
        
        return new RecoveryJournalDto(
                journal.getId(),
                journal.getUserId(),
                nickname,
                avatarUrl,
                journal.getTitle(),
                journal.getContent(),
                journal.getCreatedAt(),
                journal.getUpdatedAt()
        );
    }
    
    /**
     * 将实体列表转换为DTO列表
     */
    private List<RecoveryJournalDto> convertToDtoList(List<RecoveryJournal> journals) {
        return journals.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
}
