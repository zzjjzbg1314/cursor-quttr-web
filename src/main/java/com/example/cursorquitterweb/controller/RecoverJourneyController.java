package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.*;
import com.example.cursorquitterweb.entity.RecoverJourney;
import com.example.cursorquitterweb.service.RecoverJourneyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 康复记录控制器
 */
@RestController
@RequestMapping("/api/recover-journey")
public class RecoverJourneyController {
    
    @Autowired
    private RecoverJourneyService recoverJourneyService;
    
    /**
     * 根据ID获取康复记录
     */
    @GetMapping("/{id}")
    public ApiResponse<RecoverJourneyDto> getRecoverJourney(@PathVariable UUID id) {
        try {
            Optional<RecoverJourney> recoverJourney = recoverJourneyService.findById(id);
            if (recoverJourney.isPresent()) {
                RecoverJourneyDto dto = convertToDto(recoverJourney.get());
                return ApiResponse.success("获取康复记录成功", dto);
            } else {
                return ApiResponse.error("康复记录不存在");
            }
        } catch (Exception e) {
            return ApiResponse.error("获取康复记录失败: " + e.getMessage());
        }
    }
    
    /**
     * 创建康复记录
     */
    @PostMapping
    public ApiResponse<RecoverJourneyDto> createRecoverJourney(@RequestBody CreateRecoverJourneyRequest request) {
        try {
            RecoverJourney recoverJourney;
            if (request.getFellContent() != null && !request.getFellContent().trim().isEmpty()) {
                recoverJourney = recoverJourneyService.createRecoverJourney(
                    request.getUserId(), 
                    request.getFellContent()
                );
            } else {
                recoverJourney = recoverJourneyService.createRecoverJourney(request.getUserId());
            }
            
            RecoverJourneyDto dto = convertToDto(recoverJourney);
            return ApiResponse.success("创建康复记录成功", dto);
        } catch (Exception e) {
            return ApiResponse.error("创建康复记录失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新康复记录
     */
    @PutMapping("/{id}")
    public ApiResponse<RecoverJourneyDto> updateRecoverJourney(@PathVariable UUID id, @RequestBody UpdateRecoverJourneyRequest request) {
        try {
            Optional<RecoverJourney> optional = recoverJourneyService.findById(id);
            if (!optional.isPresent()) {
                return ApiResponse.error("康复记录不存在");
            }
            
            RecoverJourney recoverJourney = optional.get();
            if (request.getFellContent() != null) {
                recoverJourney.setFellContent(request.getFellContent());
            }
            
            RecoverJourney updated = recoverJourneyService.updateRecoverJourney(recoverJourney);
            RecoverJourneyDto dto = convertToDto(updated);
            return ApiResponse.success("更新康复记录成功", dto);
        } catch (Exception e) {
            return ApiResponse.error("更新康复记录失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除康复记录
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteRecoverJourney(@PathVariable UUID id) {
        try {
            recoverJourneyService.deleteRecoverJourney(id);
            return ApiResponse.success("删除康复记录成功", null);
        } catch (Exception e) {
            return ApiResponse.error("删除康复记录失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据用户ID获取康复记录列表
     */
    @GetMapping("/user/{userId}")
    public ApiResponse<List<RecoverJourneyDto>> getRecoverJourneysByUserId(@PathVariable UUID userId) {
        try {
            List<RecoverJourney> recoverJourneys = recoverJourneyService.findByUserId(userId);
            List<RecoverJourneyDto> dtos = recoverJourneys.stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());
            return ApiResponse.success("获取用户康复记录成功", dtos);
        } catch (Exception e) {
            return ApiResponse.error("获取用户康复记录失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取用户最新的康复记录
     */
    @GetMapping("/user/{userId}/latest")
    public ApiResponse<RecoverJourneyDto> getLatestRecoverJourneyByUserId(@PathVariable UUID userId) {
        try {
            Optional<RecoverJourney> recoverJourney = recoverJourneyService.findLatestByUserId(userId);
            if (recoverJourney.isPresent()) {
                RecoverJourneyDto dto = convertToDto(recoverJourney.get());
                return ApiResponse.success("获取最新康复记录成功", dto);
            } else {
                return ApiResponse.error("用户没有康复记录");
            }
        } catch (Exception e) {
            return ApiResponse.error("获取最新康复记录失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新康复记录感受内容
     */
    @PutMapping("/{id}/fell-content")
    public ApiResponse<RecoverJourneyDto> updateFellContent(@PathVariable UUID id, @RequestBody String fellContent) {
        try {
            RecoverJourney recoverJourney = recoverJourneyService.updateFellContent(id, fellContent);
            RecoverJourneyDto dto = convertToDto(recoverJourney);
            return ApiResponse.success("更新感受内容成功", dto);
        } catch (Exception e) {
            return ApiResponse.error("更新感受内容失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据感受内容模糊查询
     */
    @GetMapping("/search")
    public ApiResponse<List<RecoverJourneyDto>> searchByFellContent(@RequestParam String content) {
        try {
            List<RecoverJourney> recoverJourneys = recoverJourneyService.findByFellContentContaining(content);
            List<RecoverJourneyDto> dtos = recoverJourneys.stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());
            return ApiResponse.success("根据感受内容搜索成功", dtos);
        } catch (Exception e) {
            return ApiResponse.error("根据感受内容搜索失败: " + e.getMessage());
        }
    }
    
    /**
     * 统计用户康复记录
     */
    @GetMapping("/user/{userId}/statistics")
    public ApiResponse<Object> getUserRecoverJourneyStatistics(@PathVariable UUID userId) {
        try {
            long totalCount = recoverJourneyService.countByUserId(userId);
            
            java.util.Map<String, Object> statistics = new java.util.HashMap<>();
            statistics.put("totalCount", totalCount);
            
            return ApiResponse.success("获取用户康复记录统计成功", statistics);
        } catch (Exception e) {
            return ApiResponse.error("获取用户康复记录统计失败: " + e.getMessage());
        }
    }
    
    /**
     * 将实体转换为DTO
     */
    private RecoverJourneyDto convertToDto(RecoverJourney recoverJourney) {
        return new RecoverJourneyDto(
                recoverJourney.getId(),
                recoverJourney.getUserId(),
                recoverJourney.getFellContent(),
                recoverJourney.getCreateAt(),
                recoverJourney.getUpdateAt()
        );
    }
}
