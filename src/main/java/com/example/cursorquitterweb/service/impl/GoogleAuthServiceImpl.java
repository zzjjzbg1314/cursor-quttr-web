package com.example.cursorquitterweb.service.impl;

import com.example.cursorquitterweb.dto.GoogleLoginRequest;
import com.example.cursorquitterweb.dto.GoogleLoginResponse;
import com.example.cursorquitterweb.entity.User;
import com.example.cursorquitterweb.entity.UserIdentity;
import com.example.cursorquitterweb.service.GoogleAuthService;
import com.example.cursorquitterweb.service.RecoverJourneyService;
import com.example.cursorquitterweb.service.UserIdentityService;
import com.example.cursorquitterweb.service.UserService;
import com.example.cursorquitterweb.util.GoogleJwtUtil;
import com.example.cursorquitterweb.util.LogUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.security.SecureRandom;

/**
 * Google 登录服务实现类
 */
@Service
public class GoogleAuthServiceImpl implements GoogleAuthService {
    
    private static final Logger logger = LogUtil.getLogger(GoogleAuthServiceImpl.class);
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private UserIdentityService userIdentityService;
    
    @Autowired
    private RecoverJourneyService recoverJourneyService;
    
    /**
     * Google Client ID（可以从配置文件读取）
     */
    @Value("${google.client-id:}")
    private String googleClientId;
    
    @Override
    public GoogleLoginResponse login(GoogleLoginRequest request) {
        logger.info("处理 Google 登录请求，google_user_id: {}", request.getGoogleUserId());
        
        // 1. 验证 ID Token
        String verifiedGoogleUserId = verifyIdToken(request.getIdToken());
        
        if (verifiedGoogleUserId == null) {
            logger.error("ID Token 验证失败");
            throw new RuntimeException("ID Token 验证失败");
        }
        
        // 验证 google_user_id 是否与 token 中的 sub 一致
        if (!verifiedGoogleUserId.equals(request.getGoogleUserId())) {
            logger.error("Google User ID 不匹配，请求: {}, token: {}", 
                        request.getGoogleUserId(), verifiedGoogleUserId);
            throw new RuntimeException("Google User ID 不匹配");
        }
        
        logger.info("ID Token 验证成功，google_user_id: {}", verifiedGoogleUserId);
        
        // 2. 根据 Google User ID 查找用户
        UUID userId = userIdentityService.findUserIdByIdentity(
            UserIdentity.IdentityType.GOOGLE,
            request.getGoogleUserId()
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
            // 新用户，创建账号（参考 Apple 登录流程）
            logger.info("新用户注册，创建账号");
            isNewUser = true;
            
            // 生成1到30之间的随机数字用于头像
            int randomNumber = (int) (Math.random() * 30) + 1;
            String avatarUrl = "https://nofaponline.us/images/xiaohongshu/" + randomNumber + ".jpg";
            
            // 如果提供了 Google 头像，优先使用
            if (request.getPicture() != null && !request.getPicture().isEmpty()) {
                avatarUrl = request.getPicture();
            }
            
            // 使用 User.initUser() 初始化用户
            user = User.initUser();
            
            // 生成并设置昵称
            String nickname = generateNickname(request);
            user.setNickname(nickname);
            
            // 设置头像
            user.setAvatarUrl(avatarUrl);
            
            // 设置 restartCount 为 0
            user.setRestartCount(0);
            
            // 保存用户
            user = userService.save(user);
            
            logger.info("用户创建成功，user_id: {}, nickname: {}", user.getId(), nickname);
            
            // 如果提供了邮箱，记录日志
            if (request.getEmail() != null && !request.getEmail().isEmpty()) {
                logger.info("用户邮箱: {}", request.getEmail());
            }
            
            // 绑定 Google 身份
            Map<String, Object> identityData = new HashMap<>();
            identityData.put("google_user_id", request.getGoogleUserId());
            if (request.getEmail() != null) {
                identityData.put("email", request.getEmail());
            }
            if (request.getName() != null) {
                identityData.put("name", request.getName());
            }
            if (request.getGivenName() != null) {
                identityData.put("given_name", request.getGivenName());
            }
            if (request.getFamilyName() != null) {
                identityData.put("family_name", request.getFamilyName());
            }
            if (request.getPicture() != null) {
                identityData.put("picture", request.getPicture());
            }
            
            try {
                ObjectMapper mapper = new ObjectMapper();
                String identityDataJson = mapper.writeValueAsString(identityData);
                
                userIdentityService.createIdentity(
                    user.getId(),
                    UserIdentity.IdentityType.GOOGLE,
                    request.getGoogleUserId(),
                    identityDataJson
                );
                
                logger.info("Google 身份绑定成功，user_id: {}", user.getId());
                
            } catch (Exception e) {
                logger.error("创建身份数据失败", e);
                throw new RuntimeException("创建身份数据失败");
            }
            
            // 创建注册日记
            try {
                recoverJourneyService.createRecoverJourney(user.getId(), "Registered Nofapr today");
                logger.info("注册日记创建成功，user_id: {}", user.getId());
            } catch (Exception e) {
                logger.error("创建注册日记失败，user_id: {}", user.getId(), e);
                // 不抛出异常，避免影响登录流程
            }
        }
        
        // 3. 构建响应
        GoogleLoginResponse response = new GoogleLoginResponse(user, isNewUser);
        
        logger.info("Google 登录成功，user_id: {}, is_new_user: {}", user.getId(), isNewUser);
        
        return response;
    }
    
    @Override
    public String verifyIdToken(String idToken) {
        logger.debug("验证 Google ID Token");
        
        // 使用 GoogleJwtUtil 验证 token
        // 如果配置了 googleClientId，则验证 audience；否则跳过 audience 验证
        String googleUserId = GoogleJwtUtil.verifyIdToken(idToken, googleClientId);
        
        if (googleUserId != null) {
            logger.info("ID Token 验证成功，google_user_id: {}", googleUserId);
        } else {
            logger.error("ID Token 验证失败");
        }
        
        return googleUserId;
    }
    
    /**
     * 生成昵称
     */
    private String generateNickname(GoogleLoginRequest request) {
        // 如果提供了姓名，优先使用
        if (request.getName() != null && !request.getName().isEmpty()) {
            return request.getName();
        }
        
        // 如果有 given_name 和 family_name，组合使用
        if (request.getGivenName() != null && !request.getGivenName().isEmpty()) {
            String nickname = request.getGivenName();
            if (request.getFamilyName() != null && !request.getFamilyName().isEmpty()) {
                nickname += " " + request.getFamilyName();
            }
            return nickname;
        }
        
        // 否则生成随机昵称
        final String allowedCharacters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        final int nicknameLength = 10;
        SecureRandom secureRandom = new SecureRandom();
        StringBuilder nicknameBuilder = new StringBuilder(nicknameLength);
        for (int i = 0; i < nicknameLength; i++) {
            int randomIndex = secureRandom.nextInt(allowedCharacters.length());
            nicknameBuilder.append(allowedCharacters.charAt(randomIndex));
        }
        return nicknameBuilder.toString();
    }
}

