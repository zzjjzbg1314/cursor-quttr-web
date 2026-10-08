package com.example.cursorquitterweb.musicmv.billing;

import com.example.cursorquitterweb.musicmv.service.D1DatabaseClient;
import com.example.cursorquitterweb.musicmv.service.D1QueryResult;
import com.example.cursorquitterweb.musicmv.service.D1Statement;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.example.cursorquitterweb.musicmv.billing.StripeGateway.map;

// 使用真实服务与仓库SQL串联账务流程；支付渠道模拟，临时SQLite不代替真实D1。
class BillingLedgerJourneyRegressionTest {
    @TempDir Path directory;
    final ObjectMapper json=new ObjectMapper();

    @ParameterizedTest
    @CsvSource({"starter,300","creator,1000"})
    void paidReturnWebhookDedupReservationAndRefundShareOneLedger(String plan,int allowance) throws Exception {
        SqliteClient db=new SqliteClient(directory.resolve("billing.sqlite"));
        BillingRepository repo=new BillingRepository(db);StripeGateway stripe=mock(StripeGateway.class);
        BillingSettings settings=new BillingSettings();settings.enabled=true;settings.secretKey="sk_test_fixture";
        settings.webhookSecret="whsec_fixture";settings.starterPrice="price_starter";settings.creatorPrice="price_creator";
        settings.portalConfiguration="bpc_fixture";settings.siteUrl="https://www.example.test";
        MusicBillingService service=new MusicBillingService(settings,repo,stripe,json);
        AtomicBoolean paid=new AtomicBoolean(false);long start=Instant.now().getEpochSecond()-10,end=start+3600;
        when(stripe.customer("u")).thenReturn(json.createObjectNode().put("id","cus_u"));
        ObjectNode sub=json.createObjectNode().put("id","sub_u").put("status","active")
            .put("cancel_at_period_end",false).put("latest_invoice","in_u");
        sub.set("items",json.createObjectNode().put("has_more",false).set("data",json.createArrayNode().add(
            json.createObjectNode().put("current_period_end",end).set("price",json.createObjectNode().put("id","price_"+plan)))));
        when(stripe.subscriptions("cus_u")).thenAnswer(i->json.createObjectNode().put("has_more",false)
            .set("data",paid.get()?json.createArrayNode().add(sub):json.createArrayNode()));
        when(stripe.checkout(anyString(),anyString(),anyString(),anyString(),anyLong(),anyString()))
            .thenAnswer(i->session("open").put("expires_at",(Long)i.getArgument(4)));
        when(stripe.checkoutSession("cs_test_fixture")).thenAnswer(i->session(paid.get()?"complete":"open"));
        ObjectNode invoice=json.createObjectNode().put("id","in_u").put("customer","cus_u").put("livemode",false)
            .put("status","paid").put("billing_reason","subscription_create");
        invoice.set("parent",json.createObjectNode().set("subscription_details",json.createObjectNode().put("subscription","sub_u")));
        ObjectNode line=json.createObjectNode().put("quantity",1);
        line.set("pricing",json.createObjectNode().set("price_details",json.createObjectNode().put("price","price_"+plan)));
        line.set("parent",json.createObjectNode().set("subscription_item_details",json.createObjectNode().put("subscription","sub_u").put("proration",false)));
        line.set("period",json.createObjectNode().put("start",start).put("end",end));
        invoice.set("lines",json.createObjectNode().put("has_more",false).set("data",json.createArrayNode().add(line)));
        when(stripe.invoice("in_u")).thenReturn(invoice);

        assertThat(service.checkout("u",plan,"zh-CN")).containsEntry("url","https://checkout.stripe.com/fixture");
        assertThat(repo.customer("stripe","u")).containsEntry("customer_id","cus_u");
        assertThat(repo.subscription("u")).containsEntry("checkout_id","cs_test_fixture");
        paid.set(true);
        Map<String,Object> result=service.status("u","cs_test_fixture");
        assertThat(result).containsEntry("paymentConfirmed",true);
        assertThat(((Number)result.get("remaining")).intValue()).isEqualTo(allowance+10);
        send(service,settings,"evt_paid","invoice.paid",json.createObjectNode().put("id","in_u"));
        send(service,settings,"evt_paid","invoice.paid",json.createObjectNode().put("id","in_u"));
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_grants WHERE provider='stripe'" )).isEqualTo(1);
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_events WHERE event_id='evt_paid'" )).isEqualTo(1);
        service.reserve("u","request_1","job_1");service.settle("job_1","completed");
        assertThat(((Number)service.credits("u").get("remaining")).intValue()).isEqualTo(allowance);
        assertThat(db.query("SELECT g.payment_id FROM music_mv_billing_reservations r JOIN music_mv_billing_grants g ON g.grant_id=r.grant_id WHERE r.job_id='job_1'").firstRow()).containsEntry("payment_id","in_u");
        send(service,settings,"evt_refund","credit_note.created",json.createObjectNode().put("invoice","in_u"));
        send(service,settings,"evt_refund","credit_note.created",json.createObjectNode().put("invoice","in_u"));
        send(service,settings,"evt_paid_late","invoice.paid",json.createObjectNode().put("id","in_u"));
        assertThat(((Number)service.credits("u").get("remaining")).intValue()).isEqualTo(10);
        assertThat(repo.paymentGranted("u","in_u")).isFalse();
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_grants WHERE provider='stripe'" )).isEqualTo(1);
        assertThat(service.history("u",0).get("items")).asList().hasSize(3);
        service.reserve("u","request_2","job_2");service.settle("job_2","failed");
        assertThat(((Number)service.credits("u").get("remaining")).intValue()).isEqualTo(10);
        assertThat(service.credits("other").get("allowance")).isEqualTo(0);
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_grants WHERE user_id='u' AND provider='internal'" )).isEqualTo(1);
        verify(stripe).checkout(eq("cus_u"),eq("u"),eq("price_"+plan),anyString(),anyLong(),eq(settings.returnUrl("zh-CN")));
    }

