package com.example.cursorquitterweb.musicmv.repository;

import java.util.*;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.cursorquitterweb.musicmv.service.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TemplateTitleRepositoryTest {
    @Test void titleOnlyBatchPreservesDescriptionsMediaAndPointersAndRejectsStaleSnapshot() throws Exception {
        D1DatabaseClient client=mock(D1DatabaseClient.class);
        List<List<D1Statement>> captured=new ArrayList<>();
        when(client.batch(anyList())).thenAnswer(call->{List<D1Statement> statements=call.getArgument(0);captured.add(statements);List<D1QueryResult> result=new ArrayList<>();for(D1Statement ignored:statements) result.add(new D1QueryResult(Collections.singletonList(Collections.singletonMap("revision",8)),null));return result;});
        Map<String,String> titles=new LinkedHashMap<>();titles.put("zh-CN","原中文");titles.put("en","Original");titles.put("ja","日本語の題名");titles.put("de","Neuer Titel");
        assertTrue(new MusicMvTemplateCatalogRepository(client).updateTitles("tpl",7,"原中文","Original",titles));
        List<D1Statement> statements=captured.get(0);assertEquals(3,statements.size());
        String data=new ObjectMapper().writeValueAsString(statements);
        String script=String.join("\n",
            "import sqlite3,json,sys", "qs=json.loads(sys.argv[1])", "db=sqlite3.connect(':memory:')",
            "db.execute('CREATE TABLE templates(template_id TEXT PRIMARY KEY,revision INT,deleted_at TEXT,updated_at TEXT,current_version_id TEXT,status TEXT)')",
            "db.execute('CREATE TABLE template_translations(template_id TEXT,locale TEXT,name TEXT,description TEXT,PRIMARY KEY(template_id,locale))')",
            "db.execute(\"INSERT INTO templates VALUES ('tpl',7,NULL,'old','published-version','published')\")",
            "db.executemany('INSERT INTO template_translations VALUES (?,?,?,?)',[('tpl','zh-CN','原中文','中文说明'),('tpl','en','Original','English description'),('tpl','ja','旧題名','Keep Japanese description')])",
            "for q in qs: db.execute(q['sql'],q['params']).fetchall()",
            "assert db.execute(\"SELECT revision,current_version_id,status FROM templates\").fetchone()==(8,'published-version','published')",
            "assert db.execute(\"SELECT name,description FROM template_translations WHERE locale='ja'\").fetchone()==('日本語の題名','Keep Japanese description')",
            "assert db.execute(\"SELECT name,description FROM template_translations WHERE locale='de'\").fetchone()==('Neuer Titel','')",
            "assert db.execute(\"SELECT name,description FROM template_translations WHERE locale='en'\").fetchone()==('Original','English description')",
            "before=list(db.execute('SELECT * FROM template_translations ORDER BY locale'))",
            "for q in qs: db.execute(q['sql'],q['params']).fetchall()",
            "assert before==list(db.execute('SELECT * FROM template_translations ORDER BY locale'))",
            "assert db.execute('SELECT revision FROM templates').fetchone()==(8,)",
            "db.execute(\"UPDATE templates SET revision=7\")", "db.execute(\"UPDATE template_translations SET name='Changed source' WHERE locale='en'\")",
            "for q in qs: db.execute(q['sql'],q['params']).fetchall()",
            "assert db.execute('SELECT revision FROM templates').fetchone()==(7,)");
        Process process=new ProcessBuilder("python3","-c",script,data).redirectErrorStream(true).start();
        assertTrue(process.waitFor(15,java.util.concurrent.TimeUnit.SECONDS));assertEquals(0,process.exitValue(),new String(org.springframework.util.StreamUtils.copyToByteArray(process.getInputStream()),java.nio.charset.StandardCharsets.UTF_8));
    }
    @Test void metadataAndTranslatedTitlesShareOneGuardedBatch() {
        D1DatabaseClient client=mock(D1DatabaseClient.class);List<List<D1Statement>> captured=new ArrayList<>();
        when(client.batch(anyList())).thenAnswer(call->{captured.add(call.getArgument(0));return Collections.emptyList();});
        Map<String,String> titles=new LinkedHashMap<>();titles.put("zh-CN","新中文");titles.put("en","New title");titles.put("it","Titolo nuovo");
        assertFalse(new MusicMvTemplateCatalogRepository(client).updateMetadataWithTitles("tpl",7,"player","[]","新中文","说明","New title","Description","public",0,titles));
        assertEquals(1,captured.size());assertEquals(4,captured.get(0).size());
        assertTrue(captured.get(0).get(0).getSql().contains("description=excluded.description"));
        assertFalse(captured.get(0).get(2).getSql().contains("description=excluded.description"));
        for(D1Statement statement:captured.get(0)) assertTrue(statement.getSql().contains("revision=?"));
        assertTrue(captured.get(0).get(3).getSql().contains("RETURNING revision"));
    }
}
