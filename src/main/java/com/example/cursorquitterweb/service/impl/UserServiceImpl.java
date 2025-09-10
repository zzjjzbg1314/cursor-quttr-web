package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.entity.User;
import com.example.cursorquitterweb.repository.UserRepository;
import com.example.cursorquitterweb.service.UserService;
import com.example.cursorquitterweb.util.LogUtil;
import com.example.cursorquitterweb.dto.UserLeaderboardDto;
import com.example.cursorquitterweb.dto.UserRankDto;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户服务实现类
 */
@Service
public class UserServiceImpl implements UserService {
    
    private static final Logger logger = LogUtil.getLogger(UserServiceImpl.class);
    
    @Autowired
    private UserRepository userRepository;
    
    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(UUID id) {
        logger.debug("查找用户，ID: {}", id);
        return userRepository.findById(id);
    }
    
    @Override
    @Transactional
    public User save(User user) {
        logger.debug("保存用户: {}", user);
        return userRepository.save(user);
    }
    
    @Override
    @Transactional
    public User createUser(String nickname, String avatarUrl) {
        logger.info("创建新用户，昵称: {}", nickname);
        
        User user = new User(nickname);
        user.setAvatarUrl(avatarUrl);
        
        User savedUser = userRepository.save(user);
        logger.info("用户创建成功，ID: {}", savedUser.getId());
        return savedUser;
    }
    
    @Override
    @Transactional
    public User updateUser(User user) {
        logger.debug("更新用户信息: {}", user);
        user.preUpdate();
        return userRepository.save(user);
    }
    
