package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.entity.UserIdentity;
import com.example.cursorquitterweb.service.UserIdentityService;
import com.example.cursorquitterweb.util.CloudflareD1Util;
import com.example.cursorquitterweb.util.EntityMapper;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户身份服务实现类
 * 使用 CloudflareD1Util 进行数据库操作
 */
@Service
public class UserIdentityServiceImpl implements UserIdentityService {
    
    private static final Logger logger = LogUtil.getLogger(UserIdentityServiceImpl.class);
    
    @Autowired
    private CloudflareD1Util d1Util;
    
    @Override
    public UserIdentity createIdentity(UUID userId, String identityType, String identityId, String identityData) {
        logger.info("创建用户身份，用户ID: {}, 身份类型: {}, 身份ID: {}", userId, identityType, identityId);
        
        // 检查是否已存在
        if (existsByIdentityTypeAndIdentityId(identityType, identityId)) {
            logger.warn("身份已存在，身份类型: {}, 身份ID: {}", identityType, identityId);
            throw new RuntimeException("该身份已被绑定");
        }
        
        UserIdentity userIdentity = new UserIdentity(userId, identityType, identityId);
        userIdentity.setIdentityData(identityData);
        
        UserIdentity savedIdentity = saveUserIdentity(userIdentity);
        logger.info("用户身份创建成功，ID: {}", savedIdentity.getId());
        return savedIdentity;
    }
    
    @Override
    public Optional<UserIdentity> findById(Long id) {
        logger.debug("查找用户身份，ID: {}", id);
        String sql = "SELECT * FROM user_identities WHERE id = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, id);
        return row != null ? Optional.of(mapToUserIdentity(row)) : Optional.empty();
    }
    
    @Override
    public List<UserIdentity> findByUserId(UUID userId) {
        logger.debug("查找用户的所有身份，用户ID: {}", userId);
        String sql = "SELECT * FROM user_identities WHERE user_id = ? ORDER BY created_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, EntityMapper.uuidToString(userId));
        return rows.stream().map(this::mapToUserIdentity).collect(Collectors.toList());
    }
    
    @Override
    public Optional<UserIdentity> findByIdentityTypeAndIdentityId(String identityType, String identityId) {
        logger.debug("根据身份类型和ID查找，类型: {}, ID: {}", identityType, identityId);
        String sql = "SELECT * FROM user_identities WHERE identity_type = ? AND identity_id = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, identityType, identityId);
        return row != null ? Optional.of(mapToUserIdentity(row)) : Optional.empty();
    }
    
    @Override
    public Optional<UserIdentity> findByUserIdAndIdentityType(UUID userId, String identityType) {
        logger.debug("根据用户ID和身份类型查找，用户ID: {}, 类型: {}", userId, identityType);
        String sql = "SELECT * FROM user_identities WHERE user_id = ? AND identity_type = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, EntityMapper.uuidToString(userId), identityType);
        return row != null ? Optional.of(mapToUserIdentity(row)) : Optional.empty();
    }
    
    @Override
    public List<UserIdentity> findByIdentityType(String identityType) {
        logger.debug("根据身份类型查找所有用户身份，类型: {}", identityType);
        String sql = "SELECT * FROM user_identities WHERE identity_type = ? ORDER BY created_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, identityType);
        return rows.stream().map(this::mapToUserIdentity).collect(Collectors.toList());
    }
    
    @Override
    public boolean existsByIdentityTypeAndIdentityId(String identityType, String identityId) {
        logger.debug("检查身份是否存在，类型: {}, ID: {}", identityType, identityId);
        String sql = "SELECT 1 FROM user_identities WHERE identity_type = ? AND identity_id = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, identityType, identityId);
        return row != null;
    }
    
    @Override
    public UserIdentity updateIdentityData(Long id, String identityData) {
        logger.info("更新用户身份数据，身份ID: {}", id);
        
        Optional<UserIdentity> identityOpt = findById(id);
        if (!identityOpt.isPresent()) {
            logger.error("用户身份不存在，无法更新，身份ID: {}", id);
            throw new RuntimeException("用户身份不存在");
        }
        
        UserIdentity userIdentity = identityOpt.get();
        userIdentity.setIdentityData(identityData);
        
        UserIdentity savedIdentity = saveUserIdentity(userIdentity);
        logger.info("用户身份数据更新成功，身份ID: {}", id);
        return savedIdentity;
    }
    
    @Override
    public UserIdentity bindOrUpdateIdentity(UUID userId, String identityType, String identityId, String identityData) {
        logger.info("绑定或更新用户身份，用户ID: {}, 身份类型: {}, 身份ID: {}", userId, identityType, identityId);
        
        // 检查该身份是否已存在
        Optional<UserIdentity> existingIdentity = findByIdentityTypeAndIdentityId(identityType, identityId);
        
        if (existingIdentity.isPresent()) {
            UserIdentity identity = existingIdentity.get();
            logger.info("身份已存在，返回已绑定的用户ID: {}", identity.getUserId());
            
            // 如果身份数据不同，更新身份数据
            if (identityData != null && !identityData.equals(identity.getIdentityData())) {
                identity.setIdentityData(identityData);
                return saveUserIdentity(identity);
            }
            return identity;
        }
        
        // 身份不存在，创建新绑定
        UserIdentity newIdentity = new UserIdentity(userId, identityType, identityId);
        newIdentity.setIdentityData(identityData);
        
        UserIdentity savedIdentity = saveUserIdentity(newIdentity);
        logger.info("用户身份绑定成功，身份ID: {}", savedIdentity.getId());
        return savedIdentity;
    }
    
