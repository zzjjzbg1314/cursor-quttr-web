package com.example.cursorquitterweb.controller;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;
import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.VideoFileDto;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 视频控制器
 * 提供访问阿里云OSS存储的视频文件功能
 */
@RestController
@RequestMapping("/api/videos")
public class VideoController {
    
    private static final Logger logger = LogUtil.getLogger(VideoController.class);
    
    @Autowired
    private OSS ossClient;
    
    @Value("${aliyun.oss.bucket-name}")
    private String bucketName;
    
    @Value("${aliyun.oss.video-prefix}")
    private String videoPrefix;
    
    @Value("${aliyun.oss.url-expiration}")
    private Long urlExpiration;
    
    /**
     * 获取指定目录下的视频文件列表
     * @param directory 目录路径（可选，默认为根视频目录）
     * @return 视频文件列表，包含文件名和可播放的URL
     */
    @GetMapping("/list")
    public ApiResponse<List<VideoFileDto>> getVideoFiles(
            @RequestParam(required = false, defaultValue = "") String directory) {
        
        try {
            logger.info("获取视频文件列表，目录: {}", directory.isEmpty() ? "根目录" : directory);
            
            // 构建完整的目录前缀
            String fullPrefix = videoPrefix;
            if (!directory.isEmpty()) {
                // 确保目录以/结尾
                if (!directory.endsWith("/")) {
                    directory += "/";
                }
                fullPrefix += directory;
            }
            
            // 列出指定目录下的所有对象
            ObjectListing objectListing = ossClient.listObjects(bucketName, fullPrefix);
            List<OSSObjectSummary> summaries = objectListing.getObjectSummaries();
            
            List<VideoFileDto> videoFiles = new ArrayList<>();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            
            for (OSSObjectSummary summary : summaries) {
                String key = summary.getKey();
                
                // 跳过目录本身
                if (key.equals(fullPrefix)) {
                    continue;
                }
                
                // 只处理视频文件（可以根据需要扩展支持的格式）
                if (isVideoFile(key)) {
                    // 生成预签名URL，用于播放
                    Date expiration = new Date(System.currentTimeMillis() + urlExpiration * 1000);
                    URL playUrl = ossClient.generatePresignedUrl(bucketName, key, expiration);
                    
                    // 提取文件名（去掉路径前缀）
                    String fileName = extractFileName(key, fullPrefix);
                    
                    VideoFileDto videoFile = new VideoFileDto(
                        fileName,
                        playUrl.toString(),
                        summary.getSize(),
                        dateFormat.format(summary.getLastModified())
                    );
                    
                    videoFiles.add(videoFile);
                }
            }
            
            logger.info("成功获取视频文件列表，共找到 {} 个视频文件", videoFiles.size());
            return ApiResponse.success("获取视频文件列表成功", videoFiles);
            
        } catch (Exception e) {
            logger.error("获取视频文件列表失败，目录: {}, 错误: {}", directory, e.getMessage(), e);
            return ApiResponse.error("获取视频文件列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取指定视频文件的播放URL
     * @param filePath 文件路径（相对于视频根目录）
     * @return 视频文件的播放URL
     */
    @GetMapping("/play")
    public ApiResponse<String> getVideoPlayUrl(@RequestParam String filePath) {
        
        try {
            logger.info("获取视频播放URL，文件路径: {}", filePath);
            
            // 构建完整的文件键
            String fullKey = videoPrefix + filePath;
            
            // 检查文件是否存在
            if (!ossClient.doesObjectExist(bucketName, fullKey)) {
                logger.warn("视频文件不存在，文件路径: {}", filePath);
                return ApiResponse.error("视频文件不存在");
            }
            
            // 生成预签名URL
            Date expiration = new Date(System.currentTimeMillis() + urlExpiration * 1000);
            URL playUrl = ossClient.generatePresignedUrl(bucketName, fullKey, expiration);
            
            logger.info("成功生成视频播放URL，文件路径: {}", filePath);
            return ApiResponse.success("获取播放URL成功", playUrl.toString());
            
        } catch (Exception e) {
            logger.error("获取视频播放URL失败，文件路径: {}, 错误: {}", filePath, e.getMessage(), e);
            return ApiResponse.error("获取播放URL失败: " + e.getMessage());
        }
    }
    
    /**
     * 判断是否为视频文件
     * @param fileName 文件名
     * @return 是否为视频文件
     */
    private boolean isVideoFile(String fileName) {
        String lowerFileName = fileName.toLowerCase();
        return lowerFileName.endsWith(".mp4") || 
               lowerFileName.endsWith(".avi") || 
               lowerFileName.endsWith(".mov") || 
               lowerFileName.endsWith(".wmv") || 
               lowerFileName.endsWith(".flv") || 
               lowerFileName.endsWith(".webm") || 
               lowerFileName.endsWith(".mkv") ||
               lowerFileName.endsWith(".m4v");
    }
    
    /**
     * 从完整路径中提取文件名
     * @param fullPath 完整路径
     * @param prefix 前缀路径
     * @return 文件名
     */
    private String extractFileName(String fullPath, String prefix) {
        if (fullPath.startsWith(prefix)) {
            String relativePath = fullPath.substring(prefix.length());
            // 如果相对路径包含目录分隔符，取最后一部分作为文件名
            int lastSlashIndex = relativePath.lastIndexOf('/');
            if (lastSlashIndex >= 0) {
                return relativePath.substring(lastSlashIndex + 1);
            }
            return relativePath;
        }
        return fullPath;
    }
}