    @Override
    @Transactional
    public void deleteUser(UUID id) {
        logger.info("删除用户，ID: {}", id);
        userRepository.deleteById(id);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<User> searchByNickname(String nickname) {
        logger.debug("根据昵称搜索用户: {}", nickname);
        return userRepository.findByNicknameContainingIgnoreCase(nickname);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByPhoneNumber(String phoneNumber) {
        logger.debug("根据手机号查询用户: {}", phoneNumber);
        return userRepository.findByPhoneNumber(phoneNumber);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<User> findByRegistrationTimeBetween(OffsetDateTime startTime, OffsetDateTime endTime) {
        logger.debug("根据注册时间范围查询用户，开始时间: {}, 结束时间: {}", startTime, endTime);
        return userRepository.findByRegistrationTimeBetween(startTime, endTime);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<User> getUsersNeedingChallengeReset() {
        logger.debug("获取需要重置挑战的用户");
        OffsetDateTime now = OffsetDateTime.now();
        return userRepository.findUsersNeedingChallengeReset(now);
    }
    
    @Override
    @Transactional
    public void resetUserChallengeTime(UUID userId) {
        logger.info("重置用户挑战时间，用户ID: {}", userId);
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setChallengeResetTime(OffsetDateTime.now());
            userRepository.save(user);
            logger.info("用户挑战时间重置成功，用户ID: {}", userId);
        } else {
            logger.warn("用户不存在，无法重置挑战时间，用户ID: {}", userId);
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public long countUsersByRegistrationTimeBetween(OffsetDateTime startTime, OffsetDateTime endTime) {
        logger.debug("统计指定时间范围内的注册用户数，开始时间: {}, 结束时间: {}", startTime, endTime);
        return userRepository.countUsersByRegistrationTimeBetween(startTime, endTime);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Object[]> countUsersByGender() {
        logger.debug("根据性别统计用户数");
        return userRepository.countUsersByGender();
    }
    
    @Override
    @Transactional
    public User updateBestRecord(UUID userId, Integer newRecord) {
        logger.info("更新用户最佳挑战记录，用户ID: {}, 新记录: {}", userId, newRecord);
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setBestRecord(newRecord);
            user.preUpdate();
            User savedUser = userRepository.save(user);
            logger.info("用户最佳挑战记录更新成功，用户ID: {}, 新记录: {}", userId, newRecord);
            return savedUser;
        } else {
            logger.warn("用户不存在，无法更新最佳挑战记录，用户ID: {}", userId);
            throw new RuntimeException("用户不存在，用户ID: " + userId);
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public Integer getBestRecord(UUID userId) {
        logger.debug("获取用户最佳挑战记录，用户ID: {}", userId);
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            return userOpt.get().getBestRecord();
        } else {
            logger.warn("用户不存在，无法获取最佳挑战记录，用户ID: {}", userId);
            return null;
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<User> getChallengeLeaderboard(int limit) {
        logger.debug("获取挑战记录排行榜，限制数量: {}", limit);
        org.springframework.data.domain.PageRequest pageRequest = 
            org.springframework.data.domain.PageRequest.of(0, limit);
        return userRepository.findTopUsersByBestRecordOrderByBestRecordDesc(pageRequest);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<UserLeaderboardDto> getChallengeLeaderboardSimple(int limit) {
        logger.debug("获取挑战记录排行榜（简化版），限制数量: {}", limit);
        List<User> users = getChallengeLeaderboard(limit);
        
        return users.stream()
            .map(user -> new UserLeaderboardDto(user.getNickname(), user.getAvatarUrl(), user.getBestRecord()))
            .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<UserLeaderboardDto> getChallengeLeaderboardPage(int page, int size) {
        logger.debug("分页查询挑战记录排行榜，页码: {}, 每页大小: {}", page, size);
        
        // 创建分页请求
        org.springframework.data.domain.PageRequest pageRequest = 
            org.springframework.data.domain.PageRequest.of(page, size);
        
        // 查询分页数据
        org.springframework.data.domain.Page<User> userPage = 
            userRepository.findTopUsersByBestRecordOrderByBestRecordDescPage(pageRequest);
        
        // 转换为DTO并保持分页信息
        org.springframework.data.domain.Page<UserLeaderboardDto> dtoPage = userPage.map(
            user -> new UserLeaderboardDto(user.getNickname(), user.getAvatarUrl(), user.getBestRecord())
        );
        
        logger.debug("分页查询完成，总记录数: {}, 当前页记录数: {}", dtoPage.getTotalElements(), dtoPage.getContent().size());
        return dtoPage;
    }
    
    @Override
    @Transactional
    public boolean checkAndUpdateBestRecord(UUID userId, Integer currentRecord) {
        logger.debug("检查并更新用户最佳挑战记录，用户ID: {}, 当前记录: {}", userId, currentRecord);
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            Integer currentBestRecord = user.getBestRecord();
            
            if (currentBestRecord == null || currentRecord > currentBestRecord) {
                user.setBestRecord(currentRecord);
                user.preUpdate();
                userRepository.save(user);
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
    @Transactional(readOnly = true)
    public List<User> findByBestRecordBetween(Integer minRecord, Integer maxRecord) {
        logger.debug("根据最佳挑战记录范围查询用户，最小记录: {}, 最大记录: {}", minRecord, maxRecord);
        return userRepository.findByBestRecordBetween(minRecord, maxRecord);
    }
    
    @Override
    @Transactional(readOnly = true)
    public long countUsersByBestRecordGreaterThanOrEqualTo(Integer minRecord) {
        logger.debug("统计达到指定挑战记录的用户数量，最小记录: {}", minRecord);
        return userRepository.countUsersByBestRecordGreaterThanOrEqualTo(minRecord);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Long getUserRankInLeaderboard(UUID userId) {
        logger.debug("查询用户在挑战榜单中的排名，用户ID: {}", userId);
        
        // 首先检查用户是否存在且有最佳记录
        Optional<User> userOpt = userRepository.findById(userId);
        if (!userOpt.isPresent()) {
            logger.warn("用户不存在，无法查询排名，用户ID: {}", userId);
            return null;
        }
        
        User user = userOpt.get();
        if (user.getBestRecord() == null) {
            logger.warn("用户没有最佳记录，无法查询排名，用户ID: {}", userId);
            return null;
        }
        
        Long rank = userRepository.findUserRankInLeaderboard(userId);
        logger.debug("用户排名查询完成，用户ID: {}, 排名: {}", userId, rank);
        return rank;
    }
    
    @Override
    @Transactional(readOnly = true)
    public UserRankDto getUserRankAndBestRecord(UUID userId) {
        logger.debug("查询用户排名和最佳成绩，用户ID: {}", userId);
        
        // 首先检查用户是否存在且有最佳记录
        Optional<User> userOpt = userRepository.findById(userId);
        if (!userOpt.isPresent()) {
            logger.warn("用户不存在，无法查询排名和最佳成绩，用户ID: {}", userId);
            return null;
        }
        
        User user = userOpt.get();
        if (user.getBestRecord() == null) {
            logger.warn("用户没有最佳记录，无法查询排名和最佳成绩，用户ID: {}", userId);
            return null;
        }
        
        Long rank = userRepository.findUserRankInLeaderboard(userId);
        UserRankDto result = new UserRankDto(rank, user.getBestRecord());
        
        logger.debug("用户排名和最佳成绩查询完成，用户ID: {}, 排名: {}, 最佳成绩: {}", 
                   userId, rank, user.getBestRecord());
        return result;
    }
    
    @Override
    @Transactional
    public User bindPhoneNumber(UUID userId, String phoneNumber) {
        logger.info("绑定手机号码，用户ID: {}, 手机号: {}", userId, phoneNumber);
        
        // 首先检查手机号是否已经被其他用户绑定
        Optional<User> existingUser = userRepository.findByPhoneNumber(phoneNumber);
        if (existingUser.isPresent()) {
            logger.info("手机号已被绑定，返回已绑定的用户信息，手机号: {}, 用户ID: {}", 
                       phoneNumber, existingUser.get().getId());
            return existingUser.get();
        }
        
        // 手机号未被绑定，查找指定用户并绑定手机号
        Optional<User> targetUser = userRepository.findById(userId);
        if (!targetUser.isPresent()) {
            logger.error("目标用户不存在，无法绑定手机号，用户ID: {}", userId);
            throw new RuntimeException("用户不存在");
        }
        
        User user = targetUser.get();
        user.setPhoneNumber(phoneNumber);
        user.preUpdate();
        
        User savedUser = userRepository.save(user);
        logger.info("手机号绑定成功，用户ID: {}, 手机号: {}", userId, phoneNumber);
        return savedUser;
    }
    
    @Override
    @Transactional
    public User updateChallengeStartTime(String userId, OffsetDateTime newStartTime) {
        logger.info("更新用户挑战开始时间，用户ID: {}, 新开始时间: {}", userId, newStartTime);
        
        try {
            UUID uuid = UUID.fromString(userId);
            Optional<User> userOpt = userRepository.findById(uuid);
            if (!userOpt.isPresent()) {
                logger.error("用户不存在，无法更新挑战开始时间，用户ID: {}", userId);
                throw new RuntimeException("用户不存在");
            }
            
            User user = userOpt.get();
            user.setChallengeResetTime(newStartTime);
            user.preUpdate();
            
            User savedUser = userRepository.save(user);
            logger.info("挑战开始时间更新成功，用户ID: {}, 新开始时间: {}", userId, newStartTime);
            return savedUser;
        } catch (IllegalArgumentException e) {
            logger.error("用户ID格式无效: {}", userId);
            throw new RuntimeException("用户ID格式无效");
        }
    }
    
    @Override
    @Transactional
    public User updateQuitReason(UUID userId, String quitReason) {
        logger.info("更新用户戒色原因，用户ID: {}, 戒色原因: {}", userId, quitReason);
        
        Optional<User> userOpt = userRepository.findById(userId);
        if (!userOpt.isPresent()) {
            logger.error("用户不存在，无法更新戒色原因，用户ID: {}", userId);
            throw new RuntimeException("用户不存在");
        }
        
        User user = userOpt.get();
        user.setQuitReason(quitReason);
        user.preUpdate();
        
        User savedUser = userRepository.save(user);
        logger.info("戒色原因更新成功，用户ID: {}, 戒色原因: {}", userId, quitReason);
        return savedUser;
    }
}
