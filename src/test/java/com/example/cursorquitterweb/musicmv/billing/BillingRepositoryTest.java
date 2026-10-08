package com.example.cursorquitterweb.musicmv.billing;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.cursorquitterweb.musicmv.service.*;

class BillingRepositoryTest {
    @Test void reminderRequiresUntouchedWelcomeAndNoPendingGeneration() throws Exception {
        Capture db=new Capture();BillingRepository r=new BillingRepository(db);
        r.ensureWelcomeCredits("u");r.balance("u",100);r.balance("other",100);
        r.reserve("u","r","job",100,10);r.settle("job","released");
        r.reserve("u","r2","job2",100,10);r.settle("job2","consumed");
        String script=String.join("\n",
            "import sqlite3,json,pathlib,sys",
            "d=sqlite3.connect(':memory:')",
            "d.executescript(pathlib.Path('src/main/resources/db/music-mv-billing-schema.sql').read_text())",
            "s=json.loads(sys.argv[1])",
            "def run(i): return d.execute(s[i]['sql'],s[i]['params']).fetchall()",
            "run(0);run(0);assert d.execute('SELECT COUNT(*) FROM music_mv_billing_grants').fetchone()[0]==1;assert run(1)[0][-1] is not None;assert run(2)[0][-1] is None",
            "run(3);assert run(1)[0][-1] is None;run(4);assert run(1)[0][-1] is not None",
            "run(5);run(6);assert run(1)[0][-1] is None",
            "d.execute(\"UPDATE music_mv_billing_reservations SET state='released'\")",
            "d.execute(\"INSERT INTO music_mv_billing_reservations(job_id,user_id,request_id,grant_id,state,credits) VALUES('paid','u','paid','paid-grant','reserved',10)\")",
            "assert run(1)[0][-1] is None");
        runPython(script,new ObjectMapper().writeValueAsString(db.statements));
    }
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

    @Test void welcomeIsPermanentUniqueAndSpentAfterExpiringCredits() throws Exception {
        Capture db=new Capture();BillingRepository r=new BillingRepository(db);
        r.ensureWelcomeCredits("u");r.ensureWelcomeCredits("u");r.grant("stripe","paid","u","sub","starter",10,100,200);
        r.reserve("u","paid-request","paid-job",150,10);r.reserve("u","trial-request","trial-job",150,10);
        r.reserve("u","extra","extra",150,10);r.settle("trial-job","released");
        r.balance("u",9999999999L);r.ensureWelcomeCredits("u");r.reserve("u","later","later",9999999999L,10);r.settle("later","consumed");r.ensureWelcomeCredits("u");r.balance("u",9999999999L);
        String script=String.join("\n",
            "import sqlite3,json,pathlib,sys",
            "d=sqlite3.connect(':memory:');d.row_factory=sqlite3.Row",
            "d.executescript(pathlib.Path('src/main/resources/db/music-mv-billing-schema.sql').read_text())",
            "s=json.loads(sys.argv[1])",
            "def run(i): return d.execute(s[i]['sql'],s[i]['params']).fetchall()",
            "run(0);run(1);run(2);assert len(run(3))==1;assert len(run(4))==1;assert run(5)==[]",
            "assert d.execute(\"SELECT g.provider FROM music_mv_billing_grants g JOIN music_mv_billing_reservations r USING(grant_id) WHERE r.job_id='paid-job'\").fetchone()[0]=='stripe'",
            "run(6);b=run(7)[0];assert b['remaining']==10 and b['welcome_remaining']==10 and b['period_end'] is None and b['allowance']==0",
            "run(8);assert len(run(9))==1;run(10);run(11);assert run(12)[0]['remaining']==0",
            "assert d.execute(\"SELECT count(*) FROM music_mv_billing_grants WHERE provider='internal'\").fetchone()[0]==1");
        runPython(script,new ObjectMapper().writeValueAsString(db.statements));
    }

