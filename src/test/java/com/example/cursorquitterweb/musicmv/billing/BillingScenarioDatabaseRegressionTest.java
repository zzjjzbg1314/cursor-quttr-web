package com.example.cursorquitterweb.musicmv.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

// 执行仓储实际SQL，使用独立SQLite连接验证本地并发；不替代真实D1并发验收。
class BillingScenarioDatabaseRegressionTest {
    @Test void I04_actualSqlConcurrentReservationsNeverOverdraw() throws Exception {
        BillingRepositoryTest.Capture db=new BillingRepositoryTest.Capture(); BillingRepository repo=new BillingRepository(db);
        repo.grant("stripe","in_1","u","sub_1","starter",10,100,200);
        repo.reserve("u","request","job",150,10);
        run(db,String.join("\n",
            "import concurrent.futures,tempfile,sqlite3,pathlib,json,sys",
            "s=json.loads(sys.argv[1])",
            "with tempfile.TemporaryDirectory() as tmp:",
            " path=tmp+'/billing.sqlite';d=sqlite3.connect(path)",
            " d.executescript(pathlib.Path('src/main/resources/db/music-mv-billing-schema.sql').read_text())",
            " d.execute(s[0]['sql'],s[0]['params']);d.commit();d.close()",
            " def reserve(i):",
            "  c=sqlite3.connect(path,timeout=5);p=list(s[1]['params']);p[0]='j'+str(i);p[2]='r'+str(i)",
            "  result=c.execute(s[1]['sql'],p).fetchall();c.commit();c.close();return len(result)",
            " with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool: outcomes=list(pool.map(reserve,range(100)))",
            " assert sum(outcomes)==1,outcomes",
            " d=sqlite3.connect(path);assert d.execute('SELECT SUM(credits),COUNT(*) FROM music_mv_billing_reservations').fetchone()==(10,1);d.close()"));
    }

    @Test void C03_C04_crossProviderConcurrentClaimHasOnlyOneWinner() throws Exception {
        BillingRepositoryTest.Capture db=new BillingRepositoryTest.Capture();BillingRepository repo=new BillingRepository(db);
        repo.claimCheckout("stripe","u","token","starter","https://example.test/pricing",100);
        run(db,String.join("\n",
            "import concurrent.futures,tempfile,sqlite3,pathlib,json,sys,threading",
            "s=json.loads(sys.argv[1])",
            "with tempfile.TemporaryDirectory() as tmp:",
            " path=tmp+'/billing.sqlite';d=sqlite3.connect(path);d.executescript(pathlib.Path('src/main/resources/db/music-mv-billing-schema.sql').read_text());d.close();barrier=threading.Barrier(2)",
            " def claim(i):",
            "  c=sqlite3.connect(path,timeout=5);p=list(s[0]['params']);p[1]=['stripe','paypal'][i];p[2]='token'+str(i);p[3]=['starter','creator'][i];barrier.wait(timeout=3)",
            "  result=c.execute(s[0]['sql'],p).fetchall();c.commit();c.close();return len(result)",
            " with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool: results=list(pool.map(claim,range(2)))",
            " assert sum(results)==1,results",
            " d=sqlite3.connect(path);assert d.execute('SELECT COUNT(*) FROM music_mv_billing_subscriptions').fetchone()[0]==1;d.close()"));
    }

