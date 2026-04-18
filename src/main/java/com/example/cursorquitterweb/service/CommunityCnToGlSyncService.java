package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.dto.CommunityCnToGlSyncResult;

import java.time.LocalDate;

/**
 * 一次性同步国内社区数据到海外社区
 */
public interface CommunityCnToGlSyncService {

    CommunityCnToGlSyncResult syncSince(LocalDate startDate, boolean force);
}
