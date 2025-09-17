package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.entity.AppBlockSchedule;
import com.example.cursorquitterweb.repository.AppBlockScheduleRepository;
import com.example.cursorquitterweb.service.AppBlockScheduleService;
import com.example.cursorquitterweb.dto.CreateAppBlockScheduleRequest;
import com.example.cursorquitterweb.dto.UpdateAppBlockScheduleRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 应用屏蔽计划服务实现类
 */
@Service
@Transactional
public class AppBlockScheduleServiceImpl implements AppBlockScheduleService {
    
    @Autowired
    private AppBlockScheduleRepository appBlockScheduleRepository;
    
    @Override
    public Optional<AppBlockSchedule> findById(UUID id) {
        return appBlockScheduleRepository.findById(id);
    }
    
    @Override
    public AppBlockSchedule save(AppBlockSchedule appBlockSchedule) {
        return appBlockScheduleRepository.save(appBlockSchedule);
    }
    
    @Override
    public AppBlockSchedule createAppBlockSchedule(CreateAppBlockScheduleRequest request) {
        AppBlockSchedule appBlockSchedule = new AppBlockSchedule(
            request.getTitle(),
            request.getSubtitle(),
            request.getDays(),
            request.getTime(),
            request.getReason(),
            request.getImage()
        );
        return appBlockScheduleRepository.save(appBlockSchedule);
    }
    
    @Override
    public AppBlockSchedule updateAppBlockSchedule(UUID id, UpdateAppBlockScheduleRequest request) {
        Optional<AppBlockSchedule> existingScheduleOpt = appBlockScheduleRepository.findById(id);
        if (!existingScheduleOpt.isPresent()) {
            throw new RuntimeException("屏蔽计划不存在，ID: " + id);
        }
        
        AppBlockSchedule existingSchedule = existingScheduleOpt.get();
        
        if (request.getTitle() != null) {
            existingSchedule.setTitle(request.getTitle());
        }
        if (request.getSubtitle() != null) {
            existingSchedule.setSubtitle(request.getSubtitle());
        }
        if (request.getDays() != null) {
            existingSchedule.setDays(request.getDays());
        }
        if (request.getTime() != null) {
            existingSchedule.setTime(request.getTime());
        }
        if (request.getReason() != null) {
            existingSchedule.setReason(request.getReason());
        }
        if (request.getImage() != null) {
            existingSchedule.setImage(request.getImage());
        }
        
        return appBlockScheduleRepository.save(existingSchedule);
    }
    
    @Override
    public void deleteAppBlockSchedule(UUID id) {
        if (!appBlockScheduleRepository.existsById(id)) {
            throw new RuntimeException("屏蔽计划不存在，ID: " + id);
        }
        appBlockScheduleRepository.deleteById(id);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<AppBlockSchedule> getAllAppBlockSchedules() {
        return appBlockScheduleRepository.findAll();
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<AppBlockSchedule> searchByTitle(String title) {
        return appBlockScheduleRepository.findByTitleContainingIgnoreCase(title);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<AppBlockSchedule> searchByReason(String reason) {
        return appBlockScheduleRepository.findByReasonContainingIgnoreCase(reason);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<AppBlockSchedule> findByDays(String day) {
        return appBlockScheduleRepository.findByDaysContaining(day);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<AppBlockSchedule> findByTime(String timeRange) {
        return appBlockScheduleRepository.findByTimeContaining(timeRange);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<AppBlockSchedule> findByTitleAndReason(String title, String reason) {
        return appBlockScheduleRepository.findByTitleAndReason(title, reason);
    }
    
    @Override
    @Transactional(readOnly = true)
    public long countByReason(String reason) {
        return appBlockScheduleRepository.countByReason(reason);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<String> getDistinctReasons() {
        return appBlockScheduleRepository.findDistinctReasons();
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<AppBlockSchedule> findByIds(List<UUID> ids) {
        return appBlockScheduleRepository.findByIdIn(ids);
    }
}
