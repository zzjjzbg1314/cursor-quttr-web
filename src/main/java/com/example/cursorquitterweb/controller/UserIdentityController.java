package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.BindIdentityRequest;
import com.example.cursorquitterweb.dto.UserIdentityDto;
import com.example.cursorquitterweb.entity.UserIdentity;
import com.example.cursorquitterweb.service.UserIdentityService;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户身份控制器
 */
@RestController
@RequestMapping("/api/user-identities")
public class UserIdentityController {
    
    private static final Logger logger = LogUtil.getLogger(UserIdentityController.class);
    
    @Autowired
    private UserIdentityService userIdentityService;
    
    /**
     * 绑定用户身份
     * POST /api/user-identities/{userId}/bind
     */
    @PostMapping("/{userId}/bind")
    public ResponseEntity<Map<String, Object>> bindIdentity(
            @PathVariable String userId,
            @Valid @RequestBody BindIdentityRequest request) {
        
        logger.info("绑定用户身份，用户ID: {}, 请求: {}", userId, request);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            UUID userUuid = UUID.fromString(userId);
            
            // 绑定或更新身份
            UserIdentity identity = userIdentityService.bindOrUpdateIdentity(
                userUuid,
                request.getIdentityType(),
                request.getIdentityId(),
                request.getIdentityData()
            );
            
            response.put("success", true);
            response.put("message", "身份绑定成功");
            response.put("data", UserIdentityDto.fromEntity(identity));
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            logger.error("用户ID格式错误: {}", userId);
            response.put("success", false);
            response.put("message", "用户ID格式错误");
            return ResponseEntity.badRequest().body(response);
            
        } catch (Exception e) {
            logger.error("绑定用户身份失败", e);
            response.put("success", false);
            response.put("message", "绑定失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 获取用户的所有身份
     * GET /api/user-identities/{userId}
     */
    @GetMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> getUserIdentities(@PathVariable String userId) {
        
        logger.info("获取用户身份列表，用户ID: {}", userId);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            UUID userUuid = UUID.fromString(userId);
            List<UserIdentity> identities = userIdentityService.findByUserId(userUuid);
            
            List<UserIdentityDto> dtos = identities.stream()
                .map(UserIdentityDto::fromEntity)
                .collect(Collectors.toList());
            
            response.put("success", true);
            response.put("data", dtos);
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            logger.error("用户ID格式错误: {}", userId);
            response.put("success", false);
            response.put("message", "用户ID格式错误");
            return ResponseEntity.badRequest().body(response);
            
        } catch (Exception e) {
            logger.error("获取用户身份列表失败", e);
            response.put("success", false);
            response.put("message", "获取失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 根据身份类型获取用户身份
     * GET /api/user-identities/{userId}/type/{identityType}
     */
    @GetMapping("/{userId}/type/{identityType}")
    public ResponseEntity<Map<String, Object>> getUserIdentityByType(
            @PathVariable String userId,
            @PathVariable String identityType) {
        
        logger.info("获取用户指定类型身份，用户ID: {}, 类型: {}", userId, identityType);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            UUID userUuid = UUID.fromString(userId);
            Optional<UserIdentity> identityOpt = userIdentityService
                .findByUserIdAndIdentityType(userUuid, identityType);
            
            if (identityOpt.isPresent()) {
                response.put("success", true);
                response.put("data", UserIdentityDto.fromEntity(identityOpt.get()));
            } else {
                response.put("success", false);
                response.put("message", "未找到该类型的身份");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            logger.error("用户ID格式错误: {}", userId);
            response.put("success", false);
            response.put("message", "用户ID格式错误");
            return ResponseEntity.badRequest().body(response);
            
        } catch (Exception e) {
            logger.error("获取用户身份失败", e);
            response.put("success", false);
            response.put("message", "获取失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 根据身份查找用户ID
     * GET /api/user-identities/find-user?identityType=xxx&identityId=xxx
     */
    @GetMapping("/find-user")
    public ResponseEntity<Map<String, Object>> findUserByIdentity(
            @RequestParam String identityType,
            @RequestParam String identityId) {
        
        logger.info("根据身份查找用户，类型: {}, ID: {}", identityType, identityId);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            UUID userId = userIdentityService.findUserIdByIdentity(identityType, identityId);
            
            if (userId != null) {
                response.put("success", true);
                response.put("userId", userId.toString());
            } else {
                response.put("success", false);
                response.put("message", "未找到对应的用户");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("查找用户失败", e);
            response.put("success", false);
            response.put("message", "查找失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 删除用户的指定类型身份
     * DELETE /api/user-identities/{userId}/type/{identityType}
     */
    @DeleteMapping("/{userId}/type/{identityType}")
    public ResponseEntity<Map<String, Object>> deleteIdentityByType(
            @PathVariable String userId,
            @PathVariable String identityType) {
        
        logger.info("删除用户指定类型身份，用户ID: {}, 类型: {}", userId, identityType);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            UUID userUuid = UUID.fromString(userId);
            userIdentityService.deleteByUserIdAndIdentityType(userUuid, identityType);
            
            response.put("success", true);
            response.put("message", "删除成功");
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            logger.error("用户ID格式错误: {}", userId);
            response.put("success", false);
            response.put("message", "用户ID格式错误");
            return ResponseEntity.badRequest().body(response);
            
        } catch (Exception e) {
            logger.error("删除用户身份失败", e);
            response.put("success", false);
            response.put("message", "删除失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 删除用户的所有身份
     * DELETE /api/user-identities/{userId}
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> deleteAllIdentities(@PathVariable String userId) {
        
        logger.info("删除用户所有身份，用户ID: {}", userId);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            UUID userUuid = UUID.fromString(userId);
            userIdentityService.deleteByUserId(userUuid);
            
            response.put("success", true);
            response.put("message", "删除成功");
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            logger.error("用户ID格式错误: {}", userId);
            response.put("success", false);
            response.put("message", "用户ID格式错误");
            return ResponseEntity.badRequest().body(response);
            
        } catch (Exception e) {
            logger.error("删除用户所有身份失败", e);
            response.put("success", false);
            response.put("message", "删除失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 统计用户的身份数量
     * GET /api/user-identities/{userId}/count
     */
    @GetMapping("/{userId}/count")
    public ResponseEntity<Map<String, Object>> countUserIdentities(@PathVariable String userId) {
        
        logger.info("统计用户身份数量，用户ID: {}", userId);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            UUID userUuid = UUID.fromString(userId);
            long count = userIdentityService.countByUserId(userUuid);
            
            response.put("success", true);
            response.put("count", count);
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            logger.error("用户ID格式错误: {}", userId);
            response.put("success", false);
            response.put("message", "用户ID格式错误");
            return ResponseEntity.badRequest().body(response);
            
        } catch (Exception e) {
            logger.error("统计用户身份数量失败", e);
            response.put("success", false);
            response.put("message", "统计失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}

