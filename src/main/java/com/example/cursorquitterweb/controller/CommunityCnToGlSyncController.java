package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.CommunityCnToGlSyncRequest;
import com.example.cursorquitterweb.dto.CommunityCnToGlSyncResult;
import com.example.cursorquitterweb.service.CommunityCnToGlSyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/gl/community-sync")
public class CommunityCnToGlSyncController {

    @Autowired
    private CommunityCnToGlSyncService communityCnToGlSyncService;

    @PostMapping("/cn-to-gl")
    public ApiResponse<CommunityCnToGlSyncResult> syncCnToGl(
        @RequestBody(required = false) CommunityCnToGlSyncRequest request) {
        try {
            String startDate = request != null && request.getStartDate() != null && !request.getStartDate().trim().isEmpty()
                ? request.getStartDate().trim()
                : "2026-04-01";
            boolean force = request != null && Boolean.TRUE.equals(request.getForce());

            CommunityCnToGlSyncResult result = communityCnToGlSyncService.syncSince(LocalDate.parse(startDate), force);
            return ApiResponse.success("国内社区数据同步到海外社区成功", result);
        } catch (Exception e) {
            return ApiResponse.error("国内社区数据同步失败: " + e.getMessage());
        }
    }
}
