package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.dto.CreateChangeReasonRequest;
import com.example.cursorquitterweb.dto.UpdateChangeReasonRequest;
import com.example.cursorquitterweb.entity.ChangeReason;
import com.example.cursorquitterweb.repository.ChangeReasonRepository;
import com.example.cursorquitterweb.service.ChangeReasonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 用户戒色改变理由服务实现类
 */
@Service
@Transactional
public class ChangeReasonServiceImpl implements ChangeReasonService {
    
    @Autowired
    private ChangeReasonRepository changeReasonRepository;
    
    @Override
    public ChangeReason createChangeReason(CreateChangeReasonRequest request) {
        ChangeReason changeReason = new ChangeReason(UUID.fromString(request.getUserId()), request.getContent());
        return changeReasonRepository.save(changeReason);
    }
    
    @Override
    @Transactional(readOnly = true)
    public ChangeReason getChangeReasonById(UUID id) {
        return changeReasonRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("改变理由记录不存在，ID: " + id));
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<ChangeReason> getChangeReasonsByUserId(UUID userId) {
        return changeReasonRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public ChangeReason getLatestChangeReasonByUserId(UUID userId) {
        List<ChangeReason> latestReasons = changeReasonRepository.findLatestByUserId(userId);
        if (latestReasons.isEmpty()) {
            return null;
        }
        return latestReasons.get(0);
    }
    
    @Override
    public ChangeReason updateChangeReason(UpdateChangeReasonRequest request) {
        ChangeReason existingReason = getChangeReasonById(request.getId());
        existingReason.setContent(request.getContent());
        return changeReasonRepository.save(existingReason);
    }
    
    @Override
    public void deleteChangeReason(UUID id) {
        if (!changeReasonRepository.existsById(id)) {
            throw new RuntimeException("改变理由记录不存在，ID: " + id);
        }
        changeReasonRepository.deleteById(id);
    }
    
    @Override
    public void deleteChangeReasonsByUserId(UUID userId) {
        changeReasonRepository.deleteByUserId(userId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public long countChangeReasonsByUserId(UUID userId) {
        return changeReasonRepository.countByUserId(userId);
    }
}
