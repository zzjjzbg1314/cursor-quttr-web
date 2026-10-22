# 本地歌词强制对齐

不需要付费 API Key。输入已归档的音频与原始歌词，使用 Whisper 定位原歌词的句子范围，再做强制对齐（英文使用 wav2vec2 CTC，其他语言使用 stable-ts）；不使用转写结果替换原文。英文数字等无法可靠映射到字符模型的文本会明确失败，不删除后强行对齐。歌词语言自动检测，与网站界面语言独立。

## 部署

后端服务器需要 Python 3.12、FFmpeg，以及可下载 Whisper 模型的网络。

```sh
python3.12 -m venv .venv-alignment
.venv-alignment/bin/pip install -r scripts/lyrics_alignment/requirements.txt
```

配置 `MUSIC_MV_ALIGNMENT_PYTHON` 为虚拟环境 Python 绝对路径，`MUSIC_MV_ALIGNMENT_SCRIPT` 为本目录 align.py 绝对路径。`MUSIC_MV_ALIGNMENT_MODEL` 指定 Whisper 模型，默认 small。未配置 Python 时保持禁用。模型必须在启动服务前下载到运行用户的缓存中；工作进程会校验模型摘要，缺少模型时明确失败，不在用户任务内下载。可先运行 `.venv-alignment/bin/python -c "import whisper; whisper.load_model('small')"` 完成预热。英文字符模型另需运行 `.venv-alignment/bin/python -c "import torchaudio; torchaudio.pipelines.WAV2VEC2_ASR_BASE_960H.get_model()"`。TorchAudio 固定在 2.8.0，后续版本移除了此对齐 API，不应单独升级。

## 数据与执行

Suno 导入成功后自动排队；已有歌曲请求时间轴时也可排队。先尝试原始 Suno 时间轴，获取不到才运行本地模型。任务记录在 D1 `music_mv_lyric_alignment`，采用单进程工作线程及数据库租约。音频从本站归档读取并校验大小与 SHA-256，不接受任意远程音频地址。

单次 Python 任务最多 15 分钟，最多使用 4 个 CPU 线程，音频不超过 100 MiB / 10 分钟。模型内存随规格变化。失败停止，不自动重复消耗资源；运维诊断修复后可重置对应失败任务为 pending。

成功后将逐词时间、按原歌词换行组合的时间轴、来源与源版本一起保存在歌曲记录。后续读取直接复用。歌词、音频摘要或模型配置变化会使本地缓存失效。无歌词时不编造文本；越界、零时长、原文不一致及大量低置信度结果不保存。

前端仅对 `SONG_LYRICS_PROCESSING` 每 5 秒轮询，最多 20 分钟；关闭详情会停止等待。失败使用现有本地化提示。

## 验证

```sh
python3 -m py_compile scripts/lyrics_alignment/align.py scripts/lyrics_alignment/test_align.py scripts/lyrics_alignment/anchored.py scripts/lyrics_alignment/test_anchored.py
python3 -m unittest discover -s scripts/lyrics_alignment -p 'test_*.py'
```

命令行真实音频验证：

```sh
.venv-alignment/bin/python scripts/lyrics_alignment/align.py --audio /path/song.m4a --lyrics /path/lyrics.txt --output /path/timeline.json --model small
```

诊断输出 `timeline.json.diagnostic.json` 包含原文，属于用户数据，应仅在受控环境保存；后台临时音频、歌词和诊断文件在任务结束后清理。模型通过结构检查并不等同于听感完全准确，应抽查真实演唱边界。

## 本次真实歌曲验收（2026-10-22）

测试歌曲：Fragile Kind of Air，Suno 公开分享链接 `https://suno.com/s/HhSH1lS6PVz42TdF`。

- 使用与数据库归档 SHA-256 一致的原始音频及数据库原歌词。
- 本地模型执行约 11.45 秒；26 行、215 词，原文覆盖率 100%，无零时长、倒序或越界。
- 实际 D1 保存及回读验收约 18.28 秒，来源为 `forced-alignment`；连续两次读取一致并复用缓存。
- 逐词置信度低于 0.15 的词有 18 个，未超过 20% 拒绝阈值。通过结构与持久化验收不代表每个词人工逐一听审精确；不是 Suno 官方原始时间轴。
- 本次真实音频只验证英文歌曲；其他语言的原文保留和锚点逻辑有单元测试，未完成各语言真实歌曲听感验收。

模型与算法依据：[stable-ts](https://github.com/jianfch/stable-ts)、[PyTorch CTC 强制对齐](https://docs.pytorch.org/audio/2.8/tutorials/ctc_forced_alignment_api_tutorial.html)、[wav2vec2-base-960h](https://huggingface.co/facebook/wav2vec2-base-960h)。
