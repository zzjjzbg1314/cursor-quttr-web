-- 仅开放分类，不更改任何模板的发布状态、能力合同或版本指针。
INSERT INTO template_categories(category_key,parent_key,level,slug_path,is_selectable,name_zh,name_en,sort_order,enabled,created_at,updated_at)
VALUES('ai-effects',NULL,1,'ai-effects',1,'AI 特效','AI Effects',14,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
ON CONFLICT(category_key) DO UPDATE SET enabled=1,is_selectable=1,updated_at=CURRENT_TIMESTAMP;
