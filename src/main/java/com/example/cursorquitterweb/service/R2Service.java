package com.example.cursorquitterweb.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Cloudflare R2 存储服务接口
 */
public interface R2Service {
    
    /**
     * 上传用户头像到 R2
     * @param file 头像文件
     * @return 上传后的图片URL
     * @throws Exception 上传失败时抛出异常
     */
    String uploadAvatar(MultipartFile file) throws Exception;

    /**
     * 上传用户头像到指定 R2 路径
     * @param file 头像文件
     * @param objectKey R2对象路径
     * @return 上传后的图片URL
     * @throws Exception 上传失败时抛出异常
     */
    String uploadAvatar(MultipartFile file, String objectKey) throws Exception;
}
