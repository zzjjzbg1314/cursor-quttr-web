package com.example.cursorquitterweb.controller;

import com.example.cursorquitterweb.dto.ApiResponse;
import com.example.cursorquitterweb.dto.CreateWhiteNoiseRequest;
import com.example.cursorquitterweb.dto.UpdateWhiteNoiseRequest;
import com.example.cursorquitterweb.dto.WhiteNoiseDto;
import com.example.cursorquitterweb.entity.WhiteNoise;
import com.example.cursorquitterweb.service.WhiteNoiseService;
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
 * 白噪音内容控制器
 */
@RestController
@RequestMapping("/api/white-noises")
@Validated
public class WhiteNoiseController {

    private static final Logger logger = LogUtil.getLogger(WhiteNoiseController.class);

    @Autowired
    private WhiteNoiseService whiteNoiseService;

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<WhiteNoiseDto>> createWhiteNoise(@Valid @RequestBody CreateWhiteNoiseRequest request) {
        try {
            logger.info("创建白噪音内容，请求参数: {}", request);

            WhiteNoise whiteNoise = whiteNoiseService.createWhiteNoise(
                request.getImage(),
                request.getAudiourl(),
                request.getVideourl(),
                request.getVideourlLd(),
                request.getColor(),
                request.getContextText()
            );

            WhiteNoiseDto dto = whiteNoiseService.convertToDto(whiteNoise);
            return ResponseEntity.ok(ApiResponse.success("白噪音内容创建成功", dto));
        } catch (Exception e) {
            logger.error("创建白噪音内容失败", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("创建白噪音内容失败: " + e.getMessage()));
        }
    }

    @GetMapping("/{videoId}")
    public ResponseEntity<ApiResponse<WhiteNoiseDto>> getWhiteNoise(@PathVariable UUID videoId) {
        try {
            logger.info("获取白噪音内容，ID: {}", videoId);
            Optional<WhiteNoise> optionalWhiteNoise = whiteNoiseService.findById(videoId);
            if (optionalWhiteNoise.isPresent()) {
                return ResponseEntity.ok(ApiResponse.success("获取白噪音内容成功", whiteNoiseService.convertToDto(optionalWhiteNoise.get())));
            }
            return ResponseEntity.status(404).body(ApiResponse.error("白噪音内容不存在"));
        } catch (Exception e) {
            logger.error("获取白噪音内容失败，ID: {}", videoId, e);
            return ResponseEntity.badRequest().body(ApiResponse.error("获取白噪音内容失败: " + e.getMessage()));
        }
    }

    @PutMapping("/{videoId}")
    public ResponseEntity<ApiResponse<WhiteNoiseDto>> updateWhiteNoise(
            @PathVariable UUID videoId,
            @Valid @RequestBody UpdateWhiteNoiseRequest request) {
        try {
            logger.info("更新白噪音内容，ID: {}, 请求参数: {}", videoId, request);

            WhiteNoise whiteNoise = whiteNoiseService.updateWhiteNoise(
                videoId,
                request.getImage(),
                request.getAudiourl(),
                request.getVideourl(),
                request.getVideourlLd(),
                request.getColor(),
                request.getContextText()
            );

            return ResponseEntity.ok(ApiResponse.success("白噪音内容更新成功", whiteNoiseService.convertToDto(whiteNoise)));
        } catch (Exception e) {
            logger.error("更新白噪音内容失败，ID: {}", videoId, e);
            return ResponseEntity.badRequest().body(ApiResponse.error("更新白噪音内容失败: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{videoId}")
    public ResponseEntity<ApiResponse<Void>> deleteWhiteNoise(@PathVariable UUID videoId) {
        try {
            logger.info("删除白噪音内容，ID: {}", videoId);
            whiteNoiseService.deleteWhiteNoise(videoId);
            return ResponseEntity.ok(ApiResponse.success("白噪音内容删除成功", null));
        } catch (Exception e) {
            logger.error("删除白噪音内容失败，ID: {}", videoId, e);
            return ResponseEntity.badRequest().body(ApiResponse.error("删除白噪音内容失败: " + e.getMessage()));
        }
    }

    @GetMapping
    @Cacheable(value = "whiteNoises", key = "'all'")
    public ResponseEntity<ApiResponse<List<WhiteNoiseDto>>> getAllWhiteNoises() {
        try {
            logger.info("获取所有白噪音内容");
            List<WhiteNoise> whiteNoises = whiteNoiseService.getAllWhiteNoises();
            return ResponseEntity.ok(ApiResponse.success("获取白噪音内容列表成功", whiteNoiseService.convertToDtoList(whiteNoises)));
        } catch (Exception e) {
            logger.error("获取白噪音内容列表失败", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("获取白噪音内容列表失败: " + e.getMessage()));
        }
    }
}
