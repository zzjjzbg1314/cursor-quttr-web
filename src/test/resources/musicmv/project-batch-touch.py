import json
import sqlite3
import sys

for case in json.load(sys.stdin):
    db = sqlite3.connect(':memory:')
    db.executescript('''
    CREATE TABLE templates(template_id TEXT,current_version_id TEXT,deleted_at TEXT);
    CREATE TABLE template_versions(template_id TEXT,version_id TEXT);
    CREATE TABLE music_mv_projects(project_id TEXT PRIMARY KEY,user_id TEXT,name TEXT,status TEXT,current_step TEXT,song_candidate_id TEXT,template_id TEXT,template_version_id TEXT,draft_json TEXT,revision INTEGER,created_at TEXT,updated_at TEXT,deleted_at TEXT);
    CREATE TABLE music_mv_project_assets(project_id TEXT,asset_id TEXT,slot_key TEXT,timeline_order INTEGER,crop_json TEXT,created_at TEXT,updated_at TEXT);
    CREATE TABLE music_mv_user_assets(asset_id TEXT PRIMARY KEY,user_id TEXT,last_used_at TEXT,updated_at TEXT,deleted_at TEXT);
    ''')
    db.executemany("INSERT INTO music_mv_user_assets VALUES(?,'u','before','before',NULL)", [('asset_'+str(i),) for i in range(42)])
    if case['name'] != 'new':
        owner = 'other' if case['name'] == 'foreign' else 'u'
        db.execute("INSERT INTO music_mv_projects(project_id,user_id,revision,updated_at) VALUES('p',?,3,'before')", (owner,))
    db.commit()
    with db:
        for statement in case['statements']:
            db.execute(statement['sql'], statement['params'])
    expected = 42 if case['name'] == 'new' else 0
    assert db.execute("SELECT COUNT(*) FROM music_mv_user_assets WHERE last_used_at <> 'before'").fetchone()[0] == expected
    assert db.execute('SELECT COUNT(*) FROM music_mv_project_assets').fetchone()[0] == expected
    db.close()
print('42-photo atomic save, stale revision and foreign owner passed')
