import json
import sqlite3
import sys

for case in json.load(sys.stdin):
    db = sqlite3.connect(":memory:")
    db.executescript("""
        CREATE TABLE templates(template_id TEXT,current_version_id TEXT,deleted_at TEXT);
        CREATE TABLE template_versions(template_id TEXT,version_id TEXT);
        CREATE TABLE music_mv_projects(project_id TEXT PRIMARY KEY,user_id TEXT,name TEXT,status TEXT,current_step TEXT,song_candidate_id TEXT,template_id TEXT,template_version_id TEXT,draft_json TEXT,revision INTEGER,created_at TEXT,updated_at TEXT,deleted_at TEXT);
        CREATE TABLE music_mv_project_assets(project_id TEXT,asset_id TEXT);
        INSERT INTO templates VALUES('t','v2',NULL);
        INSERT INTO template_versions VALUES('t','v1'),('t','v2');
    """)
    name = case["name"]
    if name in ("bound_old", "deleted_bound"):
        db.execute("INSERT INTO music_mv_projects(project_id,user_id,template_id,template_version_id,revision,updated_at) VALUES('p','u','t','v1',1,'before')")
        db.execute("INSERT INTO music_mv_project_assets VALUES('p','photo')")
    if name == "deleted_bound":
        db.execute("DELETE FROM template_versions WHERE version_id='v1'")
    db.commit()
    with db:
        for statement in case["statements"]:
            db.execute(statement["sql"], statement["params"])
    row = db.execute("SELECT updated_at FROM music_mv_projects WHERE project_id='p'").fetchone()
    expected = name in ("current", "bound_old", "none")
    assert bool(row and row[0] == "marker") == expected, name
    if name == "deleted_bound":
        assert row[0] == "before"
        assert db.execute("SELECT COUNT(*) FROM music_mv_project_assets").fetchone()[0] == 1
    db.close()
print("7 cases passed")
