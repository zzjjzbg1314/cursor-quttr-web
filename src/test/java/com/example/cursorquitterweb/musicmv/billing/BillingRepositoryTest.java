package com.example.cursorquitterweb.musicmv.billing;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.cursorquitterweb.musicmv.service.*;

class BillingRepositoryTest {
    @Test void actualSqlProtectsAllowanceExpiryReplayAndRefundOrdering() throws Exception {
        Capture db=new Capture();BillingRepository r=new BillingRepository(db);
        r.grant("stripe","in_1","u","sub_1","starter",10,100,200);
        r.reserve("u","r1","j1",150,10);
        r.reserve("u","r2","j2",150,10);
        r.settle("j1","released");
        r.reserve("u","r2","j2",150,10);
        r.settle("j2","consumed");
        r.settle("j2","released");
        r.balance("u",150);
        r.revoke("stripe","in_2");
        r.grant("stripe","in_2","u","sub_1","starter",300,200,300);
        r.reserve("u","r3","j3",250,10);
        r.reserve("other","r4","j4",150,10);
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
        String script="import sqlite3,json,sys,pathlib\nd=sqlite3.connect(':memory:')\nd.executescript(pathlib.Path('src/main/resources/db/music-mv-billing-schema.sql').read_text())\nd.execute('CREATE TABLE ai_music_jobs(job_id TEXT,user_id TEXT,status TEXT)')\nd.executemany('INSERT INTO ai_music_jobs VALUES(?,?,?)',[('j1','u','completed'),('j2','other','failed')])\nd.executemany(\"INSERT INTO music_mv_billing_reservations(job_id,user_id,request_id,grant_id,state,credits) VALUES(?,?,?,'i','reserved',10)\", [('j1','u','r1'),('j2','other','r2')])\ns=json.loads(sys.argv[1]);d.execute(s['sql'],s['params'])\nassert d.execute('SELECT state FROM music_mv_billing_reservations ORDER BY job_id').fetchall()==[('consumed',),('reserved',)]";
        Process process=new ProcessBuilder("python3","-c",script,new ObjectMapper().writeValueAsString(db.statements.get(0))).redirectErrorStream(true).start();
        assertThat(process.waitFor(15,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        String output=new String(org.springframework.util.StreamUtils.copyToByteArray(process.getInputStream()),java.nio.charset.StandardCharsets.UTF_8);
        assertThat(process.exitValue()).withFailMessage(output).isZero();
    }
    @Test void creditsAreReservedAtomicallyAndRefundsStayInTheirOriginalPeriod() throws Exception {
        Capture db=new Capture(); BillingRepository r=new BillingRepository(db);
        r.grant("stripe","old","u","sub","starter",25,100,200);
        r.reserve("u","one","j1",150,10);
        r.reserve("u","two","j2",150,10);
        r.reserve("u","three","j3",150,10);
        r.balance("u",150);
        r.settle("j1","released");
        r.reserve("u","three","j3",150,10);
        r.grant("stripe","new","u","sub","starter",300,200,300);
        r.balance("u",200);
        r.settle("j2","released");
        r.balance("u",200);
        r.reserve("u","next","j4",200,20);
        r.balance("u",200);
        String script=String.join("\n",
            "import sqlite3,json,pathlib,sys",
            "d=sqlite3.connect(':memory:');d.row_factory=sqlite3.Row",
            "d.executescript(pathlib.Path('src/main/resources/db/music-mv-billing-schema.sql').read_text())",
            "s=json.loads(sys.argv[1])",
            "def run(i): return d.execute(s[i]['sql'],s[i]['params']).fetchall()",
            "run(0);run(1);run(2);assert run(3)==[]",
            "b=run(4)[0];assert (b['remaining'],b['reserved'],b['allowance'])==(5,20,25)",
            "run(5);run(5);assert len(run(6))==1;assert run(6)==[]",
            "run(7);assert run(8)[0]['remaining']==300",
            "run(9);assert run(10)[0]['remaining']==300",
            "run(11);b=run(12)[0];assert (b['remaining'],b['reserved'])==(280,20)");
        runPython(script,new ObjectMapper().writeValueAsString(db.statements));
    }

    @Test void providersHaveIndependentPaymentsButShareOneSubscriptionSlot() throws Exception {
        Capture db=new Capture(); BillingRepository r=new BillingRepository(db);
        r.saveCustomer("stripe","u","same");
        r.saveCustomer("paypal","u","same");
        r.grant("stripe","payment","u","sub","starter",300,100,200);
        r.grant("paypal","payment","u","sub","starter",300,100,200);
        r.revoke("stripe","payment");
        r.balance("u",150);
        r.processed("stripe","event","paid");
        r.processed("paypal","event","paid");
        r.claimCheckout("stripe","u","token1","starter","url",100);
        r.claimCheckout("paypal","u","token2","starter","url",100);
        r.syncSubscription("stripe","u","sub","active",false);
        r.claimCheckout("paypal","u","token2","starter","url",4000);
        r.syncSubscription("stripe","u",null,null,false);
        r.claimCheckout("paypal","u","token2","starter","url",4000);
        r.syncSubscription("stripe","u","late","active",false);
        r.saveCheckout("stripe","u","token2","wrong","wrong");
        r.saveCheckout("paypal","u","token2","right","right");
        r.reserve("u","request","job",150,10);
        String script=String.join("\n",
            "import sqlite3,json,pathlib,sys",
            "d=sqlite3.connect(':memory:');d.row_factory=sqlite3.Row;d.execute('PRAGMA foreign_keys=ON')",
            "d.executescript(pathlib.Path('src/main/resources/db/music-mv-billing-schema.sql').read_text())",
            "s=json.loads(sys.argv[1])",
            "def run(i): return d.execute(s[i]['sql'],s[i]['params']).fetchall()",
            "run(0);run(1);assert d.execute('SELECT COUNT(*) FROM music_mv_billing_customers').fetchone()[0]==2",
            "run(2);run(2);run(3);assert d.execute('SELECT COUNT(*) FROM music_mv_billing_grants').fetchone()[0]==2",
            "run(4);run(5);assert run(6)[0]['remaining']==300",
            "run(7);run(7);run(8);assert d.execute('SELECT COUNT(*) FROM music_mv_billing_events').fetchone()[0]==2",
            "assert len(run(9))==1;assert run(10)==[]",
            "run(11);assert run(12)==[];run(13);assert len(run(14))==1",
            "run(15);run(16);assert d.execute('SELECT checkout_id FROM music_mv_billing_subscriptions').fetchone()[0] is None",
            "run(17);row=d.execute('SELECT * FROM music_mv_billing_subscriptions').fetchone();assert (row['provider'],row['checkout_id'],row['subscription_id'])==('paypal','right',None)",
            "assert len(run(18))==1",
            "row=d.execute('SELECT g.provider,g.grant_id FROM music_mv_billing_reservations r JOIN music_mv_billing_grants g ON g.grant_id=r.grant_id').fetchone();assert row['provider']=='paypal' and row['grant_id']!='payment'");
        runPython(script,new ObjectMapper().writeValueAsString(db.statements));
    }

    private void runPython(String script,String data) throws Exception {
        Process process=new ProcessBuilder("python3","-c",script,data).redirectErrorStream(true).start();
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
