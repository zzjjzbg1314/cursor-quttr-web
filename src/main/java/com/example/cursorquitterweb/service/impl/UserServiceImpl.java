package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.entity.User;
import com.example.cursorquitterweb.service.UserService;
import com.example.cursorquitterweb.util.CloudflareD1Util;
import com.example.cursorquitterweb.util.EntityMapper;
import com.example.cursorquitterweb.util.LogUtil;
import com.example.cursorquitterweb.dto.UserLeaderboardDto;
import com.example.cursorquitterweb.dto.UserRankDto;
import com.example.cursorquitterweb.dto.UserChallengeRankDto;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.OffsetDateTime;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户服务实现类
 * 使用 CloudflareD1Util 进行数据库操作
 */
@Service
public class UserServiceImpl implements UserService {
    
    private static final Logger logger = LogUtil.getLogger(UserServiceImpl.class);
    
    @Autowired
    private CloudflareD1Util d1Util;

    private static final String CHALLENGE_DAYS_LEADERBOARD_CACHE_KEY = "top200";
    private final Cache<String, List<UserChallengeRankDto>> challengeDaysLeaderboardCache =
        Caffeine.newBuilder()
            .maximumSize(2)
            .expireAfterWrite(Duration.ofDays(1))
            .build();
    
    @Override
    public Optional<User> findById(UUID id) {
        logger.debug("查找用户，ID: {}", id);
        Map<String, Object> row = d1Util.findById("users", "id", EntityMapper.uuidToString(id));
        return row != null ? Optional.of(mapToUser(row)) : Optional.empty();
    }
    
    @Override
    public User save(User user) {
        logger.debug("保存用户: {}", user);
        if (user.getId() == null) {
            // 插入新记录
            user.setId(UUID.randomUUID());
            user.setRegistrationTime(OffsetDateTime.now());
            user.setChallengeResetTime(OffsetDateTime.now());
            user.setCreatedAt(OffsetDateTime.now());
            user.setUpdatedAt(OffsetDateTime.now());
            if (user.getBestRecord() == null) {
                user.setBestRecord(1);
            }
            Map<String, Object> data = userToMap(user);
            d1Util.insert("users", data);
            return user;
        } else {
            // 更新记录
            user.preUpdate();
            Map<String, Object> data = userToMap(user);
            d1Util.updateById("users", data, "id", EntityMapper.uuidToString(user.getId()));
            return user;
        }
    }
    
    @Override
    public User createUser(String nickname, String avatarUrl) {
        logger.info("创建新用户，昵称: {}", nickname);
        
        User user = new User(nickname);
        user.setAvatarUrl(avatarUrl);
        
        User savedUser = save(user);
        logger.info("用户创建成功，ID: {}", savedUser.getId());
        return savedUser;
    }
    
    @Override
    public User updateUser(User user) {
        logger.debug("更新用户信息: {}", user);
        user.preUpdate();
        return save(user);
    }
    
    @Override
    public void deleteUser(UUID id) {
        logger.info("删除用户，ID: {}", id);
        d1Util.deleteById("users", "id", EntityMapper.uuidToString(id));
    }
    
    @Override
    public List<User> searchByNickname(String nickname) {
        logger.debug("根据昵称搜索用户: {}", nickname);
        String sql = "SELECT * FROM users WHERE LOWER(nickname) LIKE LOWER(?) ORDER BY created_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, "%" + nickname + "%");
        return rows.stream().map(this::mapToUser).collect(Collectors.toList());
    }
    
    @Override
    public Optional<User> findByPhoneNumber(String phoneNumber) {
        logger.debug("根据手机号查询用户: {}", phoneNumber);
        String sql = "SELECT * FROM users WHERE phone_number = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, phoneNumber);
        return row != null ? Optional.of(mapToUser(row)) : Optional.empty();
    }
    
    @Override
    public List<User> findByRegistrationTimeBetween(OffsetDateTime startTime, OffsetDateTime endTime) {
        logger.debug("根据注册时间范围查询用户，开始时间: {}, 结束时间: {}", startTime, endTime);
        String sql = "SELECT * FROM users WHERE registration_time >= ? AND registration_time <= ? ORDER BY registration_time DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime));
        return rows.stream().map(this::mapToUser).collect(Collectors.toList());
    }
    
