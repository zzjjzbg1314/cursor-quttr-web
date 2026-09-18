import json
import sqlite3
import sys
from pathlib import Path

schema = Path('src/main/resources/db/music-mv-d1-schema.sql').read_text()
db = sqlite3.connect(':memory:')
db.row_factory = sqlite3.Row
db.executescript(schema)
db.execute('DROP INDEX idx_ai_music_jobs_library')
db.execute('DROP INDEX idx_ai_music_jobs_user_library')
db.execute('CREATE INDEX idx_ai_music_jobs_library ON ai_music_jobs(client_id,status,job_id)')
for _ in range(2):
    db.executescript(schema)
    assert [row['name'] for row in db.execute('PRAGMA index_info(idx_ai_music_jobs_user_library)')] == ['user_id', 'status', 'job_id']
    assert [row['name'] for row in db.execute('PRAGMA index_info(idx_ai_music_jobs_library)')] == ['client_id', 'status', 'job_id']
db.execute("INSERT INTO music_mv_users(user_id,display_name,handle,status,last_login_at,created_at,updated_at) VALUES ('user','Test','test','active',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
db.execute("INSERT INTO music_mv_user_sessions(session_id,user_id,token_sha256,expires_at,last_seen_at,created_at) VALUES ('session','user','hash',datetime('now','+1 day'),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
queries = json.loads(Path(sys.argv[1]).read_text())
def run(index):
    return db.execute(queries[index]['sql'], queries[index]['params'])
assert run(0).fetchone()['session_touch_due'] == 0
run(1)
assert db.execute('SELECT changes()').fetchone()[0] == 0
db.execute("UPDATE music_mv_user_sessions SET last_seen_at=datetime('now','-6 minutes')")
assert run(0).fetchone()['session_touch_due'] == 1
run(1)
assert db.execute('SELECT changes()').fetchone()[0] == 1
assert run(0).fetchone()['session_touch_due'] == 0
db.execute("UPDATE music_mv_user_sessions SET revoked_at=CURRENT_TIMESTAMP")
assert run(0).fetchone() is None
db.execute("UPDATE music_mv_user_sessions SET revoked_at=NULL,expires_at=datetime('now','-1 minute')")
assert run(0).fetchone() is None
db.execute("UPDATE music_mv_user_sessions SET expires_at=datetime('now','+1 day')")
db.execute("UPDATE music_mv_users SET status='disabled'")
assert run(0).fetchone() is None
print('索引重复迁移、会话写入间隔、撤销、过期与禁用校验通过')
