package com.example.cursorquitterweb.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Min;

/**
 * 更新用户最佳挑战记录请求DTO
 */
@Data
public class UpdateBestRecordRequest {
    
    /**
     * 新的挑战记录天数
     */
    @NotNull(message = "挑战记录天数不能为空")
    @Min(value = 1, message = "挑战记录天数必须大于等于1")
    private Integer newRecord;
    
    /**
     * 用户ID（可选，如果不提供则从当前登录用户获取）
     */
    private String userId;
}
