-- 只清理歌词文档的废弃属性，保留正文、修订号和更新时间，可重复执行。
UPDATE music_mv_lyrics_drafts
SET document_json = json_remove(document_json, '$.style');
UPDATE music_mv_lyrics_drafts
SET document_json = json_set(document_json, '$.history',
  json((SELECT json_group_array(json_remove(value, '$.style'))
        FROM json_each(music_mv_lyrics_drafts.document_json, '$.history'))))
WHERE json_type(document_json, '$.history') = 'array';
UPDATE music_mv_lyrics_drafts
SET document_json = json_set(document_json, '$.alternatives',
  json((SELECT json_group_array(json_remove(value, '$.style'))
        FROM json_each(music_mv_lyrics_drafts.document_json, '$.alternatives'))))
WHERE json_type(document_json, '$.alternatives') = 'array';
