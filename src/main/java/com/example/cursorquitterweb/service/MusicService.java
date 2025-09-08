package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.dto.MusicDto;
import com.example.cursorquitterweb.entity.Music;
import com.example.cursorquitterweb.repository.MusicRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 音乐服务实现类
 */
@Service
@Transactional
public class MusicService {
    
    @Autowired
    private MusicRepository musicRepository;
    
    /**
     * 根据ID查找音乐
     */
    @Transactional(readOnly = true)
    public Optional<Music> findById(UUID id) {
        return musicRepository.findById(id);
    }
    
    /**
     * 保存音乐
     */
    public Music save(Music music) {
        return musicRepository.save(music);
    }
    
    /**
     * 创建新音乐
     */
    public Music createMusic(String title, String subtitle, String time, String image, 
                            String videourl, String audiourl, String quotes, String author, String color) {
        Music music = new Music(title, subtitle, time, image, videourl, audiourl, quotes, author, color);
        return musicRepository.save(music);
    }
    
    /**
     * 更新音乐信息
     */
    public Music updateMusic(Music music) {
        music.preUpdate(); // 更新修改时间
        return musicRepository.save(music);
    }
    
    /**
     * 更新音乐信息（通过ID）
     */
    public Music updateMusic(UUID id, String title, String subtitle, String time, String image, 
                            String videourl, String audiourl, String quotes, String author, String color) {
        Music music = musicRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("音乐不存在，ID: " + id));
        
        music.setTitle(title);
        music.setSubtitle(subtitle);
        music.setTime(time);
        music.setImage(image);
        music.setVideourl(videourl);
        music.setAudiourl(audiourl);
        music.setQuotes(quotes);
        music.setAuthor(author);
        music.setColor(color);
        
