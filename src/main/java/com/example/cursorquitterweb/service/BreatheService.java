package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.dto.BreatheDto;
import com.example.cursorquitterweb.entity.Breathe;
import com.example.cursorquitterweb.util.CloudflareD1Util;
import com.example.cursorquitterweb.util.EntityMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 呼吸练习服务实现类
 * 使用 CloudflareD1Util 进行数据库操作
 */
@Service
public class BreatheService {
    
    @Autowired
    private CloudflareD1Util d1Util;
    
    /**
     * 根据ID查找呼吸练习
     */
    public Optional<Breathe> findById(UUID id) {
        Map<String, Object> row = d1Util.findById("breathe", "id", EntityMapper.uuidToString(id));
        return row != null ? Optional.of(mapToBreathe(row)) : Optional.empty();
    }
    
    /**
     * 保存呼吸练习
     */
    public Breathe save(Breathe breathe) {
        if (breathe.getId() == null) {
            // 插入新记录
            breathe.setId(UUID.randomUUID());
            breathe.setCreateAt(OffsetDateTime.now());
            breathe.setUpdateAt(OffsetDateTime.now());
            Map<String, Object> data = breatheToMap(breathe);
            d1Util.insert("breathe", data);
            return breathe;
        } else {
            // 更新记录
            breathe.preUpdate();
            Map<String, Object> data = breatheToMap(breathe);
            d1Util.updateById("breathe", data, "id", EntityMapper.uuidToString(breathe.getId()));
            return breathe;
        }
    }
    
    /**
     * 创建新呼吸练习
     * 清除缓存
     */
    @CacheEvict(value = "breathe", allEntries = true)
    public Breathe createBreathe(String title, String time, String audiourl) {
        Breathe breathe = new Breathe(title, time, audiourl);
        return save(breathe);
    }
    
    /**
     * 更新呼吸练习信息
     * 清除缓存
     */
    @CacheEvict(value = "breathe", allEntries = true)
    public Breathe updateBreathe(Breathe breathe) {
        breathe.preUpdate(); // 更新修改时间
        return save(breathe);
    }
    
    /**
     * 更新呼吸练习信息（通过ID）
     * 清除缓存
     */
    @CacheEvict(value = "breathe", allEntries = true)
    public Breathe updateBreathe(UUID id, String title, String time, String audiourl) {
        Breathe breathe = findById(id)
                .orElseThrow(() -> new RuntimeException("呼吸练习不存在，ID: " + id));
        
        breathe.setTitle(title);
        breathe.setTime(time);
        breathe.setAudiourl(audiourl);
        
        return save(breathe);
    }
    
    /**
     * 删除呼吸练习
     * 清除缓存
     */
    @CacheEvict(value = "breathe", allEntries = true)
    public void deleteBreathe(UUID id) {
        if (!d1Util.exists("breathe", "id = ?", EntityMapper.uuidToString(id))) {
            throw new RuntimeException("呼吸练习不存在，ID: " + id);
        }
        d1Util.deleteById("breathe", "id", EntityMapper.uuidToString(id));
    }
    
    /**
     * 根据标题搜索呼吸练习
     */
    public List<Breathe> searchByTitle(String title) {
        String sql = "SELECT * FROM breathe WHERE LOWER(title) LIKE LOWER(?) ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, "%" + title + "%");
        return rows.stream().map(this::mapToBreathe).collect(Collectors.toList());
    }
    
    /**
     * 根据标题精确查询呼吸练习
     */
    public Optional<Breathe> findByTitle(String title) {
        String sql = "SELECT * FROM breathe WHERE title = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, title);
        return row != null ? Optional.of(mapToBreathe(row)) : Optional.empty();
    }
    
