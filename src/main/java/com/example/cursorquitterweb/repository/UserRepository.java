package com.example.cursorquitterweb.repository;

import com.example.cursorquitterweb.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 用户数据访问接口
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    
    /**
     * 根据微信openid查找用户
     */
    Optional<User> findByWechatOpenid(String wechatOpenid);
    
    /**
     * 根据微信unionid查找用户
     */
    Optional<User> findByWechatUnionid(String wechatUnionid);
    
    /**
     * 检查微信openid是否存在
     */
    boolean existsByWechatOpenid(String wechatOpenid);
    
    /**
     * 根据昵称模糊查询用户
     */
    List<User> findByNicknameContainingIgnoreCase(String nickname);
    
    /**
     * 根据城市查询用户
     */
    List<User> findByCity(String city);
    
    /**
     * 根据省份查询用户
     */
    List<User> findByProvince(String province);
    
    /**
     * 根据注册时间范围查询用户
     */
    List<User> findByRegistrationTimeBetween(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 根据挑战重置时间查询需要重置的用户
     */
    @Query("SELECT u FROM User u WHERE u.challengeResetTime <= :resetTime")
    List<User> findUsersNeedingChallengeReset(@Param("resetTime") OffsetDateTime resetTime);
    
    /**
     * 统计指定时间范围内的注册用户数
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.registrationTime BETWEEN :startTime AND :endTime")
    long countUsersByRegistrationTimeBetween(@Param("startTime") OffsetDateTime startTime, 
                                           @Param("endTime") OffsetDateTime endTime);
    
    /**
     * 根据性别统计用户数
     */
    @Query("SELECT u.gender, COUNT(u) FROM User u WHERE u.gender IS NOT NULL GROUP BY u.gender")
    List<Object[]> countUsersByGender();
}