    @Test void G04_I13_I14_periodBoundaryAndSettlementStayWithOriginalGrant() throws Exception {
        BillingRepositoryTest.Capture db=new BillingRepositoryTest.Capture();BillingRepository repo=new BillingRepository(db);
        repo.ensureWelcomeCredits("u");repo.grant("stripe","old","u","sub","starter",300,100,200);
        repo.reserve("u","r1","j1",199,10);repo.balance("u",199);repo.balance("u",200);
        repo.grant("stripe","new","u","sub","starter",300,200,300);repo.settle("j1","consumed");repo.balance("u",200);
        repo.reserve("u","r2","j2",299,10);repo.settle("j2","released");repo.balance("u",300);
        run(db,String.join("\n",
            "import sqlite3,json,sys,pathlib",
            "s=json.loads(sys.argv[1]);d=sqlite3.connect(':memory:');d.row_factory=sqlite3.Row;d.executescript(pathlib.Path('src/main/resources/db/music-mv-billing-schema.sql').read_text())",
            "def run(i): return d.execute(s[i]['sql'],s[i]['params']).fetchall()",
            "run(0);run(1);assert len(run(2))==1",
            "assert run(3)[0]['remaining']==300;assert run(4)[0]['remaining']==10",
            "run(5);run(6);assert run(7)[0]['remaining']==310",
            "assert d.execute(\"SELECT payment_id FROM music_mv_billing_grants g JOIN music_mv_billing_reservations r ON g.grant_id=r.grant_id WHERE r.job_id='j1'\").fetchone()[0]=='old'",
            "run(8);run(9);assert run(10)[0]['remaining']==10"));
    }

    @Test void I15_RISK_splitAllowancesCannotCoverOneReservation() throws Exception {
        BillingRepositoryTest.Capture db=new BillingRepositoryTest.Capture();BillingRepository repo=new BillingRepository(db);
        repo.grant("stripe","one","u","sub","starter",5,100,200);repo.grant("stripe","two","u","sub","starter",5,100,200);
        repo.balance("u",150);repo.reserve("u","r","j",150,10);
        run(db,String.join("\n",
            "import sqlite3,json,sys,pathlib",
            "s=json.loads(sys.argv[1]);d=sqlite3.connect(':memory:');d.row_factory=sqlite3.Row;d.executescript(pathlib.Path('src/main/resources/db/music-mv-billing-schema.sql').read_text())",
            "def run(i): return d.execute(s[i]['sql'],s[i]['params']).fetchall()",
            "run(0);run(1);assert run(2)[0]['remaining']==10;assert run(3)==[]"));
    }

    @Test void J04_J10_refundReplayNeverRevivesOldPeriodOrRevokesNewInvoice() throws Exception {
        BillingRepositoryTest.Capture db=new BillingRepositoryTest.Capture();BillingRepository repo=new BillingRepository(db);
        repo.revoke("stripe","old");repo.grant("stripe","old","u","sub","starter",300,100,200);
        repo.grant("stripe","new","u","sub","starter",300,200,300);repo.revoke("stripe","old");
        repo.grant("stripe","old","u","sub","starter",300,100,200);repo.balance("u",250);
        run(db,String.join("\n",
            "import sqlite3,json,sys,pathlib",
            "s=json.loads(sys.argv[1]);d=sqlite3.connect(':memory:');d.row_factory=sqlite3.Row;d.executescript(pathlib.Path('src/main/resources/db/music-mv-billing-schema.sql').read_text())",
            "for q in s[:-1]: d.execute(q['sql'],q['params'])",
            "assert d.execute(s[-1]['sql'],s[-1]['params']).fetchone()['remaining']==300",
            "assert d.execute(\"SELECT payment_id,revoked FROM music_mv_billing_grants ORDER BY payment_id\").fetchall()[0]['revoked']==0",
            "assert d.execute(\"SELECT COUNT(*) FROM music_mv_billing_grants\").fetchone()[0]==2",
            "assert d.execute(\"SELECT revoked FROM music_mv_billing_grants WHERE payment_id='old'\").fetchone()[0]==1"));
    }

