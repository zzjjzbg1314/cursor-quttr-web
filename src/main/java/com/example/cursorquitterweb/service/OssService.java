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

    /**
     * 上传用户头像到指定OSS路径
     * @param file 头像文件
     * @param objectKey OSS对象路径
     * @return 上传后的图片URL
     * @throws Exception 上传失败时抛出异常
     */
    String uploadAvatar(MultipartFile file, String objectKey) throws Exception;
}
