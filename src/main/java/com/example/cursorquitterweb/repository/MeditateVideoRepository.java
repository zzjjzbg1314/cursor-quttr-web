package com.example.cursorquitterweb.repository;

import com.example.cursorquitterweb.entity.MeditateVideo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 冥想视频数据访问接口
 */
@Repository
public interface MeditateVideoRepository extends JpaRepository<MeditateVideo, UUID> {
    
    /**
     * 根据ID查找冥想视频（包含关联数据）
     */
    @Query("SELECT mv FROM MeditateVideo mv LEFT JOIN FETCH mv.meditateQuotes WHERE mv.id = :id")
    Optional<MeditateVideo> findByIdWithQuotes(@Param("id") UUID id);
    
    /**
     * 查找所有冥想视频（包含关联数据）
     */
    @Query("SELECT DISTINCT mv FROM MeditateVideo mv LEFT JOIN FETCH mv.meditateQuotes ORDER BY mv.createAt DESC")
    List<MeditateVideo> findAllWithQuotes();
    
    /**
     * 分页查找所有冥想视频（包含关联数据）
     */
    @Query("SELECT DISTINCT mv FROM MeditateVideo mv LEFT JOIN FETCH mv.meditateQuotes")
    Page<MeditateVideo> findAllWithQuotes(Pageable pageable);
    
    /**
     * 根据标题模糊查询冥想视频（包含关联数据）
     */
    @Query("SELECT DISTINCT mv FROM MeditateVideo mv LEFT JOIN FETCH mv.meditateQuotes WHERE mv.title LIKE %:title% ORDER BY mv.createAt DESC")
    List<MeditateVideo> findByTitleContainingWithQuotes(@Param("title") String title);
    
    /**
     * 根据颜色查找冥想视频（包含关联数据）
     */
    @Query("SELECT DISTINCT mv FROM MeditateVideo mv LEFT JOIN FETCH mv.meditateQuotes WHERE mv.color = :color ORDER BY mv.createAt DESC")
    List<MeditateVideo> findByColorWithQuotes(@Param("color") String color);
    
    /**
     * 根据ID查找冥想视频
     */
    Optional<MeditateVideo> findById(UUID id);
    
    /**
     * 根据标题模糊查询冥想视频，按创建时间倒序排列
     */
    List<MeditateVideo> findByTitleContainingIgnoreCaseOrderByCreateAtDesc(String title);
    
    /**
     * 查找所有冥想视频，按创建时间倒序排列
     */
    List<MeditateVideo> findAllByOrderByCreateAtDesc();
    
    /**
     * 分页查找所有冥想视频，支持动态排序
     */
    Page<MeditateVideo> findAll(Pageable pageable);
    
    /**
     * 分页查找所有冥想视频，按创建时间倒序排列
     */
    Page<MeditateVideo> findAllByOrderByCreateAtDesc(Pageable pageable);
    
    /**
     * 根据创建时间范围查找冥想视频，按创建时间倒序排列
     */
    List<MeditateVideo> findByCreateAtBetweenOrderByCreateAtDesc(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 根据更新时间范围查找冥想视频，按更新时间倒序排列
     */
    List<MeditateVideo> findByUpdateAtBetweenOrderByUpdateAtDesc(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 统计指定时间范围内的冥想视频数量
     */
    long countByCreateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 根据视频链接查找冥想视频
     */
    Optional<MeditateVideo> findByVideoUrl(String videoUrl);
    
    /**
     * 根据音频链接查找冥想视频
     */
    Optional<MeditateVideo> findByAudioUrl(String audioUrl);
    
    /**
     * 根据图片链接查找冥想视频
     */
    Optional<MeditateVideo> findByImage(String image);
    
    /**
     * 查找有视频链接的冥想视频
     */
    List<MeditateVideo> findByVideoUrlIsNotNullOrderByCreateAtDesc();
    
    /**
     * 查找有音频链接的冥想视频
     */
    List<MeditateVideo> findByAudioUrlIsNotNullOrderByCreateAtDesc();
    
    /**
     * 查找有图片的冥想视频
     */
    List<MeditateVideo> findByImageIsNotNullOrderByCreateAtDesc();
    
    /**
     * 根据颜色查找冥想视频
     */
    List<MeditateVideo> findByColorOrderByCreateAtDesc(String color);
    
    /**
     * 根据标题精确查询冥想视频
     */
    Optional<MeditateVideo> findByTitle(String title);
    
    /**
     * 统计冥想视频总数
     */
    long count();
}
