package com.example.cursorquitterweb.service;

import com.example.cursorquitterweb.dto.MusicDto;
import com.example.cursorquitterweb.entity.Music;
import com.example.cursorquitterweb.util.CloudflareD1Util;
import com.example.cursorquitterweb.util.EntityMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 音乐服务实现类
 * 使用 CloudflareD1Util 进行数据库操作
 */
@Service
public class MusicService {
    
    @Autowired
    private CloudflareD1Util d1Util;
    
    /**
     * 根据ID查找音乐
     */
    public Optional<Music> findById(UUID id) {
        Map<String, Object> row = d1Util.findById("music", "id", EntityMapper.uuidToString(id));
        return row != null ? Optional.of(mapToMusic(row)) : Optional.empty();
    }
    
    /**
     * 保存音乐
     */
    public Music save(Music music) {
        if (music.getId() == null) {
            // 插入新记录
            music.setId(UUID.randomUUID());
            music.setCreateAt(OffsetDateTime.now());
            music.setUpdateAt(OffsetDateTime.now());
            Map<String, Object> data = musicToMap(music);
            d1Util.insert("music", data);
            return music;
        } else {
            // 更新记录
            music.preUpdate();
            Map<String, Object> data = musicToMap(music);
            d1Util.updateById("music", data, "id", EntityMapper.uuidToString(music.getId()));
            return music;
        }
    }
    
    /**
     * 创建新音乐
     */
    public Music createMusic(String title, String subtitle, String time, String image, 
                            String videourl, String audiourl, String quotes, String author, String color) {
        Music music = new Music(title, subtitle, time, image, videourl, audiourl, quotes, author, color);
        return save(music);
    }
    
    /**
     * 更新音乐信息
     */
    public Music updateMusic(Music music) {
        music.preUpdate(); // 更新修改时间
        return save(music);
    }
    
    /**
     * 更新音乐信息（通过ID）
     */
    public Music updateMusic(UUID id, String title, String subtitle, String time, String image, 
                            String videourl, String audiourl, String quotes, String author, String color) {
        Music music = findById(id)
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
        
        return save(music);
    }
    
    /**
     * 删除音乐
     */
    public void deleteMusic(UUID id) {
        if (!d1Util.exists("music", "id = ?", EntityMapper.uuidToString(id))) {
            throw new RuntimeException("音乐不存在，ID: " + id);
        }
        d1Util.deleteById("music", "id", EntityMapper.uuidToString(id));
    }
    
    /**
     * 根据标题搜索音乐
     */
    public List<Music> searchByTitle(String title) {
        String sql = "SELECT * FROM music WHERE LOWER(title) LIKE LOWER(?) ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, "%" + title + "%");
        return rows.stream().map(this::mapToMusic).collect(Collectors.toList());
    }
    
    /**
     * 根据作者搜索音乐
     */
    public List<Music> searchByAuthor(String author) {
        String sql = "SELECT * FROM music WHERE LOWER(author) LIKE LOWER(?) ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, "%" + author + "%");
        return rows.stream().map(this::mapToMusic).collect(Collectors.toList());
    }
    
    /**
     * 根据标题精确查询音乐
     */
    public Optional<Music> findByTitle(String title) {
        String sql = "SELECT * FROM music WHERE title = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, title);
        return row != null ? Optional.of(mapToMusic(row)) : Optional.empty();
    }
    
    /**
     * 根据作者精确查询音乐
     */
    public List<Music> findByAuthor(String author) {
        String sql = "SELECT * FROM music WHERE author = ? ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, author);
        return rows.stream().map(this::mapToMusic).collect(Collectors.toList());
    }
    
    /**
     * 根据标题和作者查询音乐
     */
    public Optional<Music> findByTitleAndAuthor(String title, String author) {
        String sql = "SELECT * FROM music WHERE title = ? AND author = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, title, author);
        return row != null ? Optional.of(mapToMusic(row)) : Optional.empty();
    }
    