    @Test void historySeparatesOwnersAndReleasedCreditsWithoutInventingRefunds() throws Exception {
        Capture db=new Capture();BillingRepository r=new BillingRepository(db);
        r.ensureWelcomeCredits("u");r.ensureWelcomeCredits("other");
        r.reserve("u","r","job",100,10);r.settle("job","released");r.history("u",21,0);r.history("u",1,1);
        runPython(String.join("\n",
            "import sqlite3,json,pathlib,sys",
            "d=sqlite3.connect(':memory:');d.row_factory=sqlite3.Row",
            "d.executescript(pathlib.Path('src/main/resources/db/music-mv-billing-schema.sql').read_text())",
            "s=json.loads(sys.argv[1])",
            "def run(i): return d.execute(s[i]['sql'],s[i]['params']).fetchall()",
            "run(0);run(1);run(2);run(3)",
            "rows=run(4);assert len(rows)==2",
            "assert {(r['kind'],r['amount']) for r in rows}=={('welcome',10),('released',0)}",
            "assert len(run(5))==1"),new ObjectMapper().writeValueAsString(db.statements));
    }

    @Test void usageCountsEachGenerationOnceAndFiltersBeforePagination() throws Exception {
        Capture db=new Capture();BillingRepository r=new BillingRepository(db);
        r.ensureWelcomeCredits("u");r.ensureWelcomeCredits("other");r.reserve("u","r","j",100,10);r.settle("j","consumed");
        r.history("u",1,0,"welcome");r.history("u",1,1,"welcome");r.usage("u");r.generationSpend("u");r.generationSpend("other");
        runPython(String.join("\n",
            "import sqlite3,json,pathlib,sys",
            "d=sqlite3.connect(':memory:');d.row_factory=sqlite3.Row",
            "d.executescript(pathlib.Path('src/main/resources/db/music-mv-billing-schema.sql').read_text())",
            "d.executescript(\"CREATE TABLE ai_music_jobs(job_id TEXT,user_id TEXT); CREATE TABLE ai_music_candidates(candidate_id TEXT,job_id TEXT,title TEXT,selected INT,created_at TEXT); INSERT INTO ai_music_jobs VALUES('j','u'); INSERT INTO ai_music_candidates VALUES('c1','j','First song',0,'2026-01-01'),('c2','j','Second song',1,'2026-01-01');\")",
            "s=json.loads(sys.argv[1])",
            "def run(i): return d.execute(s[i]['sql'],s[i]['params']).fetchall()",
            "run(0);run(1);run(2);run(3)",
            "assert run(4)[0]['kind']=='welcome';assert run(5)==[]",
            "totals=run(6)[0];assert totals['earned']==10 and totals['spent']==10",
            "jobs=run(7);assert len(jobs)==1 and jobs[0]['credits']==10 and jobs[0]['title']=='Second song'",
            "assert run(8)==[]"),new ObjectMapper().writeValueAsString(db.statements));
    }

    @Test void balanceRefreshBatchesGrantReconciliationAndReadInOrder() throws Exception {
        Capture db=new Capture() {
            @Override public List<D1QueryResult> batch(List<D1Statement> statements) {
                for(D1Statement statement:statements) query(statement.getSql(),statement.getParams().toArray());
                return Arrays.asList(new D1QueryResult(Collections.emptyList(),null),new D1QueryResult(Collections.emptyList(),null),
                    new D1QueryResult(Collections.singletonList(StripeGateway.map("remaining",90,"allowance",100,"reserved",10,"welcome_remaining",10)),null));
            }
        };
        assertThat(new BillingRepository(db).refreshBalance("u",150)).containsEntry("remaining",90);
        assertThat(db.statements).hasSize(3);
        runPython(String.join("\n",
            "import sqlite3,json,pathlib,sys",
            "d=sqlite3.connect(':memory:');d.row_factory=sqlite3.Row",
            "d.executescript(pathlib.Path('src/main/resources/db/music-mv-billing-schema.sql').read_text())",
            "d.execute('CREATE TABLE ai_music_jobs(job_id TEXT,user_id TEXT,status TEXT)')",
            "d.execute(\"INSERT INTO music_mv_billing_grants(grant_id,provider,payment_id,user_id,subscription_id,plan_key,allowance,period_start,period_end) VALUES('paid','stripe','invoice','u','sub','starter',100,100,200)\")",
            "d.executemany('INSERT INTO ai_music_jobs VALUES(?,?,?)',[('done','u','completed'),('failed','u','failed'),('running','u','running'),('other','other','failed')])",
            "d.executemany(\"INSERT INTO music_mv_billing_reservations(job_id,user_id,request_id,grant_id,state,credits) VALUES(?,?,?,'paid','reserved',10)\",[('done','u','a'),('failed','u','b'),('running','u','c'),('other','other','d')])",
            "d.execute(\"UPDATE music_mv_billing_reservations SET grant_id='other-grant' WHERE user_id='other'\")",
            "s=json.loads(sys.argv[1])",
            "def run():",
            " with d:",
            "  for statement in s: rows=d.execute(statement['sql'],statement['params']).fetchall()",
            " return dict(rows[0])",
            "balance=run();assert balance['remaining']==90 and balance['reserved']==10 and balance['welcome_remaining']==10 and balance['unused_welcome_id'] is None",
            "assert {r['job_id']:r['state'] for r in d.execute('SELECT job_id,state FROM music_mv_billing_reservations')}=={'done':'consumed','failed':'released','running':'reserved','other':'reserved'}",
            "assert run()==balance;assert d.execute(\"SELECT COUNT(*) FROM music_mv_billing_grants WHERE payment_id='welcome:u'\").fetchone()[0]==1",
            "d.execute(\"UPDATE ai_music_jobs SET status='failed' WHERE job_id='running'\")",
            "balance=run();assert balance['remaining']==100 and balance['reserved']==0 and balance['unused_welcome_id'] is not None"
        ),new ObjectMapper().writeValueAsString(db.statements));
    }
    @Test void incompleteBalanceBatchFailsInsteadOfReturningAnUnverifiedBalance() {
        assertThatThrownBy(() -> new BillingRepository(new Capture()).refreshBalance("u",150))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("Incomplete billing balance batch");
    }

