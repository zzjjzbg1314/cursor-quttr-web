package com.example.cursorquitterweb.musicmv.repository;
import com.example.cursorquitterweb.musicmv.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MusicMvProjectDraftRepositoryTest {
    @Test void snapshotBindingIsCheckedByActualSqliteWrite() throws Exception {
        List<Map<String,Object>> cases=new ArrayList<>();
        for(String name:Arrays.asList("current","old","bound_old","missing","none","partial","deleted_bound")) {
            final List<D1Statement> captured=new ArrayList<>();
            D1DatabaseClient d1=new D1DatabaseClient(new ObjectMapper()) {
                @Override public List<D1QueryResult> batch(List<D1Statement> statements){captured.addAll(statements);return Collections.emptyList();}
                @Override public D1QueryResult query(String sql,Object...params){return new D1QueryResult(null,null);}
            };
            String template=name.equals("none")?null:"t";
            String version=name.equals("current")?"v2":name.equals("missing")?"missing":name.equals("none")||name.equals("partial")?null:"v1";
            new MusicMvProjectDraftRepository(d1).saveSnapshot("u","p","name","draft","photos",null,template,version,"{}",2,"marker",Collections.emptyList(),Collections.emptyList());
            Map<String,Object> item=new LinkedHashMap<>();item.put("name",name);item.put("statements",captured);cases.add(item);
        }
        Path script=Paths.get(getClass().getResource("/musicmv/project-snapshot-guard.py").toURI());
        Process process=new ProcessBuilder("python3",script.toString()).redirectErrorStream(true).start();
        new ObjectMapper().writeValue(process.getOutputStream(),cases);
        assertTrue(process.waitFor(15,java.util.concurrent.TimeUnit.SECONDS));
        String result=new String(readAll(process.getInputStream()),java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0,process.exitValue(),result);assertTrue(result.contains("7 cases passed"),result);
    }
    private byte[] readAll(java.io.InputStream input)throws Exception {
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
        while((n=input.read(buffer))!=-1)out.write(buffer,0,n);return out.toByteArray();
    }
}