    /**
     * 根据创建时间范围查询音乐
     */
    public List<Music> findByCreateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT * FROM music WHERE create_at >= ? AND create_at <= ? ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime));
        return rows.stream().map(this::mapToMusic).collect(Collectors.toList());
    }
    
    /**
     * 根据更新时间范围查询音乐
     */
    public List<Music> findByUpdateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT * FROM music WHERE update_at >= ? AND update_at <= ? ORDER BY update_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime));
        return rows.stream().map(this::mapToMusic).collect(Collectors.toList());
    }
    
    /**
     * 查询有视频链接的音乐
     */
    public List<Music> findMusicWithVideourl() {
        String sql = "SELECT * FROM music WHERE videourl IS NOT NULL AND videourl != '' ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToMusic).collect(Collectors.toList());
    }
    
    /**
     * 查询有音频链接的音乐
     */
    public List<Music> findMusicWithAudiourl() {
        String sql = "SELECT * FROM music WHERE audiourl IS NOT NULL AND audiourl != '' ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToMusic).collect(Collectors.toList());
    }
    
    /**
     * 查询有封面图片的音乐
     */
    public List<Music> findMusicWithImage() {
        String sql = "SELECT * FROM music WHERE image IS NOT NULL AND image != '' ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToMusic).collect(Collectors.toList());
    }
    
    /**
     * 统计指定时间范围内创建的音乐数量
     */
    public long countMusicByCreateAtBetween(OffsetDateTime startTime, OffsetDateTime endTime) {
        String sql = "SELECT COUNT(*) as count FROM music WHERE create_at >= ? AND create_at <= ?";
        return d1Util.queryLong(sql, 
            EntityMapper.offsetDateTimeToString(startTime), 
            EntityMapper.offsetDateTimeToString(endTime));
    }
    
    /**
     * 根据标题关键词搜索音乐（支持中文全文搜索）
     */
    public List<Music> searchMusicByTitleKeyword(String keyword) {
        String sql = "SELECT * FROM music WHERE title LIKE ? ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, "%" + keyword + "%");
        return rows.stream().map(this::mapToMusic).collect(Collectors.toList());
    }
    
    /**
     * 根据作者关键词搜索音乐（支持中文全文搜索）
     */
    public List<Music> searchMusicByAuthorKeyword(String keyword) {
        String sql = "SELECT * FROM music WHERE author LIKE ? ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql, "%" + keyword + "%");
        return rows.stream().map(this::mapToMusic).collect(Collectors.toList());
    }
    
    /**
     * 获取最新的音乐列表（按创建时间降序）
     */
    public List<Music> getLatestMusic() {
        String sql = "SELECT * FROM music ORDER BY create_at DESC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToMusic).collect(Collectors.toList());
    }
    
    /**
     * 获取最新的音乐列表（按创建时间降序，限制数量）
     */
    public List<Music> getLatestMusic(int limit) {
        String sql = "SELECT * FROM music ORDER BY create_at DESC LIMIT ?";
        List<Map<String, Object>> rows = d1Util.queryList(sql, limit);
        return rows.stream().map(this::mapToMusic).collect(Collectors.toList());
    }
    
    /**
     * 分页查询音乐列表（按创建时间降序）
     * 注意：返回的是 List，不再使用 Spring Data 的 Page 对象
     */
    public List<Music> getMusicPage(int page, int size) {
        String sql = "SELECT * FROM music ORDER BY create_at DESC";
        return d1Util.queryPage(sql, page + 1, size).stream()
            .map(this::mapToMusic)
            .collect(Collectors.toList());
    }
    
    /**
     * 根据标题模糊查询并分页
     * 注意：返回的是 List，不再使用 Spring Data 的 Page 对象
     */
    public List<Music> searchMusicByTitlePage(String title, int page, int size) {
        String sql = "SELECT * FROM music WHERE LOWER(title) LIKE LOWER(?) ORDER BY create_at DESC";
        return d1Util.queryPage(sql, page + 1, size, "%" + title + "%").stream()
            .map(this::mapToMusic)
            .collect(Collectors.toList());
    }
    
    /**
     * 根据作者模糊查询并分页
     * 注意：返回的是 List，不再使用 Spring Data 的 Page 对象
     */
    public List<Music> searchMusicByAuthorPage(String author, int page, int size) {
        String sql = "SELECT * FROM music WHERE LOWER(author) LIKE LOWER(?) ORDER BY create_at DESC";
        return d1Util.queryPage(sql, page + 1, size, "%" + author + "%").stream()
            .map(this::mapToMusic)
            .collect(Collectors.toList());
    }
    
    /**
     * 获取所有音乐（按创建时间升序排列）
     */
    public List<Music> getAllMusic() {
        String sql = "SELECT * FROM music ORDER BY create_at ASC";
        List<Map<String, Object>> rows = d1Util.queryList(sql);
        return rows.stream().map(this::mapToMusic).collect(Collectors.toList());
    }
    
    /**
     * 获取所有音乐（分页）
     * 注意：返回的是 List，不再使用 Spring Data 的 Page 对象
     */
    public List<Music> getAllMusic(int page, int size) {
        String sql = "SELECT * FROM music ORDER BY create_at DESC";
        return d1Util.queryPage(sql, page + 1, size).stream()
            .map(this::mapToMusic)
            .collect(Collectors.toList());
    }
    
    /**
     * 检查音乐标题和作者是否已存在
     */
    public boolean existsByTitleAndAuthor(String title, String author) {
        return findByTitleAndAuthor(title, author).isPresent();
    }
    
    /**
     * 根据视频链接查找音乐
     */
    public Optional<Music> findByVideourl(String videourl) {
        String sql = "SELECT * FROM music WHERE videourl = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, videourl);
        return row != null ? Optional.of(mapToMusic(row)) : Optional.empty();
    }
    
    /**
     * 根据音频链接查找音乐
     */
    public Optional<Music> findByAudiourl(String audiourl) {
        String sql = "SELECT * FROM music WHERE audiourl = ? LIMIT 1";
        Map<String, Object> row = d1Util.queryOne(sql, audiourl);
        return row != null ? Optional.of(mapToMusic(row)) : Optional.empty();
    }
    
    /**
     * 更新音乐封面链接
     */
    public Music updateImage(UUID id, String image) {
        Optional<Music> musicOpt = findById(id);
        if (musicOpt.isPresent()) {
            Music music = musicOpt.get();
            music.setImage(image);
            return save(music);
        }
        throw new RuntimeException("音乐不存在");
    }
    
    /**
     * 更新音乐视频链接
     */
    public Music updateVideourl(UUID id, String videourl) {
        Optional<Music> musicOpt = findById(id);
        if (musicOpt.isPresent()) {
            Music music = musicOpt.get();
            music.setVideourl(videourl);
            return save(music);
        }
        throw new RuntimeException("音乐不存在");
    }
    
    /**
     * 更新音乐音频链接
     */
    public Music updateAudiourl(UUID id, String audiourl) {
        Optional<Music> musicOpt = findById(id);
        if (musicOpt.isPresent()) {
            Music music = musicOpt.get();
            music.setAudiourl(audiourl);
            return save(music);
        }
        throw new RuntimeException("音乐不存在");
    }
    
    /**
     * 更新音乐主题颜色
     */
    public Music updateColor(UUID id, String color) {
        Optional<Music> musicOpt = findById(id);
        if (musicOpt.isPresent()) {
            Music music = musicOpt.get();
            music.setColor(color);
            return save(music);
        }
        throw new RuntimeException("音乐不存在");
    }
    
    /**
     * 统计音乐总数
     */
    public long count() {
        return d1Util.countTable("music");
    }
    
    /**
     * 将 Map 转换为 Music 实体
     */
    private Music mapToMusic(Map<String, Object> row) {
        Music music = new Music();
        music.setId(EntityMapper.getUUID(row, "id"));
        music.setTitle(EntityMapper.getString(row, "title"));
        music.setSubtitle(EntityMapper.getString(row, "subtitle"));
        music.setTime(EntityMapper.getString(row, "time"));
        music.setImage(EntityMapper.getString(row, "image"));
        music.setVideourl(EntityMapper.getString(row, "videourl"));
        music.setAudiourl(EntityMapper.getString(row, "audiourl"));
        music.setCreateAt(EntityMapper.getOffsetDateTime(row, "create_at"));
        music.setUpdateAt(EntityMapper.getOffsetDateTime(row, "update_at"));
        music.setQuotes(EntityMapper.getString(row, "quotes"));
        music.setAuthor(EntityMapper.getString(row, "author"));
        music.setColor(EntityMapper.getString(row, "color"));
        return music;
    }
    
    /**
     * 将 Music 实体转换为 Map（用于数据库操作）
     */
    private Map<String, Object> musicToMap(Music music) {
        Map<String, Object> data = new HashMap<>();
        EntityMapper.putIfNotNull(data, "id", music.getId());
        EntityMapper.putIfNotNull(data, "title", music.getTitle());
        EntityMapper.putIfNotNull(data, "subtitle", music.getSubtitle());
        EntityMapper.putIfNotNull(data, "time", music.getTime());
        EntityMapper.putIfNotNull(data, "image", music.getImage());
        EntityMapper.putIfNotNull(data, "videourl", music.getVideourl());
        EntityMapper.putIfNotNull(data, "audiourl", music.getAudiourl());
        EntityMapper.putIfNotNull(data, "create_at", music.getCreateAt());
        EntityMapper.putIfNotNull(data, "update_at", music.getUpdateAt());
        EntityMapper.putIfNotNull(data, "quotes", music.getQuotes());
        EntityMapper.putIfNotNull(data, "author", music.getAuthor());
        EntityMapper.putIfNotNull(data, "color", music.getColor());
        return data;
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