    @Test void clearingCheckoutCannotEraseOtherOwnerProviderNewTokenOrSubscription() throws Exception {
        Capture db=new Capture();BillingRepository r=new BillingRepository(db);
        r.claimCheckout("stripe","u","old","starter","url",100);
        r.clearCheckout("stripe","other","old");r.clearCheckout("paypal","u","old");r.clearCheckout("stripe","u","new");
        r.clearCheckout("stripe","u","old");r.claimCheckout("stripe","u","new","creator","url",100);
        r.clearCheckout("stripe","u","old");r.syncSubscription("stripe","u","sub","active",false);r.clearCheckout("stripe","u","new");
        runPython(String.join("\n",
            "import sqlite3,json,pathlib,sys",
            "d=sqlite3.connect(':memory:');d.row_factory=sqlite3.Row",
            "d.executescript(pathlib.Path('src/main/resources/db/music-mv-billing-schema.sql').read_text())",
            "s=json.loads(sys.argv[1])",
            "def run(i): return d.execute(s[i]['sql'],s[i]['params']).fetchall()",
            "run(0);assert run(1)==[] and run(2)==[] and run(3)==[]",
            "assert len(run(4))==1;assert len(run(5))==1;assert run(6)==[]",
            "run(7);assert run(8)==[]",
            "row=d.execute('SELECT * FROM music_mv_billing_subscriptions').fetchone();assert row['checkout_token']=='new' and row['subscription_id']=='sub'"
        ),new ObjectMapper().writeValueAsString(db.statements));
    }

    private void runPython(String script,String data) throws Exception {
        Process process=new ProcessBuilder("python3","-c",script,data).redirectErrorStream(true).start();
        assertThat(process.waitFor(15,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        String output=new String(org.springframework.util.StreamUtils.copyToByteArray(process.getInputStream()),java.nio.charset.StandardCharsets.UTF_8);
        assertThat(process.exitValue()).withFailMessage(output).isZero();
    }

    static class Capture extends D1DatabaseClient {
        List<Map<String,Object>> statements=new ArrayList<>();Capture(){super(new ObjectMapper());}
        @Override public D1QueryResult query(String sql,Object...params){statements.add(StripeGateway.map("sql",sql,"params",Arrays.asList(params)));return new D1QueryResult(sql.startsWith("SELECT COALESCE(SUM(g.allowance")?Collections.singletonList(StripeGateway.map("remaining",0,"allowance",0,"reserved",0,"welcome_remaining",0)):Collections.emptyList(),null);}
        @Override public List<D1QueryResult> batch(List<D1Statement> batch){for(D1Statement s:batch)query(s.getSql(),s.getParams().toArray());return Collections.emptyList();}
    }
}
