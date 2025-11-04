package com.example.cursorquitterweb.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.example.cursorquitterweb.service.OssService;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * OSS服务实现类
 */
@Service
public class OssServiceImpl implements OssService {
    
    private static final Logger logger = LogUtil.getLogger(OssServiceImpl.class);
    
    @Autowired
    private OSS ossClient;
    
    @Value("${aliyun_oss.bucket_name}")
    private String bucketName;
    
    @Value("${aliyun_oss.endpoint}")
    private String endpoint;
    
    @Value("${aliyun_oss.avatar_prefix:avatars/}")
    private String avatarPrefix;
    
    // 允许的图片类型 - 支持所有image/*类型
    private static final String[] ALLOWED_CONTENT_TYPES = {
        "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp",
        "image/bmp", "image/svg+xml", "image/x-icon", "image/tiff", "image/heic", "image/heif"
    };
    
    // 最大文件大小 5MB
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    
    @Override
    public String uploadAvatar(MultipartFile file) throws Exception {
        logger.info("开始上传用户头像");
        
        // 验证文件
        validateFile(file);
        
        // 生成文件路径
        String fileName = generateFileName(file);
        String objectKey = avatarPrefix + fileName;
        
        try (InputStream inputStream = file.getInputStream()) {
            // 设置对象元数据
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());
            metadata.setContentDisposition("inline");
            
            // 上传文件到OSS
            ossClient.putObject(bucketName, objectKey, inputStream, metadata);
            
            // 生成访问URL
            String url = generateUrl(objectKey);
            
            logger.info("头像上传成功，OSS路径: {}, URL: {}", objectKey, url);
            return url;
        } catch (Exception e) {
            logger.error("头像上传失败，错误: {}", e.getMessage(), e);
            throw new Exception("上传头像失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 验证文件
     */
    private void validateFile(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        
        // 检查文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小不能超过5MB");
        }
        
        // 检查文件类型 - 支持所有image/*类型
        String contentType = file.getContentType();
        if (contentType == null || contentType.isEmpty()) {
            throw new IllegalArgumentException("无法识别文件类型，请确保文件格式正确");
        }
        
        // 首先检查是否是image类型
        boolean isImageType = contentType.toLowerCase().startsWith("image/");
        
        // 如果不是image类型，再检查是否在允许列表中（兼容一些特殊情况）
        boolean isValidType = isImageType;
        
        if (!isValidType) {
            // 检查是否在允许的特定类型列表中
            for (String allowedType : ALLOWED_CONTENT_TYPES) {
                if (allowedType.equalsIgnoreCase(contentType)) {
                    isValidType = true;
                    break;
                }
            }
        }
        
        if (!isValidType) {
            logger.warn("不支持的文件类型: {}，文件名: {}", contentType, file.getOriginalFilename());
            throw new IllegalArgumentException("不支持的文件类型: " + contentType + "，仅支持图片格式（image/*）");
        }
        
        logger.debug("文件类型验证通过: {}, 文件名: {}", contentType, file.getOriginalFilename());
    }
    
    /**
     * 生成文件名
     */
    private String generateFileName(MultipartFile file) {
        // 获取文件扩展名
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        } else {
            // 根据content-type推断扩展名
            String contentType = file.getContentType();
            if (contentType != null) {
                contentType = contentType.toLowerCase();
                if (contentType.contains("jpeg") || contentType.contains("jpg")) {
                    extension = ".jpg";
                } else if (contentType.contains("png")) {
                    extension = ".png";
                } else if (contentType.contains("gif")) {
                    extension = ".gif";
                } else if (contentType.contains("webp")) {
                    extension = ".webp";
                } else if (contentType.contains("bmp")) {
                    extension = ".bmp";
                } else if (contentType.contains("svg")) {
                    extension = ".svg";
                } else if (contentType.contains("icon") || contentType.contains("x-icon")) {
                    extension = ".ico";
                } else if (contentType.contains("tiff")) {
                    extension = ".tiff";
                } else if (contentType.contains("heic")) {
                    extension = ".heic";
                } else if (contentType.contains("heif")) {
                    extension = ".heif";
                } else {
                    // 默认使用.jpg
                    extension = ".jpg";
                }
            } else {
                // 如果没有content-type，默认使用.jpg
                extension = ".jpg";
            }
        }
        
        // 生成文件名: timestamp_uuid.ext
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return timestamp + "_" + uuid + extension;
    }
    
    /**
     * 生成访问URL
     */
    private String generateUrl(String objectKey) {
        // 获取endpoint的域名部分
        String endpointDomain = endpoint;
        // 移除协议前缀（如果存在）
        if (endpointDomain.startsWith("https://")) {
            endpointDomain = endpointDomain.substring(8);
        } else if (endpointDomain.startsWith("http://")) {
            endpointDomain = endpointDomain.substring(7);
        }
        
        // 构建URL: https://bucket-name.endpoint/objectKey
        return "https://" + bucketName + "." + endpointDomain + "/" + objectKey;
    }
}

