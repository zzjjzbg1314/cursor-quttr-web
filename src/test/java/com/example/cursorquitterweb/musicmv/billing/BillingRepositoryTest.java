package com.example.cursorquitterweb.musicmv.billing;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.cursorquitterweb.musicmv.service.*;

class BillingRepositoryTest {
    @Test void actualSqlProtectsAllowanceExpiryReplayAndRefundOrdering() throws Exception {
        Capture db=new Capture();BillingRepository r=new BillingRepository(db);
        r.grant("in_1","u","sub_1","starter",1,100,200);
        r.reserve("u","r1","j1",150);
        r.reserve("u","r2","j2",150);
        r.settle("j1","released");
        r.reserve("u","r2","j2",150);
        r.settle("j2","consumed");
        r.settle("j2","released");
        r.balance("u",150);
        r.revoke("in_2");
        r.grant("in_2","u","sub_1","starter",30,200,300);
        r.reserve("u","r3","j3",250);
        r.reserve("other","r4","j4",150);
        r.balance("u",301);
        String script=String.join("\n",
            "import sqlite3,json,pathlib,sys",
            "d=sqlite3.connect(':memory:')",
            "d.executescript(pathlib.Path('src/main/resources/db/music-mv-billing-schema.sql').read_text())",
            "s=json.loads(sys.argv[1])",
            "def run(i): return d.execute(s[i]['sql'],s[i]['params']).fetchall()",
            "run(0);run(0)",
            "assert len(run(1))==1; assert run(1)==[]; assert run(2)==[]",
            "run(3);assert len(run(4))==1;run(5);run(6)",
            "assert run(7)[0][0]==0",
            "run(8);run(9);run(10);assert run(11)==[];assert run(12)==[];assert run(13)[0][0]==0",
            "assert d.execute('select count(*) from music_mv_billing_grants').fetchone()[0]==2",
            "assert d.execute(\"select state from music_mv_billing_reservations where job_id='j2'\").fetchone()[0]=='consumed'");
        Process process=new ProcessBuilder("python3","-c",script,new ObjectMapper().writeValueAsString(db.statements)).redirectErrorStream(true).start();
        assertThat(process.waitFor(15,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        String output=new String(org.springframework.util.StreamUtils.copyToByteArray(process.getInputStream()),java.nio.charset.StandardCharsets.UTF_8);
        assertThat(process.exitValue()).withFailMessage(output).isZero();
    }
    @Test void reconciliationOnlyChangesRequestedOwner() throws Exception {
        Capture db=new Capture(); new BillingRepository(db).reconcile("u");
        String script="import sqlite3,json,sys,pathlib\nd=sqlite3.connect(':memory:')\nd.executescript(pathlib.Path('src/main/resources/db/music-mv-billing-schema.sql').read_text())\nd.execute('CREATE TABLE ai_music_jobs(job_id TEXT,user_id TEXT,status TEXT)')\nd.executemany('INSERT INTO ai_music_jobs VALUES(?,?,?)',[('j1','u','completed'),('j2','other','failed')])\nd.executemany(\"INSERT INTO music_mv_billing_reservations(job_id,user_id,request_id,invoice_id,state) VALUES(?,?,?,'i','reserved')\", [('j1','u','r1'),('j2','other','r2')])\ns=json.loads(sys.argv[1]);d.execute(s['sql'],s['params'])\nassert d.execute('SELECT state FROM music_mv_billing_reservations ORDER BY job_id').fetchall()==[('consumed',),('reserved',)]";
        Process process=new ProcessBuilder("python3","-c",script,new ObjectMapper().writeValueAsString(db.statements.get(0))).redirectErrorStream(true).start();
        assertThat(process.waitFor(15,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        String output=new String(org.springframework.util.StreamUtils.copyToByteArray(process.getInputStream()),java.nio.charset.StandardCharsets.UTF_8);
        assertThat(process.exitValue()).withFailMessage(output).isZero();
    }
    static class Capture extends D1DatabaseClient {
        List<Map<String,Object>> statements=new ArrayList<>();Capture(){super(new ObjectMapper());}
        @Override public D1QueryResult query(String sql,Object...params){statements.add(StripeGateway.map("sql",sql,"params",Arrays.asList(params)));return new D1QueryResult(Collections.emptyList(),null);}
        @Override public List<D1QueryResult> batch(List<D1Statement> batch){for(D1Statement s:batch)query(s.getSql(),s.getParams().toArray());return Collections.emptyList();}
    }
}
