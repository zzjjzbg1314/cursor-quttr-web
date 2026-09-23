package com.example.cursorquitterweb.musicmv.service;
import java.util.*;
import java.nio.file.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import com.example.cursorquitterweb.musicmv.support.ApiException;

class MusicLyricsDraftServiceTest {
    ObjectMapper mapper=new ObjectMapper(); Sqlite db; MusicLyricsDraftService service;
    @BeforeEach void setup() throws Exception {db=new Sqlite();service=new MusicLyricsDraftService(db,mapper);}
    @AfterEach void cleanup() throws Exception {Files.deleteIfExists(db.path);}
    ObjectNode request(int revision,String text,String reason){
        ObjectNode value=mapper.createObjectNode();value.put("revision",revision);value.put("reason",reason);
        ObjectNode draft=value.putObject("draft");draft.put("title","Birthday");draft.put("text",text);draft.put("language","Chinese");return value;
    }
    @Test void typingDoesNotCreateHistoryAndRewritePreservesOriginal() {
        service.save("alice","lyric_123",request(0,"one","edit"));
        service.save("alice","lyric_123",request(1,"two","edit"));
        JsonNode changed=service.save("alice","lyric_123",request(2,"three","rewrite"));
        assertThat(changed.path("history").size()).isEqualTo(1);
        assertThat(changed.path("history").get(0).path("text").asText()).isEqualTo("two");
        assertThat(service.save("alice","lyric_123",request(3,"three","restore")).path("history").size()).isEqualTo(1);
    }
    @Test void staleDeviceCannotOverwriteAndOwnersAreIsolated(){
        service.save("alice","lyric_123",request(0,"one","edit"));
        assertThatThrownBy(()->service.save("alice","lyric_123",request(0,"stale","edit"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.get("bob","lyric_123")).isInstanceOf(ApiException.class);
        service.save("bob","lyric_123",request(0,"other","edit"));
        assertThat(service.get("alice","lyric_123").path("text").asText()).isEqualTo("one");
        assertThat(service.list("bob").path("items").size()).isEqualTo(1);
    }
    @Test void historyIsServerOwnedBoundedAndRestorable(){
        for(int i=0;i<25;i++)service.save("alice","lyric_123",request(i,"text"+i,"generate"));
        JsonNode saved=service.get("alice","lyric_123");assertThat(saved.path("history").size()).isEqualTo(20);
        assertThat(saved.path("history").get(0).path("text").asText()).isEqualTo("text23");
        ObjectNode r=request(25,"text23","restore");((ObjectNode)r.path("draft")).putArray("history");
        JsonNode restored=service.save("alice","lyric_123",r);
        assertThat(restored.path("history").size()).isEqualTo(20);assertThat(restored.path("history").get(0).path("text").asText()).isEqualTo("text24");
    }
    @Test void listOmitsSnapshotsButDetailKeepsRestorableHistory() {
        service.save("alice","lyric_123",request(0,"first","generate"));
        service.save("alice","lyric_123",request(1,"second","rewrite"));
        JsonNode summary=service.list("alice").path("items").get(0);
        assertThat(summary.has("history")).isFalse();
        assertThat(summary.has("alternatives")).isFalse();
        assertThat(summary.path("text").asText()).isEqualTo("second");
        assertThat(service.get("alice","lyric_123").path("history").get(0).path("text").asText()).isEqualTo("first");
    }
    @Test void staleAccountPageCannotWriteIntoNewSession(){
        ObjectNode oldPage=request(0,"private lyrics","edit");oldPage.put("ownerId","alice");
        assertThatThrownBy(()->service.save("bob","lyric_123",oldPage)).isInstanceOf(ApiException.class);
        assertThat(service.list("bob").path("items").size()).isZero();
    }
    @Test void rejectsOversizeInvalidIdsAndRevision(){
        assertThatThrownBy(()->service.save("alice","../bad",request(0,"x","edit"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.save("alice","lyric_123",request(-1,"x","edit"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.save("alice","lyric_123",request(0,String.join("",Collections.nCopies(5001,"x")),"edit"))).isInstanceOf(ApiException.class);
    }
    static class Sqlite extends D1DatabaseClient {
        final Path path;
        Sqlite() throws Exception {super(new ObjectMapper());path=Files.createTempFile("lyrics-draft-test-",".sqlite");}
        @Override public D1QueryResult query(String sql,Object...params){
            try{
                ObjectMapper m=new ObjectMapper();String script="import sqlite3,json,sys,pathlib\ndb=sqlite3.connect(sys.argv[1])\ndb.row_factory=sqlite3.Row\ndb.executescript(pathlib.Path('src/main/resources/db/music-mv-lyrics-drafts.sql').read_text())\ncur=db.execute(sys.argv[2],json.loads(sys.argv[3]))\nrows=[dict(r) for r in cur.fetchall()]\ndb.commit()\nprint(json.dumps(rows))";
                Process p=new ProcessBuilder("python3","-c",script,path.toString(),sql,m.writeValueAsString(params)).redirectErrorStream(true).start();
                if(!p.waitFor(10,java.util.concurrent.TimeUnit.SECONDS)){p.destroyForcibly();throw new IllegalStateException("SQLite timeout");}
                String output=new String(org.springframework.util.StreamUtils.copyToByteArray(p.getInputStream()),java.nio.charset.StandardCharsets.UTF_8);
                if(p.exitValue()!=0)throw new IllegalStateException(output);
                return new D1QueryResult(m.readValue(output,new com.fasterxml.jackson.core.type.TypeReference<List<Map<String,Object>>>(){}),null);
            }catch(Exception e){throw new IllegalStateException(e);}
        }
    }
}