    @ParameterizedTest
    @ValueSource(strings={"consumed","released"})
    void I17_J08_K05_refundKeepsSettlementHistoryAndNeverRestoresRevokedBalance(String state) throws Exception {
        BillingRepositoryTest.Capture db=new BillingRepositoryTest.Capture();BillingRepository repo=new BillingRepository(db);
        repo.grant("stripe","in_1","u","sub","starter",10,100,200);
        repo.reserve("u","r","j",150,10);repo.revoke("stripe","in_1");repo.settle("j",state);
        repo.balance("u",150);repo.usage("u");repo.history("u",21,0);
        run(db,String.join("\n",
            "import sqlite3,json,sys,pathlib",
            "s=json.loads(sys.argv[1]);d=sqlite3.connect(':memory:');d.row_factory=sqlite3.Row;d.executescript(pathlib.Path('src/main/resources/db/music-mv-billing-schema.sql').read_text())",
            "def run(i): return d.execute(s[i]['sql'],s[i]['params']).fetchall()",
            "for i in range(5): run(i)",
            "assert run(5)[0]['remaining']==0",
            "totals=run(6)[0];assert totals['earned']==10;assert totals['spent']==("+(state.equals("consumed")?"10":"0")+")",
            "history=run(7);assert len(history)==2;assert {r['kind'] for r in history}=={'revoked','"+state+"'}",
            "assert d.execute('SELECT revoked FROM music_mv_billing_grants').fetchone()[0]==1",
            "assert d.execute('SELECT state FROM music_mv_billing_reservations').fetchone()[0]=='"+state+"'"));
    }

    @Test void K09_RISK_foreignKeyRejectsMissingGrantButAllowsDifferentOwner() throws Exception {
        BillingRepositoryTest.Capture db=new BillingRepositoryTest.Capture();BillingRepository repo=new BillingRepository(db);
        repo.grant("stripe","in_1","owner","sub","starter",10,100,200);
        run(db,String.join("\n",
            "import sqlite3,json,sys,pathlib",
            "s=json.loads(sys.argv[1]);d=sqlite3.connect(':memory:');d.execute('PRAGMA foreign_keys=ON');d.executescript(pathlib.Path('src/main/resources/db/music-mv-billing-schema.sql').read_text());d.execute(s[0]['sql'],s[0]['params'])",
            "insert=\"INSERT INTO music_mv_billing_reservations(job_id,user_id,request_id,grant_id,state,credits) VALUES('j','other','r',?,'reserved',10)\"",
            "try: d.execute(insert,('missing',));raise AssertionError('missing grant was accepted')",
            "except sqlite3.IntegrityError: pass",
            "grant=d.execute('SELECT grant_id FROM music_mv_billing_grants').fetchone()[0];d.execute(insert,(grant,))",
            "assert d.execute(\"SELECT COUNT(*) FROM music_mv_billing_reservations r JOIN music_mv_billing_grants g ON g.grant_id=r.grant_id WHERE r.user_id<>g.user_id\").fetchone()[0]==1"));
    }

    @Test void K10_initializationReplayPreservesExistingLedger() throws Exception {
        BillingRepositoryTest.Capture db=new BillingRepositoryTest.Capture();BillingRepository repo=new BillingRepository(db);
        repo.grant("stripe","in_1","u","sub","starter",300,100,200);repo.reserve("u","r","j",150,10);
        run(db,String.join("\n",
            "import sqlite3,json,sys,pathlib",
            "s=json.loads(sys.argv[1]);d=sqlite3.connect(':memory:');schema=pathlib.Path('src/main/resources/db/music-mv-billing-schema.sql').read_text();d.executescript(schema)",
            "for q in s: d.execute(q['sql'],q['params'])",
            "before=d.execute('SELECT * FROM music_mv_billing_grants').fetchall();reservations=d.execute('SELECT * FROM music_mv_billing_reservations').fetchall();d.executescript(schema)",
            "assert before==d.execute('SELECT * FROM music_mv_billing_grants').fetchall();assert reservations==d.execute('SELECT * FROM music_mv_billing_reservations').fetchall()",
            "d.execute(s[0]['sql'],s[0]['params']);assert d.execute('SELECT COUNT(*) FROM music_mv_billing_grants').fetchone()[0]==1"));
    }

