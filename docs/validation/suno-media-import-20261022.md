# Suno 新版音频导入验收

## 原因与官方依据

- 实测分享链接：https://suno.com/s/HhSH1lS6PVz42TdF
- 歌曲 ID：fe43d24d-16b6-4e14-8af2-739e35e25c46。
- 官方 clip API 的 audio_url 返回 forbidden 占位地址，media_urls 提供 progressive / m4a-opus / encoding 1.0.0。
- 官方页面当前加载的 3suoowxwogo_8.js（模块 930323、913328）定义匿名播放授权协议：POST /api/mango/rights，以 glt 的 SHA-256 作为 AES-GCM 包装密钥，歌曲 ID 作为 AAD，解包 key/iv，再用 AES-CTR 读取音频。访客授权是官方主动提供的功能；不使用账户凭证，授权失败即终止。
- 参考页面：https://suno.com/song/fe43d24d-16b6-4e14-8af2-739e35e25c46
- suno.bi 公开前端只证明其使用自有音频接口和存储，无法确认其服务端内部实现。本修复不依赖该网站。

## 修复边界

- 支持 media_urls 的公开渐进音频，保留旧 MP3 支持，按实际扩展名传入现有上传服务。
- 仅允许既有 Suno CDN 和已确认的 CloudFront 分发域；后者限制为当前歌曲路径，不允许任意 CloudFront 主机。
- 保留 HTTPS、端口、DNS、重定向、文件大小与下载时限校验。编码版本仅支持当前有依据的 1.0.0，授权缺失、拒绝或校验失败都停止。
- 无界面、文案、错误码或语言选择变化，无本地化影响。

## 本次验证

命令：SUNO_LIVE_IMPORT_TEST=true mvn -q -Dtest=SunoSongImportServiceTest,MusicMvSongImportServiceTest,MusicMvSongImportControllerTest test

- 20 项测试通过，0 失败、0 跳过。覆盖旧 MP3、分享链接、M4A、归属复用、非法来源、授权拒绝、令牌缺失、密钥包装篡改、上传存储契约和控制器契约。
- 真实网络使用正式导入服务完成短链解析、元数据读取、匿名授权、完整下载及解码；存储注册在该测试中模拟，不向测试用户写入生产记录。
- 实际音频 3,284,576 字节；ffprobe 确认 Opus、48 kHz、双声道、195.84 秒。
- 没有运行完整视频渲染；本次不修改渲染逻辑。用户登录后的实际落库和视频制作不以本次模拟存储测试代替。
- 真实网络测试默认跳过，显式设置 SUNO_LIVE_IMPORT_TEST=true 开启；SUNO_LIVE_AUDIO_OUTPUT 可选保存验收音频，不保存播放授权令牌或密钥。
