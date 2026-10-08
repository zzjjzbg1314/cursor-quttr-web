package com.example.cursorquitterweb.musicmv.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
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

    private void run(BillingRepositoryTest.Capture db,String script) throws Exception {
        Process process=new ProcessBuilder("python3","-c",script,new ObjectMapper().writeValueAsString(db.statements)).redirectErrorStream(true).start();
        boolean ended=process.waitFor(15,TimeUnit.SECONDS);
        if(!ended) process.destroyForcibly();
        assertThat(ended).withFailMessage("本地SQL验证超过15秒").isTrue();
        String output=new String(org.springframework.util.StreamUtils.copyToByteArray(process.getInputStream()),StandardCharsets.UTF_8);
        assertThat(process.exitValue()).withFailMessage(output).isZero();
    }
}
