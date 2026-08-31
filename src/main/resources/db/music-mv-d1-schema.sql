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

-- Consumer accounts for the Music MV product. These tables deliberately live
-- in the dedicated Music MV D1 and are not related to cursor-quttr-web's legacy
-- users or user_identities tables.
CREATE TABLE IF NOT EXISTS music_mv_users (
  user_id TEXT PRIMARY KEY,
  display_name TEXT NOT NULL,
  handle TEXT NOT NULL,
  avatar_url TEXT,
  email TEXT,
  locale TEXT NOT NULL DEFAULT 'en',
  status TEXT NOT NULL DEFAULT 'active',
  last_login_at TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_music_mv_users_handle
  ON music_mv_users(handle);
CREATE INDEX IF NOT EXISTS idx_music_mv_users_email
  ON music_mv_users(email);

CREATE TABLE IF NOT EXISTS music_mv_user_identities (
  identity_id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  provider TEXT NOT NULL,
  provider_subject TEXT NOT NULL,
  provider_email TEXT,
  email_verified INTEGER NOT NULL DEFAULT 0,
  provider_display_name TEXT,
  provider_avatar_url TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  last_login_at TEXT NOT NULL,
  UNIQUE (provider, provider_subject),
  FOREIGN KEY (user_id) REFERENCES music_mv_users(user_id)
);

CREATE INDEX IF NOT EXISTS idx_music_mv_user_identities_user
  ON music_mv_user_identities(user_id, provider);

CREATE TABLE IF NOT EXISTS music_mv_user_sessions (
  session_id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  token_sha256 TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  last_seen_at TEXT NOT NULL,
  created_at TEXT NOT NULL,
  revoked_at TEXT,
  UNIQUE (token_sha256),
  FOREIGN KEY (user_id) REFERENCES music_mv_users(user_id)
);

CREATE INDEX IF NOT EXISTS idx_music_mv_user_sessions_user
  ON music_mv_user_sessions(user_id, expires_at);
CREATE INDEX IF NOT EXISTS idx_music_mv_user_sessions_token
  ON music_mv_user_sessions(token_sha256, expires_at, revoked_at);

-- Reusable, private user media library. Binary data remains in private R2;
-- D1 stores only ownership, lifecycle and deduplication metadata.
CREATE TABLE IF NOT EXISTS music_mv_user_assets (
  asset_id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  project_id TEXT,
  kind TEXT NOT NULL,
  storage TEXT NOT NULL,
  asset_url TEXT NOT NULL,
  file_name TEXT NOT NULL,
  content_type TEXT NOT NULL,
  size_bytes INTEGER NOT NULL,
  sha256 TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'active',
  expires_at TEXT NOT NULL,
  last_used_at TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  FOREIGN KEY (user_id) REFERENCES music_mv_users(user_id)
);

CREATE INDEX IF NOT EXISTS idx_music_mv_user_assets_library
  ON music_mv_user_assets(user_id, kind, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_music_mv_user_assets_project
  ON music_mv_user_assets(user_id, project_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_music_mv_user_assets_hash
  ON music_mv_user_assets(user_id, kind, sha256, status);

-- Cloud project drafts make the Song -> Template -> Photos flow resumable on
-- another device. The JSON is UI state only; referenced binary assets remain
-- in R2 and are linked through music_mv_project_assets.
CREATE TABLE IF NOT EXISTS music_mv_projects (
  project_id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  name TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'draft',
  current_step TEXT NOT NULL DEFAULT 'song',
  song_candidate_id TEXT,
  template_id TEXT,
  template_version_id TEXT,
  draft_json TEXT NOT NULL DEFAULT '{}',
  revision INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  submitted_at TEXT,
  deleted_at TEXT,
  FOREIGN KEY (user_id) REFERENCES music_mv_users(user_id)
);

CREATE INDEX IF NOT EXISTS idx_music_mv_projects_user
  ON music_mv_projects(user_id, status, updated_at DESC);

CREATE TABLE IF NOT EXISTS music_mv_project_assets (
  project_id TEXT NOT NULL,
  asset_id TEXT NOT NULL,
  slot_key TEXT NOT NULL,
  timeline_order INTEGER NOT NULL,
  crop_json TEXT NOT NULL DEFAULT '{}',
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  PRIMARY KEY (project_id, slot_key),
  FOREIGN KEY (project_id) REFERENCES music_mv_projects(project_id),
  FOREIGN KEY (asset_id) REFERENCES music_mv_user_assets(asset_id)
);

CREATE INDEX IF NOT EXISTS idx_music_mv_project_assets_asset
  ON music_mv_project_assets(asset_id, project_id);

-- Cloud template catalog. The website backend is the only application allowed
-- to query or mutate these tables. Browser and Mac renderer traffic goes
-- through website-backend APIs.
CREATE TABLE IF NOT EXISTS template_categories (
  category_key TEXT PRIMARY KEY,
  parent_key TEXT,
  level INTEGER NOT NULL DEFAULT 2,
  slug_path TEXT NOT NULL DEFAULT '',
  is_selectable INTEGER NOT NULL DEFAULT 1,
  name_zh TEXT NOT NULL,
  name_en TEXT NOT NULL,
  sort_order INTEGER NOT NULL,
  enabled INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_template_categories_tree
  ON template_categories(parent_key, enabled, sort_order);

CREATE TABLE IF NOT EXISTS templates (
  template_id TEXT PRIMARY KEY,
  capcut_template_id TEXT,
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
CREATE UNIQUE INDEX IF NOT EXISTS uk_templates_capcut_template_id
  ON templates(capcut_template_id) WHERE capcut_template_id IS NOT NULL;
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

-- Raw CapCut metadata is retained as source evidence. These values are never
-- rewritten into a second, internal keyword vocabulary.
CREATE TABLE IF NOT EXISTS template_source_metadata (
  template_id TEXT PRIMARY KEY,
  source_title TEXT NOT NULL DEFAULT '',
  source_description TEXT NOT NULL DEFAULT '',
  source_category TEXT NOT NULL DEFAULT '',
  source_search_keyword TEXT NOT NULL DEFAULT '',
  source_hashtags_json TEXT NOT NULL DEFAULT '[]',
  source_url TEXT NOT NULL DEFAULT '',
  classifier_version TEXT NOT NULL DEFAULT 'source-rules-v1',
  classification_locked INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  FOREIGN KEY (template_id) REFERENCES templates(template_id)
);

-- A template has one primary public category and may appear in additional
-- public categories. The legacy templates.category_key mirrors the primary
-- relation for rolling compatibility with older render/admin clients.
CREATE TABLE IF NOT EXISTS template_category_items (
  template_id TEXT NOT NULL,
  category_key TEXT NOT NULL,
  is_primary INTEGER NOT NULL DEFAULT 0,
  source TEXT NOT NULL DEFAULT 'automatic',
  confidence REAL NOT NULL DEFAULT 0,
  evidence_json TEXT NOT NULL DEFAULT '[]',
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  PRIMARY KEY (template_id, category_key),
  FOREIGN KEY (template_id) REFERENCES templates(template_id),
  FOREIGN KEY (category_key) REFERENCES template_categories(category_key)
);

CREATE INDEX IF NOT EXISTS idx_template_category_items_catalog
  ON template_category_items(category_key, is_primary, confidence, template_id);

-- Keyword landing pages are many-to-many editorial indexes. A template keeps
-- one leaf category, while collections let the same format appear in several
-- user-intent pages without duplicating the template or its render assets.
CREATE TABLE IF NOT EXISTS template_collections (
  collection_key TEXT PRIMARY KEY,
  slug TEXT NOT NULL UNIQUE,
  parent_category_key TEXT NOT NULL,
  keyword TEXT NOT NULL,
  name_zh TEXT NOT NULL,
  name_en TEXT NOT NULL,
  description_zh TEXT NOT NULL DEFAULT '',
  description_en TEXT NOT NULL DEFAULT '',
  seo_title TEXT,
  seo_description TEXT,
  sort_order INTEGER NOT NULL DEFAULT 0,
  enabled INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  FOREIGN KEY (parent_category_key) REFERENCES template_categories(category_key)
);

CREATE INDEX IF NOT EXISTS idx_template_collections_catalog
  ON template_collections(parent_category_key, enabled, sort_order);

CREATE TABLE IF NOT EXISTS template_collection_items (
  collection_key TEXT NOT NULL,
  template_id TEXT NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  source TEXT NOT NULL DEFAULT 'manual',
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  PRIMARY KEY (collection_key, template_id),
  FOREIGN KEY (collection_key) REFERENCES template_collections(collection_key),
  FOREIGN KEY (template_id) REFERENCES templates(template_id)
);

CREATE INDEX IF NOT EXISTS idx_template_collection_items_template
  ON template_collection_items(template_id, collection_key);

CREATE TABLE IF NOT EXISTS template_collection_relations (
  collection_key TEXT NOT NULL,
  related_collection_key TEXT NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (collection_key, related_collection_key),
  FOREIGN KEY (collection_key) REFERENCES template_collections(collection_key),
  FOREIGN KEY (related_collection_key) REFERENCES template_collections(collection_key)
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

-- Sanitized, browser-executable scene data. This table deliberately contains
-- no local CapCut paths or source-draft files; the customer website only sees
-- the published preview plus the exact photo-slot deltas it is allowed to
-- replace.
CREATE TABLE IF NOT EXISTS template_browser_scenes (
  version_id TEXT PRIMARY KEY,
  template_id TEXT NOT NULL,
  schema_version TEXT NOT NULL,
  manifest_sha256 TEXT NOT NULL,
  status TEXT NOT NULL,
  scene_json TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  FOREIGN KEY (template_id) REFERENCES templates(template_id),
  FOREIGN KEY (version_id) REFERENCES template_versions(version_id)
);

CREATE INDEX IF NOT EXISTS idx_template_browser_scenes_ready
  ON template_browser_scenes(template_id, status, updated_at);

-- Browser parity is versioned by the immutable scene, the official reference
-- video and the browser renderer. A scene can only be published after one of
-- these exact tuples has passed the visual comparison gate.
CREATE TABLE IF NOT EXISTS template_browser_parity_validations (
  validation_id TEXT PRIMARY KEY,
  template_id TEXT NOT NULL,
  version_id TEXT NOT NULL,
  scene_manifest_sha256 TEXT NOT NULL,
  reference_sha256 TEXT NOT NULL,
  renderer_version TEXT NOT NULL,
  status TEXT NOT NULL,
  sample_count INTEGER,
  ssim_threshold REAL,
  mae_threshold REAL,
  average_ssim REAL,
  min_ssim REAL,
  average_mae REAL,
  max_mae REAL,
  reference_duration_seconds REAL,
  output_duration_seconds REAL,
  output_sha256 TEXT,
  report_json TEXT NOT NULL DEFAULT '{}',
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  completed_at TEXT,
  UNIQUE (version_id, scene_manifest_sha256, reference_sha256, renderer_version),
  FOREIGN KEY (template_id) REFERENCES templates(template_id),
  FOREIGN KEY (version_id) REFERENCES template_versions(version_id)
);

CREATE INDEX IF NOT EXISTS idx_template_browser_parity_gate
  ON template_browser_parity_validations(version_id, status, updated_at);

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
  user_id TEXT NOT NULL,
  -- Retained during the v4 transition so older deployed readers can coexist.
  -- New writes always store the authenticated user id in both columns.
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
  UNIQUE (client_id, request_id),
  UNIQUE (user_id, request_id),
  FOREIGN KEY (user_id) REFERENCES music_mv_users(user_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_music_jobs_user
  ON ai_music_jobs(user_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS idx_ai_music_jobs_user_request
  ON ai_music_jobs(user_id, request_id) WHERE user_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ai_music_jobs_client
  ON ai_music_jobs(client_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_music_jobs_status
  ON ai_music_jobs(status, updated_at);
CREATE INDEX IF NOT EXISTS idx_ai_music_jobs_library
  ON ai_music_jobs(user_id, status, job_id);

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
  (category_key, parent_key, level, slug_path, is_selectable, name_zh, name_en,
   sort_order, enabled, created_at, updated_at)
VALUES
  ('celebrations', NULL, 1, 'celebrations', 0, '庆祝与里程碑', 'Celebrations', 10, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('birthday', 'celebrations', 2, 'celebrations/birthday', 1, '生日', 'Birthday', 11, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('wedding', 'celebrations', 2, 'celebrations/wedding', 1, '婚礼', 'Wedding', 12, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('anniversary', 'celebrations', 2, 'celebrations/anniversary', 1, '纪念日', 'Anniversary', 13, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('graduation', 'celebrations', 2, 'celebrations/graduation', 1, '毕业', 'Graduation', 14, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('holidays-parties', 'celebrations', 2, 'celebrations/holidays-parties', 1, '节日与派对', 'Holidays & Parties', 15, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('relationships', NULL, 1, 'relationships', 0, '人物与关系', 'People & Relationships', 20, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('family', 'relationships', 2, 'relationships/family', 1, '家庭', 'Family', 21, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('baby-kids', 'relationships', 2, 'relationships/baby-kids', 1, '宝宝与孩子', 'Baby & Kids', 22, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('couples', 'relationships', 2, 'relationships/couples', 1, '情侣', 'Couples', 23, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('friendship', 'relationships', 2, 'relationships/friendship', 1, '友情', 'Friendship', 24, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('life-stories', NULL, 1, 'life-stories', 0, '生活与故事', 'Life & Stories', 30, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('daily-life', 'life-stories', 2, 'life-stories/daily-life', 1, '日常生活', 'Daily Life', 31, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('travel', 'life-stories', 2, 'life-stories/travel', 1, '旅行', 'Travel', 32, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('school-life', 'life-stories', 2, 'life-stories/school-life', 1, '校园生活', 'School Life', 33, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('growing-up', 'life-stories', 2, 'life-stories/growing-up', 1, '成长记录', 'Growing Up', 34, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('recap', 'life-stories', 2, 'life-stories/recap', 1, '回顾与总结', 'Recap', 35, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('hobbies-interests', 'life-stories', 2, 'life-stories/hobbies-interests', 1, '兴趣爱好', 'Hobbies & Interests', 36, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('emotions-messages', NULL, 1, 'emotions-messages', 0, '情感与表达', 'Emotions & Messages', 40, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('motivation', 'emotions-messages', 2, 'emotions-messages/motivation', 1, '励志', 'Motivation', 41, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('healing', 'emotions-messages', 2, 'emotions-messages/healing', 1, '疗愈', 'Healing', 42, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('love-thanks', 'emotions-messages', 2, 'emotions-messages/love-thanks', 1, '爱与感谢', 'Love & Thanks', 43, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('farewell-breakup', 'emotions-messages', 2, 'emotions-messages/farewell-breakup', 1, '告别与释怀', 'Farewell & Moving On', 44, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('memorial', 'emotions-messages', 2, 'emotions-messages/memorial', 1, '纪念与缅怀', 'Memorial', 45, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT OR IGNORE INTO template_collections
  (collection_key, slug, parent_category_key, keyword, name_zh, name_en,
   description_zh, description_en, seo_title, seo_description, sort_order,
   enabled, created_at, updated_at)
VALUES
  ('family-birthday', 'family-birthday', 'birthday', 'family birthday', '家人生日', 'Family Birthday', '适合家人共同回忆的生日视频模板。', 'Birthday video templates for shared family memories.', 'Family birthday video templates', 'Create a family birthday song and video with personal photos.', 10, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('birthday-for-mom', 'birthday-for-mom', 'birthday', 'birthday mom', '妈妈生日', 'Birthday for Mom', '给妈妈创作生日歌并制作照片音乐视频。', 'Create a birthday song and photo music video for Mom.', 'Birthday video templates for Mom', 'Create an original birthday song and photo video for Mom.', 20, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('birthday-for-dad', 'birthday-for-dad', 'birthday', 'birthday dad', '爸爸生日', 'Birthday for Dad', '给爸爸创作生日歌并制作照片音乐视频。', 'Create a birthday song and photo music video for Dad.', 'Birthday video templates for Dad', 'Create an original birthday song and photo video for Dad.', 30, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('baby-first-year', 'baby-first-year', 'baby-kids', 'baby first year', '宝宝第一年', 'Baby First Year', '用歌曲和照片记录宝宝第一年的成长。', 'Turn a baby''s first year into a song-led photo story.', 'Baby first year video templates', 'Create a baby first-year song and photo music video.', 40, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('family-year-in-review', 'family-year-in-review', 'recap', 'family year recap', '家庭年度回顾', 'Family Year in Review', '用原创歌曲和家庭照片回顾这一年。', 'Review a family year with an original song and real photos.', 'Family year-in-review templates', 'Create a family year-in-review song and photo video.', 50, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('wedding-story', 'wedding-story', 'wedding', 'wedding story', '婚礼故事', 'Wedding Story', '从相遇到婚礼，用歌曲和照片讲完整故事。', 'Tell the story from meeting to wedding with song and photos.', 'Wedding story video templates', 'Create an original wedding song and photo music video.', 60, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('graduation-memories', 'graduation-memories', 'graduation', 'graduation memories', '毕业回忆', 'Graduation Memories', '把校园、同学和毕业时刻做成完整音乐视频。', 'Turn school and graduation memories into a complete music video.', 'Graduation memory video templates', 'Create a graduation song and photo music video.', 70, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('friendship-memories', 'friendship-memories', 'friendship', 'friendship memories', '友情回忆', 'Friendship Memories', '用共同经历、旅行和照片制作友情音乐视频。', 'Create a friendship music video from shared memories and trips.', 'Friendship memory video templates', 'Create an original friendship song and photo music video.', 80, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT OR IGNORE INTO template_collection_relations
  (collection_key, related_collection_key, sort_order)
VALUES
  ('family-birthday', 'birthday-for-mom', 10),
  ('family-birthday', 'birthday-for-dad', 20),
  ('birthday-for-mom', 'family-birthday', 10),
  ('birthday-for-dad', 'family-birthday', 10),
  ('baby-first-year', 'family-year-in-review', 10),
  ('family-year-in-review', 'baby-first-year', 10),
  ('wedding-story', 'friendship-memories', 10),
  ('graduation-memories', 'friendship-memories', 10),
  ('friendship-memories', 'graduation-memories', 10);
