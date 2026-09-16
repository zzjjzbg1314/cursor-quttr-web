package com.example.cursorquitterweb.musicmv.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TemplateSearchTest {
    @Test void categoryAliasesAndLiteralWildcards() {
        for(String query:Arrays.asList("婚礼","婚禮"," WEDDING ","Ｗｅｄｄｉｎｇ","boda","casamento","pernikahan"))
            assertEquals("wedding",TemplateSearch.topic(query));
        assertEquals("baby-kids",TemplateSearch.topic("Baby　&　Kids"));
        assertNull(TemplateSearch.topic("wedding planner"));
        List<Object> params=new ArrayList<>();StringBuilder sql=new StringBuilder();
        TemplateSearch.append(sql,params,"100%_!");assertEquals("%100!%!_!!%",params.get(0));
        sql.setLength(0);params.clear();TemplateSearch.append(sql,params,"　 ");assertEquals("",sql.toString());
    }
    @Test void actualSqlFindsPublishedTemplatesInAnyLanguageAndAfterInsert() throws Exception {
        List<Map<String,Object>> cases=new ArrayList<>();
        for(String query:Arrays.asList("wedding","婚礼","boda","casamento","pernikahan","unique spanish","golden memory","ocean","100%","unmatched")) {
            List<Object> params=new ArrayList<>();StringBuilder sql=new StringBuilder();TemplateSearch.append(sql,params,query);
            Map<String,Object> row=new LinkedHashMap<>();row.put("q",query);row.put("sql",sql.toString());row.put("params",params);cases.add(row);
        }
        Path payload=Files.createTempFile("template-search-", ".json");
        try {
            new ObjectMapper().writeValue(payload.toFile(),cases);
            Process process=new ProcessBuilder("python3","src/test/resources/musicmv/template-search-check.py",payload.toString()).redirectErrorStream(true).start();
            assertTrue(process.waitFor(30,TimeUnit.SECONDS));
            String output=new String(readAll(process.getInputStream()),java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0,process.exitValue(),output);
        } finally {Files.deleteIfExists(payload);}
    }
    private byte[] readAll(java.io.InputStream input)throws Exception {
        java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();byte[] b=new byte[4096];int n;
        while((n=input.read(b))>=0)output.write(b,0,n);return output.toByteArray();
    }
}
