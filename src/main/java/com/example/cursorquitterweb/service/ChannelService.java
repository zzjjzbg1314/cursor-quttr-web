package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.dto.ChannelDto;
import com.example.cursorquitterweb.entity.Channel;
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
import java.util.stream.Collectors;

/**
 * 渠道服务实现类
 * 使用 CloudflareD1Util 进行数据库操作
 */
@Service
public class ChannelService {
    
    @Autowired
    private CloudflareD1Util d1Util;
    
    /**
     * 根据ID查找渠道
     */
    public Optional<Channel> findById(Integer id) {
        Map<String, Object> row = d1Util.findById("channel", "id", id);
        return row != null ? Optional.of(mapToChannel(row)) : Optional.empty();
    }
    
    /**
     * 根据名称查找渠道
     */
    public Optional<Channel> findByName(String name) {
        String sql = "SELECT * FROM channel WHERE name = ?";
        Map<String, Object> row = d1Util.queryOne(sql, name);
        return row != null ? Optional.of(mapToChannel(row)) : Optional.empty();
    }
    
    /**
     * 检查名称是否存在
     */
    public boolean existsByName(String name) {
        return d1Util.exists("channel", "name = ?", name);
    }
    
    /**
     * 保存渠道
     */
    public Channel save(Channel channel) {
        if (channel.getId() == null) {
            // 插入新记录
            channel.setCreatedAt(OffsetDateTime.now());
            channel.setUpdatedAt(OffsetDateTime.now());
            Map<String, Object> data = channelToMap(channel);
            long rowId = d1Util.insert("channel", data);
            channel.setId((int) rowId);
            return channel;
        } else {
            // 更新记录
            channel.preUpdate();
            Map<String, Object> data = channelToMap(channel);
            d1Util.updateById("channel", data, "id", channel.getId());
            return channel;
        }
    }
    
    /**
     * 创建新渠道
     * 清除缓存
     */
    @CacheEvict(value = "channels", allEntries = true)
    public Channel createChannel(String name, String nameCn, String description, 
                                 String descriptionCn, String pic, Integer sortIndex) {
        if (existsByName(name)) {
            throw new RuntimeException("渠道名称已存在: " + name);
        }
        
        Channel channel = new Channel(name, nameCn);
        channel.setDescription(description);
        channel.setDescriptionCn(descriptionCn);
        channel.setPic(pic);
        if (sortIndex != null) {
            channel.setSortIndex(sortIndex);
        }
        return save(channel);
    }
    
    /**
     * 更新渠道信息
     * 清除缓存
     */
    @CacheEvict(value = "channels", allEntries = true)
    public Channel updateChannel(Channel channel) {
        channel.preUpdate();
        return save(channel);
    }
    
    /**
     * 删除渠道
     * 清除缓存
     */
    @CacheEvict(value = "channels", allEntries = true)
    public void deleteChannel(Integer id) {
        if (!d1Util.exists("channel", "id = ?", id)) {
            throw new RuntimeException("渠道不存在，ID: " + id);
        }
        d1Util.deleteById("channel", "id", id);
    }
    
    /**
     * 获取所有渠道（按排序索引升序）
     */
    public List<Channel> getAllChannels() {
        String sql = "SELECT * FROM channel ORDER BY sort_index ASC, created_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToChannel).collect(Collectors.toList());
    }
    
    /**
     * 分页查询渠道列表（按排序索引升序）
     */
    public List<Channel> getChannelPage(int page, int size) {
        String sql = "SELECT * FROM channel ORDER BY sort_index ASC, created_at ASC";
        return d1Util.queryPage(sql, page + 1, size).stream()
            .map(this::mapToChannel)
            .collect(Collectors.toList());
    }
    
    /**
     * 根据名称搜索渠道（模糊查询）
     */
    public List<Channel> searchByName(String keyword) {
        String sql = "SELECT * FROM channel WHERE LOWER(name) LIKE LOWER(?) OR LOWER(name_cn) LIKE LOWER(?) ORDER BY sort_index ASC, created_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, "%" + keyword + "%", "%" + keyword + "%");
        return rows.stream().map(this::mapToChannel).collect(Collectors.toList());
    }
    
    /**
     * 统计渠道总数
     */
    public long count() {
        return d1Util.countTable("channel");
    }
    
    /**
     * 将 Map 转换为 Channel 实体
     */
    private Channel mapToChannel(Map<String, Object> row) {
        Channel channel = new Channel();
        Object idValue = row.get("id");
        if (idValue != null) {
            if (idValue instanceof Integer) {
                channel.setId((Integer) idValue);
            } else if (idValue instanceof Number) {
                channel.setId(((Number) idValue).intValue());
            }
        }
        channel.setName(EntityMapper.getString(row, "name"));
        channel.setNameCn(EntityMapper.getString(row, "name_cn"));
        channel.setDescription(EntityMapper.getString(row, "description"));
        channel.setDescriptionCn(EntityMapper.getString(row, "description_cn"));
        channel.setPic(EntityMapper.getString(row, "pic"));
        channel.setSortIndex(EntityMapper.getInteger(row, "sort_index"));
        channel.setCreatedAt(EntityMapper.getOffsetDateTime(row, "created_at"));
        channel.setUpdatedAt(EntityMapper.getOffsetDateTime(row, "updated_at"));
        return channel;
    }
    
    /**
     * 将 Channel 实体转换为 Map（用于数据库操作）
     */
    private Map<String, Object> channelToMap(Channel channel) {
        Map<String, Object> data = new HashMap<>();
        // 不包含 id，因为它是自动生成的
        EntityMapper.putIfNotNull(data, "name", channel.getName());
        EntityMapper.putIfNotNull(data, "name_cn", channel.getNameCn());
        EntityMapper.putIfNotNull(data, "description", channel.getDescription());
        EntityMapper.putIfNotNull(data, "description_cn", channel.getDescriptionCn());
        EntityMapper.putIfNotNull(data, "pic", channel.getPic());
        EntityMapper.putIfNotNull(data, "sort_index", channel.getSortIndex());
        EntityMapper.putIfNotNull(data, "created_at", channel.getCreatedAt());
        EntityMapper.putIfNotNull(data, "updated_at", channel.getUpdatedAt());
        return data;
    }
    
    /**
     * 转换为DTO
     */
    public ChannelDto convertToDto(Channel channel) {
        if (channel == null) {
            return null;
        }
        return new ChannelDto(channel);
    }
    
    /**
     * 批量转换为DTO
     */
    public List<ChannelDto> convertToDtoList(List<Channel> channels) {
        if (channels == null) {
            return null;
        }
        return channels.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
}