    @Override
    public List<User> getUsersNeedingChallengeReset() {
        logger.debug("获取需要重置挑战的用户");
        OffsetDateTime now = OffsetDateTime.now();
        String sql = "SELECT * FROM users WHERE challenge_reset_time <= ? ORDER BY challenge_reset_time ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, EntityMapper.offsetDateTimeToString(now));
        return rows.stream().map(this::mapToUser).collect(Collectors.toList());
    }
    
    @Override
    public void resetUserChallengeTime(UUID userId) {
        logger.info("重置用户挑战时间，用户ID: {}", userId);
        OffsetDateTime now = OffsetDateTime.now();
        
        // 一次性更新挑战时间和重启次数
        String sql = "UPDATE users SET challenge_reset_time = ?, " +
                    "restart_count = COALESCE(restart_count, 0) + 1, " +
                    "updated_at = ? " +
                    "WHERE id = ?";
        int updatedRows = d1Util.execute(sql, 
            EntityMapper.offsetDateTimeToString(now),
            EntityMapper.offsetDateTimeToString(now),
            EntityMapper.uuidToString(userId));
        
        if (updatedRows > 0) {
            logger.info("用户挑战时间重置成功，用户ID: {}, 已自动增加重启次数", userId);
        } else {
            logger.warn("用户不存在，无法重置挑战时间，用户ID: {}", userId);
        }
    }
    
