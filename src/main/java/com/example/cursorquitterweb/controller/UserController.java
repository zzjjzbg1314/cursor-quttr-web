package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.UpdateBestRecordRequest;
import com.example.cursorquitterweb.dto.UserLeaderboardDto;
import com.example.cursorquitterweb.dto.UserRankDto;
import com.example.cursorquitterweb.entity.User;
import com.example.cursorquitterweb.service.UserService;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
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
     * 根据微信openid获取用户信息
     */
    @GetMapping("/wechat/{openid}")
    public ApiResponse<User> getUserByWechatOpenid(@PathVariable String openid) {
        logger.info("根据微信openid获取用户信息: {}", openid);
        Optional<User> user = userService.findByWechatOpenid(openid);
        if (user.isPresent()) {
            return ApiResponse.success(user.get());
        } else {
            return ApiResponse.error("用户不存在");
        }
    }
    
    /**
     * 创建新用户
     */
    @PostMapping
    public ApiResponse<User> createUser(@RequestBody CreateUserRequest request) {
        logger.info("创建新用户，微信openid: {}, 昵称: {}", request.getWechatOpenid(), request.getNickname());
        
        if (userService.existsByWechatOpenid(request.getWechatOpenid())) {
            return ApiResponse.error("用户已存在");
        }
        
        User user = userService.createUser(request.getWechatOpenid(), request.getNickname(), request.getAvatarUrl());
        return ApiResponse.success("用户创建成功", user);
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
        if (request.getCountry() != null) {
            user.setCountry(request.getCountry());
        }
        if (request.getProvince() != null) {
            user.setProvince(request.getProvince());
        }
        if (request.getCity() != null) {
            user.setCity(request.getCity());
        }
        if (request.getLanguage() != null) {
            user.setLanguage(request.getLanguage());
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
     * 根据城市查询用户
     */
    @GetMapping("/city/{city}")
    public ApiResponse<List<User>> getUsersByCity(@PathVariable String city) {
        logger.info("根据城市查询用户: {}", city);
        List<User> users = userService.findByCity(city);
        return ApiResponse.success(users);
    }
    
    /**
     * 根据省份查询用户
     */
    @GetMapping("/province/{province}")
    public ApiResponse<List<User>> getUsersByProvince(@PathVariable String province) {
        logger.info("根据省份查询用户: {}", province);
        List<User> users = userService.findByProvince(province);
        return ApiResponse.success(users);
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
    public ApiResponse<Void> resetUserChallengeTime(@PathVariable UUID id) {
        logger.info("重置用户挑战时间，ID: {}", id);
        
        if (!userService.findById(id).isPresent()) {
            return ApiResponse.error("用户不存在");
        }
        
        userService.resetUserChallengeTime(id);
        return ApiResponse.success("挑战时间重置成功", null);
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
     * 创建用户请求DTO
     */
    public static class CreateUserRequest {
        private String wechatOpenid;
        private String nickname;
        private String avatarUrl;
        
        // Getters and Setters
        public String getWechatOpenid() {
            return wechatOpenid;
        }
        
        public void setWechatOpenid(String wechatOpenid) {
            this.wechatOpenid = wechatOpenid;
        }
        
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
    }
    
    /**
     * 更新用户请求DTO
     */
    public static class UpdateUserRequest {
        private String nickname;
        private String avatarUrl;
        private Short gender;
        private String country;
        private String province;
        private String city;
        private String language;
        
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
        
        public String getCountry() {
            return country;
        }
        
        public void setCountry(String country) {
            this.country = country;
        }
        
        public String getProvince() {
            return province;
        }
        
        public void setProvince(String province) {
            this.province = province;
        }
        
        public String getCity() {
            return city;
        }
        
        public void setCity(String city) {
            this.city = city;
        }
        
        public String getLanguage() {
            return language;
        }
        
        public void setLanguage(String language) {
            this.language = language;
        }
    }
}
