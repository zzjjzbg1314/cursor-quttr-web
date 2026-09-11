import json
import sqlite3
import sys

statements = json.load(sys.stdin)
preparations = {
    "unused": "",
    "project": "INSERT INTO music_mv_projects VALUES('t','v1','{}')",
    "unknown_project": "INSERT INTO music_mv_projects VALUES('t',NULL,'{}')",
    "empty_project": "INSERT INTO music_mv_projects VALUES('t','','{}')",
    "project_snapshot": "INSERT INTO music_mv_projects VALUES('other','v3','v1')",
    "job": "INSERT INTO music_mv_render_jobs VALUES('v1','{}','{}','{}')",
    "job_snapshot": "INSERT INTO music_mv_render_jobs VALUES('v2','v1','{}','{}')",
    "offline": "UPDATE templates SET status='offline'",
    "unready_current": "UPDATE template_versions SET validation_status='blocked' WHERE version_id='v2'",
    "unfinished_runtime": "UPDATE template_runtime_packages SET status='uploading' WHERE version_id='v1'",
    "unfinished_media": "UPDATE template_media SET status='uploading' WHERE version_id='v1'",
}
retired = {"unused", "unknown_project", "empty_project"}
for name, prepare in preparations.items():
    db = sqlite3.connect(":memory:")
    db.execute("PRAGMA foreign_keys=ON")
    db.executescript("""
        CREATE TABLE templates(template_id TEXT PRIMARY KEY,current_version_id TEXT,status TEXT,deleted_at TEXT);
        CREATE TABLE template_versions(version_id TEXT PRIMARY KEY,template_id TEXT,version_number INTEGER,status TEXT,validation_status TEXT);
        CREATE TABLE music_mv_projects(template_id TEXT,template_version_id TEXT,draft_json TEXT);
        CREATE TABLE music_mv_render_jobs(version_id TEXT REFERENCES template_versions(version_id),request_json TEXT,result_json TEXT,evidence_json TEXT);
        CREATE TABLE music_mv_user_assets(asset_id TEXT);
        INSERT INTO music_mv_user_assets VALUES('user-photo');
        CREATE TABLE template_media(version_id TEXT REFERENCES template_versions(version_id),template_id TEXT,provider TEXT,provider_asset_id TEXT,status TEXT);
        CREATE TABLE template_runtime_packages(version_id TEXT REFERENCES template_versions(version_id),template_id TEXT,object_key TEXT,status TEXT);
        CREATE TABLE template_media_cleanup(provider TEXT,provider_asset_id TEXT,template_id TEXT,version_id TEXT,PRIMARY KEY(provider,provider_asset_id));
        INSERT INTO templates VALUES('t','v2','published',NULL);
        INSERT INTO template_versions VALUES('v1','t',1,'published','browser_ready'),('v2','t',2,'published','browser_ready'),('v3','t',3,'draft','browser_ready');
        INSERT INTO template_media VALUES('v1','t','cloudflare_images','old-image','ready'),('v2','t','cloudflare_images','current-image','ready');
        INSERT INTO template_runtime_packages VALUES('v1','t','old-package','ready'),('v2','t','current-package','ready');
    """)
    for table in ("template_version_resource_refs", "template_slots", "template_validation_records", "template_browser_parity_validations", "template_browser_scenes"):
        db.execute(f"CREATE TABLE {table}(version_id TEXT REFERENCES template_versions(version_id))")
        db.executemany(f"INSERT INTO {table} VALUES(?)", [("v1",), ("v2",)])
    if prepare:
        db.execute(prepare)
    db.commit()
    with db:
        for statement in statements:
            db.execute(statement["sql"], statement["params"])
    remaining = [r[0] for r in db.execute("SELECT version_id FROM template_versions ORDER BY version_id")]
    assert remaining == (["v2", "v3"] if name in retired else ["v1", "v2", "v3"]), (name, remaining)
    queue = list(db.execute("SELECT provider,provider_asset_id FROM template_media_cleanup ORDER BY provider"))
    assert queue == ([("cloudflare_images", "old-image"), ("r2", "old-package")] if name in retired else []), (name, queue)
    assert db.execute("SELECT * FROM music_mv_user_assets").fetchall() == [("user-photo",)]
    assert db.execute("SELECT current_version_id FROM templates").fetchone()[0] == "v2"
    assert db.execute("PRAGMA foreign_key_check").fetchall() == []
    db.close()
print("11 retirement cases passed")
