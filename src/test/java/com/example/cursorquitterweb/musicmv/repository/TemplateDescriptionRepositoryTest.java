package com.example.cursorquitterweb.musicmv.repository;

import java.util.*;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.cursorquitterweb.musicmv.service.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TemplateDescriptionRepositoryTest {
    @Test void descriptionOnlyBatchPreservesTitlesMediaAndPointersAndRejectsStaleSnapshot() throws Exception {
        D1DatabaseClient client=mock(D1DatabaseClient.class);
        List<List<D1Statement>> captured=new ArrayList<>();
        when(client.batch(anyList())).thenAnswer(call->{List<D1Statement> statements=call.getArgument(0);captured.add(statements);List<D1QueryResult> result=new ArrayList<>();for(D1Statement ignored:statements) result.add(new D1QueryResult(Collections.singletonList(Collections.singletonMap("revision",8)),null));return result;});
        Map<String,String> titles=new LinkedHashMap<>();titles.put("zh-CN","中文说明");titles.put("en","English description");titles.put("ja","日本語の説明");titles.put("de","Neue Beschreibung");
        assertTrue(new MusicMvTemplateCatalogRepository(client).updateDescriptions("tpl",7,"中文说明","English description",titles));
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
            "assert db.execute(\"SELECT name,description FROM template_translations WHERE locale='ja'\").fetchone()==('旧題名','日本語の説明')",
            "assert db.execute(\"SELECT name,description FROM template_translations WHERE locale='de'\").fetchone()==('','Neue Beschreibung')",
            "assert db.execute(\"SELECT name,description FROM template_translations WHERE locale='en'\").fetchone()==('Original','English description')",
            "before=list(db.execute('SELECT * FROM template_translations ORDER BY locale'))",
            "for q in qs: db.execute(q['sql'],q['params']).fetchall()",
            "assert before==list(db.execute('SELECT * FROM template_translations ORDER BY locale'))",
            "assert db.execute('SELECT revision FROM templates').fetchone()==(8,)",
            "db.execute(\"UPDATE templates SET revision=7\")", "db.execute(\"UPDATE template_translations SET description='Changed source' WHERE locale='en'\")",
            "for q in qs: db.execute(q['sql'],q['params']).fetchall()",
            "assert db.execute('SELECT revision FROM templates').fetchone()==(7,)");
        Process process=new ProcessBuilder("python3","-c",script,data).redirectErrorStream(true).start();
        assertTrue(process.waitFor(15,java.util.concurrent.TimeUnit.SECONDS));assertEquals(0,process.exitValue(),new String(org.springframework.util.StreamUtils.copyToByteArray(process.getInputStream()),java.nio.charset.StandardCharsets.UTF_8));
    }
    @Test void metadataPersistsTranslatedDescriptionsAndSourceCopyInOneRevisionGuard() {
        D1DatabaseClient client=mock(D1DatabaseClient.class);List<List<D1Statement>> captured=new ArrayList<>();
        when(client.batch(anyList())).thenAnswer(call->{captured.add(call.getArgument(0));return Collections.emptyList();});
        Map<String,String> titles=new LinkedHashMap<>(),descriptions=new LinkedHashMap<>();
        titles.put("zh-CN","标题");titles.put("en","Title");titles.put("it","Titolo");
        descriptions.put("zh-CN","中文说明");descriptions.put("en","English description");descriptions.put("it","Descrizione italiana");
        MusicMvTemplateCatalogRepository repository=new MusicMvTemplateCatalogRepository(client);
        assertFalse(repository.updateMetadataWithTitles("tpl",7,"player","[]","标题","中文说明","Title","English description","public",0,titles,descriptions));
        assertFalse(repository.updateSourceCopy("tpl",7,titles,descriptions));
        for(List<D1Statement> statements:captured) {
            assertEquals(4,statements.size());
            for(int index=0;index<3;index++) {
                assertTrue(statements.get(index).getSql().contains("description=excluded.description"));
                assertTrue(statements.get(index).getSql().contains("revision=?"));
            }
            assertTrue(statements.get(2).getParams().contains("Descrizione italiana"));
            assertTrue(statements.get(3).getSql().contains("RETURNING revision"));
        }
    }
}
