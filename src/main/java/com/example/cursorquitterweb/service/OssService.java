package com.example.cursorquitterweb.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * OSS服务接口
 */
public interface OssService {
    
    /**
     * 上传用户头像到OSS
     * @param file 头像文件
     * @return 上传后的图片URL
     * @throws Exception 上传失败时抛出异常
     */
    String uploadAvatar(MultipartFile file) throws Exception;
}