        return musicRepository.save(music);
    }
    
    /**
     * 删除音乐
     */
    public void deleteMusic(UUID id) {
        if (!musicRepository.existsById(id)) {
            throw new RuntimeException("音乐不存在，ID: " + id);
        }
        musicRepository.deleteById(id);
    }
    
    /**
     * 根据标题搜索音乐
     */
    @Transactional(readOnly = true)
    public List<Music> searchByTitle(String title) {
        return musicRepository.findByTitleContainingIgnoreCaseOrderByCreateAtDesc(title);
    }
    
    /**
     * 根据作者搜索音乐
     */
    @Transactional(readOnly = true)
    public List<Music> searchByAuthor(String author) {
        return musicRepository.findByAuthorContainingIgnoreCaseOrderByCreateAtDesc(author);
    }
    
    /**
     * 根据标题精确查询音乐
     */
    @Transactional(readOnly = true)
    public Optional<Music> findByTitle(String title) {
        return musicRepository.findByTitle(title);
    }
    
    /**
     * 根据作者精确查询音乐
     */
    @Transactional(readOnly = true)
    public List<Music> findByAuthor(String author) {
        return musicRepository.findByAuthor(author);
    }
    
    /**
     * 根据标题和作者查询音乐
     */
    @Transactional(readOnly = true)
    public Optional<Music> findByTitleAndAuthor(String title, String author) {
        return musicRepository.findByTitleAndAuthor(title, author);
    }
    
    /**
     * 根据创建时间范围查询音乐
     */
    @Transactional(readOnly = true)
    public List<Music> findByCreateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime) {
        return musicRepository.findByCreateAtBetweenOrderByCreateAtDesc(startTime, endTime);
    }
    
    /**
     * 根据更新时间范围查询音乐
     */
    @Transactional(readOnly = true)
    public List<Music> findByUpdateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime) {
        return musicRepository.findByUpdateAtBetweenOrderByUpdateAtDesc(startTime, endTime);
    }
    
    /**
     * 查询有视频链接的音乐
     */
    @Transactional(readOnly = true)
    public List<Music> findMusicWithVideourl() {
        return musicRepository.findByVideourlIsNotNullOrderByCreateAtDesc();
    }
    
    /**
     * 查询有音频链接的音乐
     */
    @Transactional(readOnly = true)
    public List<Music> findMusicWithAudiourl() {
        return musicRepository.findByAudiourlIsNotNullOrderByCreateAtDesc();
    }
    
    /**
     * 查询有封面图片的音乐
     */
    @Transactional(readOnly = true)
    public List<Music> findMusicWithImage() {
        return musicRepository.findByImageIsNotNullOrderByCreateAtDesc();
    }
    
    /**
     * 统计指定时间范围内创建的音乐数量
     */
    @Transactional(readOnly = true)
    public long countMusicByCreateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime) {
        return musicRepository.countByCreateAtBetween(startTime, endTime);
    }
    
    /**
     * 根据标题关键词搜索音乐（支持中文全文搜索）
     */
    @Transactional(readOnly = true)
    public List<Music> searchMusicByTitleKeyword(String keyword) {
        return musicRepository.searchMusicByTitleKeyword(keyword);
    }
    
    /**
     * 根据作者关键词搜索音乐（支持中文全文搜索）
     */
    @Transactional(readOnly = true)
    public List<Music> searchMusicByAuthorKeyword(String keyword) {
        return musicRepository.searchMusicByAuthorKeyword(keyword);
    }
    
    /**
     * 获取最新的音乐列表（按创建时间降序）
     */
    @Transactional(readOnly = true)
    public List<Music> getLatestMusic() {
        return musicRepository.findLatestMusic();
    }
    
    /**
     * 获取最新的音乐列表（按创建时间降序，限制数量）
     */
    @Transactional(readOnly = true)
    public List<Music> getLatestMusic(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return musicRepository.findLatestMusic(pageable);
    }
    
    /**
     * 分页查询音乐列表（按创建时间降序）
     */
    @Transactional(readOnly = true)
    public Page<Music> getMusicPage(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return musicRepository.findMusicPage(pageable);
    }
    
    /**
     * 根据标题模糊查询并分页
     */
    @Transactional(readOnly = true)
    public Page<Music> searchMusicByTitlePage(String title, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return musicRepository.findByTitleContainingIgnoreCasePage(title, pageable);
    }
    
    /**
     * 根据作者模糊查询并分页
     */
    @Transactional(readOnly = true)
    public Page<Music> searchMusicByAuthorPage(String author, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return musicRepository.findByAuthorContainingIgnoreCasePage(author, pageable);
    }
    
    /**
     * 获取所有音乐（按创建时间升序排列）
     */
    @Transactional(readOnly = true)
    public List<Music> getAllMusic() {
        return musicRepository.findAllByOrderByCreateAtAsc();
    }
    
    /**
     * 获取所有音乐（分页）
     */
    @Transactional(readOnly = true)
    public Page<Music> getAllMusic(Pageable pageable) {
        return musicRepository.findAll(pageable);
    }
    
    /**
     * 检查音乐标题和作者是否已存在
     */
    @Transactional(readOnly = true)
    public boolean existsByTitleAndAuthor(String title, String author) {
        return musicRepository.findByTitleAndAuthor(title, author).isPresent();
    }
    
    /**
     * 根据视频链接查找音乐
     */
    @Transactional(readOnly = true)
    public Optional<Music> findByVideourl(String videourl) {
        return musicRepository.findByVideourl(videourl);
    }
    
    /**
     * 根据音频链接查找音乐
     */
    @Transactional(readOnly = true)
    public Optional<Music> findByAudiourl(String audiourl) {
        return musicRepository.findByAudiourl(audiourl);
    }
    
    /**
     * 更新音乐封面链接
     */
    public Music updateImage(UUID id, String image) {
        Optional<Music> musicOpt = musicRepository.findById(id);
        if (musicOpt.isPresent()) {
            Music music = musicOpt.get();
            music.setImage(image);
            return musicRepository.save(music);
        }
        throw new RuntimeException("音乐不存在");
    }
    
    /**
     * 更新音乐视频链接
     */
    public Music updateVideourl(UUID id, String videourl) {
        Optional<Music> musicOpt = musicRepository.findById(id);
        if (musicOpt.isPresent()) {
            Music music = musicOpt.get();
            music.setVideourl(videourl);
            return musicRepository.save(music);
        }
        throw new RuntimeException("音乐不存在");
    }
    
    /**
     * 更新音乐音频链接
     */
    public Music updateAudiourl(UUID id, String audiourl) {
        Optional<Music> musicOpt = musicRepository.findById(id);
        if (musicOpt.isPresent()) {
            Music music = musicOpt.get();
            music.setAudiourl(audiourl);
            return musicRepository.save(music);
        }
        throw new RuntimeException("音乐不存在");
    }
    
    /**
     * 更新音乐主题颜色
     */
    public Music updateColor(UUID id, String color) {
        Optional<Music> musicOpt = musicRepository.findById(id);
        if (musicOpt.isPresent()) {
            Music music = musicOpt.get();
            music.setColor(color);
            return musicRepository.save(music);
        }
        throw new RuntimeException("音乐不存在");
    }
    
    /**
     * 统计音乐总数
     */
    @Transactional(readOnly = true)
    public long count() {
        return musicRepository.count();
    }
    
    /**
     * 转换为DTO
     */
    public MusicDto convertToDto(Music music) {
        if (music == null) {
            return null;
        }
        return new MusicDto(
                music.getId(),
                music.getTitle(),
                music.getSubtitle(),
                music.getTime(),
                music.getImage(),
                music.getVideourl(),
                music.getAudiourl(),
                music.getCreateAt(),
                music.getUpdateAt(),
                music.getQuotes(),
                music.getAuthor(),
                music.getColor()
        );
    }
    
    /**
     * 批量转换为DTO
     */
    public List<MusicDto> convertToDtoList(List<Music> music) {
        if (music == null) {
            return null;
        }
        return music.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
}
