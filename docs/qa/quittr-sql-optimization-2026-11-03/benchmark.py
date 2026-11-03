"""本地 SQLite 合成数据对照，不连接线上，输出查询计划与逐次耗时。"""
import sqlite3, json, time, statistics, pathlib, re
root=pathlib.Path(__file__).resolve().parent
source=(root.parents[2]/'src/main/java/com/example/cursorquitterweb/service/impl/PostServiceImpl.java').read_text()
section=source.split('public PostPageResult getAllPostsWithUpvotesAndCount',1)[1].split('int offset',1)[0].split('String sql = String.format(',1)[1]
actual=''.join(json.loads(s) for s in re.findall(r'"(?:[^"\\]|\\.)*"',section))
assert actual==(root/'after.sql').read_text().strip(), '实际 Java SQL 必须与测量版本一致'
old=(root/'before.sql').read_text().strip()
con=sqlite3.connect(':memory:');con.row_factory=sqlite3.Row
fields=['post_id','user_id','user_nickname','user_stage','avatar_url','content','is_deleted','created_at','updated_at','original_language','content_zh','content_en','content_ja','content_ko','content_de','content_fr','content_pt','content_es','translation_status','translated_at','emojiCountry']
con.execute('CREATE TABLE posts ('+', '.join(f+' '+('INTEGER' if f=='is_deleted' else 'TEXT')+(' PRIMARY KEY' if f=='post_id' else '') for f in fields)+')')
con.execute('CREATE TABLE post_likes (post_id TEXT PRIMARY KEY, like_count INTEGER)')
con.execute('CREATE TABLE comments (post_id TEXT, is_deleted INTEGER)')
con.execute('CREATE TABLE music (id INTEGER PRIMARY KEY, create_at TEXT, title TEXT)')
for i in range(10000):
 values={f:f+'-'+str(i).zfill(6) for f in fields};values.update(post_id=str(i),is_deleted=int(i%13==0))
 con.execute('INSERT INTO posts VALUES ('+','.join('?' for _ in fields)+')',[values[f] for f in fields])
 if i%3:con.execute('INSERT INTO post_likes VALUES (?,?)',(str(i),i%20))
con.executemany('INSERT INTO comments VALUES (?,?)',((str(i%10000),int(i%7==0)) for i in range(100000)))
con.executemany('INSERT INTO music VALUES (?,?,?)',((i,str(10000-i).zfill(6),'音乐'+str(i)) for i in range(10000)))
con.commit()
def query(sql,args):return [dict(r) for r in con.execute(sql,args)]
def measure(sql,args):
 samples=[]
 for _ in range(9):
  t=time.perf_counter();query(sql,args);samples.append((time.perf_counter()-t)*1000)
 return {'median_ms':round(statistics.median(samples),3),'samples_ms':[round(x,3) for x in samples]}
results=[];checks=0
for indexed in [False,True]:
 if indexed:
  con.execute('CREATE INDEX idx_comments_post_id_is_deleted ON comments(post_id,is_deleted)')
  con.execute('CREATE INDEX idx_posts_is_deleted_created_at ON posts(is_deleted,created_at DESC)')
 for field in ['created_at','updated_at','user_nickname','user_stage']:
  for direction in ['ASC','DESC']:
   before=old%(field,direction);after=actual%(field,direction,field,direction)
   for size,page in [(0,0),(1,0),(5,0),(10,1),(50,20),(1000,0),(10,2000)]:
    a=(0,0,size,page*size);b=(0,size,page*size,0)
    assert query(before,a)==query(after,b),(indexed,field,direction,size,page)
    checks+=1
 for size,page in [(5,0),(10,1),(50,20),(1000,0)]:
  a=(0,0,size,page*size);b=(0,size,page*size,0)
  before=old%('created_at','DESC');after=actual%('created_at','DESC','created_at','DESC')
  query(before,a);query(after,b)
  paired={'before':[], 'after':[]}
  for repeat in range(9):
   for label in (['before','after'] if repeat%2==0 else ['after','before']):
    statement,params=(before,a) if label=='before' else (after,b)
    t=time.perf_counter();query(statement,params);paired[label].append((time.perf_counter()-t)*1000)
  result={'indexed':indexed,'size':size,'page':page,**{label:{'median_ms':round(statistics.median(values),3),'samples_ms':[round(v,3) for v in values]} for label,values in paired.items()}}
  result['before_plan']=[dict(r) for r in con.execute('EXPLAIN QUERY PLAN '+before,a)]
  result['after_plan']=[dict(r) for r in con.execute('EXPLAIN QUERY PLAN '+after,b)]
  results.append(result)
# 相同排序值、空表和全删除场景也需保持现有查询结果。
for scenario in ['ties','all_deleted','empty']:
 if scenario=='ties':con.execute("UPDATE posts SET user_stage='same'")
 elif scenario=='all_deleted':con.execute('UPDATE posts SET is_deleted=1')
 else:con.execute('DELETE FROM posts')
 for direction in ['ASC','DESC']:
  for size,page in [(5,0),(10,1),(50,20),(10,2000)]:
   assert query(old%('user_stage',direction),(0,0,size,page*size))==query(actual%('user_stage',direction,'user_stage',direction),(0,size,page*size,0)),scenario
   checks+=1
# 音乐只验证候选索引，不擅自执行生产 DDL。
music='SELECT * FROM music ORDER BY create_at ASC LIMIT ? OFFSET ?'
music_before=measure(music,(10,0)); expected=query(music,(10,0))
con.execute('CREATE INDEX idx_music_create_at ON music(create_at)')
assert query(music,(10,0))==expected
music_after=measure(music,(10,0))
out={'sqlite_version':sqlite3.sqlite_version,'dataset':{'posts':10000,'comments':100000,'music':10000},'boundary':'合成数据本地SQLite执行时间，非D1端到端延迟；每组9次热缓存样本，非P95','equivalence_checks':checks,'posts':results,'music_candidate_index':{'before':music_before,'after':music_after}}
(root/'benchmark-results.json').write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'checks':checks,'posts':[{k:r[k] for k in ['indexed','size','page']}|{'before_ms':r['before']['median_ms'],'after_ms':r['after']['median_ms']} for r in results],'music_before_ms':music_before['median_ms'],'music_after_ms':music_after['median_ms']},ensure_ascii=False))
