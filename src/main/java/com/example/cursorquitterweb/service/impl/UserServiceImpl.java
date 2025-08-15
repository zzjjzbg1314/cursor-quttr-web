package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.entity.User;
import com.example.cursorquitterweb.repository.UserRepository;
import com.example.cursorquitterweb.service.UserService;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    @Transactional(readOnly = true)
    public Optional<User> findByWechatOpenid(String wechatOpenid) {
        logger.debug("根据微信openid查找用户: {}", wechatOpenid);
        return userRepository.findByWechatOpenid(wechatOpenid);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByWechatUnionid(String wechatUnionid) {
        logger.debug("根据微信unionid查找用户: {}", wechatUnionid);
        return userRepository.findByWechatUnionid(wechatUnionid);
    }
    
    @Override
    @Transactional
    public User save(User user) {
        logger.debug("保存用户: {}", user);
        return userRepository.save(user);
    }
    
    @Override
    @Transactional
    public User createUser(String wechatOpenid, String nickname, String avatarUrl) {
        logger.info("创建新用户，微信openid: {}, 昵称: {}", wechatOpenid, nickname);
        
        User user = new User(wechatOpenid);
        user.setNickname(nickname);
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
    public boolean existsByWechatOpenid(String wechatOpenid) {
        return userRepository.existsByWechatOpenid(wechatOpenid);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<User> searchByNickname(String nickname) {
        logger.debug("根据昵称搜索用户: {}", nickname);
        return userRepository.findByNicknameContainingIgnoreCase(nickname);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<User> findByCity(String city) {
        logger.debug("根据城市查询用户: {}", city);
        return userRepository.findByCity(city);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<User> findByProvince(String province) {
        logger.debug("根据省份查询用户: {}", province);
        return userRepository.findByProvince(province);
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
}