    @Override
    public long countUsersByRegistrationTimeBetween(OffsetDateTime startTime, OffsetDateTime endTime) {
        logger.debug("统计指定时间范围内的注册用户数，开始时间: {}, 结束时间: {}", startTime, endTime);
        String sql = "SELECT COUNT(*) as count FROM users WHERE registration_time >= ? AND registration_time <= ?";
        return d1Util.queryLong(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime));
    }
    
    @Override
    public List<Object[]> countUsersByGender() {
        logger.debug("根据性别统计用户数");
        String sql = "SELECT gender, COUNT(*) as count FROM users WHERE gender IS NOT NULL GROUP BY gender";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream()
            .map(row -> new Object[]{
                EntityMapper.getInteger(row, "gender"),
                EntityMapper.getLong(row, "count")
            })
            .collect(Collectors.toList());
    }
    
    @Override
    public User updateBestRecord(UUID userId, Integer newRecord) {
        logger.info("更新用户最佳挑战记录，用户ID: {}, 新记录: {}", userId, newRecord);
        Optional<User> userOpt = findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setBestRecord(newRecord);
            user.preUpdate();
            User savedUser = save(user);
            logger.info("用户最佳挑战记录更新成功，用户ID: {}, 新记录: {}", userId, newRecord);
            return savedUser;
        } else {
            logger.warn("用户不存在，无法更新最佳挑战记录，用户ID: {}", userId);
            throw new RuntimeException("用户不存在，用户ID: " + userId);
        }
    }
    
    @Override
    public Integer getBestRecord(UUID userId) {
        logger.debug("获取用户最佳挑战记录，用户ID: {}", userId);
        Optional<User> userOpt = findById(userId);
        if (userOpt.isPresent()) {
            return userOpt.get().getBestRecord();
        } else {
            logger.warn("用户不存在，无法获取最佳挑战记录，用户ID: {}", userId);
            return null;
        }
    }
    
    @Override
    public List<User> getChallengeLeaderboard(int limit) {
        logger.debug("获取挑战记录排行榜，限制数量: {}", limit);
        String sql = "SELECT * FROM users WHERE best_record IS NOT NULL ORDER BY best_record DESC LIMIT ?";
        List<Map<String, Object>> rows = d1Util.queryList(sql, limit);
        return rows.stream().map(this::mapToUser).collect(Collectors.toList());
    }
    
    @Override
    public List<UserLeaderboardDto> getChallengeLeaderboardSimple(int limit) {
        logger.debug("获取挑战记录排行榜（简化版），限制数量: {}", limit);
        List<User> users = getChallengeLeaderboard(limit);
        
        return users.stream()
            .map(user -> new UserLeaderboardDto(user.getNickname(), user.getAvatarUrl(), user.getBestRecord()))
            .collect(Collectors.toList());
    }
    
    @Override
    public List<UserLeaderboardDto> getChallengeLeaderboardPage(int page, int size) {
        logger.debug("分页查询挑战记录排行榜，页码: {}, 每页大小: {}", page, size);
        
        String sql = "SELECT * FROM users WHERE best_record IS NOT NULL ORDER BY best_record DESC";
        List<Map<String, Object>> rows = d1Util.queryPage(sql, page + 1, size);
        List<User> users = rows.stream().map(this::mapToUser).collect(Collectors.toList());
        
        List<UserLeaderboardDto> result = users.stream()
            .map(user -> new UserLeaderboardDto(user.getNickname(), user.getAvatarUrl(), user.getBestRecord()))
            .collect(Collectors.toList());
        
        logger.debug("分页查询完成，当前页记录数: {}", result.size());
        return result;
    }
    
    @Override
    public boolean checkAndUpdateBestRecord(UUID userId, Integer currentRecord) {
        logger.debug("检查并更新用户最佳挑战记录，用户ID: {}, 当前记录: {}", userId, currentRecord);
        Optional<User> userOpt = findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            Integer currentBestRecord = user.getBestRecord();
            
            if (currentBestRecord == null || currentRecord > currentBestRecord) {
                user.setBestRecord(currentRecord);
                user.preUpdate();
                save(user);
                logger.info("用户最佳挑战记录已更新，用户ID: {}, 原记录: {}, 新记录: {}", 
                           userId, currentBestRecord, currentRecord);
                return true;
            } else {
                logger.debug("当前记录未超过最佳记录，无需更新，用户ID: {}, 当前记录: {}, 最佳记录: {}", 
                           userId, currentRecord, currentBestRecord);
                return false;
            }
        } else {
            logger.warn("用户不存在，无法检查最佳挑战记录，用户ID: {}", userId);
            return false;
        }
    }
    
    @Override
    public List<User> findByBestRecordBetween(Integer minRecord, Integer maxRecord) {
        logger.debug("根据最佳挑战记录范围查询用户，最小记录: {}, 最大记录: {}", minRecord, maxRecord);
        String sql = "SELECT * FROM users WHERE best_record >= ? AND best_record <= ? ORDER BY best_record DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, minRecord, maxRecord);
        return rows.stream().map(this::mapToUser).collect(Collectors.toList());
    }
    
    @Override
    public long countUsersByBestRecordGreaterThanOrEqualTo(Integer minRecord) {
        logger.debug("统计达到指定挑战记录的用户数量，最小记录: {}", minRecord);
        String sql = "SELECT COUNT(*) as count FROM users WHERE best_record >= ?";
        return d1Util.queryLong(sql, minRecord);
    }
    
    @Override
    public Long getUserRankInLeaderboard(UUID userId) {
        logger.debug("查询用户在挑战榜单中的排名，用户ID: {}", userId);
        
        // 首先检查用户是否存在且有最佳记录
        Optional<User> userOpt = findById(userId);
        if (!userOpt.isPresent()) {
            logger.warn("用户不存在，无法查询排名，用户ID: {}", userId);
            return null;
        }
        
        User user = userOpt.get();
        if (user.getBestRecord() == null) {
            logger.warn("用户没有最佳记录，无法查询排名，用户ID: {}", userId);
            return null;
        }
        
        // 查询排名：统计最佳记录大于当前用户的用户数量 + 1
        String sql = "SELECT COUNT(*) + 1 as rank FROM users WHERE best_record > " +
                    "(SELECT best_record FROM users WHERE id = ?) AND best_record IS NOT NULL";
        Long rank = d1Util.queryLong(sql, EntityMapper.uuidToString(userId));
        logger.debug("用户排名查询完成，用户ID: {}, 排名: {}", userId, rank);
        return rank;
    }
    
    @Override
    public UserRankDto getUserRankAndBestRecord(UUID userId) {
        logger.debug("查询用户排名和最佳成绩，用户ID: {}", userId);
        
        // 首先检查用户是否存在且有最佳记录
        Optional<User> userOpt = findById(userId);
        if (!userOpt.isPresent()) {
            logger.warn("用户不存在，无法查询排名和最佳成绩，用户ID: {}", userId);
            return null;
        }
        
        User user = userOpt.get();
        if (user.getBestRecord() == null) {
            logger.warn("用户没有最佳记录，无法查询排名和最佳成绩，用户ID: {}", userId);
            return null;
        }
        
        Long rank = getUserRankInLeaderboard(userId);
        UserRankDto result = new UserRankDto(rank, user.getBestRecord());
        
        logger.debug("用户排名和最佳成绩查询完成，用户ID: {}, 排名: {}, 最佳成绩: {}", 
                   userId, rank, user.getBestRecord());
        return result;
    }

    @Override
    public Long getUserRankByChallengeResetTime(UUID userId) {
        logger.debug("查询用户戒色排名（challenge_reset_time），用户ID: {}", userId);

        String sql =
            "SELECT " +
            "  (SELECT COUNT(*) + 1 FROM users u2 " +
            "   WHERE u2.challenge_reset_time IS NOT NULL AND ( " +
            "     u2.challenge_reset_time < u1.challenge_reset_time OR " +
            "     (u2.challenge_reset_time = u1.challenge_reset_time AND ( " +
            "        COALESCE(u2.created_at, u2.challenge_reset_time) < COALESCE(u1.created_at, u1.challenge_reset_time) OR " +
            "        (COALESCE(u2.created_at, u2.challenge_reset_time) = COALESCE(u1.created_at, u1.challenge_reset_time) AND u2.id < u1.id) " +
            "     )) " +
            "   ) " +
            "  ) AS rank " +
            "FROM users u1 " +
            "WHERE u1.id = ? AND u1.challenge_reset_time IS NOT NULL";

        Map<String, Object> row = d1Util.queryOne(sql, EntityMapper.uuidToString(userId));
        if (row == null || row.isEmpty()) {
            logger.warn("用户不存在或没有挑战开始时间，无法查询戒色排名，用户ID: {}", userId);
            return null;
        }

        Object value = row.values().iterator().next();
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            logger.warn("戒色排名解析失败，用户ID: {}, 原始值: {}", userId, value);
            return null;
        }
    }
    
    @Override
    public User bindPhoneNumber(UUID userId, String phoneNumber) {
        logger.info("绑定手机号码，用户ID: {}, 手机号: {}", userId, phoneNumber);
        
        // 首先检查手机号是否已经被其他用户绑定
        Optional<User> existingUser = findByPhoneNumber(phoneNumber);
        if (existingUser.isPresent()) {
            logger.info("手机号已被绑定，返回已绑定的用户信息，手机号: {}, 用户ID: {}", 
                       phoneNumber, existingUser.get().getId());
            return existingUser.get();
        }
        
        // 手机号未被绑定，查找指定用户并绑定手机号
        Optional<User> targetUser = findById(userId);
        if (!targetUser.isPresent()) {
            logger.error("目标用户不存在，无法绑定手机号，用户ID: {}", userId);
            throw new RuntimeException("用户不存在");
        }
        
        User user = targetUser.get();
        user.setPhoneNumber(phoneNumber);
        user.preUpdate();
        
        User savedUser = save(user);
        logger.info("手机号绑定成功，用户ID: {}, 手机号: {}", userId, phoneNumber);
        return savedUser;
    }
    
    @Override
    public User updateChallengeStartTime(String userId, OffsetDateTime newStartTime) {
        logger.info("更新用户挑战开始时间，用户ID: {}, 新开始时间: {}", userId, newStartTime);
        
        try {
            UUID uuid = UUID.fromString(userId);
            Optional<User> userOpt = findById(uuid);
            if (!userOpt.isPresent()) {
                logger.error("用户不存在，无法更新挑战开始时间，用户ID: {}", userId);
                throw new RuntimeException("用户不存在");
            }
            
            User user = userOpt.get();
            user.setChallengeResetTime(newStartTime);
            user.preUpdate();
            
            User savedUser = save(user);
            logger.info("挑战开始时间更新成功，用户ID: {}, 新开始时间: {}", userId, newStartTime);
            return savedUser;
        } catch (IllegalArgumentException e) {
            logger.error("用户ID格式无效: {}", userId);
            throw new RuntimeException("用户ID格式无效");
        }
    }
    
    @Override
    public User updateQuitReason(UUID userId, String quitReason) {
        logger.info("更新用户戒色原因，用户ID: {}, 戒色原因: {}", userId, quitReason);
        
        Optional<User> userOpt = findById(userId);
        if (!userOpt.isPresent()) {
            logger.error("用户不存在，无法更新戒色原因，用户ID: {}", userId);
            throw new RuntimeException("用户不存在");
        }
        
        User user = userOpt.get();
        user.setQuitReason(quitReason);
        user.preUpdate();
        
        User savedUser = save(user);
        logger.info("戒色原因更新成功，用户ID: {}, 戒色原因: {}", userId, quitReason);
        return savedUser;
    }

    @Override
    public List<UserChallengeRankDto> getChallengeDaysLeaderboardTop200() {
        List<UserChallengeRankDto> cached = challengeDaysLeaderboardCache.getIfPresent(CHALLENGE_DAYS_LEADERBOARD_CACHE_KEY);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        String sql = "SELECT id, nickname, avatar_url, challenge_reset_time, created_at " +
                     "FROM users " +
                     "WHERE challenge_reset_time IS NOT NULL " +
                     "ORDER BY challenge_reset_time ASC, created_at ASC, id ASC " +
                     "LIMIT 200";
        List<Map<String, Object>> rows = d1Util.queryList(sql);

        OffsetDateTime now = OffsetDateTime.now();
        List<UserChallengeRankDto> result = rows.stream()
            .map(row -> {
                OffsetDateTime resetTime = EntityMapper.getOffsetDateTime(row, "challenge_reset_time");
                long days = resetTime != null ? ChronoUnit.DAYS.between(resetTime, now) : 0;
                if (days < 0) {
                    days = 0;
                }
                return new UserChallengeRankDto(
                    EntityMapper.getString(row, "nickname"),
                    EntityMapper.getString(row, "avatar_url"),
                    days
                );
            })
            .collect(Collectors.toList());

        for (int i = 0; i < result.size(); i++) {
            result.get(i).setRank(i + 1);
        }

        if (!result.isEmpty()) {
            challengeDaysLeaderboardCache.put(CHALLENGE_DAYS_LEADERBOARD_CACHE_KEY, result);
        }
        return result;
    }
    
    /**
     * 将 Map 转换为 User 实体
     */
    private User mapToUser(Map<String, Object> row) {
        User user = new User();
        user.setId(EntityMapper.getUUID(row, "id"));
        user.setNickname(EntityMapper.getString(row, "nickname"));
        user.setAvatarUrl(EntityMapper.getString(row, "avatar_url"));
        user.setGender(EntityMapper.getInteger(row, "gender") != null ? 
            EntityMapper.getInteger(row, "gender").shortValue() : null);
        user.setLanguage(EntityMapper.getString(row, "language"));
        user.setPhoneNumber(EntityMapper.getString(row, "phone_number"));
        user.setRegistrationTime(EntityMapper.getOffsetDateTime(row, "registration_time"));
        user.setChallengeResetTime(EntityMapper.getOffsetDateTime(row, "challenge_reset_time"));
        user.setBestRecord(EntityMapper.getInteger(row, "best_record"));
        user.setQuitReason(EntityMapper.getString(row, "quit_reason"));
        user.setAge(EntityMapper.getInteger(row, "age"));
        user.setRestartCount(EntityMapper.getInteger(row, "restart_count"));
        user.setCreatedAt(EntityMapper.getOffsetDateTime(row, "created_at"));
        user.setUpdatedAt(EntityMapper.getOffsetDateTime(row, "updated_at"));
        return user;
    }
    
    /**
     * 将 User 实体转换为 Map（用于数据库操作）
     */
    private Map<String, Object> userToMap(User user) {
        Map<String, Object> data = new HashMap<>();
        EntityMapper.putIfNotNull(data, "id", user.getId());
        EntityMapper.putIfNotNull(data, "nickname", user.getNickname());
        EntityMapper.putIfNotNull(data, "avatar_url", user.getAvatarUrl());
        if (user.getGender() != null) {
            data.put("gender", user.getGender().intValue());
        }
        EntityMapper.putIfNotNull(data, "language", user.getLanguage());
        EntityMapper.putIfNotNull(data, "phone_number", user.getPhoneNumber());
        EntityMapper.putIfNotNull(data, "registration_time", user.getRegistrationTime());
        EntityMapper.putIfNotNull(data, "challenge_reset_time", user.getChallengeResetTime());
        EntityMapper.putIfNotNull(data, "best_record", user.getBestRecord());
        EntityMapper.putIfNotNull(data, "quit_reason", user.getQuitReason());
        EntityMapper.putIfNotNull(data, "age", user.getAge());
        EntityMapper.putIfNotNull(data, "restart_count", user.getRestartCount());
        EntityMapper.putIfNotNull(data, "created_at", user.getCreatedAt());
        EntityMapper.putIfNotNull(data, "updated_at", user.getUpdatedAt());
        return data;
    }
}
