package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.dto.BreatheDto;
import com.example.cursorquitterweb.entity.Breathe;
import com.example.cursorquitterweb.repository.BreatheRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 呼吸练习服务实现类
 */
@Service
@Transactional
public class BreatheService {
    
    @Autowired
    private BreatheRepository breatheRepository;
    
    /**
     * 根据ID查找呼吸练习
     */
    @Transactional(readOnly = true)
    public Optional<Breathe> findById(UUID id) {
        return breatheRepository.findById(id);
    }
    
    /**
     * 保存呼吸练习
     */
    public Breathe save(Breathe breathe) {
        return breatheRepository.save(breathe);
    }
    
    /**
     * 创建新呼吸练习
     */
    public Breathe createBreathe(String title, String time, String audiourl) {
        Breathe breathe = new Breathe(title, time, audiourl);
        return breatheRepository.save(breathe);
    }
    
    /**
     * 更新呼吸练习信息
     */
    public Breathe updateBreathe(Breathe breathe) {
        breathe.preUpdate(); // 更新修改时间
        return breatheRepository.save(breathe);
    }
    
    /**
     * 更新呼吸练习信息（通过ID）
     */
    public Breathe updateBreathe(UUID id, String title, String time, String audiourl) {
        Breathe breathe = breatheRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("呼吸练习不存在，ID: " + id));
        
        breathe.setTitle(title);
        breathe.setTime(time);
        breathe.setAudiourl(audiourl);
        
        return breatheRepository.save(breathe);
    }
    
    /**
     * 删除呼吸练习
     */
    public void deleteBreathe(UUID id) {
        if (!breatheRepository.existsById(id)) {
            throw new RuntimeException("呼吸练习不存在，ID: " + id);
        }
        breatheRepository.deleteById(id);
    }
    
    /**
     * 根据标题搜索呼吸练习
     */
    @Transactional(readOnly = true)
    public List<Breathe> searchByTitle(String title) {
        return breatheRepository.findByTitleContainingIgnoreCaseOrderByCreateAtDesc(title);
    }
    
    /**
     * 根据标题精确查询呼吸练习
     */
    @Transactional(readOnly = true)
    public Optional<Breathe> findByTitle(String title) {
        return breatheRepository.findByTitle(title);
    }
    
    /**
     * 根据创建时间范围查询呼吸练习
     */
    @Transactional(readOnly = true)
    public List<Breathe> findByCreateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime) {
        return breatheRepository.findByCreateAtBetweenOrderByCreateAtDesc(startTime, endTime);
    }
    
    /**
     * 根据更新时间范围查询呼吸练习
     */
    @Transactional(readOnly = true)
    public List<Breathe> findByUpdateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime) {
        return breatheRepository.findByUpdateAtBetweenOrderByUpdateAtDesc(startTime, endTime);
    }
    
    /**
     * 查询有音频链接的呼吸练习
     */
    @Transactional(readOnly = true)
    public List<Breathe> findBreatheWithAudiourl() {
        return breatheRepository.findByAudiourlIsNotNullOrderByCreateAtDesc();
    }
    
    /**
     * 统计指定时间范围内创建的呼吸练习数量
     */
    @Transactional(readOnly = true)
    public long countBreatheByCreateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime) {
        return breatheRepository.countByCreateAtBetween(startTime, endTime);
    }
    
    /**
     * 根据标题关键词搜索呼吸练习（支持中文全文搜索）
     */
    @Transactional(readOnly = true)
    public List<Breathe> searchBreatheByTitleKeyword(String keyword) {
        return breatheRepository.searchBreatheByTitleKeyword(keyword);
    }
    
    /**
     * 获取最新的呼吸练习列表（按创建时间降序）
     */
    @Transactional(readOnly = true)
    public List<Breathe> getLatestBreathe() {
        return breatheRepository.findLatestBreathe();
    }
    
    /**
     * 获取最新的呼吸练习列表（按创建时间降序，限制数量）
     */
    @Transactional(readOnly = true)
    public List<Breathe> getLatestBreathe(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return breatheRepository.findLatestBreathe(pageable);
    }
    
    /**
     * 分页查询呼吸练习列表（按创建时间降序）
     */
    @Transactional(readOnly = true)
    public Page<Breathe> getBreathePage(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return breatheRepository.findBreathePage(pageable);
    }
    
    /**
     * 根据标题模糊查询并分页
     */
    @Transactional(readOnly = true)
    public Page<Breathe> searchBreatheByTitlePage(String title, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return breatheRepository.findByTitleContainingIgnoreCasePage(title, pageable);
    }
    
    /**
     * 获取所有呼吸练习（按创建时间升序排列）
     */
    @Transactional(readOnly = true)
    public List<Breathe> getAllBreathe() {
        return breatheRepository.findAllByOrderByCreateAtAsc();
    }
    
    /**
     * 获取所有呼吸练习（分页）
     */
    @Transactional(readOnly = true)
    public Page<Breathe> getAllBreathe(Pageable pageable) {
        return breatheRepository.findAll(pageable);
    }
    
    /**
     * 检查呼吸练习标题是否已存在
     */
    @Transactional(readOnly = true)
    public boolean existsByTitle(String title) {
        return breatheRepository.findByTitle(title).isPresent();
    }
    
    /**
     * 根据音频链接查找呼吸练习
     */
    @Transactional(readOnly = true)
    public Optional<Breathe> findByAudiourl(String audiourl) {
        return breatheRepository.findByAudiourl(audiourl);
    }
    
    /**
     * 更新呼吸练习音频链接
     */
    public Breathe updateAudiourl(UUID id, String audiourl) {
        Optional<Breathe> breatheOpt = breatheRepository.findById(id);
        if (breatheOpt.isPresent()) {
            Breathe breathe = breatheOpt.get();
            breathe.setAudiourl(audiourl);
            return breatheRepository.save(breathe);
        }
        throw new RuntimeException("呼吸练习不存在");
    }
    
    /**
     * 统计呼吸练习总数
     */
    @Transactional(readOnly = true)
    public long count() {
        return breatheRepository.count();
    }
    
    /**
     * 转换为DTO
     */
    public BreatheDto convertToDto(Breathe breathe) {
        if (breathe == null) {
            return null;
        }
        return new BreatheDto(
                breathe.getId(),
                breathe.getTitle(),
                breathe.getTime(),
                breathe.getAudiourl(),
                breathe.getCreateAt(),
                breathe.getUpdateAt()
        );
    }
    
    /**
     * 批量转换为DTO
     */
    public List<BreatheDto> convertToDtoList(List<Breathe> breathe) {
        if (breathe == null) {
            return null;
        }
        return breathe.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
}
