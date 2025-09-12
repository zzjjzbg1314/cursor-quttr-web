package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.entity.RecoverJourney;
import com.example.cursorquitterweb.repository.RecoverJourneyRepository;
import com.example.cursorquitterweb.service.RecoverJourneyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 康复记录服务实现类
 */
@Service
@Transactional
public class RecoverJourneyServiceImpl implements RecoverJourneyService {
    
    @Autowired
    private RecoverJourneyRepository recoverJourneyRepository;
    
    @Override
    @Transactional(readOnly = true)
    public Optional<RecoverJourney> findById(UUID id) {
        return recoverJourneyRepository.findById(id);
    }
    
    @Override
    public RecoverJourney save(RecoverJourney recoverJourney) {
        return recoverJourneyRepository.save(recoverJourney);
    }
    
    @Override
    public RecoverJourney createRecoverJourney(String userId) {
        UUID uuid = UUID.fromString(userId);
        RecoverJourney recoverJourney = new RecoverJourney(uuid);
        return recoverJourneyRepository.save(recoverJourney);
    }
    
    @Override
    public RecoverJourney createRecoverJourney(String userId, String fellContent) {
        UUID uuid = UUID.fromString(userId);
        RecoverJourney recoverJourney = new RecoverJourney(uuid, fellContent);
        return recoverJourneyRepository.save(recoverJourney);
    }
    
    @Override
    public RecoverJourney createRecoverJourney(UUID userId) {
        RecoverJourney recoverJourney = new RecoverJourney(userId);
        return recoverJourneyRepository.save(recoverJourney);
    }
    
    @Override
    public RecoverJourney createRecoverJourney(UUID userId, String fellContent) {
        RecoverJourney recoverJourney = new RecoverJourney(userId, fellContent);
        return recoverJourneyRepository.save(recoverJourney);
    }
    
    @Override
    public RecoverJourney updateRecoverJourney(RecoverJourney recoverJourney) {
        return recoverJourneyRepository.save(recoverJourney);
    }
    
    @Override
    public void deleteRecoverJourney(UUID id) {
        recoverJourneyRepository.deleteById(id);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<RecoverJourney> findByUserId(UUID userId) {
        return recoverJourneyRepository.findByUserIdOrderByCreateAtDesc(userId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<RecoverJourney> findLatestByUserId(UUID userId) {
        return recoverJourneyRepository.findFirstByUserIdOrderByCreateAtDesc(userId);
    }
    
    @Override
    public RecoverJourney updateFellContent(UUID journeyId, String fellContent) {
        Optional<RecoverJourney> optional = recoverJourneyRepository.findById(journeyId);
        if (!optional.isPresent()) {
            throw new IllegalArgumentException("康复记录不存在");
        }
        
        RecoverJourney recoverJourney = optional.get();
        recoverJourney.setFellContent(fellContent);
        return recoverJourneyRepository.save(recoverJourney);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<RecoverJourney> findByCreateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime) {
        return recoverJourneyRepository.findByCreateAtBetween(startTime, endTime);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<RecoverJourney> findByUpdateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime) {
        return recoverJourneyRepository.findByUpdateAtBetween(startTime, endTime);
    }
    
    @Override
    @Transactional(readOnly = true)
    public long countByUserId(UUID userId) {
        return recoverJourneyRepository.countByUserId(userId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<RecoverJourney> findByFellContentContaining(String content) {
        return recoverJourneyRepository.findByFellContentContainingIgnoreCase(content);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<RecoverJourney> findByUserIdAndCreateAtBetween(UUID userId, OffsetDateTime startTime, OffsetDateTime endTime) {
        return recoverJourneyRepository.findByUserIdAndCreateAtBetween(userId, startTime, endTime);
    }
}