    @Override
    public void deleteIdentity(Long id) {
        logger.info("删除用户身份，身份ID: {}", id);
        String sql = "DELETE FROM user_identities WHERE id = ?";
        d1Util.execute(sql, id);
    }
    
    @Override
    public void deleteByUserId(UUID userId) {
        logger.info("删除用户的所有身份，用户ID: {}", userId);
        String sql = "DELETE FROM user_identities WHERE user_id = ?";
        d1Util.execute(sql, EntityMapper.uuidToString(userId));
    }
    
    @Override
    public void deleteByUserIdAndIdentityType(UUID userId, String identityType) {
        logger.info("删除用户的指定类型身份，用户ID: {}, 身份类型: {}", userId, identityType);
        String sql = "DELETE FROM user_identities WHERE user_id = ? AND identity_type = ?";
        d1Util.execute(sql, EntityMapper.uuidToString(userId), identityType);
    }
    
    @Override
    public long countByUserId(UUID userId) {
        logger.debug("统计用户的身份数量，用户ID: {}", userId);
        String sql = "SELECT COUNT(*) as count FROM user_identities WHERE user_id = ?";
        return d1Util.queryLong(sql, EntityMapper.uuidToString(userId));
    }
    
    @Override
    public long countDistinctUsersByIdentityType(String identityType) {
        logger.debug("统计指定身份类型的用户数量，身份类型: {}", identityType);
        String sql = "SELECT COUNT(DISTINCT user_id) as count FROM user_identities WHERE identity_type = ?";
        return d1Util.queryLong(sql, identityType);
    }
    
    @Override
    public UUID findUserIdByIdentity(String identityType, String identityId) {
        logger.debug("根据身份查找用户ID，身份类型: {}, 身份ID: {}", identityType, identityId);
        
        Optional<UserIdentity> identityOpt = findByIdentityTypeAndIdentityId(identityType, identityId);
        
        if (identityOpt.isPresent()) {
            UUID userId = identityOpt.get().getUserId();
            logger.debug("找到用户ID: {}", userId);
            return userId;
        }
        
        logger.debug("未找到对应的用户");
        return null;
    }
    
    /**
     * 保存用户身份
     */
    private UserIdentity saveUserIdentity(UserIdentity userIdentity) {
        if (userIdentity.getId() == null) {
            // 插入新记录（注意：user_identities 表的 id 是自增的，但我们需要先获取）
            userIdentity.setCreatedAt(OffsetDateTime.now());
            Map<String, Object> data = userIdentityToMap(userIdentity);
            // 移除 id，让数据库自动生成
            data.remove("id");
            long lastRowId = d1Util.insert("user_identities", data);
            userIdentity.setId(lastRowId);
            return userIdentity;
        } else {
            // 更新记录
            Map<String, Object> data = userIdentityToMap(userIdentity);
            d1Util.updateById("user_identities", data, "id", userIdentity.getId());
            return userIdentity;
        }
    }
    
    /**
     * 将 Map 转换为 UserIdentity 实体
     */
    private UserIdentity mapToUserIdentity(Map<String, Object> row) {
        UserIdentity userIdentity = new UserIdentity();
        Object idObj = row.get("id");
        if (idObj instanceof Number) {
            userIdentity.setId(((Number) idObj).longValue());
        }
        userIdentity.setUserId(EntityMapper.getUUID(row, "user_id"));
        userIdentity.setIdentityType(EntityMapper.getString(row, "identity_type"));
        userIdentity.setIdentityId(EntityMapper.getString(row, "identity_id"));
        userIdentity.setIdentityData(EntityMapper.getString(row, "identity_data"));
        userIdentity.setCreatedAt(EntityMapper.getOffsetDateTime(row, "created_at"));
        return userIdentity;
    }
    
    /**
     * 将 UserIdentity 实体转换为 Map（用于数据库操作）
     */
    private Map<String, Object> userIdentityToMap(UserIdentity userIdentity) {
        Map<String, Object> data = new HashMap<>();
        if (userIdentity.getId() != null) {
            data.put("id", userIdentity.getId());
        }
        EntityMapper.putIfNotNull(data, "user_id", userIdentity.getUserId());
        EntityMapper.putIfNotNull(data, "identity_type", userIdentity.getIdentityType());
        EntityMapper.putIfNotNull(data, "identity_id", userIdentity.getIdentityId());
        EntityMapper.putIfNotNull(data, "identity_data", userIdentity.getIdentityData());
        EntityMapper.putIfNotNull(data, "created_at", userIdentity.getCreatedAt());
        return data;
    }
}

