package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.ChannelDto;
import com.example.cursorquitterweb.entity.Channel;
import com.example.cursorquitterweb.service.ChannelService;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Optional;

/**
 * 渠道控制器
 * 提供渠道的CRUD操作和查询功能
 */
@RestController
@RequestMapping("/api/channels")
public class ChannelController {
    
    private static final Logger logger = LogUtil.getLogger(ChannelController.class);
    
    @Autowired
    private ChannelService channelService;
    
    /**
     * 根据ID获取渠道信息
     */
    @GetMapping("/{id}")
    public ApiResponse<ChannelDto> getChannelById(@PathVariable Integer id) {
        logger.info("获取渠道信息，ID: {}", id);
        Optional<Channel> channel = channelService.findById(id);
        if (channel.isPresent()) {
            return ApiResponse.success(channelService.convertToDto(channel.get()));
        } else {
            return ApiResponse.error("渠道不存在");
        }
    }
    
    /**
     * 创建新渠道
     */
    @PostMapping("/create")
    public ApiResponse<ChannelDto> createChannel(@Valid @RequestBody CreateChannelRequest request) {
        logger.info("创建新渠道，名称: {}, 中文名称: {}", request.getName(), request.getNameCn());
        
        try {
            Channel channel = channelService.createChannel(
                request.getName(),
                request.getNameCn(),
                request.getDescription(),
                request.getDescriptionCn(),
                request.getPic(),
                request.getSortIndex()
            );
            return ApiResponse.success("渠道创建成功", channelService.convertToDto(channel));
        } catch (RuntimeException e) {
            return ApiResponse.error(e.getMessage());
        }
    }
    
    /**
     * 更新渠道信息
     */
    @PutMapping("/{id}")
    public ApiResponse<ChannelDto> updateChannel(@PathVariable Integer id, 
                                                  @Valid @RequestBody UpdateChannelRequest request) {
        logger.info("更新渠道信息，ID: {}", id);
        
        Optional<Channel> channelOpt = channelService.findById(id);
        if (!channelOpt.isPresent()) {
            return ApiResponse.error("渠道不存在");
        }
        
        Channel channel = channelOpt.get();
        if (request.getName() != null) {
            // 检查新名称是否与其他渠道冲突
            Optional<Channel> existingChannel = channelService.findByName(request.getName());
            if (existingChannel.isPresent() && !existingChannel.get().getId().equals(id)) {
                return ApiResponse.error("渠道名称已存在");
            }
            channel.setName(request.getName());
        }
        if (request.getNameCn() != null) {
            channel.setNameCn(request.getNameCn());
        }
        if (request.getDescription() != null) {
            channel.setDescription(request.getDescription());
        }
        if (request.getDescriptionCn() != null) {
            channel.setDescriptionCn(request.getDescriptionCn());
        }
        if (request.getPic() != null) {
            channel.setPic(request.getPic());
        }
        if (request.getSortIndex() != null) {
            channel.setSortIndex(request.getSortIndex());
        }
        
        Channel updatedChannel = channelService.updateChannel(channel);
        return ApiResponse.success("渠道信息更新成功", channelService.convertToDto(updatedChannel));
    }
    
    /**
     * 删除渠道
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteChannel(@PathVariable Integer id) {
        logger.info("删除渠道，ID: {}", id);
        
        if (!channelService.findById(id).isPresent()) {
            return ApiResponse.error("渠道不存在");
        }
        
        try {
            channelService.deleteChannel(id);
            return ApiResponse.success("渠道删除成功", null);
        } catch (RuntimeException e) {
            return ApiResponse.error(e.getMessage());
        }
    }
    
    /**
     * 获取所有渠道（按排序索引升序）
     * 使用缓存
     */
    @GetMapping("/all")
    @Cacheable(value = "channels", key = "'all'")
    public ApiResponse<List<ChannelDto>> getAllChannels() {
        logger.info("获取所有渠道");
        List<Channel> channels = channelService.getAllChannels();
        List<ChannelDto> channelDtos = channelService.convertToDtoList(channels);
        return ApiResponse.success(channelDtos);
    }
    
    /**
     * 分页查询渠道列表
     */
    @GetMapping("/page")
    public ApiResponse<List<ChannelDto>> getChannelPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        logger.info("分页查询渠道列表，页码: {}, 每页大小: {}", page, size);
        
        if (page < 0) {
            return ApiResponse.error("页码不能小于0");
        }
        if (size <= 0 || size > 100) {
            return ApiResponse.error("每页大小必须在1-100之间");
        }
        
        List<Channel> channelList = channelService.getChannelPage(page, size);
        List<ChannelDto> channelDtos = channelService.convertToDtoList(channelList);
        return ApiResponse.success(channelDtos);
    }
    
    /**
     * 根据名称搜索渠道
     */
    @GetMapping("/search")
    public ApiResponse<List<ChannelDto>> searchChannels(@RequestParam String keyword) {
        logger.info("搜索渠道，关键词: {}", keyword);
        List<Channel> channels = channelService.searchByName(keyword);
        List<ChannelDto> channelDtos = channelService.convertToDtoList(channels);
        return ApiResponse.success(channelDtos);
    }
    
    /**
     * 统计渠道总数
     */
    @GetMapping("/count")
    public ApiResponse<Long> countChannels() {
        logger.info("统计渠道总数");
        long count = channelService.count();
        return ApiResponse.success(count);
    }
    
    /**
     * 创建渠道请求DTO
     */
    public static class CreateChannelRequest {
        private String name;
        private String nameCn;
        private String description;
        private String descriptionCn;
        private String pic;
        private Integer sortIndex;
        
        // Getters and Setters
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getNameCn() {
            return nameCn;
        }
        
        public void setNameCn(String nameCn) {
            this.nameCn = nameCn;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
        
        public String getDescriptionCn() {
            return descriptionCn;
        }
        
        public void setDescriptionCn(String descriptionCn) {
            this.descriptionCn = descriptionCn;
        }
        
        public String getPic() {
            return pic;
        }
        
        public void setPic(String pic) {
            this.pic = pic;
        }
        
        public Integer getSortIndex() {
            return sortIndex;
        }
        
        public void setSortIndex(Integer sortIndex) {
            this.sortIndex = sortIndex;
        }
    }
    
    /**
     * 更新渠道请求DTO
     */
    public static class UpdateChannelRequest {
        private String name;
        private String nameCn;
        private String description;
        private String descriptionCn;
        private String pic;
        private Integer sortIndex;
        
        // Getters and Setters
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getNameCn() {
            return nameCn;
        }
        
        public void setNameCn(String nameCn) {
            this.nameCn = nameCn;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
        
        public String getDescriptionCn() {
            return descriptionCn;
        }
        
        public void setDescriptionCn(String descriptionCn) {
            this.descriptionCn = descriptionCn;
        }
        
        public String getPic() {
            return pic;
        }
        
        public void setPic(String pic) {
            this.pic = pic;
        }
        
        public Integer getSortIndex() {
            return sortIndex;
        }
        
        public void setSortIndex(Integer sortIndex) {
            this.sortIndex = sortIndex;
        }
    }
}

