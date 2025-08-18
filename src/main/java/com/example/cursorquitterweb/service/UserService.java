package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.entity.User;
import com.example.cursorquitterweb.dto.UserLeaderboardDto;
import com.example.cursorquitterweb.dto.UserRankDto;

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
    
    /**
     * 更新用户最佳挑战记录
     * @param userId 用户ID
     * @param newRecord 新的挑战记录天数
     * @return 更新后的用户对象
     */
    User updateBestRecord(UUID userId, Integer newRecord);
    
    /**
     * 获取用户最佳挑战记录
     * @param userId 用户ID
     * @return 最佳挑战记录天数
     */
    Integer getBestRecord(UUID userId);
    
    /**
     * 获取挑战记录排行榜（按最佳记录降序排列）
     * @param limit 限制返回数量
     * @return 用户排行榜列表
     */
    List<User> getChallengeLeaderboard(int limit);
    
    /**
     * 获取挑战记录排行榜（简化版，只包含必要字段）
     * @param limit 限制返回数量
     * @return 用户排行榜简化列表
     */
    List<UserLeaderboardDto> getChallengeLeaderboardSimple(int limit);
    
    /**
     * 检查并更新最佳记录（如果新记录更好）
     * @param userId 用户ID
     * @param currentRecord 当前挑战记录天数
     * @return 是否更新了最佳记录
     */
    boolean checkAndUpdateBestRecord(UUID userId, Integer currentRecord);
    
    /**
     * 根据最佳挑战记录范围查询用户
     * @param minRecord 最小记录
     * @param maxRecord 最大记录
     * @return 用户列表
     */
    List<User> findByBestRecordBetween(Integer minRecord, Integer maxRecord);
    
    /**
     * 统计达到指定挑战记录的用户数量
     * @param minRecord 最小记录
     * @return 用户数量
     */
    long countUsersByBestRecordGreaterThanOrEqualTo(Integer minRecord);
    
    /**
     * 查询用户在挑战榜单中的排名
     * @param userId 用户ID
     * @return 用户排名（从1开始，如果用户不存在或没有最佳记录则返回null）
     */
    Long getUserRankInLeaderboard(UUID userId);
    
    /**
     * 查询用户在挑战榜单中的排名和最佳成绩
     * @param userId 用户ID
     * @return 用户排名和最佳成绩信息
     */
    UserRankDto getUserRankAndBestRecord(UUID userId);
}