    @ParameterizedTest(name="K01 混合账本筛选={0}")
    @ValueSource(strings={"all","welcome","subscription","reserved","consumed","released","revoked"})
    void historyFiltersAndPagesMixedOwnersUsingActualServiceAndSql(String filter) {
        SqliteClient db=new SqliteClient(directory.resolve("history.sqlite"));
        BillingSettings settings=new BillingSettings();settings.enabled=true;
        StripeGateway stripe=mock(StripeGateway.class);
        MusicBillingService service=new MusicBillingService(settings,new BillingRepository(db),stripe,json);
        List<D1Statement> seeds=new ArrayList<>();
        Map<String,String> kinds=new HashMap<>();Map<String,Integer> amounts=new HashMap<>();
        for(String owner:Arrays.asList("a","b")) {
            for(String kind:Arrays.asList("welcome","subscription","revoked")) {
                for(int i=0;i<25;i++) {
                    String id=owner+"_"+kind+"_"+String.format("%02d",i);
                    seeds.add(D1Statement.of("INSERT INTO music_mv_billing_grants(grant_id,user_id,provider,payment_id,subscription_id,plan_key,allowance,period_start,period_end,revoked,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                        id,owner,kind.equals("welcome")?"internal":"stripe",id,"sub_"+owner,kind.equals("welcome")?"welcome":"starter",10,100,200,kind.equals("revoked")?1:0,"2026-10-08 01:00:00"));
                    if(owner.equals("a")){kinds.put("grant:"+id,kind);amounts.put("grant:"+id,kind.equals("revoked")?0:10);}
                }
            }
            for(String kind:Arrays.asList("reserved","consumed","released")) {
                for(int i=0;i<25;i++) {
                    String id=owner+"_"+kind+"_"+String.format("%02d",i);
                    seeds.add(D1Statement.of("INSERT INTO music_mv_billing_reservations(job_id,user_id,request_id,grant_id,state,credits,updated_at) VALUES(?,?,?,?,?,?,?)",
                        id,owner,id,owner+"_subscription_00",kind,10,"2026-10-08 01:00:00"));
                    if(owner.equals("a")){kinds.put("job:"+id,kind);amounts.put("job:"+id,kind.equals("released")?0:-10);}
                }
            }
        }
        db.batch(seeds);
        List<String> expected=new ArrayList<>();
        for(String id:kinds.keySet())if(filter.equals("all")||filter.equals(kinds.get(id)))expected.add(id);
        expected.sort(Collections.reverseOrder());
        List<String> actual=new ArrayList<>();
        for(int offset=0;offset<expected.size();offset+=20) {
            Map<String,Object> page=service.history("a",offset,filter);
            @SuppressWarnings("unchecked") List<Map<String,Object>> items=(List<Map<String,Object>>)page.get("items");
            assertThat(items).hasSize(Math.min(20,expected.size()-offset));
            assertThat(page.get("hasMore")).isEqualTo(offset+20<expected.size());
            for(Map<String,Object> item:items){
                String id=(String)item.get("id");actual.add(id);
                assertThat(item.get("kind")).isEqualTo(kinds.get(id));
                assertThat(((Number)item.get("amount")).intValue()).isEqualTo(amounts.get(id));
            }
        }
        assertThat(actual).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
        assertThat(service.history("a",expected.size(),filter)).containsEntry("hasMore",false);
        assertThat(service.history("a",expected.size(),filter).get("items")).asList().isEmpty();
        @SuppressWarnings("unchecked") List<Map<String,Object>> repeat=(List<Map<String,Object>>)service.history("a",0,filter).get("items");
        assertThat(repeat).extracting(item->item.get("id")).containsExactlyElementsOf(expected.subList(0,20));
        verifyNoInteractions(stripe);
    }

    private int count(SqliteClient db,String sql){return ((Number)db.query(sql).firstRow().get("n")).intValue();}
    private ObjectNode session(String status){return json.createObjectNode().put("id","cs_test_fixture").put("status",status)
        .put("client_reference_id","u").put("customer","cus_u").put("livemode",false).put("mode","subscription")
        .put("payment_status","paid").put("invoice","in_u").put("subscription","sub_u").put("url","https://checkout.stripe.com/fixture");}
    private void send(MusicBillingService service,BillingSettings settings,String id,String type,ObjectNode object) throws Exception {
        String payload=json.writeValueAsString(json.createObjectNode().put("id",id).put("object","event").put("livemode",false)
            .put("type",type).set("data",json.createObjectNode().set("object",object)));
        long timestamp=Instant.now().getEpochSecond();Mac mac=Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(settings.webhookSecret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        StringBuilder hex=new StringBuilder();for(byte b:mac.doFinal((timestamp+"."+payload).getBytes(StandardCharsets.UTF_8)))hex.append(String.format("%02x",b));
        service.webhook(payload,"t="+timestamp+",v1="+hex);
    }

    class SqliteClient extends D1DatabaseClient {
        final Path file;
        SqliteClient(Path file){super(json);this.file=file;execute(Collections.emptyList());}
        @Override public D1QueryResult query(String sql,Object...params){return execute(Collections.singletonList(map("sql",sql,"params",Arrays.asList(params)))).get(0);}
        @Override public List<D1QueryResult> batch(List<D1Statement> statements){
            List<Map<String,Object>> list=new ArrayList<>();for(D1Statement s:statements)list.add(map("sql",s.getSql(),"params",s.getParams()));return execute(list);
        }
        private List<D1QueryResult> execute(List<Map<String,Object>> statements){
            String script=String.join("\n","import sqlite3,json,sys,pathlib",
                "d=sqlite3.connect(sys.argv[1]);d.row_factory=sqlite3.Row;d.execute('PRAGMA foreign_keys=ON')",
                "d.executescript(pathlib.Path('src/main/resources/db/music-mv-billing-schema.sql').read_text())",
                "d.execute('CREATE TABLE IF NOT EXISTS ai_music_jobs(job_id TEXT,user_id TEXT,status TEXT)')",
                "result=[]",
                "with d:"," for s in json.loads(sys.argv[2]): result.append([dict(r) for r in d.execute(s['sql'],s['params']).fetchall()])",
                "print(json.dumps(result))");
            try {
                Process p=new ProcessBuilder("python3","-c",script,file.toString(),json.writeValueAsString(statements)).redirectErrorStream(true).start();
                if(!p.waitFor(15,TimeUnit.SECONDS)){p.destroyForcibly();throw new AssertionError("账本联合验证超时");}
                String output=new String(org.springframework.util.StreamUtils.copyToByteArray(p.getInputStream()),StandardCharsets.UTF_8);
                assertThat(p.exitValue()).withFailMessage(output).isZero();
                List<List<Map<String,Object>>> rows=json.readValue(output,new TypeReference<List<List<Map<String,Object>>>>(){});
                List<D1QueryResult> result=new ArrayList<>();for(List<Map<String,Object>> row:rows)result.add(new D1QueryResult(row,null));return result;
            }catch(Exception e){throw new AssertionError("账本联合验证失败",e);}
        }
    }
}
