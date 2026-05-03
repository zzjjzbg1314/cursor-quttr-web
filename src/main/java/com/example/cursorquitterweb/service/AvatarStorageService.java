package com.example.cursorquitterweb.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 用户头像存储服务
 */
public interface AvatarStorageService {

    /**
     * 并行上传用户头像到海外 R2 和国内 OSS。
     * R2 成功即返回 R2 URL，OSS 失败只记录日志。
     *
     * @param file 头像文件
     * @return R2 海外访问 URL
     * @throws Exception R2 上传失败时抛出异常
     */
    String uploadAvatar(MultipartFile file) throws Exception;
}
