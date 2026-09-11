import json
import sqlite3
import sys
from pathlib import Path

connection = sqlite3.connect(":memory:")
connection.executescript(Path("src/main/resources/db/music-mv-d1-schema.sql").read_text())
for template_id, category in [("graduation", "graduation"), ("memorial", "memorial"), ("birthday", "birthday"), ("dad", "fathers-day")]:
    connection.execute("INSERT INTO templates(template_id,slug,category_key,status,visibility,created_at,updated_at) VALUES (?,?,?,'published','public','old','old')", (template_id, template_id, category))
    connection.execute("INSERT INTO template_category_items(template_id,category_key,is_primary,source,confidence,evidence_json,created_at,updated_at) VALUES (?,?,1,'manual',1,'[]','old','old')", (template_id, category))
queries = json.loads(Path(sys.argv[1]).read_text())
for attempt in range(2):
    for query in queries:
        sql = query["sql"]
        if sql.startswith(("CREATE TABLE IF NOT EXISTS template_taxonomy_history", "INSERT OR IGNORE INTO template_taxonomy_history", "UPDATE template_categories", "UPDATE templates SET category_key", "INSERT OR IGNORE INTO template_category_items", "UPDATE template_category_items", "DELETE FROM template_category_items")):
            connection.execute(sql, query["params"])
    assert connection.execute("SELECT COUNT(*) FROM templates").fetchone()[0] == 4
    assert connection.execute("SELECT COUNT(*) FROM template_categories WHERE enabled=1 AND level=1 AND parent_key IS NULL AND is_selectable=1").fetchone()[0] == 11
    assert dict(connection.execute("SELECT template_id,category_key FROM templates")) == {"graduation": "school-life", "memorial": "memorial", "birthday": "birthday", "dad": "family"}
    assert dict(connection.execute("SELECT template_id,old_category_key FROM template_taxonomy_history"))["graduation"] == "graduation"
    assert connection.execute("SELECT COUNT(*) FROM template_taxonomy_history").fetchone()[0] == 4
    assert connection.execute("SELECT COUNT(*) FROM template_category_items i JOIN template_categories c ON c.category_key=i.category_key WHERE c.enabled=0").fetchone()[0] == 0
    pending = connection.execute("SELECT t.template_id FROM templates t JOIN template_categories c ON c.category_key=t.category_key WHERE c.enabled=0").fetchall()
    assert pending == [("memorial",)]
print("迁移和重复执行通过：4 个模板保留，11 个主题，1 个待人工归类，历史归属完整。")
