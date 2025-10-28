package com.example.cursorquitterweb.repository;

import com.example.cursorquitterweb.entity.UserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 用户身份数据访问接口
 */
@Repository
public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {
    
    /**
     * 根据用户ID查询所有身份
     */
    List<UserIdentity> findByUserId(UUID userId);
    
    /**
     * 根据身份类型和身份ID查询（用于验证唯一性）
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
    @Query("SELECT COUNT(ui) FROM UserIdentity ui WHERE ui.userId = :userId")
    long countByUserId(@Param("userId") UUID userId);
    
    /**
     * 统计指定身份类型的用户数量
     */
    @Query("SELECT COUNT(DISTINCT ui.userId) FROM UserIdentity ui WHERE ui.identityType = :identityType")
    long countDistinctUsersByIdentityType(@Param("identityType") String identityType);
}

