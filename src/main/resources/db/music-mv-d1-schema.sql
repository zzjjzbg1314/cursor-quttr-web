-- Dedicated Cloudflare D1 schema for the isolated AI music MV module.
-- Apply this file only to MUSIC_MV_CLOUDFLARE_D1_DATABASE_ID.
-- Never apply it to the existing cursor-quttr-web production D1 database.

-- The initializer writes the current schema version and source-file digest
-- only after all idempotent DDL and category seed statements have succeeded.
CREATE TABLE IF NOT EXISTS music_mv_schema_metadata (
  schema_key TEXT PRIMARY KEY,
  schema_version INTEGER NOT NULL,
  schema_sha256 TEXT NOT NULL,
  applied_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

-- Cloud template catalog. The website backend is the only application allowed
-- to query or mutate these tables. Browser and Mac renderer traffic goes
-- through website-backend APIs.
CREATE TABLE IF NOT EXISTS template_categories (
  category_key TEXT PRIMARY KEY,
  name_zh TEXT NOT NULL,
  name_en TEXT NOT NULL,
  sort_order INTEGER NOT NULL,
  enabled INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS templates (
  template_id TEXT PRIMARY KEY,
  slug TEXT NOT NULL,
  default_locale TEXT NOT NULL DEFAULT 'zh-CN',
  category_key TEXT NOT NULL,
  tags_json TEXT NOT NULL DEFAULT '[]',
  status TEXT NOT NULL,
  visibility TEXT NOT NULL DEFAULT 'public',
  current_version_id TEXT,
  sort_order INTEGER NOT NULL DEFAULT 0,
  revision INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  published_at TEXT,
  deleted_at TEXT,
  FOREIGN KEY (category_key) REFERENCES template_categories(category_key)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_templates_slug ON templates(slug);
CREATE INDEX IF NOT EXISTS idx_templates_catalog
  ON templates(status, visibility, category_key, sort_order, updated_at);

CREATE TABLE IF NOT EXISTS template_translations (
  template_id TEXT NOT NULL,
  locale TEXT NOT NULL,
  name TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  seo_title TEXT,
  seo_description TEXT,
  PRIMARY KEY (template_id, locale),
  FOREIGN KEY (template_id) REFERENCES templates(template_id)
);

CREATE TABLE IF NOT EXISTS renderer_nodes (
  node_id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  status TEXT NOT NULL,
  runtime_version TEXT,
  runtime_sha256 TEXT,
  last_seen_at TEXT,
  last_error TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS template_versions (
  version_id TEXT PRIMARY KEY,
  template_id TEXT NOT NULL,
  version_number INTEGER NOT NULL,
  status TEXT NOT NULL,
  width INTEGER NOT NULL,
  height INTEGER NOT NULL,
  fps REAL NOT NULL,
  duration_seconds REAL NOT NULL,
  base_duration_seconds REAL NOT NULL,
  cycle_duration_seconds REAL NOT NULL,
  slot_count INTEGER NOT NULL DEFAULT 0,
  validation_status TEXT NOT NULL,
  validation_render_job_id TEXT NOT NULL,
  validation_master_sha256 TEXT NOT NULL,
  draft_snapshot_sha256 TEXT NOT NULL,
  timeline_evidence_sha256 TEXT NOT NULL,
  native_runtime_version TEXT NOT NULL,
  native_runtime_sha256 TEXT NOT NULL,
  renderer_version TEXT NOT NULL,
  source_node_id TEXT NOT NULL,
  source_local_key TEXT NOT NULL,
  source_availability TEXT NOT NULL DEFAULT 'unknown',
  last_source_verified_at TEXT,
  source_provenance_json TEXT NOT NULL DEFAULT '{}',
  created_at TEXT NOT NULL,
  published_at TEXT,
  UNIQUE (template_id, version_number),
  FOREIGN KEY (template_id) REFERENCES templates(template_id),
  FOREIGN KEY (source_node_id) REFERENCES renderer_nodes(node_id)
);

CREATE INDEX IF NOT EXISTS idx_template_versions_template
  ON template_versions(template_id, version_number DESC);
CREATE INDEX IF NOT EXISTS idx_template_versions_ready
  ON template_versions(status, validation_status, source_availability);
CREATE UNIQUE INDEX IF NOT EXISTS uk_template_versions_validation_job
  ON template_versions(validation_render_job_id);

CREATE TABLE IF NOT EXISTS template_slots (
  slot_id TEXT PRIMARY KEY,
  version_id TEXT NOT NULL,
  slot_key TEXT NOT NULL,
  slot_type TEXT NOT NULL,
  display_name TEXT NOT NULL,
  timeline_order INTEGER NOT NULL,
  aspect_ratio TEXT,
  crop_policy TEXT NOT NULL,
  repeat_policy TEXT NOT NULL,
  is_required INTEGER NOT NULL DEFAULT 1,
  material_id TEXT,
  material_group TEXT,
  UNIQUE (version_id, slot_key),
  FOREIGN KEY (version_id) REFERENCES template_versions(version_id)
);

CREATE INDEX IF NOT EXISTS idx_template_slots_version
  ON template_slots(version_id, timeline_order, slot_key);

CREATE TABLE IF NOT EXISTS template_media (
  media_id TEXT PRIMARY KEY,
  template_id TEXT NOT NULL,
  version_id TEXT NOT NULL,
  media_role TEXT NOT NULL,
  provider TEXT NOT NULL,
  provider_asset_id TEXT NOT NULL,
  status TEXT NOT NULL,
  source_sha256 TEXT NOT NULL,
  source_size_bytes INTEGER,
  width INTEGER,
  height INTEGER,
  duration_seconds REAL,
  provider_details_json TEXT NOT NULL DEFAULT '{}',
  error_message TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  ready_at TEXT,
  UNIQUE (version_id, media_role),
  UNIQUE (provider, provider_asset_id),
  FOREIGN KEY (template_id) REFERENCES templates(template_id),
  FOREIGN KEY (version_id) REFERENCES template_versions(version_id)
);

CREATE INDEX IF NOT EXISTS idx_template_media_lookup
  ON template_media(template_id, version_id, media_role, status);

-- Provider-neutral AI songwriting tasks. Provider-specific identifiers and
-- payloads are kept in attempts so the product contract never depends on KIE.
CREATE TABLE IF NOT EXISTS ai_music_jobs (
  job_id TEXT PRIMARY KEY,
  client_id TEXT NOT NULL,
  request_id TEXT NOT NULL,
  status TEXT NOT NULL,
  stage TEXT NOT NULL,
  progress REAL NOT NULL DEFAULT 0,
  primary_provider_code TEXT NOT NULL,
  active_attempt_id TEXT,
  selected_candidate_id TEXT,
  request_fingerprint TEXT NOT NULL,
  request_json TEXT NOT NULL,
  error_code TEXT,
  error_message TEXT,
  retryable INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  completed_at TEXT,
  UNIQUE (client_id, request_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_music_jobs_client
  ON ai_music_jobs(client_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_music_jobs_status
  ON ai_music_jobs(status, updated_at);

CREATE TABLE IF NOT EXISTS ai_music_provider_attempts (
  attempt_id TEXT PRIMARY KEY,
  job_id TEXT NOT NULL,
  provider_code TEXT NOT NULL,
  provider_task_id TEXT,
  status TEXT NOT NULL,
  attempt_number INTEGER NOT NULL,
  request_json TEXT NOT NULL,
  response_json TEXT,
  credits_used REAL,
  submission_unknown INTEGER NOT NULL DEFAULT 0,
  error_code TEXT,
  error_message TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  completed_at TEXT,
  UNIQUE (provider_code, provider_task_id),
  UNIQUE (job_id, attempt_number),
  FOREIGN KEY (job_id) REFERENCES ai_music_jobs(job_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_music_provider_attempts_job
  ON ai_music_provider_attempts(job_id, attempt_number DESC);

CREATE TABLE IF NOT EXISTS ai_music_candidates (
  candidate_id TEXT PRIMARY KEY,
  job_id TEXT NOT NULL,
  attempt_id TEXT NOT NULL,
  provider_code TEXT NOT NULL,
  provider_task_id TEXT NOT NULL,
  provider_audio_id TEXT NOT NULL,
  status TEXT NOT NULL,
  title TEXT,
  lyrics TEXT,
  style TEXT,
  duration_seconds REAL,
  provider_audio_url TEXT,
  provider_stream_url TEXT,
  provider_image_url TEXT,
  storage_key TEXT,
  storage_url TEXT,
  storage_sha256 TEXT,
  storage_size_bytes INTEGER,
  storage_file_name TEXT,
  storage_content_type TEXT,
  selected INTEGER NOT NULL DEFAULT 0,
  raw_json TEXT NOT NULL DEFAULT '{}',
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  UNIQUE (provider_code, provider_task_id, provider_audio_id),
  FOREIGN KEY (job_id) REFERENCES ai_music_jobs(job_id),
  FOREIGN KEY (attempt_id) REFERENCES ai_music_provider_attempts(attempt_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_music_candidates_job
  ON ai_music_candidates(job_id, created_at, candidate_id);

CREATE TABLE IF NOT EXISTS ai_music_job_events (
  event_id TEXT PRIMARY KEY,
  job_id TEXT NOT NULL,
  event_type TEXT NOT NULL,
  status TEXT NOT NULL,
  provider_code TEXT,
  detail_json TEXT NOT NULL DEFAULT '{}',
  created_at TEXT NOT NULL,
  FOREIGN KEY (job_id) REFERENCES ai_music_jobs(job_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_music_job_events_job
  ON ai_music_job_events(job_id, created_at, event_id);

-- Asynchronous user-facing AI music MV render queue. Large music, image and
-- video bytes stay outside D1; only contracts, leases and immutable evidence
-- are stored here.
CREATE TABLE IF NOT EXISTS music_mv_render_jobs (
  job_id TEXT PRIMARY KEY,
  client_id TEXT NOT NULL,
  request_id TEXT NOT NULL,
  template_id TEXT NOT NULL,
  version_id TEXT NOT NULL,
  status TEXT NOT NULL,
  stage TEXT NOT NULL,
  progress REAL NOT NULL DEFAULT 0,
  priority INTEGER NOT NULL DEFAULT 0,
  attempt_count INTEGER NOT NULL DEFAULT 0,
  max_attempts INTEGER NOT NULL DEFAULT 2,
  request_fingerprint TEXT NOT NULL,
  request_json TEXT NOT NULL,
  claimed_node_id TEXT,
  lease_token TEXT,
  lease_expires_at TEXT,
  cancel_requested INTEGER NOT NULL DEFAULT 0,
  output_storage_key TEXT,
  output_content_type TEXT,
  output_size_bytes INTEGER,
  output_sha256 TEXT,
  output_duration_seconds REAL,
  semantic_integrity TEXT,
  video_encode_count INTEGER,
  intermediate_video_count INTEGER,
  writer_sidecar_count INTEGER,
  native_task_id TEXT,
  native_render_job_id TEXT,
  result_json TEXT,
  evidence_json TEXT,
  error_code TEXT,
  error_message TEXT,
  retryable INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  started_at TEXT,
  completed_at TEXT,
  UNIQUE (client_id, request_id),
  FOREIGN KEY (template_id) REFERENCES templates(template_id),
  FOREIGN KEY (version_id) REFERENCES template_versions(version_id),
  FOREIGN KEY (claimed_node_id) REFERENCES renderer_nodes(node_id)
);

CREATE INDEX IF NOT EXISTS idx_music_mv_render_jobs_queue
  ON music_mv_render_jobs(status, priority DESC, created_at);
CREATE INDEX IF NOT EXISTS idx_music_mv_render_jobs_lease
  ON music_mv_render_jobs(claimed_node_id, lease_expires_at);

CREATE TABLE IF NOT EXISTS music_mv_render_job_events (
  event_id TEXT PRIMARY KEY,
  job_id TEXT NOT NULL,
  event_type TEXT NOT NULL,
  status TEXT NOT NULL,
  node_id TEXT,
  detail_json TEXT NOT NULL DEFAULT '{}',
  created_at TEXT NOT NULL,
  FOREIGN KEY (job_id) REFERENCES music_mv_render_jobs(job_id)
);

CREATE INDEX IF NOT EXISTS idx_music_mv_render_job_events_job
  ON music_mv_render_job_events(job_id, created_at);

CREATE TABLE IF NOT EXISTS template_validation_records (
  validation_id TEXT PRIMARY KEY,
  version_id TEXT NOT NULL,
  render_job_id TEXT NOT NULL,
  semantic_integrity TEXT NOT NULL,
  video_encode_count INTEGER NOT NULL,
  intermediate_video_count INTEGER NOT NULL,
  external_resource_read_count INTEGER NOT NULL,
  missing_resource_count INTEGER NOT NULL,
  renderer_version TEXT NOT NULL,
  elapsed_seconds REAL NOT NULL,
  evidence_json TEXT NOT NULL DEFAULT '{}',
  validated_at TEXT NOT NULL,
  FOREIGN KEY (version_id) REFERENCES template_versions(version_id)
);

CREATE INDEX IF NOT EXISTS idx_template_validation_version
  ON template_validation_records(version_id, validated_at DESC);

INSERT OR IGNORE INTO template_categories
  (category_key, name_zh, name_en, sort_order, enabled, created_at, updated_at)
VALUES
  ('birthday', '生日祝福', 'Birthday', 10, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('family', '亲情家庭', 'Family', 20, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('baby-growth', '宝宝成长', 'Baby & Growth', 30, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('love', '恋爱告白', 'Love', 40, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('friendship', '友情纪念', 'Friendship', 50, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('wedding-anniversary', '婚礼与纪念日', 'Wedding & Anniversary', 60, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('graduation', '毕业青春', 'Graduation', 70, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('inspiration', '励志成长', 'Inspiration', 80, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('healing', '情绪疗愈', 'Healing', 90, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('breakup', '失恋告别', 'Breakup & Farewell', 100, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('party-festival', '派对节日', 'Party & Festival', 110, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('gaming-anime', '游戏与二次元', 'Gaming & Anime', 120, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
