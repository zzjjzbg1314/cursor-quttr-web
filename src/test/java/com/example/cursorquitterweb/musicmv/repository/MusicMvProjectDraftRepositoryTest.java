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
        for(String name:Arrays.asList("current","old","bound_old","missing","none","partial","deleted_bound","unbound_existing","unbound_other_owner","unbound_other_template","unbound_deleted")) {
            final List<D1Statement> captured=new ArrayList<>();
            D1DatabaseClient d1=new D1DatabaseClient(new ObjectMapper()) {
                @Override public List<D1QueryResult> batch(List<D1Statement> statements){captured.addAll(statements);return Collections.emptyList();}
                @Override public D1QueryResult query(String sql,Object...params){return new D1QueryResult(null,null);}
            };
            String template=name.equals("none")?null:"t";
            String version=name.equals("current")?"v2":name.equals("missing")?"missing":name.equals("none")||name.equals("partial")||name.startsWith("unbound_")?null:"v1";
            new MusicMvProjectDraftRepository(d1).saveSnapshot("u","p","name","draft","photos",null,template,version,"{}",2,"marker",Collections.emptyList(),Collections.emptyList());
            Map<String,Object> item=new LinkedHashMap<>();item.put("name",name);item.put("statements",captured);cases.add(item);
        }
        Path script=Paths.get(getClass().getResource("/musicmv/project-snapshot-guard.py").toURI());
        Process process=new ProcessBuilder("python3",script.toString()).redirectErrorStream(true).start();
        new ObjectMapper().writeValue(process.getOutputStream(),cases);
        assertTrue(process.waitFor(15,java.util.concurrent.TimeUnit.SECONDS));
        String result=new String(readAll(process.getInputStream()),java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0,process.exitValue(),result);assertTrue(result.contains("11 cases passed"),result);
    }
    @Test void touchIsAtomicAndStaleOrForeignSaveDoesNotTouchPhotos() throws Exception {
        List<Map<String,Object>> cases = new ArrayList<>();
        for (String name : Arrays.asList("new", "stale", "foreign")) {
            final List<D1Statement> captured = new ArrayList<>();
            D1DatabaseClient db = new D1DatabaseClient(new ObjectMapper()) {
                @Override public List<D1QueryResult> batch(List<D1Statement> statements) { captured.addAll(statements); return Collections.emptyList(); }
                @Override public D1QueryResult query(String sql,Object... params) { return new D1QueryResult(null,null); }
            };
            List<com.example.cursorquitterweb.musicmv.dto.MusicMvProjectDraftRequest.ProjectAsset> assets = new ArrayList<>();
            for (int index=0; index<42; index++) {
                com.example.cursorquitterweb.musicmv.dto.MusicMvProjectDraftRequest.ProjectAsset asset = new com.example.cursorquitterweb.musicmv.dto.MusicMvProjectDraftRequest.ProjectAsset();
                asset.setAssetId("asset_" + index); asset.setSlotKey("photo_" + index); assets.add(asset);
            }
            new MusicMvProjectDraftRepository(db).saveSnapshot("u","p","name","draft","photos",null,null,null,"{}",2,"marker",assets,Collections.nCopies(42,"{}"));
            Map<String,Object> item = new LinkedHashMap<>(); item.put("name",name); item.put("statements",captured); cases.add(item);
        }
        Process process = new ProcessBuilder("python3",Paths.get(getClass().getResource("/musicmv/project-batch-touch.py").toURI()).toString()).redirectErrorStream(true).start();
        new ObjectMapper().writeValue(process.getOutputStream(), cases);
        assertTrue(process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS));
        String output = new String(readAll(process.getInputStream()),java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(),output);
    }
    private byte[] readAll(java.io.InputStream input)throws Exception {
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
        while((n=input.read(buffer))!=-1)out.write(buffer,0,n);return out.toByteArray();
    }
}
