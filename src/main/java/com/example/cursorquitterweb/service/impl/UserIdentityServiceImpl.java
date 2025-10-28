package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.entity.UserIdentity;
import com.example.cursorquitterweb.repository.UserIdentityRepository;
import com.example.cursorquitterweb.service.UserIdentityService;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 用户身份服务实现类
 */
@Service
public class UserIdentityServiceImpl implements UserIdentityService {
    
    private static final Logger logger = LogUtil.getLogger(UserIdentityServiceImpl.class);
    
    @Autowired
    private UserIdentityRepository userIdentityRepository;
    
    @Override
    @Transactional
    public UserIdentity createIdentity(UUID userId, String identityType, String identityId, String identityData) {
        logger.info("创建用户身份，用户ID: {}, 身份类型: {}, 身份ID: {}", userId, identityType, identityId);
        
        // 检查是否已存在
        if (userIdentityRepository.existsByIdentityTypeAndIdentityId(identityType, identityId)) {
            logger.warn("身份已存在，身份类型: {}, 身份ID: {}", identityType, identityId);
            throw new RuntimeException("该身份已被绑定");
        }
        
        UserIdentity userIdentity = new UserIdentity(userId, identityType, identityId);
        userIdentity.setIdentityData(identityData);
        
        UserIdentity savedIdentity = userIdentityRepository.save(userIdentity);
        logger.info("用户身份创建成功，ID: {}", savedIdentity.getId());
        return savedIdentity;
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<UserIdentity> findById(Long id) {
        logger.debug("查找用户身份，ID: {}", id);
        return userIdentityRepository.findById(id);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<UserIdentity> findByUserId(UUID userId) {
        logger.debug("查找用户的所有身份，用户ID: {}", userId);
        return userIdentityRepository.findByUserId(userId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<UserIdentity> findByIdentityTypeAndIdentityId(String identityType, String identityId) {
        logger.debug("根据身份类型和ID查找，类型: {}, ID: {}", identityType, identityId);
        return userIdentityRepository.findByIdentityTypeAndIdentityId(identityType, identityId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Optional<UserIdentity> findByUserIdAndIdentityType(UUID userId, String identityType) {
        logger.debug("根据用户ID和身份类型查找，用户ID: {}, 类型: {}", userId, identityType);
        return userIdentityRepository.findByUserIdAndIdentityType(userId, identityType);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<UserIdentity> findByIdentityType(String identityType) {
        logger.debug("根据身份类型查找所有用户身份，类型: {}", identityType);
        return userIdentityRepository.findByIdentityType(identityType);
    }
    
    @Override
    @Transactional(readOnly = true)
    public boolean existsByIdentityTypeAndIdentityId(String identityType, String identityId) {
        logger.debug("检查身份是否存在，类型: {}, ID: {}", identityType, identityId);
        return userIdentityRepository.existsByIdentityTypeAndIdentityId(identityType, identityId);
    }
    
    @Override
    @Transactional
    public UserIdentity updateIdentityData(Long id, String identityData) {
        logger.info("更新用户身份数据，身份ID: {}", id);
        
        Optional<UserIdentity> identityOpt = userIdentityRepository.findById(id);
        if (!identityOpt.isPresent()) {
            logger.error("用户身份不存在，无法更新，身份ID: {}", id);
            throw new RuntimeException("用户身份不存在");
        }
        
        UserIdentity userIdentity = identityOpt.get();
        userIdentity.setIdentityData(identityData);
        
        UserIdentity savedIdentity = userIdentityRepository.save(userIdentity);
        logger.info("用户身份数据更新成功，身份ID: {}", id);
        return savedIdentity;
    }
    
    @Override
    @Transactional
    public UserIdentity bindOrUpdateIdentity(UUID userId, String identityType, String identityId, String identityData) {
        logger.info("绑定或更新用户身份，用户ID: {}, 身份类型: {}, 身份ID: {}", userId, identityType, identityId);
        
        // 检查该身份是否已存在
        Optional<UserIdentity> existingIdentity = userIdentityRepository
            .findByIdentityTypeAndIdentityId(identityType, identityId);
        
        if (existingIdentity.isPresent()) {
            UserIdentity identity = existingIdentity.get();
            logger.info("身份已存在，返回已绑定的用户ID: {}", identity.getUserId());
            
            // 如果身份数据不同，更新身份数据
            if (identityData != null && !identityData.equals(identity.getIdentityData())) {
                identity.setIdentityData(identityData);
                return userIdentityRepository.save(identity);
            }
            return identity;
        }
        
        // 身份不存在，创建新绑定
        UserIdentity newIdentity = new UserIdentity(userId, identityType, identityId);
        newIdentity.setIdentityData(identityData);
        
        UserIdentity savedIdentity = userIdentityRepository.save(newIdentity);
        logger.info("用户身份绑定成功，身份ID: {}", savedIdentity.getId());
        return savedIdentity;
    }
    
    @Override
    @Transactional
    public void deleteIdentity(Long id) {
        logger.info("删除用户身份，身份ID: {}", id);
        userIdentityRepository.deleteById(id);
    }
    
    @Override
    @Transactional
    public void deleteByUserId(UUID userId) {
        logger.info("删除用户的所有身份，用户ID: {}", userId);
        userIdentityRepository.deleteByUserId(userId);
    }
    
    @Override
    @Transactional
    public void deleteByUserIdAndIdentityType(UUID userId, String identityType) {
        logger.info("删除用户的指定类型身份，用户ID: {}, 身份类型: {}", userId, identityType);
        userIdentityRepository.deleteByUserIdAndIdentityType(userId, identityType);
    }
    
    @Override
    @Transactional(readOnly = true)
    public long countByUserId(UUID userId) {
        logger.debug("统计用户的身份数量，用户ID: {}", userId);
        return userIdentityRepository.countByUserId(userId);
    }
    
    @Override
    @Transactional(readOnly = true)
    public long countDistinctUsersByIdentityType(String identityType) {
        logger.debug("统计指定身份类型的用户数量，身份类型: {}", identityType);
        return userIdentityRepository.countDistinctUsersByIdentityType(identityType);
    }
    
    @Override
    @Transactional(readOnly = true)
    public UUID findUserIdByIdentity(String identityType, String identityId) {
        logger.debug("根据身份查找用户ID，身份类型: {}, 身份ID: {}", identityType, identityId);
        
        Optional<UserIdentity> identityOpt = userIdentityRepository
            .findByIdentityTypeAndIdentityId(identityType, identityId);
        
        if (identityOpt.isPresent()) {
            UUID userId = identityOpt.get().getUserId();
            logger.debug("找到用户ID: {}", userId);
            return userId;
        }
        
        logger.debug("未找到对应的用户");
        return null;
    }
}

