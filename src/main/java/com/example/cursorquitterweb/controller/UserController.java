package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.BindPhoneRequest;
import com.example.cursorquitterweb.dto.UpdateBestRecordRequest;
import com.example.cursorquitterweb.dto.UserLeaderboardDto;
import com.example.cursorquitterweb.dto.UserRankDto;
import com.example.cursorquitterweb.entity.User;
import com.example.cursorquitterweb.service.UserService;
import com.example.cursorquitterweb.service.RecoverJourneyService;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    private static final Logger logger = LogUtil.getLogger(UserController.class);
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private RecoverJourneyService recoverJourneyService;
    
    /**
     * 根据ID获取用户信息
     */
    @GetMapping("/{id}")
    public ApiResponse<User> getUserById(@PathVariable UUID id) {
        logger.info("获取用户信息，ID: {}", id);
        Optional<User> user = userService.findById(id);
        if (user.isPresent()) {
            return ApiResponse.success(user.get());
        } else {
            return ApiResponse.error("用户不存在");
        }
    }
    
    /**
     * 初始化用户信息
     * 返回一个包含所有基础字段信息的用户对象
     */
    @PostMapping("/init")
    public ApiResponse<User> initUser(@RequestBody InitUserRequest request) {
        logger.info("初始化用户信息，昵称: {}, 年龄: {}, 性别: {}", 
                    request.getNickname(), request.getAge(), request.getGender());
        
        // 生成1到1100之间的随机数字
        int randomNumber = (int) (Math.random() * 1100) + 1;
        String avatarUrl = "https://my-avatar-images.oss-cn-hangzhou.aliyuncs.com/images/mp1-bos.yinews.cn/" + randomNumber + ".jpg";
        
        User user = User.initUser();
        user.setNickname(request.getNickname());
        user.setAge(request.getAge());
        user.setGender(request.getGender());
        user.setAvatarUrl(avatarUrl);
        user.setRestartCount(0);
        User savedUser = userService.save(user);
        
        // 用户数据初始化完成后，创建一条康复记录
        try {
            recoverJourneyService.createRecoverJourney(savedUser.getId(), "今天注册了克己。");
            logger.info("为用户 {} 创建初始康复记录成功", savedUser.getId());
        } catch (Exception e) {
            logger.error("为用户 {} 创建初始康复记录失败: {}", savedUser.getId(), e.getMessage());
            // 不抛出异常，避免影响用户创建流程
        }
        
        return ApiResponse.success("用户初始化并保存成功", savedUser);
    }
    
    /**
     * 创建新用户
     */
    @PostMapping
    public ApiResponse<User> createUser(@RequestBody CreateUserRequest request) {
        logger.info("创建新用户，昵称: {}", request.getNickname());
        
        User user = userService.createUser(request.getNickname(), request.getAvatarUrl());
        if (request.getQuitReason() != null) {
            user.setQuitReason(request.getQuitReason());
        }
        if (request.getAge() != null) {
            user.setAge(request.getAge());
        }
        if (request.getRestartCount() != null) {
            user.setRestartCount(request.getRestartCount());
        }
        user = userService.updateUser(user);
        return ApiResponse.success("用户创建成功", user);
    }
    
    /**
     * 更新用户基本信息（昵称、年龄、性别）
     * POST /api/users/{id}/basic-info
     */
    @PostMapping("/{id}/basic-info")
    public ApiResponse<User> updateUserBasicInfo(@PathVariable UUID id, @RequestBody UpdateUserBasicInfoRequest request) {
        logger.info("更新用户基本信息，ID: {}, 昵称: {}, 年龄: {}, 性别: {}", 
                   id, request.getNickname(), request.getAge(), request.getGender());
        
        Optional<User> userOpt = userService.findById(id);
        if (!userOpt.isPresent()) {
            return ApiResponse.error("用户不存在");
        }
        
        User user = userOpt.get();
        
        // 更新昵称
        if (request.getNickname() != null && !request.getNickname().isEmpty()) {
            user.setNickname(request.getNickname());
        }
        
        // 更新年龄
        if (request.getAge() != null) {
            user.setAge(request.getAge());
        }
        
        // 更新性别
        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }
        
        User updatedUser = userService.updateUser(user);
        logger.info("用户基本信息更新成功，ID: {}", id);
        return ApiResponse.success("用户基本信息更新成功", updatedUser);
    }
    
    /**
     * 更新用户信息
     */
    @PutMapping("/{id}")
    public ApiResponse<User> updateUser(@PathVariable UUID id, @RequestBody UpdateUserRequest request) {
        logger.info("更新用户信息，ID: {}", id);
        
        Optional<User> userOpt = userService.findById(id);
        if (!userOpt.isPresent()) {
            return ApiResponse.error("用户不存在");
        }
        
        User user = userOpt.get();
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }
        if (request.getLanguage() != null) {
            user.setLanguage(request.getLanguage());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getQuitReason() != null) {
            user.setQuitReason(request.getQuitReason());
        }
        if (request.getAge() != null) {
            user.setAge(request.getAge());
        }
        if (request.getRestartCount() != null) {
            user.setRestartCount(request.getRestartCount());
        }
        
        User updatedUser = userService.updateUser(user);
        return ApiResponse.success("用户信息更新成功", updatedUser);
    }
    
    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable UUID id) {
        logger.info("删除用户，ID: {}", id);
        
        if (!userService.findById(id).isPresent()) {
            return ApiResponse.error("用户不存在");
        }
        
        userService.deleteUser(id);
        return ApiResponse.success("用户删除成功", null);
    }
    
    /**
     * 根据昵称搜索用户
     */
    @GetMapping("/search")
    public ApiResponse<List<User>> searchUsers(@RequestParam String nickname) {
        logger.info("搜索用户，昵称: {}", nickname);
        List<User> users = userService.searchByNickname(nickname);
        return ApiResponse.success(users);
    }
    
    /**
     * 根据手机号查询用户
     */
    @GetMapping("/phone/{phoneNumber}")
    public ApiResponse<User> getUserByPhoneNumber(@PathVariable String phoneNumber) {
        logger.info("根据手机号查询用户: {}", phoneNumber);
        Optional<User> user = userService.findByPhoneNumber(phoneNumber);
        if (user.isPresent()) {
            return ApiResponse.success(user.get());
        } else {
            return ApiResponse.error("用户不存在");
        }
    }
    
    /**
     * 根据注册时间范围查询用户
     */
    @GetMapping("/registration-time")
    public ApiResponse<List<User>> getUsersByRegistrationTime(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {
        logger.info("根据注册时间范围查询用户，开始时间: {}, 结束时间: {}", startTime, endTime);
        List<User> users = userService.findByRegistrationTimeBetween(startTime, endTime);
        return ApiResponse.success(users);
    }
    
    /**
     * 重置用户挑战时间
     */
    @PostMapping("/{id}/reset-challenge")
    public ApiResponse<Void> resetUserChallengeTime(@PathVariable String id) {
        logger.info("重置用户挑战时间，ID: {}", id);
        
        try {
            UUID userId = UUID.fromString(id);
            if (!userService.findById(userId).isPresent()) {
                return ApiResponse.error("用户不存在");
            }
            
            userService.resetUserChallengeTime(userId);
            return ApiResponse.success("挑战时间重置成功", null);
        } catch (IllegalArgumentException e) {
            logger.error("用户ID格式无效: {}", id);
            return ApiResponse.error("用户ID格式无效");
        }
    }
    
    /**
     * 获取用户统计信息
     */
    @GetMapping("/stats/registration")
    public ApiResponse<Long> getRegistrationStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {
        logger.info("获取注册统计信息，开始时间: {}, 结束时间: {}", startTime, endTime);
        long count = userService.countUsersByRegistrationTimeBetween(startTime, endTime);
        return ApiResponse.success(count);
    }
    
    /**
     * 获取性别统计信息
     */
    @GetMapping("/stats/gender")
    public ApiResponse<List<Object[]>> getGenderStats() {
        logger.info("获取性别统计信息");
        List<Object[]> stats = userService.countUsersByGender();
        return ApiResponse.success(stats);
    }
    
    /**
     * 获取用户最佳挑战记录
     */
    @GetMapping("/{id}/best-record")
    public ApiResponse<Integer> getUserBestRecord(@PathVariable UUID id) {
        logger.info("获取用户最佳挑战记录，ID: {}", id);
        
        if (!userService.findById(id).isPresent()) {
            return ApiResponse.error("用户不存在");
        }
        
        Integer bestRecord = userService.getBestRecord(id);
        return ApiResponse.success(bestRecord);
    }
    
    /**
     * 更新用户最佳挑战记录
     */
    @PutMapping("/{id}/best-record")
    public ApiResponse<User> updateUserBestRecord(@PathVariable UUID id, @RequestBody UpdateBestRecordRequest request) {
        logger.info("更新用户最佳挑战记录，ID: {}, 新记录: {}", id, request.getNewRecord());
        
        if (!userService.findById(id).isPresent()) {
            return ApiResponse.error("用户不存在");
        }
        
        try {
            User updatedUser = userService.updateBestRecord(id, request.getNewRecord());
            return ApiResponse.success("最佳挑战记录更新成功", updatedUser);
        } catch (Exception e) {
            logger.error("更新最佳挑战记录失败，用户ID: {}, 错误: {}", id, e.getMessage());
            return ApiResponse.error("更新失败: " + e.getMessage());
        }
    }
    
    /**
     * 检查并更新用户最佳挑战记录
     */
    @PostMapping("/{id}/check-best-record")
    public ApiResponse<Boolean> checkAndUpdateBestRecord(@PathVariable UUID id, @RequestBody UpdateBestRecordRequest request) {
        logger.info("检查并更新用户最佳挑战记录，ID: {}, 当前记录: {}", id, request.getNewRecord());
        
        if (!userService.findById(id).isPresent()) {
            return ApiResponse.error("用户不存在");
        }
        
        boolean updated = userService.checkAndUpdateBestRecord(id, request.getNewRecord());
        if (updated) {
            return ApiResponse.success("最佳挑战记录已更新", true);
        } else {
            return ApiResponse.success("当前记录未超过最佳记录", false);
        }
    }
    
    /**
     * 获取挑战记录排行榜
     */
    @GetMapping("/leaderboard")
    public ApiResponse<List<UserLeaderboardDto>> getChallengeLeaderboard(@RequestParam(defaultValue = "30") int limit) {
        logger.info("获取挑战记录排行榜，限制数量: {}", limit);
        
        if (limit <= 0 || limit > 100) {
            return ApiResponse.error("限制数量必须在1-100之间");
        }
        
        List<UserLeaderboardDto> leaderboard = userService.getChallengeLeaderboardSimple(limit);
        return ApiResponse.success(leaderboard);
    }
    
    /**
     * 分页查询挑战记录排行榜
     */
    @GetMapping("/leaderboard/page")
    public ApiResponse<org.springframework.data.domain.Page<UserLeaderboardDto>> getChallengeLeaderboardPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        logger.info("分页查询挑战记录排行榜，页码: {}, 每页大小: {}", page, size);
        
        if (page < 0) {
            return ApiResponse.error("页码不能小于0");
        }
        if (size <= 0 || size > 100) {
            return ApiResponse.error("每页大小必须在1-100之间");
        }
        
        org.springframework.data.domain.Page<UserLeaderboardDto> leaderboardPage = 
            userService.getChallengeLeaderboardPage(page, size);
        return ApiResponse.success(leaderboardPage);
    }
    
    /**
     * 根据挑战记录范围查询用户
     */
    @GetMapping("/best-record/range")
    public ApiResponse<List<User>> getUsersByBestRecordRange(
            @RequestParam Integer minRecord,
            @RequestParam Integer maxRecord) {
        logger.info("根据挑战记录范围查询用户，最小记录: {}, 最大记录: {}", minRecord, maxRecord);
        
        if (minRecord == null || maxRecord == null || minRecord < 0 || maxRecord < minRecord) {
            return ApiResponse.error("记录范围参数无效");
        }
        
        List<User> users = userService.findByBestRecordBetween(minRecord, maxRecord);
        return ApiResponse.success(users);
    }
    
    /**
     * 统计达到指定挑战记录的用户数量
     */
    @GetMapping("/stats/best-record")
    public ApiResponse<Long> getBestRecordStats(@RequestParam Integer minRecord) {
        logger.info("统计达到指定挑战记录的用户数量，最小记录: {}", minRecord);
        
        if (minRecord == null || minRecord < 0) {
            return ApiResponse.error("最小记录参数无效");
        }
        
        long count = userService.countUsersByBestRecordGreaterThanOrEqualTo(minRecord);
        return ApiResponse.success(count);
    }
    
    /**
     * 更新用户挑战开始时间
     */
    @PutMapping("/{id}/challenge-start-time")
    public ApiResponse<User> updateChallengeStartTime(@PathVariable String id, @RequestBody UpdateChallengeStartTimeRequest request) {
        logger.info("更新用户挑战开始时间，用户ID: {}", id);
        
        try {
            OffsetDateTime newStartTime = OffsetDateTime.parse(request.getNewStartTime());
            User updatedUser = userService.updateChallengeStartTime(id, newStartTime);
            return ApiResponse.success("挑战开始时间更新成功", updatedUser);
        } catch (DateTimeParseException e) {
            logger.error("时间格式解析失败: {}", request.getNewStartTime());
            return ApiResponse.error("时间格式无效，请使用ISO-8601格式");
        } catch (Exception e) {
            logger.error("更新挑战开始时间失败: {}", e.getMessage());
            return ApiResponse.error("更新失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新用户戒色原因
     */
    @PutMapping("/{id}/quit-reason")
    public ApiResponse<User> updateQuitReason(@PathVariable UUID id, @RequestBody UpdateQuitReasonRequest request) {
        logger.info("更新用户戒色原因，用户ID: {}", id);
        
        try {
            User updatedUser = userService.updateQuitReason(id, request.getQuitReason());
            return ApiResponse.success("戒色原因更新成功", updatedUser);
        } catch (Exception e) {
            logger.error("更新戒色原因失败: {}", e.getMessage());
            return ApiResponse.error("更新失败: " + e.getMessage());
        }
    }
    



    

    /**
     * 获取用户挑战开始时间
     */
    @GetMapping("/{id}/challenge-start-time")
    public ApiResponse<OffsetDateTime> getChallengeStartTime(@PathVariable UUID id) {
        logger.info("获取用户挑战开始时间，ID: {}", id);
        
        Optional<User> userOpt = userService.findById(id);
        if (!userOpt.isPresent()) {
            return ApiResponse.error("用户不存在");
        }
        
        User user = userOpt.get();
        return ApiResponse.success("获取挑战开始时间成功", user.getChallengeResetTime());
    }
    
    /**
     * 查询用户在挑战榜单中的排名
     */
    @GetMapping("/{id}/rank")
    public ApiResponse<UserRankDto> getUserRankInLeaderboard(@PathVariable UUID id) {
        logger.info("查询用户挑战榜单排名，ID: {}", id);
        
                 UserRankDto rankInfo = userService.getUserRankAndBestRecord(id);
        if (rankInfo != null) {
            return ApiResponse.success("排名查询成功", rankInfo);
        } else {
            return ApiResponse.error("用户不存在或没有最佳记录");
        }
    }
    
    /**
     * 绑定手机号码
     * 根据手机号查询是否已存在绑定的用户
     * 如果存在，返回已绑定的用户信息
     * 如果不存在，将手机号绑定到指定用户
     */
    @PostMapping("/bind-phone")
    public ApiResponse<User> bindPhoneNumber(@RequestBody BindPhoneRequest request) {
        logger.info("绑定手机号码，用户ID: {}, 手机号: {}", request.getUserId(), request.getPhoneNumber());
        
        try {
            // 将String类型的userId转换为UUID
            UUID userId = UUID.fromString(request.getUserId());
            User resultUser = userService.bindPhoneNumber(userId, request.getPhoneNumber());
            return ApiResponse.success("手机号绑定成功", resultUser);
        } catch (IllegalArgumentException e) {
            logger.error("用户ID格式无效: {}", request.getUserId());
            return ApiResponse.error("用户ID格式无效");
        } catch (Exception e) {
            logger.error("手机号绑定失败，用户ID: {}, 手机号: {}, 错误: {}", 
                request.getUserId(), request.getPhoneNumber(), e.getMessage());
            return ApiResponse.error("绑定失败: " + e.getMessage());
        }
    }
    
    /**
     * 初始化用户请求DTO
     */
    public static class InitUserRequest {
        private String nickname;
        private Integer age;
        private Short gender;
        
        // Getters and Setters
        public String getNickname() {
            return nickname;
        }
        
        public void setNickname(String nickname) {
            this.nickname = nickname;
        }
        
        public Integer getAge() {
            return age;
        }
        
        public void setAge(Integer age) {
            this.age = age;
        }
        
        public Short getGender() {
            return gender;
        }
        
        public void setGender(Short gender) {
            this.gender = gender;
        }
    }
    
    /**
     * 创建用户请求DTO
     */
    public static class CreateUserRequest {
        private String nickname;
        private String avatarUrl;
        private String quitReason;
        private Integer age;
        private Integer restartCount;
        
        // Getters and Setters
        public String getNickname() {
            return nickname;
        }
        
        public void setNickname(String nickname) {
            this.nickname = nickname;
        }
        
        public String getAvatarUrl() {
            return avatarUrl;
        }
        
        public void setAvatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
        }
        
        public String getQuitReason() {
            return quitReason;
        }
        
        public void setQuitReason(String quitReason) {
            this.quitReason = quitReason;
        }
        
        public Integer getAge() {
            return age;
        }
        
        public void setAge(Integer age) {
            this.age = age;
        }
        
        public Integer getRestartCount() {
            return restartCount;
        }
        
        public void setRestartCount(Integer restartCount) {
            this.restartCount = restartCount;
        }
    }
    
    /**
     * 更新用户请求DTO
     */
    public static class UpdateUserRequest {
        private String nickname;
        private String avatarUrl;
        private Short gender;
        private String language;
        private String phoneNumber;
        private String quitReason;
        private Integer age;
        private Integer restartCount;
        
        // Getters and Setters
        public String getNickname() {
            return nickname;
        }
        
        public void setNickname(String nickname) {
            this.nickname = nickname;
        }
        
        public String getAvatarUrl() {
            return avatarUrl;
        }
        
        public void setAvatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
        }
        
        public Short getGender() {
            return gender;
        }
        
        public void setGender(Short gender) {
            this.gender = gender;
        }
        
        public String getLanguage() {
            return language;
        }
        
        public void setLanguage(String language) {
            this.language = language;
        }
        
        public String getPhoneNumber() {
            return phoneNumber;
        }
        
        public void setPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
        }
        
        public String getQuitReason() {
            return quitReason;
        }
        
        public void setQuitReason(String quitReason) {
            this.quitReason = quitReason;
        }
        
        public Integer getAge() {
            return age;
        }
        
        public void setAge(Integer age) {
            this.age = age;
        }
        
        public Integer getRestartCount() {
            return restartCount;
        }
        
        public void setRestartCount(Integer restartCount) {
            this.restartCount = restartCount;
        }
    }
    
    /**
     * 更新挑战开始时间请求DTO
     */
    public static class UpdateChallengeStartTimeRequest {
        private String newStartTime;
        
        // Getters and Setters
        public String getNewStartTime() {
            return newStartTime;
        }
        
        public void setNewStartTime(String newStartTime) {
            this.newStartTime = newStartTime;
        }
    }

    /**
     * 更新用户戒色原因请求DTO
     */
    public static class UpdateQuitReasonRequest {
        private String quitReason;

        // Getters and Setters
        public String getQuitReason() {
            return quitReason;
        }

        public void setQuitReason(String quitReason) {
            this.quitReason = quitReason;
        }
    }
    
    /**
     * 更新用户基本信息请求DTO
     */
    public static class UpdateUserBasicInfoRequest {
        private String nickname;
        private Integer age;
        private Short gender;
        
        // Getters and Setters
        public String getNickname() {
            return nickname;
        }
        
        public void setNickname(String nickname) {
            this.nickname = nickname;
        }
        
        public Integer getAge() {
            return age;
        }
        
        public void setAge(Integer age) {
            this.age = age;
        }
        
        public Short getGender() {
            return gender;
        }
        
        public void setGender(Short gender) {
            this.gender = gender;
        }
    }

}
