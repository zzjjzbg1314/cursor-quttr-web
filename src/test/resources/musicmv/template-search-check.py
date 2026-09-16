import json
import sqlite3
import sys

# 使用 SQLite 执行生产谓词，覆盖无重建索引的新模板、跨语种与字面通配符。
db = sqlite3.connect(':memory:')
db.executescript('''
CREATE TABLE templates(template_id TEXT,slug TEXT,tags_json TEXT,category_key TEXT,status TEXT);
CREATE TABLE template_translations(template_id TEXT,locale TEXT,name TEXT,description TEXT);
CREATE TABLE template_source_metadata(template_id TEXT,source_title TEXT,source_description TEXT,source_category TEXT,source_search_keyword TEXT,source_hashtags_json TEXT);
CREATE TABLE template_categories(category_key TEXT,enabled INT,is_selectable INT);
CREATE TABLE template_category_items(template_id TEXT,category_key TEXT);
INSERT INTO template_categories VALUES('wedding',1,1),('family',1,1);
INSERT INTO templates VALUES('one','number-001','["ocean"]','wedding','published'),('two','number-002','[]','family','published'),('draft','number-003','[]','wedding','draft');
INSERT INTO template_category_items VALUES('two','wedding');
INSERT INTO template_translations VALUES('one','es','unique spanish','golden memory'),('one','en','Number one',''),('two','en','100% special','');
''')
for case in json.load(open(sys.argv[1])):
    where = " FROM templates t WHERE t.status='published' " + case['sql']
    rows = db.execute('SELECT t.template_id'+where+' ORDER BY t.template_id',case['params']).fetchall()
    count = db.execute('SELECT COUNT(*)'+where,case['params']).fetchone()[0]
    assert count == len(rows)
    expected = {'one','two'} if case['q'] in ('wedding','婚礼','boda','casamento','pernikahan') else {'one'} if case['q'] in ('unique spanish','golden memory','ocean') else {'two'} if case['q']=='100%' else set()
    assert {r[0] for r in rows} == expected, (case,rows)
    if case['q']=='wedding':
        db.execute("INSERT INTO templates VALUES('new','004','[]','wedding','published')")
        assert db.execute('SELECT COUNT(*)'+where,case['params']).fetchone()[0] == 3
        db.execute("UPDATE templates SET category_key='family' WHERE template_id='new'")
        assert db.execute('SELECT COUNT(*)'+where,case['params']).fetchone()[0] == 2
        db.execute("DELETE FROM templates WHERE template_id='new'")
print('SQLite search scenarios passed')
