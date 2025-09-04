package com.example.cursorquitterweb.repository;

import com.example.cursorquitterweb.entity.VideoScenario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 视频场景数据访问接口
 */
@Repository
public interface VideoScenarioRepository extends JpaRepository<VideoScenario, UUID> {
    
    /**
     * 根据视频ID查找视频场景
     */
    Optional<VideoScenario> findByVideoId(UUID videoId);
    
    /**
     * 根据类型查找视频场景，按创建时间正序排列
     */
    List<VideoScenario> findByTypeOrderByCreateAtAsc(String type);
    
    /**
     * 根据类型分页查找视频场景，按创建时间倒序排列
     */
    Page<VideoScenario> findByTypeOrderByCreateAtDesc(String type, Pageable pageable);
    
    /**
     * 根据标题模糊查询视频场景，按创建时间倒序排列
     */
    List<VideoScenario> findByTitleContainingIgnoreCaseOrderByCreateAtDesc(String title);
    
    /**
     * 根据副标题模糊查询视频场景，按创建时间倒序排列
     */
    List<VideoScenario> findBySubtitleContainingIgnoreCaseOrderByCreateAtDesc(String subtitle);
    
    /**
     * 根据颜色查找视频场景，按创建时间倒序排列
     */
    List<VideoScenario> findByColorOrderByCreateAtDesc(String color);
    
    /**
     * 根据作者查找视频场景，按创建时间倒序排列
     */
    List<VideoScenario> findByAuthorOrderByCreateAtDesc(String author);
    
    /**
     * 查找所有视频场景，按创建时间倒序排列
     */
    List<VideoScenario> findAllByOrderByCreateAtDesc();
    
    /**
     * 分页查找所有视频场景，支持动态排序
     */
    Page<VideoScenario> findAll(Pageable pageable);
    
    /**
     * 分页查找所有视频场景，按创建时间倒序排列
     */
    Page<VideoScenario> findAllByOrderByCreateAtDesc(Pageable pageable);
    
    /**
     * 根据创建时间范围查找视频场景，按创建时间倒序排列
     */
    List<VideoScenario> findByCreateAtBetweenOrderByCreateAtDesc(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 根据更新时间范围查找视频场景，按更新时间倒序排列
     */
    List<VideoScenario> findByUpdateAtBetweenOrderByUpdateAtDesc(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 统计指定类型的视频场景数量
     */
    long countByType(String type);
    
    /**
     * 统计指定时间范围内的视频场景数量
     */
    long countByCreateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 根据类型和标题模糊查询视频场景
     */
    List<VideoScenario> findByTypeAndTitleContainingIgnoreCaseOrderByCreateAtDesc(String type, String title);
    
    /**
     * 查找有音频URL的视频场景
     */
    List<VideoScenario> findByAudiourlIsNotNullOrderByCreateAtDesc();
    
    /**
     * 查找有视频URL的视频场景
     */
    List<VideoScenario> findByVideourlIsNotNullOrderByCreateAtDesc();
    
    /**
     * 查找有封面图片的视频场景
     */
    List<VideoScenario> findByImageIsNotNullOrderByCreateAtDesc();
}
