package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.dto.CreateChangeReasonRequest;
import com.example.cursorquitterweb.dto.UpdateChangeReasonRequest;
import com.example.cursorquitterweb.entity.ChangeReason;

import java.util.List;
import java.util.UUID;

/**
 * 用户戒色改变理由服务接口
 */
public interface ChangeReasonService {
    
    /**
     * 创建改变理由记录
     * @param request 创建请求
     * @return 创建的改变理由记录
     */
    ChangeReason createChangeReason(CreateChangeReasonRequest request);
    
    /**
     * 根据ID获取改变理由记录
     * @param id 记录ID
     * @return 改变理由记录
     */
    ChangeReason getChangeReasonById(UUID id);
    
    /**
     * 根据用户ID获取所有改变理由记录
     * @param userId 用户ID
     * @return 改变理由记录列表
     */
    List<ChangeReason> getChangeReasonsByUserId(UUID userId);
    
    /**
     * 获取用户最近的改变理由记录
     * @param userId 用户ID
     * @return 最近的改变理由记录
     */
    ChangeReason getLatestChangeReasonByUserId(UUID userId);
    
    /**
     * 更新改变理由记录
     * @param request 更新请求
     * @return 更新后的改变理由记录
     */
    ChangeReason updateChangeReason(UpdateChangeReasonRequest request);
    
    /**
     * 删除改变理由记录
     * @param id 记录ID
     */
    void deleteChangeReason(UUID id);
    
    /**
     * 删除用户的所有改变理由记录
     * @param userId 用户ID
     */
    void deleteChangeReasonsByUserId(UUID userId);
    
    /**
     * 统计用户的改变理由记录数量
     * @param userId 用户ID
     * @return 记录数量
     */
    long countChangeReasonsByUserId(UUID userId);
}
