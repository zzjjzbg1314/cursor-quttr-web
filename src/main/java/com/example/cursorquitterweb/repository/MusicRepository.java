package com.example.cursorquitterweb.repository;

import com.example.cursorquitterweb.entity.Music;
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
 * 音乐数据访问接口
 */
@Repository
public interface MusicRepository extends JpaRepository<Music, UUID> {
    
    /**
     * 根据ID查找音乐
     */
    Optional<Music> findById(UUID id);
    
    /**
     * 根据标题模糊查询音乐，按创建时间倒序排列
     */
    List<Music> findByTitleContainingIgnoreCaseOrderByCreateAtDesc(String title);
    
    /**
     * 根据作者模糊查询音乐，按创建时间倒序排列
     */
    List<Music> findByAuthorContainingIgnoreCaseOrderByCreateAtDesc(String author);
    
    /**
     * 查找所有音乐，按创建时间倒序排列
     */
    List<Music> findAllByOrderByCreateAtDesc();
    
    /**
     * 查找所有音乐，按创建时间升序排列
     */
    List<Music> findAllByOrderByCreateAtAsc();
    
    /**
     * 分页查找所有音乐，支持动态排序
     */
    Page<Music> findAll(Pageable pageable);
    
    /**
     * 分页查找所有音乐，按创建时间倒序排列
     */
    Page<Music> findAllByOrderByCreateAtDesc(Pageable pageable);
    
    /**
     * 根据创建时间范围查找音乐，按创建时间倒序排列
     */
    List<Music> findByCreateAtBetweenOrderByCreateAtDesc(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 根据更新时间范围查找音乐，按更新时间倒序排列
     */
    List<Music> findByUpdateAtBetweenOrderByUpdateAtDesc(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 统计指定时间范围内的音乐数量
     */
    long countByCreateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime);
    
    /**
     * 根据视频链接查找音乐
     */
    Optional<Music> findByVideourl(String videourl);
    
    /**
     * 根据音频链接查找音乐
     */
    Optional<Music> findByAudiourl(String audiourl);
    
    /**
     * 查找有视频链接的音乐
     */
    List<Music> findByVideourlIsNotNullOrderByCreateAtDesc();
    
    /**
     * 查找有音频链接的音乐
     */
    List<Music> findByAudiourlIsNotNullOrderByCreateAtDesc();
    
    /**
     * 查找有封面图片的音乐
     */
    List<Music> findByImageIsNotNullOrderByCreateAtDesc();
    
    /**
     * 根据标题精确查询音乐
     */
    Optional<Music> findByTitle(String title);
    
    /**
     * 根据作者精确查询音乐
     */
    List<Music> findByAuthor(String author);
    
    /**
     * 根据标题和作者查询音乐
     */
    Optional<Music> findByTitleAndAuthor(String title, String author);
    
    /**
     * 统计音乐总数
     */
    long count();
    
    /**
     * 根据标题关键词搜索音乐（支持中文全文搜索）
     */
    @Query(value = "SELECT * FROM music WHERE to_tsvector('chinese', title) @@ plainto_tsquery('chinese', :keyword)", nativeQuery = true)
    List<Music> searchMusicByTitleKeyword(@Param("keyword") String keyword);
    
    /**
     * 根据作者关键词搜索音乐（支持中文全文搜索）
     */
    @Query(value = "SELECT * FROM music WHERE to_tsvector('chinese', author) @@ plainto_tsquery('chinese', :keyword)", nativeQuery = true)
    List<Music> searchMusicByAuthorKeyword(@Param("keyword") String keyword);
    
    /**
     * 获取最新的音乐列表（按创建时间降序）
     */
    @Query("SELECT m FROM Music m ORDER BY m.createAt DESC")
    List<Music> findLatestMusic();
    
    /**
     * 获取最新的音乐列表（按创建时间降序，限制数量）
     */
    @Query("SELECT m FROM Music m ORDER BY m.createAt DESC")
    List<Music> findLatestMusic(Pageable pageable);
    
    /**
     * 分页查询音乐列表（按创建时间降序）
     */
    @Query("SELECT m FROM Music m ORDER BY m.createAt DESC")
    Page<Music> findMusicPage(Pageable pageable);
    
    /**
     * 根据标题模糊查询并分页
     */
    @Query("SELECT m FROM Music m WHERE LOWER(m.title) LIKE LOWER(CONCAT('%', :title, '%')) ORDER BY m.createAt DESC")
    Page<Music> findByTitleContainingIgnoreCasePage(@Param("title") String title, Pageable pageable);
    
    /**
     * 根据作者模糊查询并分页
     */
    @Query("SELECT m FROM Music m WHERE LOWER(m.author) LIKE LOWER(CONCAT('%', :author, '%')) ORDER BY m.createAt DESC")
    Page<Music> findByAuthorContainingIgnoreCasePage(@Param("author") String author, Pageable pageable);
}
