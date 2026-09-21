# 模板运营标签

标签以 `tag_key` 标识，与分类、原始素材 Hashtag 独立。模板关联保存在 `template_tag_items`，可不关联或关联多个标签。

- `template_tags` 保存固定标识、排序、创建时间。
- `template_tag_translations` 按 `(tag_key, locale)` 保存名称；新增语言不增加数据库列，不改变模板关联。
- 首批已部署的 `name_zh` / `name_en` 列保留为兼容字段，名称读取以翻译表为准。当前新增管理表单要求中英文；接口也支持 `translations` 对象，允许一并提供其他语言。
- 返回 `translations`、兼容字段 `nameZh` / `nameEn` 及按请求语言解析的 `name`。回退顺序：完整语言标识、基础语言、英文、简体中文。

受既有客户端鉴权保护的管理接口：

- `GET /api/music-mv/v1/admin/template-tags?locale=ja-JP`
- `POST /api/music-mv/v1/admin/template-tags`，例如 `{"key":"new-year","translations":{"zh-CN":"新年","en":"New Year","ja":"新年のお祝い"}}`。
- 模板资料更新和晋升接口接受 `tagKeys`。省略表示保留，`[]` 表示清空；不存在的标识被拒绝。

首次访问标签服务时以 D1 事务创建独立标签表、预置七个标签，并为主分类或关联分类含 `birthday` 的已有模板补标。`template_tag_migrations` 的 `birthday-v1` 标记使补标只执行一次；之后人工取消不会被启动或刷新补回。新增标签及其翻译在同一事务写入。

客户网站通过受服务端凭据保护的 GET /api/music-mv/v1/template-tags 获取标签，GET /api/music-mv/v1/templates?tag=birthday 按独立标签查询。标签过滤在分页前执行，列表与总数条件一致，沿用公开、已发布、未删除限制；未知标签返回校验错误，不回退分类或全部模板。不涉及渲染行为。验证包括服务测试及执行真实迁移/查询 SQL 的内存 SQLite 测试。

婚礼场景暂时统一使用 `wedding`。一次性迁移 `merge-wedding-v1` 将原 `wedding-anniversary` 关联去重合并到 `wedding`，随后删除旧标签及其翻译；不修改分类。

一次性迁移 `wedding-category-v1` 为主分类或关联分类含 `wedding` 的历史模板补上同名标签，保留其他标签并自动去重。迁移后人工取消关联或新增婚礼分类模板，不会被再次自动补标。

2026-09-21 实际补标核对：婚礼分类历史模板 15 个，婚礼标签关联 15 个，遗漏 0；公开婚礼主题查询返回 15 个。聚焦标签服务测试 9 项通过。

标签列表统一按 sort_order 升序展示。默认场景优先级：生日、婚礼、情人节、母亲节、圣诞节、毕业、父亲节。这是产品使用场景判断，不是用户行为统计；一次性迁移 occasion-priority-v1 更新既有七个标签的顺序，后续新增标签排在末尾，未来可按真实点击与创作数据调整。页脚按从左到右、从上到下的顺序排列，主题导航与后台同步使用该顺序。
