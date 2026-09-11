package com.example.cursorquitterweb.musicmv.repository;
import com.example.cursorquitterweb.musicmv.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MusicMvTemplateRetirementTest {
    @Test void retirementOnlyRemovesUnreferencedSupersededTemplateData() throws Exception {
        List<D1Statement> captured=new ArrayList<>();
        D1DatabaseClient d1=new D1DatabaseClient(new ObjectMapper()) {
            @Override public D1QueryResult query(String sql,Object...params){return new D1QueryResult(null,null);}
            @Override public List<D1QueryResult> batch(List<D1Statement> statements){captured.addAll(statements);return Collections.emptyList();}
        };
        new MusicMvTemplateCatalogRepository(d1).retireUnusedTemplateContents();
        Path script=Paths.get(getClass().getResource("/musicmv/template-retirement-guard.py").toURI());
        Process process=new ProcessBuilder("python3",script.toString()).redirectErrorStream(true).start();
        new ObjectMapper().writeValue(process.getOutputStream(),captured);
        boolean exited=process.waitFor(15,java.util.concurrent.TimeUnit.SECONDS);
        if(!exited)process.destroyForcibly();assertTrue(exited);
        java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
        while((n=process.getInputStream().read(buffer))!=-1)output.write(buffer,0,n);
        String result=output.toString("UTF-8");assertEquals(0,process.exitValue(),result);assertTrue(result.contains("retirement cases passed"),result);
    }
}
