# 新生成歌曲自动保存

- 只由新建生成任务登记 `ai_music_song_sync`，与任务创建使用同一 D1 batch；没有历史回填或全库扫描。
- 任务生成完成后，每 5 秒的调度唤起独立单线程保存，不阻塞回调、试听或其他定时任务。
- 音频和封面复制原始字节到现有 `music-mv.preview-r2` 指定的桶，默认 `video-previews`：
  - `songs/<candidateId>/audio.<原格式扩展名>`
  - `songs/<candidateId>/cover.<原格式扩展名>`
- 默认访问域名 `https://video.musicvideomaker.net`，使用现有桶的公开访问方式。代码没有为这些对象添加过期时间，也不纳入 `music-mv-inputs/`、`music-mv-imports/` 清理。
- 音频上传成功后才写回存储地址、SHA-256、大小、文件名与类型。数据库确认失败时，重试覆盖固定对象键，不产生随机副本。
- 原始 `alignedWords` 保存至歌曲 `raw_json.storyaiAlignedWordsV1`；当前逐句时间轴继续保存至 `storyaiLyricsTimelineV1`。纯音乐跳过歌词；封面原地址为空时跳过封面。
- 音频、封面、歌词分别尝试，已完成部分直接复用。失败退避重试（首次约 2 分钟，最长 6 小时），只下载/查询已有生成任务，不重新付费生成。租约允许服务重启后继续处理。
- 新歌在后台保存结束前进入视频制作，也走同一个歌曲目录；历史歌曲保留原有按需处理方式。
- 公开 API 优先返回自己的音频、封面；在保存完成前仍提供服务商地址试听。重复生成回调保留本地存储标记和歌词缓存。

## 发布与验证

发布后首次使用会幂等创建同步登记表；也可以预先执行 `src/main/resources/db/music-mv-song-sync.sql`。不修改核心模板 schema 的版本或校验值。

部署前确认现有预览桶凭据具有对象写入权限，域名与桶绑定正确，CORS 允许网站 GET/HEAD（播放器和浏览器制作需要），生命周期规则没有覆盖 `songs/`。本次没有修改云端设置。

可通过同步表的 `status`、`attempts`、`retry_at`、`last_error` 和后台按歌曲/分项日志排查。`completed` 表示本次有返回的音频、封面和所需歌词均已保存；`pending` 保留重试，不代表已备份。
