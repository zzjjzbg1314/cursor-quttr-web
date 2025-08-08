package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.entity.User;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 用户服务接口
 */
public interface UserService {
    
    /**
     * 根据ID查找用户
     */
    Optional<User> findById(UUID id);
    
    /**
     * 根据微信openid查找用户
     */
    Optional<User> findByWechatOpenid(String wechatOpenid);
    
    /**
     * 根据微信unionid查找用户
     */
    Optional<User> findByWechatUnionid(String wechatUnionid);
    
    /**
     * 保存用户
     */
    User save(User user);
    
    /**
     * 创建新用户
     */
    User createUser(String wechatOpenid, String nickname, String avatarUrl);
    
    /**
     * 更新用户信息
     */
    User updateUser(User user);
    
    /**
     * 删除用户
     */
    void deleteUser(UUID id);
    
    /**
     * 检查用户是否存在
     */
    boolean existsByWechatOpenid(String wechatOpenid);
    
    /**
     * 根据昵称搜索用户
     */
    List<User> searchByNickname(String nickname);
    
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
     * 获取需要重置挑战的用户
     */
    List<User> getUsersNeedingChallengeReset();
    
    /**
     * 重置用户挑战时间
     */
    void resetUserChallengeTime(UUID userId);
    
    /**
     * 统计指定时间范围内的注册用户数
     */
    long countUsersByRegistrationTimeBetween(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 根据性别统计用户数
     */
    List<Object[]> countUsersByGender();
}
