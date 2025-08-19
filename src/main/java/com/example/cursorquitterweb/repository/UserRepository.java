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
     * 根据昵称模糊查询用户
     */
    List<User> findByNicknameContainingIgnoreCase(String nickname);
    
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
    
    /**
     * 根据最佳挑战记录获取排行榜（降序排列）
     */
    @Query("SELECT u FROM User u WHERE u.bestRecord IS NOT NULL ORDER BY u.bestRecord DESC")
    List<User> findTopUsersByBestRecordOrderByBestRecordDesc();
    
    /**
     * 根据最佳挑战记录获取排行榜（降序排列，限制数量）
     */
    @Query("SELECT u FROM User u WHERE u.bestRecord IS NOT NULL ORDER BY u.bestRecord DESC")
    List<User> findTopUsersByBestRecordOrderByBestRecordDesc(org.springframework.data.domain.Pageable pageable);
    
    /**
     * 根据最佳挑战记录获取排行榜（降序排列，分页查询）
     */
    @Query("SELECT u FROM User u WHERE u.bestRecord IS NOT NULL ORDER BY u.bestRecord DESC")
    org.springframework.data.domain.Page<User> findTopUsersByBestRecordOrderByBestRecordDescPage(org.springframework.data.domain.Pageable pageable);
    
    /**
     * 根据最佳挑战记录范围查询用户
     */
    @Query("SELECT u FROM User u WHERE u.bestRecord BETWEEN :minRecord AND :maxRecord ORDER BY u.bestRecord DESC")
    List<User> findByBestRecordBetween(@Param("minRecord") Integer minRecord, @Param("maxRecord") Integer maxRecord);
    
    /**
     * 统计达到指定挑战记录的用户数量
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.bestRecord >= :minRecord")
    long countUsersByBestRecordGreaterThanOrEqualTo(@Param("minRecord") Integer minRecord);
    
    /**
     * 查询用户在挑战榜单中的排名
     * @param userId 用户ID
     * @return 用户排名（从1开始，如果用户不存在或没有最佳记录则返回null）
     */
    @Query("SELECT COUNT(u) + 1 FROM User u WHERE u.bestRecord > (SELECT u2.bestRecord FROM User u2 WHERE u2.id = :userId) AND u.bestRecord IS NOT NULL")
    Long findUserRankInLeaderboard(@Param("userId") UUID userId);
}