    @Test void K06_RISK_offsetPaginationIsStableOnlyWithoutConcurrentInsert() throws Exception {
        BillingRepositoryTest.Capture db=new BillingRepositoryTest.Capture();BillingRepository repo=new BillingRepository(db);
        repo.history("u",20,0);repo.history("u",20,20);
        run(db,String.join("\n",
            "import sqlite3,json,sys,pathlib",
            "s=json.loads(sys.argv[1]);d=sqlite3.connect(':memory:');d.row_factory=sqlite3.Row;d.executescript(pathlib.Path('src/main/resources/db/music-mv-billing-schema.sql').read_text())",
            "insert=\"INSERT INTO music_mv_billing_grants(grant_id,user_id,provider,payment_id,subscription_id,plan_key,allowance,period_start,period_end,created_at) VALUES(?,'u','stripe',?,'sub','starter',10,100,200,?)\"",
            "for i in range(40): d.execute(insert,('g%02d'%i,'in%02d'%i,'2026-10-08 01:00:00'))",
            "def page(i): return [r['id'] for r in d.execute(s[i]['sql'],s[i]['params'])]",
            "first=page(0);second=page(1);assert len(set(first+second))==40;assert page(0)==first",
            "d.execute(insert,('new','in_new','2026-10-08 02:00:00'));changed=page(1)",
            "assert set(first)&set(changed);assert len(set(first+changed))==39"));
    }

    @ParameterizedTest
    @ValueSource(strings={"paid","refund"})
    void F04_F13_actualSqlConcurrentPaidAndRefundReplaysConverge(String first) throws Exception {
        BillingRepositoryTest.Capture db=new BillingRepositoryTest.Capture();BillingRepository repo=new BillingRepository(db);
        repo.grant("stripe","in_1","u","sub","starter",300,100,200);repo.revoke("stripe","in_1");
        repo.processed("stripe","evt_paid","invoice.paid");repo.processed("stripe","evt_refund","credit_note.created");
        repo.balance("u",150);repo.ensureWelcomeCredits("u");
        run(db,String.join("\n",
            "import sqlite3,json,sys,pathlib,tempfile,concurrent.futures,random",
            "s=json.loads(sys.argv[1])",
            "with tempfile.TemporaryDirectory() as tmp:",
            " path=tmp+'/billing.sqlite';d=sqlite3.connect(path);d.executescript(pathlib.Path('src/main/resources/db/music-mv-billing-schema.sql').read_text());d.execute(s[6]['sql'],s[6]['params']);d.commit();d.close()",
            " def apply(task):",
            "  kind,i=task;c=sqlite3.connect(path,timeout=5)",
            "  with c:",
            "   if kind=='paid':",
            "    p=list(s[0]['params']);p[0]='grant'+str(i);c.execute(s[0]['sql'],p);c.execute(s[3]['sql'],s[3]['params'])",
            "   else:",
            "    c.execute(s[1]['sql'],s[1]['params']);c.execute(s[2]['sql'],s[2]['params']);c.execute(s[4]['sql'],s[4]['params'])",
            "  c.close()",
            " apply(('"+first+"',-1))",
            " tasks=[(kind,i) for kind in ('paid','refund') for i in range(100)];random.Random(7).shuffle(tasks)",
            " with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool: list(pool.map(apply,tasks))",
            " d=sqlite3.connect(path);d.row_factory=sqlite3.Row",
            " assert d.execute(\"SELECT COUNT(*),MIN(revoked) FROM music_mv_billing_grants WHERE provider='stripe'\").fetchone()[:]==(1,1)",
            " assert d.execute('SELECT COUNT(*) FROM music_mv_billing_events').fetchone()[0]==2",
            " assert d.execute('SELECT COUNT(*) FROM music_mv_billing_revocations').fetchone()[0]==1",
            " balance=d.execute(s[5]['sql'],s[5]['params']).fetchone();assert balance['remaining']==10 and balance['reserved']==0;d.close()"));
    }

