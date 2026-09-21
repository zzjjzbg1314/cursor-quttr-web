package com.example.cursorquitterweb.musicmv.service;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class TemplateTagServiceTest {
    @Test void 婚礼分类一次性补标保留其他标签且取消后不再补回() throws Exception {
        D1DatabaseClient d1=mock(D1DatabaseClient.class);
        new TemplateTagService(d1).list();
        ArgumentCaptor<List<D1Statement>> batch=ArgumentCaptor.forClass(List.class);
        verify(d1).batch(batch.capture());
        String script=String.join("\n",
            "import sqlite3,json,sys",
            "db=sqlite3.connect(':memory:');db.execute('PRAGMA foreign_keys=ON')",
            "db.executescript('CREATE TABLE templates(template_id TEXT PRIMARY KEY,category_key TEXT,tags_json TEXT); CREATE TABLE template_category_items(template_id TEXT,category_key TEXT);')",
            "db.executemany('INSERT INTO templates VALUES (?,?,?)',[('primary','wedding','[]'),('secondary','birthday','[]'),('unrelated','family','[]')])",
            "db.execute(\"INSERT INTO template_category_items VALUES ('secondary','wedding')\");db.commit()",
            "batch=json.loads(sys.argv[1])",
            "def run():",
            " with db:",
            "  for item in batch: db.execute(item['sql'],item['params'])",
            "run();run()",
            "assert db.execute(\"SELECT template_id FROM template_tag_items WHERE tag_key='wedding' ORDER BY template_id\").fetchall()==[('primary',),('secondary',)]",
            "assert db.execute(\"SELECT tag_key FROM template_tag_items WHERE template_id='secondary' ORDER BY tag_key\").fetchall()==[('birthday',),('wedding',)]",
            "db.execute(\"DELETE FROM template_tag_items WHERE template_id='primary' AND tag_key='wedding'\")",
            "db.execute(\"INSERT INTO templates VALUES ('future','wedding','[]')\");db.commit();run()",
            "assert db.execute(\"SELECT template_id FROM template_tag_items WHERE tag_key='wedding'\").fetchall()==[('secondary',)]");
        Process process=new ProcessBuilder("python3","-c",script,new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(batch.getValue())).redirectErrorStream(true).start();
        assertTrue(process.waitFor(15,java.util.concurrent.TimeUnit.SECONDS));
        String output=new String(org.springframework.util.StreamUtils.copyToByteArray(process.getInputStream()),java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0,process.exitValue(),output);
    }
    @Test void 初始化只执行一次且保留原始标签() {
        D1DatabaseClient d1=mock(D1DatabaseClient.class); TemplateTagService service=new TemplateTagService(d1);
        service.list(); service.list();
        ArgumentCaptor<List<D1Statement>> batch=ArgumentCaptor.forClass(List.class);
        verify(d1,times(1)).batch(batch.capture());
        List<D1Statement> sql=batch.getValue();
        assertEquals(7,sql.stream().filter(s->s.getSql().startsWith("INSERT OR IGNORE INTO template_tags(")).count());
        assertTrue(sql.stream().anyMatch(s->s.getSql().contains("NOT EXISTS (SELECT 1 FROM template_tag_migrations")));
        assertFalse(sql.stream().anyMatch(s->s.getSql().contains("tags_json")));
        assertTrue(sql.stream().anyMatch(s->s.getSql().contains("template_category_items")));
    }
    @Test void 省略标签保留关联而空列表清空() {
        D1DatabaseClient d1=mock(D1DatabaseClient.class); TemplateTagService service=new TemplateTagService(d1);
        service.replace("tpl_1",null); verifyNoInteractions(d1);
        service.replace("tpl_1",Collections.emptyList());
        ArgumentCaptor<List<D1Statement>> batch=ArgumentCaptor.forClass(List.class);
        verify(d1,times(2)).batch(batch.capture());
        List<D1Statement> last=batch.getAllValues().get(1);
        assertEquals(1,last.size()); assertEquals(Collections.singletonList("tpl_1"),last.get(0).getParams());
        assertTrue(last.get(0).getSql().startsWith("DELETE FROM template_tag_items"));
    }
    @Test void 不存在标签不能清除已有标签() {
        D1DatabaseClient d1=mock(D1DatabaseClient.class); TemplateTagService service=new TemplateTagService(d1);
        assertThrows(com.example.cursorquitterweb.musicmv.support.ApiException.class,()->service.replace("tpl_1",Arrays.asList("unknown")));
        verify(d1,times(1)).batch(anyList());
    }
    @Test void 多选去重且未知标签被拒绝() {
        D1DatabaseClient d1=mock(D1DatabaseClient.class);
        when(d1.query(eq("SELECT tag_key FROM template_tags"),any(Object[].class))).thenReturn(new D1QueryResult(Arrays.asList(Collections.singletonMap("tag_key","birthday"),Collections.singletonMap("tag_key","wedding")),null));
        TemplateTagService service=new TemplateTagService(d1);
        assertEquals(Arrays.asList("birthday","wedding"),service.validate(Arrays.asList("birthday","wedding","birthday")));
    }
    @Test void 新增校验先于数据库写入() {
        D1DatabaseClient d1=mock(D1DatabaseClient.class); TemplateTagService service=new TemplateTagService(d1);
        Map<String,Object> request=new HashMap<>(); request.put("key","Bad Key"); request.put("nameZh","新年"); request.put("nameEn","New Year");
        assertThrows(com.example.cursorquitterweb.musicmv.support.ApiException.class,()->service.add(request)); verifyNoInteractions(d1);
        request.put("key","new-year"); request.put("nameEn"," ");
        assertThrows(com.example.cursorquitterweb.musicmv.support.ApiException.class,()->service.add(request)); verifyNoInteractions(d1);
    }
    @Test void 新增支持双语且重复名称不写入第二行() {
        D1DatabaseClient d1=mock(D1DatabaseClient.class); TemplateTagService service=new TemplateTagService(d1);
        Map<String,Object> request=new HashMap<>(); request.put("key","new-year"); request.put("nameZh"," 新年 "); request.put("nameEn"," New Year ");
        when(d1.query(eq("SELECT tag_key FROM template_tags WHERE tag_key=?"),eq("new-year")))
                .thenReturn(new D1QueryResult(Collections.emptyList(),null))
                .thenReturn(new D1QueryResult(Collections.singletonList(Collections.singletonMap("tag_key","new-year")),null));
        service.add(request);
        ArgumentCaptor<List<D1Statement>> batch=ArgumentCaptor.forClass(List.class);
        verify(d1,times(2)).batch(batch.capture());
        assertEquals(Arrays.asList("new-year","新年","New Year"),batch.getAllValues().get(1).get(0).getParams());
        com.example.cursorquitterweb.musicmv.support.ApiException error=assertThrows(com.example.cursorquitterweb.musicmv.support.ApiException.class,()->service.add(request));
        assertEquals("TEMPLATE_TAG_EXISTS",error.getCode());
    }
    @Test void 多语种名称共用标识并按英文兜底() {
        Map<String,String> names=new HashMap<>(); names.put("zh-CN","情人节"); names.put("en","Valentine's Day"); names.put("ja","バレンタインデー");
        assertEquals("バレンタインデー",TemplateTagService.localizedName(names,"ja-JP"));
        assertEquals("Valentine's Day",TemplateTagService.localizedName(names,"es"));
        assertEquals("情人节",TemplateTagService.localizedName(names,"zh-CN"));
        D1DatabaseClient d1=mock(D1DatabaseClient.class); TemplateTagService service=new TemplateTagService(d1);
        Map<String,Object> request=new HashMap<>(); request.put("key","test-occasion"); request.put("translations",names); service.add(request);
        ArgumentCaptor<List<D1Statement>> batch=ArgumentCaptor.forClass(List.class); verify(d1,times(2)).batch(batch.capture());
        assertEquals(4,batch.getAllValues().get(1).size());
        assertTrue(batch.getAllValues().get(1).stream().allMatch(q->q.getParams().get(0).equals("test-occasion")));
    }
    @Test void SQLite验证新增事务和生日迁移不会重复补回() throws Exception {
        D1DatabaseClient d1=mock(D1DatabaseClient.class); TemplateTagService service=new TemplateTagService(d1);
        service.list(); Map<String,Object> request=new HashMap<>();request.put("key","new-year");
        Map<String,String> names=new LinkedHashMap<>(); names.put("zh-CN","新年"); names.put("en","New Year"); names.put("ja","新年のお祝い"); request.put("translations",names); service.add(request);
        service.replace("birthday-template",Collections.emptyList());
        ArgumentCaptor<List<D1Statement>> batch=ArgumentCaptor.forClass(List.class); verify(d1,times(3)).batch(batch.capture());
        String script=String.join("\n",
            "import sqlite3,json,sys",
            "db=sqlite3.connect(':memory:');db.execute('PRAGMA foreign_keys=ON')",
            "db.execute('CREATE TABLE templates(template_id TEXT PRIMARY KEY,category_key TEXT,tags_json TEXT)')",
            "db.execute('CREATE TABLE template_category_items(template_id TEXT,category_key TEXT)')",
            "db.executemany('INSERT INTO templates VALUES (?,?,?)',[('birthday-template','birthday','[\"original\"]'),('family-template','family','[]'),('other-template','couples','[]')])",
            "db.execute(\"INSERT INTO template_category_items VALUES ('family-template','birthday')\");db.commit()",
            "batches=json.loads(sys.argv[1])",
            "def run(batch):",
            " with db:",
            "  for item in batch: db.execute(item['sql'],item['params'])",
            "run(batches[0])",
            "assert db.execute('SELECT COUNT(*) FROM template_tags').fetchone()[0]==7",
            "assert db.execute('SELECT COUNT(*) FROM template_tag_items').fetchone()[0]==2",
            "assert db.execute('SELECT COUNT(*) FROM template_tag_translations').fetchone()[0]==14",
            "db.execute(\"DELETE FROM template_tag_migrations WHERE migration_key='merge-wedding-v1'\")",
            "db.execute(\"INSERT INTO template_tags(tag_key,name_zh,name_en) VALUES ('wedding-anniversary','结婚纪念日','Wedding Anniversary')\")",
            "db.execute(\"INSERT INTO template_tag_translations VALUES ('wedding-anniversary','en','Wedding Anniversary')\")",
            "db.executemany('INSERT INTO template_tag_items VALUES (?,?)',[('family-template','wedding-anniversary'),('other-template','wedding-anniversary'),('other-template','wedding')]);db.commit()",
            "run(batches[0]);run(batches[0])",
            "assert db.execute(\"SELECT COUNT(*) FROM template_tag_items WHERE tag_key='wedding'\").fetchone()[0]==2",
            "assert db.execute(\"SELECT COUNT(*) FROM template_tags WHERE tag_key='wedding-anniversary'\").fetchone()[0]==0",
            "assert db.execute(\"SELECT COUNT(*) FROM template_tag_translations WHERE tag_key='wedding-anniversary'\").fetchone()[0]==0",
            "assert db.execute(\"SELECT COUNT(*) FROM template_tag_items WHERE tag_key='birthday'\").fetchone()[0]==2",
            "run(batches[1])",
            "assert db.execute(\"SELECT name FROM template_tag_translations WHERE tag_key='new-year' AND locale='ja'\").fetchone()[0]=='新年のお祝い'",
            "run(batches[2]);run(batches[0])",
            "assert db.execute(\"SELECT COUNT(*) FROM template_tag_items WHERE template_id='birthday-template'\").fetchone()[0]==0",
            "assert db.execute(\"SELECT tags_json FROM templates WHERE template_id='birthday-template'\").fetchone()[0]=='[\"original\"]'",
            "bad=json.loads(json.dumps(batches[1]));bad[0]['params']=['another-key','不同','Different'];bad[1]['params']=['another-key','zh-CN','新年']",
            "try: run(bad);raise AssertionError('duplicate accepted')",
            "except sqlite3.IntegrityError: pass",
            "assert db.execute(\"SELECT COUNT(*) FROM template_tags WHERE tag_key='another-key'\").fetchone()[0]==0");
        Process process=new ProcessBuilder("python3","-c",script,new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(batch.getAllValues())).redirectErrorStream(true).start();
        assertTrue(process.waitFor(15,java.util.concurrent.TimeUnit.SECONDS));
        String output=new String(org.springframework.util.StreamUtils.copyToByteArray(process.getInputStream()),java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0,process.exitValue(),output);
    }
}
