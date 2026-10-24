# 模板标题十语言同步

支持 `en`、`zh-CN`、`zh-TW`、`ja`、`ko`、`es`、`pt-BR`、`fr`、`de`、`it`。

中英文源标题由资料维护接口提供；其他八种标题利用 `music-mv.text-ai`（兼容原 `deepseek.api` 配置）一次请求翻译。新增、源标题变更及发布缺失标题时自动调用。已有完整标题且源名未变不再调用。翻译失败返回明确错误，不生成占位译文，不触发真实视频渲染。模板描述不新增翻译，空描述读取时回退英文。

## 内部接口

`POST /internal/music-mv/v1/templates/{templateId}/titles/localize?dryRun=true&expectedRevision=7`

必须提供 `X-Music-Mv-Template-Sync-Token`。`dryRun` 默认为 `true`。不提供请求体时返回缺失语言计划和当前 `before`、`source`、`revision`、完整 `backup` 翻译行，不调用模型、不写库。执行自动翻译使用 `dryRun=false`。

人工复核译稿使用以下可选请求体（必须恰好八种目标语言，不得提供中英文覆盖值）：

```json
{
  "titles": {
    "zh-TW": "水面倒影黑膠",
    "ja": "水面に映るレコード",
    "ko": "수면에 비친 레코드",
    "es": "Vinilo entre reflejos",
    "pt-BR": "Vinil entre reflexos",
    "fr": "Vinyle aux reflets d’eau",
    "de": "Schallplatte im Wasserspiegel",
    "it": "Vinile tra i riflessi"
  },
  "expectedSource": {"zh-CN": "水面倒影黑胶", "en": "Ripple Reflection Vinyl"}
}
```

人工译稿必须携带 `expectedRevision` 查询参数以及完全匹配的 `expectedSource`。先 dry-run 会返回拟写入 `after`，校验通过再执行。执行返回写入前后标题和递增后的 `revision`；源名或版本发生并发变化返回 `TEMPLATE_TITLE_SOURCE_CHANGED`，重新获取并复核，不自动覆盖。

逐模板保存 dry-run JSON 作为备份，再同步。接口只更新目标语言标题并递增资料 revision，保留原有 description、SEO字段、媒体、版本状态及发布指针。不需要数据库迁移。重复提交完全相同译稿不产生写入。

发布仍要求已有中英文设计说明满足原有质量门禁，并额外要求十语言标题完整。接口校验目标键、长度、危险标记和主要字符集，不能替代人工语义复核；新增自动翻译遵循同一术语提示，但未进行真实付费模型验收。