    /**
     * 根据创建时间范围查询呼吸练习
     */
    public List<Breathe> findByCreateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT * FROM breathe WHERE create_at >= ? AND create_at <= ? ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime));
        return rows.stream().map(this::mapToBreathe).collect(Collectors.toList());
    }
    
    /**
     * 根据更新时间范围查询呼吸练习
     */
    public List<Breathe> findByUpdateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT * FROM breathe WHERE update_at >= ? AND update_at <= ? ORDER BY update_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime));
        return rows.stream().map(this::mapToBreathe).collect(Collectors.toList());
    }
    
    /**
     * 查询有音频链接的呼吸练习
     */
    public List<Breathe> findBreatheWithAudiourl() {
        String sql = "SELECT * FROM breathe WHERE audiourl IS NOT NULL AND audiourl != '' ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToBreathe).collect(Collectors.toList());
    }
    
    /**
     * 统计指定时间范围内创建的呼吸练习数量
     */
    public long countBreatheByCreateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT COUNT(*) as count FROM breathe WHERE create_at >= ? AND create_at <= ?";
        return d1Util.queryLong(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime));
    }
    
    /**
     * 根据标题关键词搜索呼吸练习（支持中文全文搜索）
     */
    public List<Breathe> searchBreatheByTitleKeyword(String keyword) {
        String sql = "SELECT * FROM breathe WHERE title LIKE ? ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, "%" + keyword + "%");
        return rows.stream().map(this::mapToBreathe).collect(Collectors.toList());
    }
    
    /**
     * 获取最新的呼吸练习列表（按创建时间降序）
     */
    public List<Breathe> getLatestBreathe() {
        String sql = "SELECT * FROM breathe ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToBreathe).collect(Collectors.toList());
    }
    
    /**
     * 获取最新的呼吸练习列表（按创建时间降序，限制数量）
     */
    public List<Breathe> getLatestBreathe(int limit) {
        String sql = "SELECT * FROM breathe ORDER BY create_at ASC LIMIT ?";
        List<Map<String, Object>> rows = d1Util.queryList(sql, limit);
        return rows.stream().map(this::mapToBreathe).collect(Collectors.toList());
    }
    
    /**
     * 分页查询呼吸练习列表（按创建时间降序）
     * 注意：返回的是 List，不再使用 Spring Data 的 Page 对象
     */
    public List<Breathe> getBreathePage(int page, int size) {
        String sql = "SELECT * FROM breathe ORDER BY create_at ASC";
        return d1Util.queryPage(sql, page + 1, size).stream()
            .map(this::mapToBreathe)
            .collect(Collectors.toList());
    }
    
    /**
     * 根据标题模糊查询并分页
     * 注意：返回的是 List，不再使用 Spring Data 的 Page 对象
     */
    public List<Breathe> searchBreatheByTitlePage(String title, int page, int size) {
        String sql = "SELECT * FROM breathe WHERE LOWER(title) LIKE LOWER(?) ORDER BY create_at ASC";
        return d1Util.queryPage(sql, page + 1, size, "%" + title + "%").stream()
            .map(this::mapToBreathe)
            .collect(Collectors.toList());
    }
    
    /**
     * 获取所有呼吸练习（按创建时间升序排列）
     */
    public List<Breathe> getAllBreathe() {
        String sql = "SELECT * FROM breathe ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToBreathe).collect(Collectors.toList());
    }
    
    /**
     * 获取所有呼吸练习（分页）
     * 注意：返回的是 List，不再使用 Spring Data 的 Page 对象
     */
    public List<Breathe> getAllBreathe(int page, int size) {
        String sql = "SELECT * FROM breathe ORDER BY create_at ASC";
        return d1Util.queryPage(sql, page + 1, size).stream()
            .map(this::mapToBreathe)
            .collect(Collectors.toList());
    }
    
    /**
     * 检查呼吸练习标题是否已存在
     */
    public boolean existsByTitle(String title) {
        return findByTitle(title).isPresent();
    }
    
    /**
     * 根据音频链接查找呼吸练习
     */
    public Optional<Breathe> findByAudiourl(String audiourl) {
        String sql = "SELECT * FROM breathe WHERE audiourl = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, audiourl);
        return row != null ? Optional.of(mapToBreathe(row)) : Optional.empty();
    }
    
    /**
     * 更新呼吸练习音频链接
     */
    public Breathe updateAudiourl(UUID id, String audiourl) {
        Optional<Breathe> breatheOpt = findById(id);
        if (breatheOpt.isPresent()) {
            Breathe breathe = breatheOpt.get();
            breathe.setAudiourl(audiourl);
            return save(breathe);
        }
        throw new RuntimeException("呼吸练习不存在");
    }
    
    /**
     * 统计呼吸练习总数
     */
    public long count() {
        return d1Util.countTable("breathe");
    }
    
    /**
     * 将 Map 转换为 Breathe 实体
     */
    private Breathe mapToBreathe(Map<String, Object> row) {
        Breathe breathe = new Breathe();
        breathe.setId(EntityMapper.getUUID(row, "id"));
        breathe.setTitle(EntityMapper.getString(row, "title"));
        breathe.setTime(EntityMapper.getString(row, "time"));
        breathe.setAudiourl(EntityMapper.getString(row, "audiourl"));
        breathe.setCreateAt(EntityMapper.getOffsetDateTime(row, "create_at"));
        breathe.setUpdateAt(EntityMapper.getOffsetDateTime(row, "update_at"));
        return breathe;
    }
    
    /**
     * 将 Breathe 实体转换为 Map（用于数据库操作）
     */
    private Map<String, Object> breatheToMap(Breathe breathe) {
        Map<String, Object> data = new HashMap<>();
        EntityMapper.putIfNotNull(data, "id", breathe.getId());
        EntityMapper.putIfNotNull(data, "title", breathe.getTitle());
        EntityMapper.putIfNotNull(data, "time", breathe.getTime());
        EntityMapper.putIfNotNull(data, "audiourl", breathe.getAudiourl());
        EntityMapper.putIfNotNull(data, "create_at", breathe.getCreateAt());
        EntityMapper.putIfNotNull(data, "update_at", breathe.getUpdateAt());
        return data;
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
