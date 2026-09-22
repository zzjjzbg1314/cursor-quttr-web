# 歌词草稿存储

新增独立迁移 `src/main/resources/db/music-mv-lyrics-drafts.sql`，必须在 Music MV 专用 D1 执行，不运行旧业务数据库迁移。现有本地配置对应网站数据库中已校验 music_mv_users/music_mv_projects 并执行这份新增表/索引迁移；没有改动既有行。

接口：`GET /api/music-mv/v1/lyric-drafts` 返回最近50份草稿；`GET /{id}` 获取该账号草稿；`PUT /{id}` 接收 revision、draft、reason。账号仅从登录会话获取，clientId不是权限依据。用户ID和草稿ID构成复合主键。

revision为客户端读取的修订号，新建为0。保存先检查修订号，SQL更新再次执行等值条件，并核对write_marker；冲突409，不覆盖新版本。历史由服务端维护，忽略客户端history字段。edit只更新当前稿，generate/rewrite/alternative/restore/apply在内容变化时保留旧稿；去重最近快照，最多20条。记录原标题、歌词、主题、语言、风格、时间和来源。

实际SQLite执行测试覆盖普通编辑、快照/恢复、上限、跨账号访问、旧修订冲突、输入边界。控制器测试验证会话身份与401响应；沿用歌词生成服务的任务归属测试。

验证命令：`mvn -q -Dtest=MusicLyricsDraftServiceTest,MusicLyricsDraftControllerTest,AiLyricsGenerationServiceTest,MusicMvProjectDraftControllerTest test`。

不修改歌曲生成/渲染逻辑，不需要视频渲染或SSIM。浏览器真实模型调用未执行，前端交互验收使用隔离API夹具。
