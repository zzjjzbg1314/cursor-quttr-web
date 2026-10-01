-- 部署后端前执行；保留请求占用用于跨节点去重。

CREATE TABLE IF NOT EXISTS ai_lyrics_submissions (
  user_id TEXT NOT NULL,
  request_id TEXT NOT NULL,
  prompt TEXT NOT NULL,
  task_handle TEXT,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, request_id)
);