    @ParameterizedTest(name="C21 清理时现状={0}")
    @ValueSource(strings={"same","new-token","new-subscription","new-provider"})
    void staleCheckoutClearPreservesNewerState(String change) throws Exception {
        BillingRepositoryTest.Capture db=new BillingRepositoryTest.Capture();BillingRepository repo=new BillingRepository(db);
        repo.clearCheckout("stripe","u","old-token");
        run(db,String.join("\n",
            "import sqlite3,json,sys,pathlib",
            "s=json.loads(sys.argv[1]);d=sqlite3.connect(':memory:');d.row_factory=sqlite3.Row;d.executescript(pathlib.Path('src/main/resources/db/music-mv-billing-schema.sql').read_text())",
            "change='"+change+"';provider='paypal' if change=='new-provider' else 'stripe';token='new-token' if change=='new-token' else 'old-token';subscription='sub_new' if change=='new-subscription' else None",
            "d.execute(\"INSERT INTO music_mv_billing_subscriptions(user_id,provider,subscription_id,status,checkout_token,checkout_plan,checkout_expires,checkout_id,checkout_url,checkout_return_url) VALUES('u',?,?,'active',?,'creator',200,'cs_new','https://checkout.test/new','https://site.test/pricing')\",(provider,subscription,token))",
            "before=dict(d.execute('SELECT * FROM music_mv_billing_subscriptions').fetchone())",
            "result=d.execute(s[0]['sql'],s[0]['params']).fetchall();after=dict(d.execute('SELECT * FROM music_mv_billing_subscriptions').fetchone())",
            "if change=='same':",
            " assert len(result)==1;assert all(after[k] is None for k in ('checkout_token','checkout_plan','checkout_expires','checkout_id','checkout_url','checkout_return_url'));assert after['provider']=='stripe'",
            "else: assert result==[] and after==before,(result,before,after)"));
    }

    @ParameterizedTest(name="C23 接管时刻={0} 成功={1}")
    @CsvSource({"99,false","100,false","101,true"})
    void crossProviderCheckoutClaimUsesStrictExpiryBoundary(long now,boolean accepted) throws Exception {
        BillingRepositoryTest.Capture db=new BillingRepositoryTest.Capture();BillingRepository repo=new BillingRepository(db);
        repo.claimCheckout("stripe","u","new-token","starter","https://site.test/pricing",now);
        run(db,String.join("\n",
            "import sqlite3,json,sys,pathlib",
            "s=json.loads(sys.argv[1]);d=sqlite3.connect(':memory:');d.row_factory=sqlite3.Row;d.executescript(pathlib.Path('src/main/resources/db/music-mv-billing-schema.sql').read_text())",
            "d.execute(\"INSERT INTO music_mv_billing_subscriptions(user_id,provider,status,cancel_at_period_end,checkout_token,checkout_plan,checkout_expires,checkout_id,checkout_url) VALUES('u','paypal','old',1,'old-token','creator',100,'old_id','old_url')\")",
            "before=dict(d.execute('SELECT * FROM music_mv_billing_subscriptions').fetchone());result=d.execute(s[0]['sql'],s[0]['params']).fetchall();after=dict(d.execute('SELECT * FROM music_mv_billing_subscriptions').fetchone())",
            accepted?"assert len(result)==1;assert after['provider']=='stripe' and after['checkout_token']=='new-token' and after['checkout_plan']=='starter';assert after['checkout_expires']=="+(now+3600)+";assert after['checkout_id'] is None and after['checkout_url'] is None and after['status'] is None and after['cancel_at_period_end']==0":"assert result==[] and after==before"));
    }

    private void run(BillingRepositoryTest.Capture db,String script) throws Exception {
        Process process=new ProcessBuilder("python3","-c",script,new ObjectMapper().writeValueAsString(db.statements)).redirectErrorStream(true).start();
        boolean ended=process.waitFor(15,TimeUnit.SECONDS);
        if(!ended) process.destroyForcibly();
        assertThat(ended).withFailMessage("本地SQL验证超过15秒").isTrue();
        String output=new String(org.springframework.util.StreamUtils.copyToByteArray(process.getInputStream()),StandardCharsets.UTF_8);
        assertThat(process.exitValue()).withFailMessage(output).isZero();
    }
}
