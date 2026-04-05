package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.CreatePureMusicRequest;
import com.example.cursorquitterweb.dto.PureMusicDto;
import com.example.cursorquitterweb.dto.PureMusicLanguageContentDto;
import com.example.cursorquitterweb.dto.UpdatePureMusicRequest;
import com.example.cursorquitterweb.entity.PureMusic;
import com.example.cursorquitterweb.service.PureMusicService;
import com.example.cursorquitterweb.util.LogUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 纯音乐内容控制器
 */
@RestController
@RequestMapping("/api/pure-musics")
@Validated
public class PureMusicController {

    private static final Logger logger = LogUtil.getLogger(PureMusicController.class);

    @Autowired
    private PureMusicService pureMusicService;

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<PureMusicDto>> createPureMusic(@Valid @RequestBody CreatePureMusicRequest request) {
        try {
            logger.info("创建纯音乐内容，请求参数: {}", request);

            PureMusic pureMusic = pureMusicService.createPureMusic(
                request.getImage(),
                request.getAudiourl(),
                request.getVideourl(),
                request.getVideourlLd(),
                request.getColor(),
                request.getContextText()
            );

            return ResponseEntity.ok(ApiResponse.success("纯音乐内容创建成功", pureMusicService.convertToDto(pureMusic)));
        } catch (Exception e) {
            logger.error("创建纯音乐内容失败", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("创建纯音乐内容失败: " + e.getMessage()));
        }
    }

    @GetMapping("/{videoId}")
    public ResponseEntity<ApiResponse<PureMusicDto>> getPureMusic(@PathVariable UUID videoId) {
        try {
            logger.info("获取纯音乐内容，ID: {}", videoId);
            Optional<PureMusic> optionalPureMusic = pureMusicService.findById(videoId);
            if (optionalPureMusic.isPresent()) {
                return ResponseEntity.ok(ApiResponse.success("获取纯音乐内容成功", pureMusicService.convertToDto(optionalPureMusic.get())));
            }
            return ResponseEntity.status(404).body(ApiResponse.error("纯音乐内容不存在"));
        } catch (Exception e) {
            logger.error("获取纯音乐内容失败，ID: {}", videoId, e);
            return ResponseEntity.badRequest().body(ApiResponse.error("获取纯音乐内容失败: " + e.getMessage()));
        }
    }

    @GetMapping("/{videoId}/context/{lang}")
    public ResponseEntity<ApiResponse<PureMusicLanguageContentDto>> getPureMusicContextByLang(
            @PathVariable UUID videoId,
            @PathVariable String lang) {
        try {
            logger.info("获取纯音乐指定语言文案，ID: {}, lang: {}", videoId, lang);
            Optional<PureMusicLanguageContentDto> content = pureMusicService.getContextTextByLang(videoId, lang);
            if (content.isPresent()) {
                return ResponseEntity.ok(ApiResponse.success("获取纯音乐指定语言文案成功", content.get()));
            }
            return ResponseEntity.status(404).body(ApiResponse.error("纯音乐内容不存在或指定语言文案不存在"));
        } catch (Exception e) {
            logger.error("获取纯音乐指定语言文案失败，ID: {}, lang: {}", videoId, lang, e);
            return ResponseEntity.badRequest().body(ApiResponse.error("获取纯音乐指定语言文案失败: " + e.getMessage()));
        }
    }

    @PutMapping("/{videoId}")
    public ResponseEntity<ApiResponse<PureMusicDto>> updatePureMusic(
            @PathVariable UUID videoId,
            @Valid @RequestBody UpdatePureMusicRequest request) {
        try {
            logger.info("更新纯音乐内容，ID: {}, 请求参数: {}", videoId, request);

            PureMusic pureMusic = pureMusicService.updatePureMusic(
                videoId,
                request.getImage(),
                request.getAudiourl(),
                request.getVideourl(),
                request.getVideourlLd(),
                request.getColor(),
                request.getContextText()
            );

            return ResponseEntity.ok(ApiResponse.success("纯音乐内容更新成功", pureMusicService.convertToDto(pureMusic)));
        } catch (Exception e) {
            logger.error("更新纯音乐内容失败，ID: {}", videoId, e);
            return ResponseEntity.badRequest().body(ApiResponse.error("更新纯音乐内容失败: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{videoId}")
    public ResponseEntity<ApiResponse<Void>> deletePureMusic(@PathVariable UUID videoId) {
        try {
            logger.info("删除纯音乐内容，ID: {}", videoId);
            pureMusicService.deletePureMusic(videoId);
            return ResponseEntity.ok(ApiResponse.success("纯音乐内容删除成功", null));
        } catch (Exception e) {
            logger.error("删除纯音乐内容失败，ID: {}", videoId, e);
            return ResponseEntity.badRequest().body(ApiResponse.error("删除纯音乐内容失败: " + e.getMessage()));
        }
    }

    @GetMapping
    @Cacheable(value = "pureMusics", key = "'all'")
    public ResponseEntity<ApiResponse<List<PureMusicDto>>> getAllPureMusics() {
        try {
            logger.info("获取所有纯音乐内容");
            List<PureMusic> pureMusics = pureMusicService.getAllPureMusics();
            return ResponseEntity.ok(ApiResponse.success("获取纯音乐内容列表成功", pureMusicService.convertToDtoList(pureMusics)));
        } catch (Exception e) {
            logger.error("获取纯音乐内容列表失败", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("获取纯音乐内容列表失败: " + e.getMessage()));
        }
    }
}
