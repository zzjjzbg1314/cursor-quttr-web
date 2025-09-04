package com.example.cursorquitterweb.repository;

import com.example.cursorquitterweb.entity.Video;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 视频数据访问接口
 */
@Repository
public interface VideoRepository extends JpaRepository<Video, UUID> {
    
    /**
     * 根据ID查找视频
     */
    Optional<Video> findById(UUID id);
    
    /**
     * 根据标题模糊查询视频，按创建时间倒序排列
     */
    List<Video> findByTitleContainingIgnoreCaseOrderByCreateAtDesc(String title);
    
    /**
     * 查找所有视频，按创建时间倒序排列
     */
    List<Video> findAllByOrderByCreateAtDesc();
    
    /**
     * 分页查找所有视频，支持动态排序
     */
    Page<Video> findAll(Pageable pageable);
    
    /**
     * 分页查找所有视频，按创建时间倒序排列
     */
    Page<Video> findAllByOrderByCreateAtDesc(Pageable pageable);
    
    /**
     * 根据创建时间范围查找视频，按创建时间倒序排列
     */
    List<Video> findByCreateAtBetweenOrderByCreateAtDesc(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 根据更新时间范围查找视频，按更新时间倒序排列
     */
    List<Video> findByUpdateAtBetweenOrderByUpdateAtDesc(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 统计指定时间范围内的视频数量
     */
    long countByCreateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 根据播放链接查找视频
     */
    Optional<Video> findByPlayurl(String playurl);
    
    /**
     * 根据海报链接查找视频
     */
    Optional<Video> findByPosturl(String posturl);
    
    /**
     * 查找有播放链接的视频
     */
    List<Video> findByPlayurlIsNotNullOrderByCreateAtDesc();
    
    /**
     * 查找有海报链接的视频
     */
    List<Video> findByPosturlIsNotNullOrderByCreateAtDesc();
    
    /**
     * 根据标题精确查询视频
     */
    Optional<Video> findByTitle(String title);
    
    /**
     * 统计视频总数
     */
    long count();
}
