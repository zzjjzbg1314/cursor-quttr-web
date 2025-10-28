package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.entity.UserIdentity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 用户身份服务接口
 */
public interface UserIdentityService {
    
    /**
     * 创建用户身份
     * @param userId 用户ID
     * @param identityType 身份类型
     * @param identityId 身份ID
     * @param identityData 身份数据（JSON字符串）
     * @return 创建的用户身份
     */
    UserIdentity createIdentity(UUID userId, String identityType, String identityId, String identityData);
    
    /**
     * 根据ID查询用户身份
     */
    Optional<UserIdentity> findById(Long id);
    
    /**
     * 根据用户ID查询所有身份
     */
    List<UserIdentity> findByUserId(UUID userId);
    
    /**
     * 根据身份类型和身份ID查询
     */
    Optional<UserIdentity> findByIdentityTypeAndIdentityId(String identityType, String identityId);
    
    /**
     * 根据用户ID和身份类型查询
     */
    Optional<UserIdentity> findByUserIdAndIdentityType(UUID userId, String identityType);
    
    /**
     * 根据身份类型查询所有用户身份
     */
    List<UserIdentity> findByIdentityType(String identityType);
    
    /**
     * 检查指定身份是否已存在
     */
    boolean existsByIdentityTypeAndIdentityId(String identityType, String identityId);
    
    /**
     * 更新用户身份数据
     * @param id 身份ID
     * @param identityData 新的身份数据（JSON字符串）
     * @return 更新后的用户身份
     */
    UserIdentity updateIdentityData(Long id, String identityData);
    
    /**
     * 绑定或更新用户身份
     * 如果该身份类型和ID已存在，则返回已绑定的用户ID
     * 如果不存在，则创建新绑定
     * @param userId 用户ID
     * @param identityType 身份类型
     * @param identityId 身份ID
     * @param identityData 身份数据（JSON字符串）
     * @return 绑定的用户身份
     */
    UserIdentity bindOrUpdateIdentity(UUID userId, String identityType, String identityId, String identityData);
    
    /**
     * 删除用户身份
     */
    void deleteIdentity(Long id);
    
    /**
     * 删除用户的所有身份
     */
    void deleteByUserId(UUID userId);
    
    /**
     * 删除用户的指定类型身份
     */
    void deleteByUserIdAndIdentityType(UUID userId, String identityType);
    
    /**
     * 统计用户的身份数量
     */
    long countByUserId(UUID userId);
    
    /**
     * 统计指定身份类型的用户数量
     */
    long countDistinctUsersByIdentityType(String identityType);
    
    /**
     * 根据身份信息查找或创建用户
     * 如果身份已存在，返回对应的用户ID
     * 如果不存在，返回null（需要调用方创建新用户）
     * @param identityType 身份类型
     * @param identityId 身份ID
     * @return 用户ID，如果不存在则返回null
     */
    UUID findUserIdByIdentity(String identityType, String identityId);
}

