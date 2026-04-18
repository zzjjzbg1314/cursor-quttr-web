CREATE TABLE IF NOT EXISTS comment_reports (
  id TEXT PRIMARY KEY DEFAULT (lower(hex(randomblob(16)))),
  reported_comment_id TEXT NOT NULL,
  report_reason TEXT NOT NULL,
  report_notes TEXT,
  reporter_user_id TEXT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_comment_reports_reported_comment_id
ON comment_reports (reported_comment_id);

CREATE INDEX IF NOT EXISTS idx_comment_reports_reporter_user_id
ON comment_reports (reporter_user_id);

CREATE INDEX IF NOT EXISTS idx_comment_reports_created_at
ON comment_reports (created_at);
