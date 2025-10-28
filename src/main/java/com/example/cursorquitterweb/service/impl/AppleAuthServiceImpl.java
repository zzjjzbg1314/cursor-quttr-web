package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.dto.AppleLoginRequest;
import com.example.cursorquitterweb.dto.AppleLoginResponse;
import com.example.cursorquitterweb.entity.User;
import com.example.cursorquitterweb.entity.UserIdentity;
import com.example.cursorquitterweb.service.AppleAuthService;
import com.example.cursorquitterweb.service.UserIdentityService;
import com.example.cursorquitterweb.service.UserService;
import com.example.cursorquitterweb.util.AppleJwtUtil;
import com.example.cursorquitterweb.util.LogUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Apple 登录服务实现类
 */
@Service
public class AppleAuthServiceImpl implements AppleAuthService {
    
    private static final Logger logger = LogUtil.getLogger(AppleAuthServiceImpl.class);
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private UserIdentityService userIdentityService;
    
    /**
     * Apple App ID / Client ID（可以从配置文件读取）
     * 例如：com.yourcompany.yourapp
     */
    @Value("${apple.client-id:}")
    private String appleClientId;
    
    @Override
    @Transactional
    public AppleLoginResponse login(AppleLoginRequest request) {
        logger.info("处理 Apple 登录请求，apple_user_id: {}", request.getAppleUserId());
        
        // 1. 验证 Identity Token
        String verifiedAppleUserId = verifyIdentityToken(request.getIdentityToken());
        
        if (verifiedAppleUserId == null) {
            logger.error("Identity Token 验证失败");
            throw new RuntimeException("Identity Token 验证失败");
        }
        
        // 验证 apple_user_id 是否与 token 中的 sub 一致
        if (!verifiedAppleUserId.equals(request.getAppleUserId())) {
            logger.error("Apple User ID 不匹配，请求: {}, token: {}", 
                        request.getAppleUserId(), verifiedAppleUserId);
            throw new RuntimeException("Apple User ID 不匹配");
        }
        
        logger.info("Identity Token 验证成功，apple_user_id: {}", verifiedAppleUserId);
        
        // 2. 根据 Apple User ID 查找用户
        UUID userId = userIdentityService.findUserIdByIdentity(
            UserIdentity.IdentityType.APPLE,
            request.getAppleUserId()
        );
        
        boolean isNewUser = false;
        User user;
        
        if (userId != null) {
            // 用户已存在，直接返回
            logger.info("用户已存在，user_id: {}", userId);
            Optional<User> userOpt = userService.findById(userId);
            
            if (!userOpt.isPresent()) {
                logger.error("用户数据不一致，身份存在但用户不存在，user_id: {}", userId);
                throw new RuntimeException("用户数据不一致");
            }
            
            user = userOpt.get();
            
            // 更新登录时间（通过 preUpdate 自动更新 updated_at）
            user.preUpdate();
            user = userService.save(user);
            
        } else {
            // 新用户，创建账号（参考 UserController.initUser 方法）
            logger.info("新用户注册，创建账号");
            isNewUser = true;
            
            // 生成1到1100之间的随机数字用于头像
            int randomNumber = (int) (Math.random() * 1100) + 1;
            String avatarUrl = "https://my-avatar-images.oss-cn-hangzhou.aliyuncs.com/images/mp1-bos.yinews.cn/" + randomNumber + ".jpg";
            
            // 使用 User.initUser() 初始化用户
            user = User.initUser();
            
            // 生成并设置昵称
            String nickname = generateNickname(request);
            user.setNickname(nickname);
            
            // 设置随机头像
            user.setAvatarUrl(avatarUrl);
            
            // 设置 restartCount 为 0
            user.setRestartCount(0);
            
            // 保存用户
            user = userService.save(user);
            
            logger.info("用户创建成功，user_id: {}, nickname: {}", user.getId(), nickname);
            
            // 如果提供了邮箱，记录日志
            // 注意：邮箱可能为空，因为 Apple 只在首次授权时提供
            if (request.getEmail() != null && !request.getEmail().isEmpty()) {
                logger.info("用户邮箱: {}", request.getEmail());
            }
            
            // 绑定 Apple 身份
            Map<String, Object> identityData = new HashMap<>();
            identityData.put("apple_user_id", request.getAppleUserId());
            if (request.getEmail() != null) {
                identityData.put("email", request.getEmail());
            }
            if (request.getGivenName() != null) {
                identityData.put("given_name", request.getGivenName());
            }
            if (request.getFamilyName() != null) {
                identityData.put("family_name", request.getFamilyName());
            }
            
            try {
                ObjectMapper mapper = new ObjectMapper();
                String identityDataJson = mapper.writeValueAsString(identityData);
                
                userIdentityService.createIdentity(
                    user.getId(),
                    UserIdentity.IdentityType.APPLE,
                    request.getAppleUserId(),
                    identityDataJson
                );
                
                logger.info("Apple 身份绑定成功，user_id: {}", user.getId());
                
            } catch (Exception e) {
                logger.error("创建身份数据失败", e);
                throw new RuntimeException("创建身份数据失败");
            }
        }
        
        // 3. 构建响应
        AppleLoginResponse response = new AppleLoginResponse(user, isNewUser);
        
        logger.info("Apple 登录成功，user_id: {}, is_new_user: {}", user.getId(), isNewUser);
        
        return response;
    }
    
    @Override
    public String verifyIdentityToken(String identityToken) {
        logger.debug("验证 Apple Identity Token");
        
        // 使用 AppleJwtUtil 验证 token
        // 如果配置了 appleClientId，则验证 audience；否则跳过 audience 验证
        String appleUserId = AppleJwtUtil.verifyIdentityToken(identityToken, appleClientId);
        
        if (appleUserId != null) {
            logger.info("Identity Token 验证成功，apple_user_id: {}", appleUserId);
        } else {
            logger.error("Identity Token 验证失败");
        }
        
        return appleUserId;
    }
    
    /**
     * 生成昵称
     */
    private String generateNickname(AppleLoginRequest request) {
        // 优先使用用户提供的名字
        if (request.getGivenName() != null && !request.getGivenName().isEmpty()) {
            String nickname = request.getGivenName();
            if (request.getFamilyName() != null && !request.getFamilyName().isEmpty()) {
                nickname = request.getFamilyName() + nickname;
            }
            return nickname;
        }
        
        // 如果没有提供名字，使用默认昵称
        return "Apple用户" + System.currentTimeMillis() % 10000;
    }
}

