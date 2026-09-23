import pathlib
import sqlite3

root = pathlib.Path(__file__).resolve().parents[3]
db = sqlite3.connect(':memory:')
db.executescript((root/'main/resources/db/music-mv-d1-schema.sql').read_text())
queries = [
    "SELECT project_id FROM music_mv_projects WHERE user_id='u' AND deleted_at IS NULL ORDER BY updated_at DESC LIMIT 100",
    "SELECT asset_id FROM music_mv_user_assets WHERE user_id='u' AND kind='image' AND status='active' AND deleted_at IS NULL AND datetime(expires_at)>CURRENT_TIMESTAMP ORDER BY last_used_at DESC,created_at DESC LIMIT 40",
    "SELECT job_id FROM music_mv_render_jobs WHERE client_id='u' ORDER BY created_at DESC,job_id DESC LIMIT 20",
    "SELECT job_id FROM music_mv_render_jobs WHERE client_id='u' AND status='completed' AND output_storage_key IS NOT NULL ORDER BY created_at DESC,job_id DESC LIMIT 21 OFFSET 0",
]
for sql in queries:
    plan = [row[3] for row in db.execute('EXPLAIN QUERY PLAN '+sql)]
    assert not any('TEMP B-TREE' in line for line in plan), plan
    assert any('INDEX' in line for line in plan), plan
print('4 list query plans use indexes without temporary sorting')
