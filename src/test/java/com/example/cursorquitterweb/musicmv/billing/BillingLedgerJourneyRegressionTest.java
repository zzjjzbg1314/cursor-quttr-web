package com.example.cursorquitterweb.musicmv.billing;

import com.example.cursorquitterweb.musicmv.service.D1DatabaseClient;
import com.example.cursorquitterweb.musicmv.aimusic.*;
import com.example.cursorquitterweb.musicmv.repository.AiMusicJobRepository;
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

    @ParameterizedTest(name="I10/I11 终态={0} 结算中断={1}")
    @CsvSource({"completed,false","failed,false","completed,true","failed,true"})
    void terminalCallbackAndInterruptedSettlementKeepJobsAndLedgerConsistent(String terminal,boolean interrupt) {
        AtomicBoolean failNextSettlement=new AtomicBoolean(false);
        SqliteClient db=new SqliteClient(directory.resolve("terminal.sqlite")) {
            @Override public D1QueryResult query(String sql,Object...params) {
                if(sql.startsWith("UPDATE music_mv_billing_reservations SET state=?")&&failNextSettlement.compareAndSet(true,false))
                    throw new IllegalStateException("测试注入一次结算写入中断");
                return super.query(sql,params);
            }
        };
        BillingSettings settings=new BillingSettings();settings.enabled=true;
        BillingRepository ledger=new BillingRepository(db);
        MusicBillingService billing=new MusicBillingService(settings,ledger,mock(StripeGateway.class),json);
        ledger.ensureWelcomeCredits("u");ledger.ensureWelcomeCredits("other");
        ledger.reserve("u","r","j",150,10);ledger.reserve("other","r_other","j_other",150,10);
        for(String owner:Arrays.asList("u","other")) {
            String job=owner.equals("u")?"j":"j_other";
            db.query("INSERT INTO music_mv_users(user_id,display_name,handle,last_login_at,created_at,updated_at) VALUES(?,?,?,'2026-10-08','2026-10-08','2026-10-08')",owner,owner,owner);
            db.query("INSERT INTO ai_music_jobs(job_id,user_id,client_id,request_id,status,stage,primary_provider_code,active_attempt_id,request_fingerprint,request_json,created_at,updated_at) VALUES(?,?,?,?,?,'queued','sunoapi',?,'fp','{}','2026-10-08','2026-10-08')",
                job,owner,owner,"req_"+owner,owner.equals("u")?"queued":"failed","attempt_"+owner);
            db.query("INSERT INTO ai_music_provider_attempts(attempt_id,job_id,provider_code,provider_task_id,status,attempt_number,request_json,created_at,updated_at) VALUES(?,?,'sunoapi',?,'queued',1,'{}','2026-10-08','2026-10-08')","attempt_"+owner,job,"task_"+owner);
        }
        AiMusicJobRepository jobs=new AiMusicJobRepository(db);
        AiMusicProviderRegistry providers=mock(AiMusicProviderRegistry.class);
        AiMusicCandidateStorageService storage=mock(AiMusicCandidateStorageService.class);
        AiMusicGenerationService music=new AiMusicGenerationService(jobs,providers,storage,json,"sunoapi","https://example.test");
        org.springframework.test.util.ReflectionTestUtils.setField(music,"billing",billing);
        AiMusicProvider.TaskSnapshot first=new AiMusicProvider.TaskSnapshot();first.setProviderTaskId("task_u");first.setStatus(terminal);
        try {
            failNextSettlement.set(interrupt);
            if(interrupt) {
                assertThatThrownBy(()->music.acceptProviderCallback("sunoapi","j",first)).isInstanceOf(IllegalStateException.class);
                assertThat(db.query("SELECT state FROM music_mv_billing_reservations WHERE job_id='j'").firstRow()).containsEntry("state","reserved");
            } else music.acceptProviderCallback("sunoapi","j",first);
            assertThat(jobs.byId("j")).containsEntry("status",terminal);
            String settled=terminal.equals("completed")?"consumed":"released";
            Map<String,Object> balance=billing.credits("u");
            assertThat(((Number)balance.get("remaining")).intValue()).isEqualTo(terminal.equals("completed")?0:10);
            assertThat(db.query("SELECT state FROM music_mv_billing_reservations WHERE job_id='j'").firstRow()).containsEntry("state",settled);
            assertThat(db.query("SELECT state FROM music_mv_billing_reservations WHERE job_id='j_other'").firstRow()).containsEntry("state","reserved");
            AiMusicProvider.TaskSnapshot late=new AiMusicProvider.TaskSnapshot();late.setProviderTaskId("task_u");late.setStatus(terminal.equals("completed")?"failed":"completed");
            music.acceptProviderCallback("sunoapi","j",late);
            music.acceptProviderCallback("sunoapi","j",first);
            jobs.applySnapshot("j","attempt_u",late.getStatus(),"{}",null,null,false);
            assertThat(jobs.byId("j")).containsEntry("status",terminal);
            assertThat(db.query("SELECT status FROM ai_music_provider_attempts WHERE attempt_id='attempt_u'").firstRow()).containsEntry("status",terminal);
            assertThat(db.query("SELECT state FROM music_mv_billing_reservations WHERE job_id='j'").firstRow()).containsEntry("state",settled);
            assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_reservations WHERE user_id='u'")).isEqualTo(1);
            assertThat(((Number)billing.credits("u").get("remaining")).intValue()).isEqualTo(terminal.equals("completed")?0:10);
            verifyNoInteractions(providers,storage);
        } finally { music.close(); }
    }

    @ParameterizedTest(name="G05/E11 三个账期套餐={0}")
    @CsvSource({"starter,300","creator,1000"})
    void renewalInvoicesAcrossLeapFebruaryNeverCarryOverOldAllowance(String plan,int allowance) throws Exception {
        SqliteClient db=new SqliteClient(directory.resolve("renewal.sqlite"));
        BillingRepository ledger=new BillingRepository(db);StripeGateway stripe=mock(StripeGateway.class);
        BillingSettings settings=new BillingSettings();settings.enabled=true;settings.secretKey="sk_test_fixture";
        settings.webhookSecret="whsec_fixture";settings.starterPrice="price_starter";settings.creatorPrice="price_creator";
        settings.portalConfiguration="bpc_fixture";settings.siteUrl="https://www.example.test";
        MusicBillingService service=new MusicBillingService(settings,ledger,stripe,json);
        ledger.saveCustomer("stripe","u","cus_u");ledger.ensureWelcomeCredits("u");
        long[] boundaries=new long[4];
        for(int i=0;i<4;i++)boundaries[i]=java.time.LocalDate.of(2028,i+1,1).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond();
        assertThat((boundaries[2]-boundaries[1])/86400).isEqualTo(29);
        ObjectNode subscription=json.createObjectNode().put("id","sub_u").put("status","active").put("cancel_at_period_end",false);
        subscription.set("items",json.createObjectNode().set("data",json.createArrayNode().add(
            json.createObjectNode().put("current_period_end",boundaries[3]).set("price",json.createObjectNode().put("id","price_"+plan)))));
        when(stripe.subscriptions("cus_u")).thenReturn(json.createObjectNode().put("has_more",false).set("data",json.createArrayNode().add(subscription)));
        for(int i=0;i<3;i++) {
            String invoiceId="in_month_"+i;
            ObjectNode invoice=json.createObjectNode().put("id",invoiceId).put("customer","cus_u").put("livemode",false)
                .put("status","paid").put("billing_reason",i==0?"subscription_create":"subscription_cycle");
            invoice.set("parent",json.createObjectNode().set("subscription_details",json.createObjectNode().put("subscription","sub_u")));
            ObjectNode line=json.createObjectNode().put("quantity",1);
            line.set("pricing",json.createObjectNode().set("price_details",json.createObjectNode().put("price","price_"+plan)));
            line.set("parent",json.createObjectNode().set("subscription_item_details",json.createObjectNode().put("subscription","sub_u").put("proration",false)));
            line.set("period",json.createObjectNode().put("start",boundaries[i]).put("end",boundaries[i+1]));
            invoice.set("lines",json.createObjectNode().put("has_more",false).set("data",json.createArrayNode().add(line)));
            when(stripe.invoice(invoiceId)).thenReturn(invoice);
            send(service,settings,"evt_month_"+i,"invoice.paid",json.createObjectNode().put("id",invoiceId));
            send(service,settings,"evt_month_"+i,"invoice.paid",json.createObjectNode().put("id",invoiceId));
            send(service,settings,"evt_month_second_"+i,"invoice.paid",json.createObjectNode().put("id",invoiceId));
        }
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_grants WHERE provider='stripe'")).isEqualTo(3);
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_events")).isEqualTo(6);
        assertThat(((Number)ledger.balance("u",boundaries[0]-1).get("remaining")).intValue()).isEqualTo(10);
        for(int i=0;i<3;i++) {
            assertThat(((Number)ledger.balance("u",boundaries[i]).get("remaining")).intValue()).isEqualTo(allowance+10);
            assertThat(((Number)ledger.balance("u",boundaries[i+1]-1).get("remaining")).intValue()).isEqualTo(allowance+10);
        }
        assertThat(ledger.reserve("u","held","held_job",boundaries[1]-1,10)).isTrue();
        assertThat(db.query("SELECT g.payment_id FROM music_mv_billing_reservations r JOIN music_mv_billing_grants g ON r.grant_id=g.grant_id WHERE r.job_id='held_job'").firstRow()).containsEntry("payment_id","in_month_0");
        assertThat(((Number)ledger.balance("u",boundaries[1]).get("remaining")).intValue()).isEqualTo(allowance+10);
        ledger.settle("held_job","released");ledger.settle("held_job","released");
        assertThat(((Number)ledger.balance("u",boundaries[1]).get("remaining")).intValue()).isEqualTo(allowance+10);
        assertThat(ledger.reserve("u","new","new_job",boundaries[1],10)).isTrue();ledger.settle("new_job","consumed");
        assertThat(db.query("SELECT g.payment_id FROM music_mv_billing_reservations r JOIN music_mv_billing_grants g ON r.grant_id=g.grant_id WHERE r.job_id='new_job'").firstRow()).containsEntry("payment_id","in_month_1");
        assertThat(((Number)ledger.balance("u",boundaries[1]).get("remaining")).intValue()).isEqualTo(allowance);
        assertThat(((Number)ledger.balance("u",boundaries[2]).get("remaining")).intValue()).isEqualTo(allowance+10);
        assertThat(((Number)ledger.balance("u",boundaries[3]).get("remaining")).intValue()).isEqualTo(10);
        send(service,settings,"evt_old_late","invoice.paid",json.createObjectNode().put("id","in_month_0"));
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_grants WHERE provider='stripe'")).isEqualTo(3);
        assertThat(((Number)ledger.balance("u",boundaries[3]).get("remaining")).intValue()).isEqualTo(10);
    }

    @ParameterizedTest(name="C07/C24 套餐={0} 保存失败位置={1}")
    @CsvSource({"starter,customer","creator,customer","starter,checkout","creator,checkout"})
    void sdkIdempotencyAndPersistedSlotRecoverAfterLocalWriteFailure(String plan,String failure) throws Exception {
        AtomicBoolean failOnce=new AtomicBoolean(true);
        Path file=directory.resolve("checkout-recovery.sqlite");
        SqliteClient db=new SqliteClient(file) {
            @Override public D1QueryResult query(String sql,Object...params) {
                boolean target=failure.equals("customer")?sql.startsWith("INSERT INTO music_mv_billing_customers"):
                    sql.startsWith("UPDATE music_mv_billing_subscriptions SET checkout_id=?");
                if(target&&failOnce.compareAndSet(true,false))throw new IllegalStateException("测试注入一次账务保存失败");
                return super.query(sql,params);
            }
        };
        BillingSettings settings=new BillingSettings();settings.enabled=true;settings.secretKey="sk_test_fixture";
        settings.webhookSecret="whsec_fixture";settings.starterPrice="price_starter";settings.creatorPrice="price_creator";
        settings.portalConfiguration="bpc_fixture";settings.siteUrl="https://www.example.test";
        java.util.concurrent.atomic.AtomicInteger customerPosts=new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger checkoutPosts=new java.util.concurrent.atomic.AtomicInteger();
        Map<String,String> providerBodies=new HashMap<>();Map<String,String> providerObjects=new HashMap<>();
        com.sun.net.httpserver.HttpServer server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/",exchange->{
            String path=exchange.getRequestURI().getPath();String response;int status=200;
            if(path.equals("/v1/subscriptions")) response="{\"object\":\"list\",\"data\":[],\"has_more\":false}";
            else if(exchange.getRequestMethod().equals("GET"))response=providerObjects.get("checkout");
            else {
                String key=exchange.getRequestHeaders().getFirst("Idempotency-Key");
                String body=java.net.URLDecoder.decode(new String(org.springframework.util.StreamUtils.copyToByteArray(exchange.getRequestBody()),StandardCharsets.UTF_8),"UTF-8");
                boolean customer=path.equals("/v1/customers");
                if(customer)customerPosts.incrementAndGet();else checkoutPosts.incrementAndGet();
                String old=providerBodies.putIfAbsent(key,body);
                if(old!=null&&!old.equals(body)){status=409;response="{\"error\":{\"type\":\"idempotency_error\",\"message\":\"参数变化\"}}";}
                else if(customer){response="{\"object\":\"customer\",\"id\":\"cus_u\"}";providerObjects.put(key,response);}
                else {
                    response="{\"object\":\"checkout.session\",\"id\":\"cs_test_fixture\",\"url\":\"https://checkout.stripe.com/fixture\",\"status\":\"open\",\"client_reference_id\":\"u\",\"customer\":\"cus_u\",\"livemode\":false,\"mode\":\"subscription\"}";
                    providerObjects.put(key,response);providerObjects.put("checkout",response);
                }
            }
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();String original=com.stripe.Stripe.getApiBase();
        try {
            com.stripe.Stripe.overrideApiBase("http://127.0.0.1:"+server.getAddress().getPort());
            MusicBillingService first=new MusicBillingService(settings,new BillingRepository(db),new StripeGateway(settings,json),json);
            assertThatThrownBy(()->first.checkout("u",plan,"zh-CN")).isInstanceOf(IllegalStateException.class);
            if(failure.equals("checkout")){
                Map<String,Object> slot=new BillingRepository(db).subscription("u");
                assertThat(slot).containsEntry("checkout_id",null);assertThat(slot.get("checkout_token")).isNotNull();
            } else assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_customers")).isZero();
            Path recoveryLog=directory.resolve("independent-jvm.log");
            Process recovery=new ProcessBuilder(System.getProperty("java.home")+"/bin/java","-Xmx128m","-cp",
                System.getProperty("java.class.path"),CheckoutRecoveryWorker.class.getName(),file.toString(),plan,
                "http://127.0.0.1:"+server.getAddress().getPort()).redirectErrorStream(true).redirectOutput(recoveryLog.toFile()).start();
            boolean ended=recovery.waitFor(30,TimeUnit.SECONDS);if(!ended)recovery.destroyForcibly();
            assertThat(ended).withFailMessage("独立JVM账务恢复超过30秒").isTrue();
            String recoveryOutput=new String(java.nio.file.Files.readAllBytes(recoveryLog),StandardCharsets.UTF_8);
            assertThat(recovery.exitValue()).withFailMessage(recoveryOutput).isZero();
            assertThat(recoveryOutput).contains("RECOVERY_RESULT:{\"url\":\"https://checkout.stripe.com/fixture\"}");
            SqliteClient restartedDb=new SqliteClient(file);BillingRepository restartedRepo=new BillingRepository(restartedDb);
            MusicBillingService restarted=new MusicBillingService(settings,restartedRepo,new StripeGateway(settings,json),json);
            assertThat(restarted.checkout("u",plan,"zh-CN")).containsEntry("url","https://checkout.stripe.com/fixture");
            assertThat(count(restartedDb,"SELECT COUNT(*) AS n FROM music_mv_billing_customers")).isEqualTo(1);
            assertThat(restartedRepo.customer("stripe","u")).containsEntry("customer_id","cus_u");
            assertThat(restartedRepo.customer("stripe","other")).isNull();
            assertThatThrownBy(()->restartedRepo.saveCustomer("stripe","other","cus_u")).isInstanceOf(AssertionError.class);
            assertThat(restartedRepo.owner("stripe","cus_u")).isEqualTo("u");
            assertThat(restartedRepo.customer("stripe","other")).isNull();
            Map<String,Object> slot=restartedRepo.subscription("u");assertThat(slot).containsEntry("checkout_id","cs_test_fixture").containsEntry("checkout_plan",plan);
            assertThat(providerBodies).hasSize(2);
            assertThat(providerBodies.get("sunofilm-customer-u")).contains("metadata[sunofilm_user_id]=u");
            String checkoutBody=providerBodies.get("sunofilm-checkout-"+slot.get("checkout_token"));
            assertThat(checkoutBody).contains("mode=subscription","line_items[0][quantity]=1","line_items[0][price]=price_"+plan,"client_reference_id=u","/zh-cn/pricing?billing=return");
            assertThat(customerPosts.get()).isEqualTo(failure.equals("customer")?2:1);
            assertThat(checkoutPosts.get()).isEqualTo(failure.equals("checkout")?2:1);
        } finally {com.stripe.Stripe.overrideApiBase(original);server.stop(0);}
    }

    @ParameterizedTest(name="R18 旧查询晚返回={0}")
    @ValueSource(strings={"empty","canceled-flag","old-subscription"})
    void R18_RISK_lateSubscriptionQueryOverwritesNewerSnapshot(String staleKind) throws Exception {
        SqliteClient db=new SqliteClient(directory.resolve("stale-snapshot.sqlite"));
        BillingRepository ledger=new BillingRepository(db);StripeGateway stripe=mock(StripeGateway.class);
        BillingSettings settings=new BillingSettings();settings.enabled=true;settings.secretKey="sk_test_fixture";
        settings.webhookSecret="whsec_fixture";settings.starterPrice="price_starter";settings.creatorPrice="price_creator";
        settings.portalConfiguration="bpc_fixture";settings.siteUrl="https://www.example.test";
        ledger.saveCustomer("stripe","u","cus_u");
        ObjectNode fresh=json.createObjectNode().put("has_more",false);
        ObjectNode current=json.createObjectNode().put("id","sub_new").put("status","active").put("cancel_at_period_end",false);
        current.set("items",json.createObjectNode().set("data",json.createArrayNode().add(
            json.createObjectNode().put("current_period_end",2000000000L).set("price",json.createObjectNode().put("id","price_starter")))));
        fresh.set("data",json.createArrayNode().add(current));
        ObjectNode stale=fresh.deepCopy();
        if(staleKind.equals("empty"))stale.set("data",json.createArrayNode());
        else if(staleKind.equals("canceled-flag"))((ObjectNode)stale.path("data").get(0)).put("cancel_at_period_end",true);
        else ((ObjectNode)stale.path("data").get(0)).put("id","sub_old");
        java.util.concurrent.CountDownLatch captured=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger reads=new java.util.concurrent.atomic.AtomicInteger();
        when(stripe.subscriptions("cus_u")).thenAnswer(invocation->{
            if(reads.getAndIncrement()==0){captured.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("测试旧查询等待超时");return stale;}
            return fresh;
        });
        MusicBillingService slow=new MusicBillingService(settings,ledger,stripe,json);
        MusicBillingService fast=new MusicBillingService(settings,new BillingRepository(db),stripe,json);
        java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
        java.util.concurrent.Future<Map<String,Object>> pending=worker.submit(()->slow.status("u"));
        try {
            assertThat(captured.await(5,TimeUnit.SECONDS)).isTrue();
            fast.status("u");
            assertThat(ledger.subscription("u")).containsEntry("subscription_id","sub_new").containsEntry("cancel_at_period_end",0);
            release.countDown();pending.get(5,TimeUnit.SECONDS);
            Map<String,Object> finalSnapshot=ledger.subscription("u");
            if(staleKind.equals("empty"))assertThat(finalSnapshot).containsEntry("subscription_id",null).containsEntry("status",null);
            else if(staleKind.equals("canceled-flag"))assertThat(finalSnapshot).containsEntry("subscription_id","sub_new").containsEntry("cancel_at_period_end",1);
            else assertThat(finalSnapshot).containsEntry("subscription_id","sub_old");
            assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_grants WHERE provider='stripe'")).isZero();
        } finally {release.countDown();worker.shutdownNow();assertThat(worker.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
    }

    @ParameterizedTest(name="I06 生成预提交持久化失败={0}")
    @ValueSource(strings={"job","attempt","attempt-pointer","event"})
    void preSubmissionPersistenceFailureKeepsHoldWithoutSubmittingAgain(String point) throws Exception {
        AtomicBoolean failOnce=new AtomicBoolean(true);
        SqliteClient db=new SqliteClient(directory.resolve("pre-submit.sqlite")) {
            @Override public D1QueryResult query(String sql,Object...params) {
                boolean target=point.equals("job")?sql.startsWith("INSERT INTO ai_music_jobs "):
                    point.equals("attempt")?sql.startsWith("INSERT INTO ai_music_provider_attempts "):
                    point.equals("attempt-pointer")?sql.startsWith("UPDATE ai_music_jobs SET active_attempt_id=?"):
                    sql.startsWith("INSERT INTO ai_music_job_events ");
                if(target&&failOnce.compareAndSet(true,false))throw new IllegalStateException("测试注入生成持久化失败："+point);
                return super.query(sql,params);
            }
        };
        db.query("INSERT INTO music_mv_users(user_id,display_name,handle,last_login_at,created_at,updated_at) VALUES('u','u','u','2026-10-08','2026-10-08','2026-10-08')");
        BillingSettings settings=new BillingSettings();settings.enabled=true;
        BillingRepository ledger=new BillingRepository(db);
        MusicBillingService billing=new MusicBillingService(settings,ledger,mock(StripeGateway.class),json);
        AiMusicProvider provider=mock(AiMusicProvider.class);when(provider.providerCode()).thenReturn("sunoapi");
        when(provider.defaultModel()).thenReturn("V4_5");
        AiMusicGenerationService music=new AiMusicGenerationService(new AiMusicJobRepository(db),new AiMusicProviderRegistry(Collections.singletonList(provider)),
            mock(AiMusicCandidateStorageService.class),json,"sunoapi","https://example.test");
        org.springframework.test.util.ReflectionTestUtils.setField(music,"billing",billing);
        com.example.cursorquitterweb.musicmv.dto.AiMusicSongCreateRequest request=new com.example.cursorquitterweb.musicmv.dto.AiMusicSongCreateRequest();
        request.setRequestId("req");request.setStory("A birthday song");
        try {
            assertThatThrownBy(()->music.create("u",request,"https://example.test")).isInstanceOf(IllegalStateException.class);
            Map<String,Object> reservation=db.query("SELECT * FROM music_mv_billing_reservations WHERE user_id='u' AND request_id='req'").firstRow();
            assertThat(reservation).containsEntry("state","reserved").containsEntry("credits",10);assertThat(reservation.get("job_id")).isNotNull();
            assertThat(count(db,"SELECT COUNT(*) AS n FROM ai_music_jobs")).isEqualTo(point.equals("job")?0:1);
            assertThat(count(db,"SELECT COUNT(*) AS n FROM ai_music_provider_attempts")).isEqualTo(point.equals("job")||point.equals("attempt")?0:1);
            assertThat(count(db,"SELECT COUNT(*) AS n FROM ai_music_job_events")).isZero();
            assertThat(((Number)billing.credits("u").get("remaining")).intValue()).isZero();
            if(point.equals("job"))assertThatThrownBy(()->music.create("u",request,"https://example.test")).isInstanceOf(com.example.cursorquitterweb.musicmv.support.ApiException.class);
            else assertThat(music.create("u",request,"https://example.test")).containsEntry("status","submitting");
            ledger.reconcile("u");ledger.reconcile();
            assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_reservations")).isEqualTo(1);
            assertThat(db.query("SELECT state FROM music_mv_billing_reservations WHERE user_id='u'").firstRow()).containsEntry("state","reserved");
            assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_reservations r LEFT JOIN ai_music_jobs j ON j.job_id=r.job_id WHERE j.job_id IS NULL")).isEqualTo(point.equals("job")?1:0);
            verify(provider,never()).submit(any());
        } finally {music.close();}
    }

    @ParameterizedTest(name="I08 提交结果不确定={0}")
    @ValueSource(strings={"timeout","missing-task","unexpected-exception","attempt-save","job-save","accepted-event"})
    void uncertainSubmissionAndAcceptedWriteFailuresKeepHoldAcrossServiceRecreation(String scenario) throws Exception {
        AtomicBoolean failOnce=new AtomicBoolean(true);
        SqliteClient db=new SqliteClient(directory.resolve("unknown-submit.sqlite")) {
            @Override public D1QueryResult query(String sql,Object...params) {
                boolean target=scenario.equals("attempt-save")?sql.startsWith("UPDATE ai_music_provider_attempts SET provider_task_id=?"):
                    scenario.equals("job-save")?sql.startsWith("UPDATE ai_music_jobs SET status='queued'"):
                    scenario.equals("accepted-event")&&sql.startsWith("INSERT INTO ai_music_job_events ")&&params.length>2&&"provider_submitted".equals(params[2]);
                if(target&&failOnce.compareAndSet(true,false))throw new IllegalStateException("测试注入受理后写入失败");
                return super.query(sql,params);
            }
        };
        db.query("INSERT INTO music_mv_users(user_id,display_name,handle,last_login_at,created_at,updated_at) VALUES('u','u','u','2026-10-08','2026-10-08','2026-10-08')");
        BillingSettings settings=new BillingSettings();settings.enabled=true;
        BillingRepository ledger=new BillingRepository(db);
        MusicBillingService billing=new MusicBillingService(settings,ledger,mock(StripeGateway.class),json);
        org.springframework.test.web.client.MockRestServiceServer http=null;
        AiMusicProvider provider;
        boolean realAdapter=scenario.equals("timeout")||scenario.equals("missing-task");
        if(realAdapter) {
            org.springframework.web.client.RestTemplate client=new org.springframework.web.client.RestTemplate();
            http=org.springframework.test.web.client.MockRestServiceServer.bindTo(client).build();
            org.springframework.test.web.client.ResponseActions response=http.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://sunoapi.test/api/v1/generate"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.method(org.springframework.http.HttpMethod.POST));
            if(scenario.equals("missing-task"))response.andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess("{\"code\":200,\"data\":{}}",org.springframework.http.MediaType.APPLICATION_JSON));
            else response.andRespond(request->{throw new java.net.SocketTimeoutException("测试提交超时");});
            provider=new SunoApiAiMusicProvider(json,"https://sunoapi.test","fixture-api-key","fixture-callback-secret-long-enough","V6");
            org.springframework.test.util.ReflectionTestUtils.setField(provider,"restTemplate",client);
        } else {
            provider=mock(AiMusicProvider.class);when(provider.providerCode()).thenReturn("sunoapi");when(provider.defaultModel()).thenReturn("V6");
            if(scenario.equals("unexpected-exception"))when(provider.submit(any())).thenThrow(new IllegalStateException("未知提交结果"));
            else when(provider.submit(any())).thenReturn(new AiMusicProvider.Submission("accepted-task",Collections.emptyMap()));
        }
        AiMusicProviderRegistry registry=new AiMusicProviderRegistry(Collections.singletonList(provider));
        AiMusicCandidateStorageService storage=mock(AiMusicCandidateStorageService.class);
        AiMusicGenerationService music=new AiMusicGenerationService(new AiMusicJobRepository(db),registry,storage,json,"sunoapi","https://example.test");
        org.springframework.test.util.ReflectionTestUtils.setField(music,"billing",billing);
        com.example.cursorquitterweb.musicmv.dto.AiMusicSongCreateRequest request=new com.example.cursorquitterweb.musicmv.dto.AiMusicSongCreateRequest();
        request.setRequestId("req");request.setStory("A birthday song");
        boolean unknown=realAdapter||scenario.equals("unexpected-exception");
        try {
            if(unknown)assertThat(music.create("u",request,"https://example.test")).containsEntry("status","submission_unknown");
            else assertThatThrownBy(()->music.create("u",request,"https://example.test")).isInstanceOf(IllegalStateException.class);
            assertThat(((Number)billing.credits("u").get("remaining")).intValue()).isZero();
            assertThat(db.query("SELECT state FROM music_mv_billing_reservations").firstRow()).containsEntry("state","reserved");
            AiMusicGenerationService recreated=new AiMusicGenerationService(new AiMusicJobRepository(db),registry,storage,json,"sunoapi","https://example.test");
            org.springframework.test.util.ReflectionTestUtils.setField(recreated,"billing",billing);
            try {assertThat(recreated.create("u",request,"https://example.test")).containsEntry("idempotentReplay",true);}
            finally {recreated.close();}
            ledger.reconcile();assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_reservations")).isEqualTo(1);
            assertThat(db.query("SELECT state FROM music_mv_billing_reservations").firstRow()).containsEntry("state","reserved");
            assertThat(count(db,"SELECT COUNT(*) AS n FROM ai_music_jobs")).isEqualTo(1);
            Map<String,Object> attempt=db.query("SELECT provider_task_id,submission_unknown FROM ai_music_provider_attempts").firstRow();
            if(unknown)assertThat(attempt).containsEntry("provider_task_id",null).containsEntry("submission_unknown",1);
            else if(scenario.equals("attempt-save"))assertThat(attempt).containsEntry("provider_task_id",null);
            else assertThat(attempt).containsEntry("provider_task_id","accepted-task");
            if(http!=null)http.verify();else verify(provider,times(1)).submit(any());
        } finally {music.close();}
    }

    @ParameterizedTest(name="I07/I09 生成终局={0}")
    @ValueSource(strings={"completed-two-songs","quota-rejected","rate-rejected"})
    void explicitRejectionAndTwoCandidateCompletionSettleOneReservation(String scenario) throws Exception {
        SqliteClient db=new SqliteClient(directory.resolve("settlement-journey.sqlite"));
        db.query("INSERT INTO music_mv_users(user_id,display_name,handle,last_login_at,created_at,updated_at) VALUES('u','u','u','2026-10-08','2026-10-08','2026-10-08')");
        BillingSettings settings=new BillingSettings();settings.enabled=true;
        BillingRepository ledger=new BillingRepository(db);
        MusicBillingService billing=new MusicBillingService(settings,ledger,mock(StripeGateway.class),json);
        org.springframework.web.client.RestTemplate client=new org.springframework.web.client.RestTemplate();
        org.springframework.test.web.client.MockRestServiceServer http=org.springframework.test.web.client.MockRestServiceServer.bindTo(client).build();
        boolean completed=scenario.equals("completed-two-songs");
        String response=completed?"{\"code\":200,\"data\":{\"taskId\":\"accepted-task\"}}":
            scenario.equals("quota-rejected")?"{\"code\":429,\"msg\":\"fixture quota\"}":"{\"code\":405,\"msg\":\"fixture rate\"}";
        http.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://sunoapi.test/api/v1/generate"))
            .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.method(org.springframework.http.HttpMethod.POST))
            .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(response,org.springframework.http.MediaType.APPLICATION_JSON));
        AiMusicProvider provider=new SunoApiAiMusicProvider(json,"https://sunoapi.test","fixture-key","fixture-callback-secret-long-enough","V6");
        org.springframework.test.util.ReflectionTestUtils.setField(provider,"restTemplate",client);
        AiMusicGenerationService music=new AiMusicGenerationService(new AiMusicJobRepository(db),new AiMusicProviderRegistry(Collections.singletonList(provider)),
            mock(AiMusicCandidateStorageService.class),json,"sunoapi","https://example.test");
        org.springframework.test.util.ReflectionTestUtils.setField(music,"billing",billing);
        com.example.cursorquitterweb.musicmv.dto.AiMusicSongCreateRequest request=new com.example.cursorquitterweb.musicmv.dto.AiMusicSongCreateRequest();
        request.setRequestId("req");request.setStory("A birthday song");
        try {
            if(completed) {
                music.create("u",request,"https://example.test");
                assertThat(((Number)billing.credits("u").get("remaining")).intValue()).isZero();
                AiMusicProvider.TaskSnapshot snapshot=new AiMusicProvider.TaskSnapshot();snapshot.setProviderTaskId("accepted-task");snapshot.setStatus("completed");
                AiMusicProvider.Candidate first=new AiMusicProvider.Candidate();first.setProviderAudioId("audio_1");first.setTitle("First song");
                AiMusicProvider.Candidate second=new AiMusicProvider.Candidate();second.setProviderAudioId("audio_2");second.setTitle("Second song");
                snapshot.setCandidates(Arrays.asList(first,second));
                String job=(String)db.query("SELECT job_id FROM ai_music_jobs").firstRow().get("job_id");
                music.acceptProviderCallback("sunoapi",job,snapshot);music.acceptProviderCallback("sunoapi",job,snapshot);
                assertThat(count(db,"SELECT COUNT(*) AS n FROM ai_music_candidates")).isEqualTo(2);
                assertThat(ledger.generationSpend("u")).hasSize(1);assertThat(ledger.generationSpend("other")).isEmpty();
                assertThat(ledger.generationSpend("u").get(0).get("title")).isIn("First song","Second song");
            } else {
                assertThatThrownBy(()->music.create("u",request,"https://example.test")).isInstanceOfSatisfying(com.example.cursorquitterweb.musicmv.support.ApiException.class,
                    e->assertThat(e.getCode()).isEqualTo(scenario.equals("quota-rejected")?"SUNOAPI_CREDITS_EXHAUSTED":"SUNOAPI_RATE_LIMITED"));
                String job=(String)db.query("SELECT job_id FROM ai_music_jobs").firstRow().get("job_id");
                billing.settle(job,"failed");billing.settle(job,"failed");
                assertThat(count(db,"SELECT COUNT(*) AS n FROM ai_music_candidates")).isZero();assertThat(ledger.generationSpend("u")).isEmpty();
            }
            assertThat(music.create("u",request,"https://example.test")).containsEntry("idempotentReplay",true).containsEntry("status",completed?"completed":"failed");
            assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_reservations")).isEqualTo(1);
            assertThat(db.query("SELECT state,credits FROM music_mv_billing_reservations").firstRow()).containsEntry("state",completed?"consumed":"released").containsEntry("credits",10);
            assertThat(((Number)billing.credits("u").get("remaining")).intValue()).isEqualTo(completed?0:10);
            assertThat(((Number)ledger.usage("u").get("spent")).intValue()).isEqualTo(completed?10:0);
            assertThat(((Number)ledger.usage("u").get("earned")).intValue()).isEqualTo(10);
            List<Map<String,Object>> history=ledger.history("u",21,0);assertThat(history).hasSize(2);
            assertThat(history).extracting(row->row.get("kind")).containsExactlyInAnyOrder("welcome",completed?"consumed":"released");
            Map<String,Object> reservationHistory=history.stream().filter(row->!"welcome".equals(row.get("kind"))).findFirst().get();
            assertThat(((Number)reservationHistory.get("amount")).intValue()).isEqualTo(completed?-10:0);
            http.verify();
        } finally {music.close();}
    }

    @org.junit.jupiter.api.Test
    void refundInvoicePaymentListRevokesOnlyListedPeriodsAndReplaysOnce() throws Exception {
        SqliteClient db=new SqliteClient(directory.resolve("multi-invoice-refund.sqlite"));
        BillingRepository ledger=new BillingRepository(db);StripeGateway stripe=mock(StripeGateway.class);
        BillingSettings settings=new BillingSettings();settings.enabled=true;settings.secretKey="sk_test_fixture";
        settings.webhookSecret="whsec_fixture";settings.starterPrice="price_starter";settings.creatorPrice="price_creator";
        settings.portalConfiguration="bpc_fixture";settings.siteUrl="https://www.example.test";
        MusicBillingService service=new MusicBillingService(settings,ledger,stripe,json);
        ledger.ensureWelcomeCredits("u");ledger.grant("stripe","in_1","u","sub_u","starter",300,100,200);
        ledger.grant("stripe","in_2","u","sub_u","creator",1000,200,300);
        ledger.grant("stripe","in_3","u","sub_u","starter",300,300,400);
        ledger.grant("stripe","in_other","other","sub_other","creator",1000,100,400);
        when(stripe.invoicePayments("pi_1")).thenReturn(json.createObjectNode().put("has_more",false).set("data",
            json.createArrayNode().add(json.createObjectNode().put("invoice","in_1")).add(json.createObjectNode().put("invoice","in_2"))));
        ObjectNode charge=json.createObjectNode().put("payment_intent","pi_1");
        send(service,settings,"evt_refund","charge.refunded",charge);
        send(service,settings,"evt_refund","charge.refunded",charge);
        send(service,settings,"evt_refund_second","charge.refunded",charge);
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_events")).isEqualTo(2);
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_revocations")).isEqualTo(2);
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_grants WHERE revoked=1")).isEqualTo(2);
        assertThat(((Number)ledger.balance("u",150).get("remaining")).intValue()).isEqualTo(10);
        assertThat(((Number)ledger.balance("u",250).get("remaining")).intValue()).isEqualTo(10);
        assertThat(((Number)ledger.balance("u",350).get("remaining")).intValue()).isEqualTo(310);
        assertThat(((Number)ledger.balance("other",150).get("remaining")).intValue()).isEqualTo(1000);
        assertThat(db.query("SELECT revoked FROM music_mv_billing_grants WHERE payment_id='in_3'").firstRow()).containsEntry("revoked",0);
        verify(stripe,times(2)).invoicePayments("pi_1");
    }

    @ParameterizedTest(name="I17 退款后任务终态={0}")
    @ValueSource(strings={"completed","failed"})
    void refundedReservedJobCallbackNeverRestoresMonthlyGrant(String terminal) throws Exception {
        SqliteClient db=new SqliteClient(directory.resolve("reserved-refund.sqlite"));
        BillingRepository ledger=new BillingRepository(db);StripeGateway stripe=mock(StripeGateway.class);
        BillingSettings settings=new BillingSettings();settings.enabled=true;settings.secretKey="sk_test_fixture";
        settings.webhookSecret="whsec_fixture";settings.starterPrice="price_starter";settings.creatorPrice="price_creator";settings.portalConfiguration="bpc_fixture";
        MusicBillingService billing=new MusicBillingService(settings,ledger,stripe,json);
        long now=Instant.now().getEpochSecond();
        ledger.ensureWelcomeCredits("u");ledger.grant("stripe","in_u","u","sub_u","starter",300,now-10,now+3600);
        billing.reserve("u","request_u","job_u");
        db.query("INSERT INTO music_mv_users(user_id,display_name,handle,last_login_at,created_at,updated_at) VALUES('u','u','u','2026-10-08','2026-10-08','2026-10-08')");
        db.query("INSERT INTO ai_music_jobs(job_id,user_id,client_id,request_id,status,stage,primary_provider_code,active_attempt_id,request_fingerprint,request_json,created_at,updated_at) VALUES('job_u','u','u','request_u','queued','queued','sunoapi','attempt_u','fp','{}','2026-10-08','2026-10-08')");
        db.query("INSERT INTO ai_music_provider_attempts(attempt_id,job_id,provider_code,provider_task_id,status,attempt_number,request_json,created_at,updated_at) VALUES('attempt_u','job_u','sunoapi','task_u','queued',1,'{}','2026-10-08','2026-10-08')");
        send(billing,settings,"evt_refund","credit_note.created",json.createObjectNode().put("invoice","in_u"));
        assertThat(db.query("SELECT state FROM music_mv_billing_reservations WHERE job_id='job_u'").firstRow()).containsEntry("state","reserved");
        assertThat(((Number)billing.credits("u").get("remaining")).intValue()).isEqualTo(10);
        AiMusicJobRepository jobs=new AiMusicJobRepository(db);
        AiMusicProviderRegistry providers=mock(AiMusicProviderRegistry.class);
        AiMusicCandidateStorageService storage=mock(AiMusicCandidateStorageService.class);
        AiMusicGenerationService music=new AiMusicGenerationService(jobs,providers,storage,json,"sunoapi","https://example.test");
        org.springframework.test.util.ReflectionTestUtils.setField(music,"billing",billing);
        try {
            AiMusicProvider.TaskSnapshot snapshot=new AiMusicProvider.TaskSnapshot();snapshot.setProviderTaskId("task_u");snapshot.setStatus(terminal);
            music.acceptProviderCallback("sunoapi","job_u",snapshot);
            music.acceptProviderCallback("sunoapi","job_u",snapshot);
            send(billing,settings,"evt_refund","credit_note.created",json.createObjectNode().put("invoice","in_u"));
            String settled=terminal.equals("completed")?"consumed":"released";
            assertThat(jobs.byId("job_u")).containsEntry("status",terminal);
            assertThat(db.query("SELECT state FROM music_mv_billing_reservations WHERE job_id='job_u'").firstRow()).containsEntry("state",settled);
            assertThat(db.query("SELECT revoked FROM music_mv_billing_grants WHERE payment_id='in_u'").firstRow()).containsEntry("revoked",1);
            assertThat(((Number)billing.credits("u").get("remaining")).intValue()).isEqualTo(10);
            assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_reservations")).isEqualTo(1);
            assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_revocations")).isEqualTo(1);
            assertThat(billing.history("u",0,settled).get("items")).asList().hasSize(1);
            verifyNoInteractions(providers,storage,stripe);
        } finally {music.close();}
    }

    @ParameterizedTest(name="J08 已消费全部额度套餐={0}")
    @CsvSource({"starter,300","creator,1000"})
    void fullySpentMonthlyGrantRefundPreservesConsumptionWithoutNegativeReturn(String plan,int allowance) throws Exception {
        SqliteClient db=new SqliteClient(directory.resolve("spent-refund.sqlite"));
        BillingRepository ledger=new BillingRepository(db);StripeGateway stripe=mock(StripeGateway.class);
        BillingSettings settings=new BillingSettings();settings.enabled=true;settings.secretKey="sk_test_fixture";settings.webhookSecret="whsec_fixture";settings.starterPrice="price_starter";settings.creatorPrice="price_creator";settings.portalConfiguration="bpc_fixture";
        MusicBillingService billing=new MusicBillingService(settings,ledger,stripe,json);
        long now=Instant.now().getEpochSecond();
        ledger.ensureWelcomeCredits("u");ledger.grant("stripe","in_u","u","sub_u",plan,allowance,now-10,now+3600);
        for(int i=0;i<allowance/10;i++){billing.reserve("u","request_"+i,"job_"+i);billing.settle("job_"+i,"completed");}
        assertThat(((Number)billing.credits("u").get("remaining")).intValue()).isEqualTo(10);
        send(billing,settings,"evt_refund","credit_note.created",json.createObjectNode().put("invoice","in_u"));
        send(billing,settings,"evt_refund","credit_note.created",json.createObjectNode().put("invoice","in_u"));
        assertThat(((Number)billing.credits("u").get("remaining")).intValue()).isEqualTo(10);
        assertThat(((Number)ledger.usage("u").get("spent")).intValue()).isEqualTo(allowance);
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_reservations WHERE state='consumed' AND credits=10")).isEqualTo(allowance/10);
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_reservations WHERE state='released'")).isZero();
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_revocations")).isEqualTo(1);
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_events")).isEqualTo(1);
        assertThat(billing.history("u",0,"revoked").get("items")).asList().hasSize(1);
        assertThat(ledger.paymentGranted("u","in_u")).isFalse();
        verifyNoInteractions(stripe);
    }

    @ParameterizedTest(name="F09 撤回事件={0} 中断点={1}")
    @CsvSource({"charge.refunded,batch_before","charge.refunded,batch_after","charge.refunded,processed_before","charge.dispute.created,batch_before","charge.dispute.created,batch_after","charge.dispute.created,processed_before"})
    void refundWriteFailureReplayCompletesRevocationWithoutRevivingAllowance(String type,String point) throws Exception {
        AtomicBoolean armed=new AtomicBoolean(false);
        SqliteClient db=new SqliteClient(directory.resolve("refund-retry.sqlite")) {
            @Override public List<D1QueryResult> batch(List<D1Statement> statements) {
                boolean revoke=statements.get(0).getSql().startsWith("INSERT INTO music_mv_billing_revocations");
                if(revoke&&point.startsWith("batch_")&&armed.compareAndSet(true,false)) {
                    if(point.equals("batch_after"))super.batch(statements);
                    throw new IllegalStateException("测试注入撤回批次响应中断");
                }
                return super.batch(statements);
            }
            @Override public D1QueryResult query(String sql,Object...params) {
                if(point.equals("processed_before")&&sql.startsWith("INSERT INTO music_mv_billing_events")&&armed.compareAndSet(true,false))
                    throw new IllegalStateException("测试注入通知完成记录写入中断");
                return super.query(sql,params);
            }
        };
        BillingRepository ledger=new BillingRepository(db);StripeGateway stripe=mock(StripeGateway.class);
        BillingSettings settings=new BillingSettings();settings.enabled=true;settings.secretKey="sk_test_fixture";settings.webhookSecret="whsec_fixture";
        settings.starterPrice="price_starter";settings.creatorPrice="price_creator";settings.portalConfiguration="bpc_fixture";
        MusicBillingService service=new MusicBillingService(settings,ledger,stripe,json);
        ledger.ensureWelcomeCredits("u");ledger.grant("stripe","in_1","u","sub_u","starter",300,100,200);
        ledger.grant("stripe","in_2","u","sub_u","creator",1000,200,300);
        ledger.grant("stripe","in_other","other","sub_other","starter",300,100,300);
        ObjectNode charge=json.createObjectNode().put("payment_intent","pi_u");
        when(stripe.charge("ch_u")).thenReturn(charge);
        when(stripe.invoicePayments("pi_u")).thenReturn(json.createObjectNode().put("has_more",false).set("data",
            json.createArrayNode().add(json.createObjectNode().put("invoice","in_1")).add(json.createObjectNode().put("invoice","in_2"))));
        ObjectNode object=type.equals("charge.refunded")?charge:json.createObjectNode().put("charge","ch_u");
        armed.set(true);
        assertThatThrownBy(()->send(service,settings,"evt_retry",type,object)).isInstanceOf(IllegalStateException.class);
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_events")).isZero();
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_revocations")).isEqualTo(point.equals("batch_before")?0:point.equals("batch_after")?1:2);
        send(service,settings,"evt_retry",type,object);send(service,settings,"evt_retry",type,object);
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_events")).isEqualTo(1);
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_revocations")).isEqualTo(2);
        ledger.grant("stripe","in_1","u","sub_u","starter",300,100,200);
        ledger.grant("stripe","in_2","u","sub_u","creator",1000,200,300);
        assertThat(((Number)ledger.balance("u",150).get("remaining")).intValue()).isEqualTo(10);
        assertThat(((Number)ledger.balance("u",250).get("remaining")).intValue()).isEqualTo(10);
        assertThat(((Number)ledger.balance("other",150).get("remaining")).intValue()).isEqualTo(300);
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_grants WHERE revoked=1")).isEqualTo(2);
        verify(stripe,times(2)).invoicePayments("pi_u");
    }

    @ParameterizedTest(name="J05 争议查询失败点={0}")
    @ValueSource(strings={"charge","invoicePayments"})
    void disputeQueryFailurePreservesLedgerAndRetryCompletes(String point) throws Exception {
        SqliteClient db=new SqliteClient(directory.resolve("dispute-query.sqlite"));
        BillingRepository ledger=new BillingRepository(db);StripeGateway stripe=mock(StripeGateway.class);
        BillingSettings settings=new BillingSettings();settings.enabled=true;settings.secretKey="sk_test_fixture";settings.webhookSecret="whsec_fixture";
        settings.starterPrice="price_starter";settings.creatorPrice="price_creator";settings.portalConfiguration="bpc_fixture";
        MusicBillingService service=new MusicBillingService(settings,ledger,stripe,json);
        ledger.grant("stripe","in_u","u","sub_u","starter",300,100,200);
        ObjectNode charge=json.createObjectNode().put("payment_intent","pi_u");
        ObjectNode list=json.createObjectNode().put("has_more",false).set("data",json.createArrayNode().add(json.createObjectNode().put("invoice","in_u")));
        if(point.equals("charge"))when(stripe.charge("ch_u")).thenThrow(new IllegalStateException("测试渠道查询失败")).thenReturn(charge);
        else when(stripe.charge("ch_u")).thenReturn(charge);
        if(point.equals("invoicePayments"))when(stripe.invoicePayments("pi_u")).thenThrow(new IllegalStateException("测试关联查询失败")).thenReturn(list);
        else when(stripe.invoicePayments("pi_u")).thenReturn(list);
        ObjectNode object=json.createObjectNode().put("charge","ch_u");
        assertThatThrownBy(()->send(service,settings,"evt_dispute","charge.dispute.created",object)).isInstanceOf(IllegalStateException.class);
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_events")).isZero();
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_revocations")).isZero();
        assertThat(((Number)ledger.balance("u",150).get("remaining")).intValue()).isEqualTo(300);
        send(service,settings,"evt_dispute","charge.dispute.created",object);
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_events")).isEqualTo(1);
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_revocations")).isEqualTo(1);
        assertThat(((Number)ledger.balance("u",150).get("remaining")).intValue()).isZero();
    }

    @ParameterizedTest(name="F08 到账套餐={0} 写入中断={2}")
    @CsvSource({"starter,300,grant_before","starter,300,grant_after","starter,300,processed_before","creator,1000,grant_before","creator,1000,grant_after","creator,1000,processed_before"})
    void paidInvoiceWriteFailureRecreatedServiceCompletesExactlyOneGrant(String plan,int allowance,String point) throws Exception {
        AtomicBoolean armed=new AtomicBoolean(false);
        SqliteClient db=new SqliteClient(directory.resolve("paid-retry.sqlite")) {
            @Override public D1QueryResult query(String sql,Object...params) {
                boolean grant=sql.startsWith("INSERT INTO music_mv_billing_grants")&&sql.contains("EXISTS(SELECT 1 FROM music_mv_billing_revocations");
                boolean processed=sql.startsWith("INSERT INTO music_mv_billing_events");
                if(((point.startsWith("grant_")&&grant)||(point.equals("processed_before")&&processed))&&armed.compareAndSet(true,false)) {
                    if(point.equals("grant_after"))super.query(sql,params);
                    throw new IllegalStateException("测试注入付款到账写入响应中断");
                }
                return super.query(sql,params);
            }
        };
        BillingRepository ledger=new BillingRepository(db);StripeGateway stripe=mock(StripeGateway.class);
        BillingSettings settings=new BillingSettings();settings.enabled=true;settings.secretKey="sk_test_fixture";settings.webhookSecret="whsec_fixture";
        settings.starterPrice="price_starter";settings.creatorPrice="price_creator";settings.portalConfiguration="bpc_fixture";
        ledger.saveCustomer("stripe","u","cus_u");ledger.ensureWelcomeCredits("u");
        ledger.grant("stripe","in_other","other","sub_other","starter",300,100,200);
        ObjectNode sub=json.createObjectNode().put("id","sub_u").put("status","active").put("cancel_at_period_end",false);
        sub.set("items",json.createObjectNode().set("data",json.createArrayNode().add(json.createObjectNode().put("current_period_end",200).set("price",json.createObjectNode().put("id","price_"+plan)))));
        when(stripe.subscriptions("cus_u")).thenReturn(json.createObjectNode().put("has_more",false).set("data",json.createArrayNode().add(sub)));
        ObjectNode invoice=json.createObjectNode().put("id","in_u").put("customer","cus_u").put("livemode",false).put("status","paid").put("billing_reason","subscription_create");
        invoice.set("parent",json.createObjectNode().set("subscription_details",json.createObjectNode().put("subscription","sub_u")));
        ObjectNode line=json.createObjectNode().put("quantity",1);
        line.set("pricing",json.createObjectNode().set("price_details",json.createObjectNode().put("price","price_"+plan)));
        line.set("parent",json.createObjectNode().set("subscription_item_details",json.createObjectNode().put("subscription","sub_u").put("proration",false)));
        line.set("period",json.createObjectNode().put("start",100).put("end",200));
        invoice.set("lines",json.createObjectNode().put("has_more",false).set("data",json.createArrayNode().add(line)));
        when(stripe.invoice("in_u")).thenReturn(invoice);
        MusicBillingService first=new MusicBillingService(settings,ledger,stripe,json);
        ObjectNode object=json.createObjectNode().put("id","in_u");armed.set(true);
        assertThatThrownBy(()->send(first,settings,"evt_paid_retry","invoice.paid",object)).isInstanceOf(IllegalStateException.class);
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_events")).isZero();
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_grants WHERE payment_id='in_u'")).isEqualTo(point.equals("grant_before")?0:1);
        MusicBillingService restarted=new MusicBillingService(settings,new BillingRepository(db),stripe,json);
        send(restarted,settings,"evt_paid_retry","invoice.paid",object);
        send(restarted,settings,"evt_paid_retry","invoice.paid",object);
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_grants WHERE payment_id='in_u'")).isEqualTo(1);
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_events")).isEqualTo(1);
        assertThat(((Number)ledger.balance("u",150).get("remaining")).intValue()).isEqualTo(allowance+10);
        assertThat(((Number)ledger.balance("other",150).get("remaining")).intValue()).isEqualTo(300);
        assertThat(ledger.subscription("u")).containsEntry("subscription_id","sub_u").containsEntry("status","active");
        verify(stripe,times(2)).invoice("in_u");
    }

    @ParameterizedTest(name="D03/D04 未确认回站={0}")
    @ValueSource(strings={"complete-unpaid","open","expired","payment-mode","missing-invoice"})
    void unconfirmedCheckoutReturnNeverConfusesWelcomeBalanceWithPayment(String scenario) {
        SqliteClient db=new SqliteClient(directory.resolve("unconfirmed-return.sqlite"));
        BillingRepository ledger=new BillingRepository(db);StripeGateway stripe=mock(StripeGateway.class);
        BillingSettings settings=new BillingSettings();settings.enabled=true;settings.secretKey="sk_test_fixture";settings.webhookSecret="whsec_fixture";
        settings.starterPrice="price_starter";settings.creatorPrice="price_creator";settings.portalConfiguration="bpc_fixture";
        ledger.saveCustomer("stripe","u","cus_u");
        when(stripe.subscriptions("cus_u")).thenReturn(json.createObjectNode().put("has_more",false).set("data",json.createArrayNode()));
        ObjectNode checkout=session("complete");
        if(scenario.equals("complete-unpaid"))checkout.put("payment_status","unpaid");
        if(scenario.equals("open")||scenario.equals("expired"))checkout.put("status",scenario);
        if(scenario.equals("payment-mode"))checkout.put("mode","payment");
        if(scenario.equals("missing-invoice"))checkout.remove("invoice");
        when(stripe.checkoutSession("cs_test_fixture")).thenReturn(checkout);
        MusicBillingService service=new MusicBillingService(settings,ledger,stripe,json);
        for(int i=0;i<2;i++) {
            assertThat(service.status("u","cs_test_fixture")).containsEntry("paymentConfirmed",false).containsEntry("remaining",10);
        }
        assertThat(service.credits("u")).containsEntry("remaining",10);
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_grants WHERE provider='stripe'")).isZero();
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_grants WHERE provider='internal'")).isEqualTo(1);
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_events")).isZero();
        verify(stripe,never()).invoice(anyString());
        verify(stripe,never()).checkout(anyString(),anyString(),anyString(),anyString(),anyLong(),anyString());
    }

    @ParameterizedTest(name="D10 显式回站发票状态={0}")
    @ValueSource(strings={"open","draft","void","uncollectible","missing-mode","wrong-mode"})
    void paidSessionCannotAuthorizeInvalidInvoiceStateInActualLedger(String scenario) {
        SqliteClient db=new SqliteClient(directory.resolve("invalid-return-invoice.sqlite"));
        BillingRepository ledger=new BillingRepository(db);StripeGateway stripe=mock(StripeGateway.class);
        BillingSettings settings=new BillingSettings();settings.enabled=true;settings.secretKey="sk_test_fixture";settings.webhookSecret="whsec_fixture";
        settings.starterPrice="price_starter";settings.creatorPrice="price_creator";settings.portalConfiguration="bpc_fixture";
        ledger.saveCustomer("stripe","u","cus_u");
        when(stripe.subscriptions("cus_u")).thenReturn(json.createObjectNode().put("has_more",false).set("data",json.createArrayNode()));
        when(stripe.checkoutSession("cs_test_fixture")).thenReturn(session("complete"));
        ObjectNode invoice=json.createObjectNode().put("id","in_u").put("customer","cus_u").put("status","paid").put("livemode",false);
        invoice.set("parent",json.createObjectNode().set("subscription_details",json.createObjectNode().put("subscription","sub_u")));
        if(scenario.equals("missing-mode"))invoice.remove("livemode");
        else if(scenario.equals("wrong-mode"))invoice.put("livemode",true);
        else invoice.put("status",scenario);
        when(stripe.invoice("in_u")).thenReturn(invoice);
        MusicBillingService service=new MusicBillingService(settings,ledger,stripe,json);
        assertThatThrownBy(()->service.status("u","cs_test_fixture")).isInstanceOf(IllegalStateException.class);
        assertThat(service.credits("u")).containsEntry("remaining",10);
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_grants WHERE provider='stripe'")).isZero();
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_events")).isZero();
        verify(stripe).invoice("in_u");
    }

    @ParameterizedTest(name="G07 续费未付状态={0}")
    @ValueSource(strings={"past_due","unpaid"})
    void failedRenewalAfterOldPeriodExpiresKeepsOnlyWelcomeAndManagement(String state) {
        SqliteClient db=new SqliteClient(directory.resolve("failed-renewal.sqlite"));
        BillingRepository ledger=new BillingRepository(db);StripeGateway stripe=mock(StripeGateway.class);
        BillingSettings settings=new BillingSettings();settings.enabled=true;settings.secretKey="sk_test_fixture";settings.webhookSecret="whsec_fixture";
        settings.starterPrice="price_starter";settings.creatorPrice="price_creator";settings.portalConfiguration="bpc_fixture";settings.siteUrl="https://example.test";
        long now=Instant.now().getEpochSecond();ledger.saveCustomer("stripe","u","cus_u");
        ledger.grant("stripe","in_old","u","sub_u","starter",300,now-3600,now-1);
        ObjectNode sub=json.createObjectNode().put("id","sub_u").put("status",state).put("latest_invoice","in_unpaid");
        sub.set("items",json.createObjectNode().set("data",json.createArrayNode().add(json.createObjectNode().put("current_period_end",now+3600).set("price",json.createObjectNode().put("id","price_starter")))));
        when(stripe.subscriptions("cus_u")).thenReturn(json.createObjectNode().put("has_more",false).set("data",json.createArrayNode().add(sub)));
        ObjectNode invoice=json.createObjectNode().put("id","in_unpaid").put("customer","cus_u").put("status","open").put("livemode",false).put("billing_reason","subscription_cycle");
        invoice.set("parent",json.createObjectNode().set("subscription_details",json.createObjectNode().put("subscription","sub_u")));
        when(stripe.invoice("in_unpaid")).thenReturn(invoice);
        MusicBillingService service=new MusicBillingService(settings,ledger,stripe,json);
        Map<String,Object> status=service.status("u");
        assertThat(status).containsEntry("remaining",10).containsEntry("canManage",true);
        assertThat(((Map<?,?>)status.get("subscription")).get("status")).isEqualTo(state);
        assertThat(ledger.subscription("u")).containsEntry("subscription_id","sub_u").containsEntry("status",state);
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_grants WHERE provider='stripe'")).isEqualTo(1);
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_grants WHERE payment_id='in_unpaid'")).isZero();
        assertThatThrownBy(()->service.checkout("u","creator","en")).isInstanceOf(com.example.cursorquitterweb.musicmv.support.ApiException.class);
        verify(stripe,never()).checkout(anyString(),anyString(),anyString(),anyString(),anyLong(),anyString());
    }

    @ParameterizedTest(name="G10 试用转已付套餐={0}")
    @CsvSource({"starter,300","creator,1000"})
    void trialToPaidActiveTransitionGrantsOnceAndUsesCurrentPeriod(String plan,int allowance) {
        SqliteClient db=new SqliteClient(directory.resolve("trial-paid.sqlite"));
        BillingRepository ledger=new BillingRepository(db);StripeGateway stripe=mock(StripeGateway.class);
        BillingSettings settings=new BillingSettings();settings.enabled=true;settings.secretKey="sk_test_fixture";settings.webhookSecret="whsec_fixture";
        settings.starterPrice="price_starter";settings.creatorPrice="price_creator";settings.portalConfiguration="bpc_fixture";
        long now=Instant.now().getEpochSecond();ledger.saveCustomer("stripe","u","cus_u");
        ObjectNode sub=json.createObjectNode().put("id","sub_u").put("status","trialing").put("latest_invoice","in_u");
        sub.set("items",json.createObjectNode().set("data",json.createArrayNode().add(json.createObjectNode().put("current_period_end",now+3600).set("price",json.createObjectNode().put("id","price_"+plan)))));
        when(stripe.subscriptions("cus_u")).thenReturn(json.createObjectNode().put("has_more",false).set("data",json.createArrayNode().add(sub)));
        ObjectNode invoice=json.createObjectNode().put("id","in_u").put("customer","cus_u").put("status","open").put("livemode",false).put("billing_reason","subscription_create");
        invoice.set("parent",json.createObjectNode().set("subscription_details",json.createObjectNode().put("subscription","sub_u")));
        ObjectNode line=json.createObjectNode().put("quantity",1);line.set("pricing",json.createObjectNode().set("price_details",json.createObjectNode().put("price","price_"+plan)));
        line.set("parent",json.createObjectNode().set("subscription_item_details",json.createObjectNode().put("subscription","sub_u").put("proration",false)));
        line.set("period",json.createObjectNode().put("start",now-10).put("end",now+3600));
        invoice.set("lines",json.createObjectNode().put("has_more",false).set("data",json.createArrayNode().add(line)));
        when(stripe.invoice("in_u")).thenReturn(invoice);
        MusicBillingService service=new MusicBillingService(settings,ledger,stripe,json);
        assertThat(service.status("u")).containsEntry("remaining",10);
        assertThat(ledger.subscription("u")).containsEntry("status","trialing");
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_grants WHERE provider='stripe'")).isZero();
        sub.put("status","active");invoice.put("status","paid");
        assertThat(service.status("u")).containsEntry("remaining",allowance+10);
        assertThat(service.status("u")).containsEntry("remaining",allowance+10);
        assertThat(ledger.subscription("u")).containsEntry("status","active");
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_grants WHERE provider='stripe'")).isEqualTo(1);
        Map<String,Object> grant=db.query("SELECT allowance,period_start,period_end FROM music_mv_billing_grants WHERE payment_id='in_u'").firstRow();
        assertThat(((Number)grant.get("allowance")).intValue()).isEqualTo(allowance);
        assertThat(((Number)grant.get("period_start")).longValue()).isEqualTo(now-10);
        assertThat(((Number)grant.get("period_end")).longValue()).isEqualTo(now+3600);
        verify(stripe,never()).checkoutSession(anyString());
    }

    @org.junit.jupiter.api.Test
    void expiredInitialSubscriptionAllowsFreshCheckoutWithoutGrantingOldPayment() {
        SqliteClient db=new SqliteClient(directory.resolve("initial-expired.sqlite"));
        BillingRepository ledger=new BillingRepository(db);StripeGateway stripe=mock(StripeGateway.class);
        BillingSettings settings=new BillingSettings();settings.enabled=true;settings.secretKey="sk_test_fixture";settings.webhookSecret="whsec_fixture";
        settings.starterPrice="price_starter";settings.creatorPrice="price_creator";settings.portalConfiguration="bpc_fixture";settings.siteUrl="https://example.test";
        long now=Instant.now().getEpochSecond();ledger.saveCustomer("stripe","u","cus_u");
        ledger.claimCheckout("stripe","u","old_token","starter",settings.returnUrl("zh-CN"),now-10);
        ledger.saveCheckout("stripe","u","old_token","cs_old","https://checkout.stripe.com/old");
        ledger.syncSubscription("stripe","u","sub_old","incomplete",false);
        ObjectNode ended=json.createObjectNode().put("id","sub_old").put("customer","cus_u").put("status","incomplete_expired").put("livemode",false);
        when(stripe.subscriptions("cus_u")).thenReturn(json.createObjectNode().put("has_more",false).set("data",json.createArrayNode().add(ended)));
        when(stripe.subscription("sub_old")).thenReturn(ended);
        when(stripe.checkoutSession("cs_old")).thenReturn(session("complete").put("id","cs_old").put("subscription","sub_old").put("payment_status","unpaid"));
        when(stripe.checkout(anyString(),anyString(),anyString(),anyString(),anyLong(),anyString())).thenAnswer(i->session("open").put("expires_at",(Long)i.getArgument(4)));
        MusicBillingService service=new MusicBillingService(settings,ledger,stripe,json);
        assertThat(service.checkout("u","creator","zh-CN")).containsEntry("url","https://checkout.stripe.com/fixture");
        assertThat(ledger.subscription("u")).containsEntry("checkout_plan","creator").containsEntry("checkout_id","cs_test_fixture");
        assertThat(ledger.subscription("u").get("checkout_token")).isNotEqualTo("old_token");
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_grants WHERE provider='stripe'")).isZero();
        verify(stripe,never()).invoice(anyString());
        verify(stripe,times(1)).checkout(eq("cus_u"),eq("u"),eq("price_creator"),anyString(),anyLong(),eq(settings.returnUrl("zh-CN")));
    }

    @ParameterizedTest(name="I02/I05 生成前剩余额度={0}")
    @org.junit.jupiter.params.provider.ValueSource(ints={0,9,10,11})
    void generationQuotaBoundaryAndRequestReplayUseOneReservation(int balance) {
        SqliteClient db=new SqliteClient(directory.resolve("quota-boundary.sqlite"));
        db.query("INSERT INTO music_mv_users(user_id,display_name,handle,last_login_at,created_at,updated_at) VALUES('u','u','u','2026-10-08','2026-10-08','2026-10-08')");
        BillingSettings settings=new BillingSettings();settings.enabled=true;
        BillingRepository ledger=new BillingRepository(db);
        MusicBillingService billing=new MusicBillingService(settings,ledger,mock(StripeGateway.class),json);
        long now=Instant.now().getEpochSecond();ledger.ensureWelcomeCredits("u");
        ledger.grant("stripe","in_u","u","sub_u","starter",300,now-10,now+3600);
        // 用历史余额快照设置边界；9/11不由当前正常每次10积分的消费自然产生。
        String welcome=(String)db.query("SELECT grant_id FROM music_mv_billing_grants WHERE user_id='u' AND provider='internal'").firstRow().get("grant_id");
        String monthly=(String)db.query("SELECT grant_id FROM music_mv_billing_grants WHERE payment_id='in_u'").firstRow().get("grant_id");
        db.query("INSERT INTO music_mv_billing_reservations(job_id,user_id,request_id,grant_id,state,credits) VALUES('historical_welcome','u','old_welcome',?,'consumed',10)",welcome);
        db.query("INSERT INTO music_mv_billing_reservations(job_id,user_id,request_id,grant_id,state,credits) VALUES('historical_month','u','old_month',?,'consumed',?)",monthly,300-balance);
        assertThat(billing.credits("u")).containsEntry("remaining",balance);
        AiMusicProvider provider=mock(AiMusicProvider.class);
        when(provider.providerCode()).thenReturn("sunoapi");when(provider.defaultModel()).thenReturn("V6");
        when(provider.submit(any())).thenReturn(new AiMusicProvider.Submission("accepted-task",Collections.emptyMap()));
        AiMusicGenerationService music=new AiMusicGenerationService(new AiMusicJobRepository(db),new AiMusicProviderRegistry(Collections.singletonList(provider)),
            mock(AiMusicCandidateStorageService.class),json,"sunoapi","https://example.test");
        org.springframework.test.util.ReflectionTestUtils.setField(music,"billing",billing);
        com.example.cursorquitterweb.musicmv.dto.AiMusicSongCreateRequest request=new com.example.cursorquitterweb.musicmv.dto.AiMusicSongCreateRequest();
        request.setRequestId("req");request.setStory("A birthday song");
        try {
            if(balance<10) {
                assertThatThrownBy(()->music.create("u",request,"https://example.test")).isInstanceOfSatisfying(com.example.cursorquitterweb.musicmv.support.ApiException.class,
                    e->{assertThat(e.getStatus().value()).isEqualTo(402);assertThat(e.getCode()).isEqualTo("BILLING_QUOTA_EXHAUSTED");});
                verify(provider,never()).submit(any());
                assertThat(count(db,"SELECT COUNT(*) AS n FROM ai_music_jobs")).isZero();
                assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_reservations WHERE state='reserved'")).isZero();
                assertThat(billing.credits("u")).containsEntry("remaining",balance);
            } else {
                assertThat(music.create("u",request,"https://example.test")).containsEntry("status","queued");
                assertThat(music.create("u",request,"https://example.test")).containsEntry("idempotentReplay",true);
                request.setStory("A different anniversary song");
                assertThatThrownBy(()->music.create("u",request,"https://example.test")).isInstanceOfSatisfying(com.example.cursorquitterweb.musicmv.support.ApiException.class,
                    e->{assertThat(e.getStatus().value()).isEqualTo(409);assertThat(e.getCode()).isEqualTo("AI_MUSIC_IDEMPOTENCY_CONFLICT");});
                assertThat(count(db,"SELECT COUNT(*) AS n FROM ai_music_jobs")).isEqualTo(1);
                assertThat(count(db,"SELECT COUNT(*) AS n FROM ai_music_provider_attempts")).isEqualTo(1);
                assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_reservations WHERE state='reserved' AND credits=10")).isEqualTo(1);
                assertThat(billing.credits("u")).containsEntry("remaining",balance-10);
                verify(provider,times(1)).submit(any());
            }
        } finally {music.close();}
    }

    @org.junit.jupiter.api.Test
    void mixedPeriodUsageAndTwoCandidateTitlesStayOwnedAndCountEachJobOnce() {
        SqliteClient db=new SqliteClient(directory.resolve("owned-usage.sqlite"));
        BillingRepository ledger=new BillingRepository(db);BillingSettings settings=new BillingSettings();settings.enabled=true;
        StripeGateway stripe=mock(StripeGateway.class);MusicBillingService service=new MusicBillingService(settings,ledger,stripe,json);
        long now=Instant.now().getEpochSecond();Map<String,Set<String>> ownedIds=new HashMap<>();
        for(String owner:Arrays.asList("a","b")) {
            db.query("INSERT INTO music_mv_users(user_id,display_name,handle,last_login_at,created_at,updated_at) VALUES(?,?,?,'2026-10-08','2026-10-08','2026-10-08')",owner,owner,owner);
            ledger.ensureWelcomeCredits(owner);
            ledger.grant("stripe",owner+"_old",owner,"sub_"+owner,"starter",300,now-7200,now-3600);
            ledger.grant("stripe",owner+"_active",owner,"sub_"+owner,"starter",300,now-100,now+3600);
            ledger.grant("stripe",owner+"_refunded",owner,"sub_"+owner,"creator",1000,now-100,now+7200);
            ledger.revoke("stripe",owner+"_refunded");
            Set<String> ids=new HashSet<>();ownedIds.put(owner,ids);
            for(Map<String,Object> row:db.query("SELECT grant_id FROM music_mv_billing_grants WHERE user_id=?",owner).getRows())ids.add("grant:"+row.get("grant_id"));
            List<D1Statement> seeds=new ArrayList<>();
            for(String kind:Arrays.asList("old","refunded","released","reserved")) {
                String job=owner+"_"+kind,attempt="attempt_"+job;
                String state=kind.equals("released")?"released":kind.equals("reserved")?"reserved":"consumed";
                String status=state.equals("released")?"failed":state.equals("reserved")?"queued":"completed";
                String payment=owner+"_"+(kind.equals("released")||kind.equals("reserved")?"active":kind);
                String grant=(String)db.query("SELECT grant_id FROM music_mv_billing_grants WHERE payment_id=?",payment).firstRow().get("grant_id");
                ids.add("job:"+job);
                seeds.add(D1Statement.of("INSERT INTO ai_music_jobs(job_id,user_id,client_id,request_id,status,stage,primary_provider_code,active_attempt_id,request_fingerprint,request_json,created_at,updated_at) VALUES(?,?,?,?,?,?,'sunoapi',?,'fp','{}','2026-10-08','2026-10-08')",job,owner,owner,job,status,status,attempt));
                seeds.add(D1Statement.of("INSERT INTO ai_music_provider_attempts(attempt_id,job_id,provider_code,provider_task_id,status,attempt_number,request_json,created_at,updated_at) VALUES(?,?,'sunoapi',?,?,1,'{}','2026-10-08','2026-10-08')",attempt,job,"task_"+job,status));
                seeds.add(D1Statement.of("INSERT INTO music_mv_billing_reservations(job_id,user_id,request_id,grant_id,state,credits) VALUES(?,?,?,?,?,10)",job,owner,job,grant,state));
                for(int i=0;i<2;i++)seeds.add(D1Statement.of("INSERT INTO ai_music_candidates(candidate_id,job_id,attempt_id,provider_code,provider_task_id,provider_audio_id,status,title,selected,created_at,updated_at) VALUES(?,?,?,'sunoapi',?,?,'completed',?,?,'2026-10-08','2026-10-08')",job+"_candidate_"+i,job,attempt,"task_"+job,"audio_"+job+"_"+i,job+(i==1?"_chosen":"_alternate"),i));
            }
            db.batch(seeds);
        }
        for(String owner:Arrays.asList("a","b")) {
            Map<String,Object> usage=service.usage(owner);
            assertThat(usage).containsEntry("remaining",300).containsEntry("reserved",10).containsEntry("allowance",300);
            Map<?,?> totals=(Map<?,?>)usage.get("totals");
            assertThat(((Number)totals.get("earned")).intValue()).isEqualTo(1610);
            assertThat(((Number)totals.get("spent")).intValue()).isEqualTo(20);
            @SuppressWarnings("unchecked") List<Map<String,Object>> generations=(List<Map<String,Object>>)usage.get("generations");
            assertThat(generations).hasSize(3);
            assertThat(generations).extracting(row->row.get("jobId")).containsExactlyInAnyOrder(owner+"_old",owner+"_refunded",owner+"_reserved");
            for(Map<String,Object> row:generations)assertThat(row).containsEntry("credits",10).containsEntry("title",row.get("jobId")+"_chosen");
            @SuppressWarnings("unchecked") List<Map<String,Object>> history=(List<Map<String,Object>>)service.history(owner,0).get("items");
            assertThat(history).hasSize(8);
            assertThat(new HashSet<>(history.stream().map(row->(String)row.get("id")).collect(java.util.stream.Collectors.toList()))).isEqualTo(ownedIds.get(owner));
        }
        assertThat(count(db,"SELECT COUNT(*) AS n FROM ai_music_candidates")).isEqualTo(16);
        verifyNoInteractions(stripe);
    }

    @ParameterizedTest(name="H07 套餐变更={0}→{2}")
    @CsvSource({"starter,300,creator,1000","creator,1000,starter,300"})
    void externalPlanChangeAndManualInvoiceGrantOnlyNextMonthlyCycle(String oldPlan,int oldAllowance,String newPlan,int newAllowance) throws Exception {
        SqliteClient db=new SqliteClient(directory.resolve("plan-change.sqlite"));
        BillingRepository ledger=new BillingRepository(db);StripeGateway stripe=mock(StripeGateway.class);
        BillingSettings settings=new BillingSettings();settings.enabled=true;settings.secretKey="sk_test_fixture";settings.webhookSecret="whsec_fixture";
        settings.starterPrice="price_starter";settings.creatorPrice="price_creator";settings.portalConfiguration="bpc_fixture";
        MusicBillingService service=new MusicBillingService(settings,ledger,stripe,json);
        ledger.saveCustomer("stripe","u","cus_u");ledger.ensureWelcomeCredits("u");
        ObjectNode subscription=json.createObjectNode().put("id","sub_u").put("status","active");
        subscription.set("items",json.createObjectNode().set("data",json.createArrayNode().add(json.createObjectNode().put("current_period_end",200).set("price",json.createObjectNode().put("id","price_"+oldPlan)))));
        when(stripe.subscriptions("cus_u")).thenReturn(json.createObjectNode().put("has_more",false).set("data",json.createArrayNode().add(subscription)));
        ObjectNode invoice=json.createObjectNode().put("id","in_initial").put("customer","cus_u").put("livemode",false).put("status","paid").put("billing_reason","subscription_create");
        invoice.set("parent",json.createObjectNode().set("subscription_details",json.createObjectNode().put("subscription","sub_u")));
        ObjectNode line=json.createObjectNode().put("quantity",1);
        line.set("pricing",json.createObjectNode().set("price_details",json.createObjectNode().put("price","price_"+oldPlan)));
        line.set("parent",json.createObjectNode().set("subscription_item_details",json.createObjectNode().put("subscription","sub_u").put("proration",false)));
        line.set("period",json.createObjectNode().put("start",100).put("end",200));
        invoice.set("lines",json.createObjectNode().put("has_more",false).set("data",json.createArrayNode().add(line)));
        when(stripe.invoice("in_initial")).thenReturn(invoice);
        send(service,settings,"evt_initial","invoice.paid",json.createObjectNode().put("id","in_initial"));
        ObjectNode manual=invoice.deepCopy().put("id","in_manual").put("billing_reason","manual");
        when(stripe.invoice("in_manual")).thenReturn(manual);
        send(service,settings,"evt_manual","invoice.paid",json.createObjectNode().put("id","in_manual"));
        ObjectNode adjustment=invoice.deepCopy().put("id","in_adjustment").put("billing_reason","subscription_update");
        ObjectNode adjustmentLine=(ObjectNode)adjustment.path("lines").path("data").get(0);
        ((ObjectNode)adjustmentLine.path("parent").path("subscription_item_details")).put("proration",true);
        ((ObjectNode)adjustmentLine.path("pricing").path("price_details")).put("price","price_"+newPlan);
        when(stripe.invoice("in_adjustment")).thenReturn(adjustment);
        send(service,settings,"evt_adjustment","invoice.paid",json.createObjectNode().put("id","in_adjustment"));
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_grants WHERE provider='stripe'")).isEqualTo(1);
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_events")).isEqualTo(3);
        assertThat(((Number)ledger.balance("u",150).get("remaining")).intValue()).isEqualTo(oldAllowance+10);
        ObjectNode cycle=adjustment.deepCopy().put("id","in_cycle").put("billing_reason","subscription_cycle");
        ObjectNode cycleLine=(ObjectNode)cycle.path("lines").path("data").get(0);
        ((ObjectNode)cycleLine.path("parent").path("subscription_item_details")).put("proration",false);
        cycleLine.set("period",json.createObjectNode().put("start",200).put("end",300));
        ((ObjectNode)subscription.path("items").path("data").get(0)).put("current_period_end",300);
        ((ObjectNode)subscription.path("items").path("data").get(0).path("price")).put("id","price_"+newPlan);
        when(stripe.invoice("in_cycle")).thenReturn(cycle);
        send(service,settings,"evt_cycle","invoice.paid",json.createObjectNode().put("id","in_cycle"));
        send(service,settings,"evt_cycle","invoice.paid",json.createObjectNode().put("id","in_cycle"));
        send(service,settings,"evt_cycle_duplicate","invoice.paid",json.createObjectNode().put("id","in_cycle"));
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_grants WHERE provider='stripe'")).isEqualTo(2);
        assertThat(count(db,"SELECT COUNT(*) AS n FROM music_mv_billing_events")).isEqualTo(5);
        assertThat(db.query("SELECT plan_key,allowance FROM music_mv_billing_grants WHERE payment_id='in_cycle'").firstRow()).containsEntry("plan_key",newPlan).containsEntry("allowance",newAllowance);
        assertThat(((Number)ledger.balance("u",199).get("remaining")).intValue()).isEqualTo(oldAllowance+10);
        assertThat(((Number)ledger.balance("u",200).get("remaining")).intValue()).isEqualTo(newAllowance+10);
        assertThat(((Number)ledger.balance("u",250).get("remaining")).intValue()).isEqualTo(newAllowance+10);
    }

    // 独立JVM仅连接测试临时账本与本地模拟渠道，验证恢复不依赖原服务进程内存。
    public static class CheckoutRecoveryWorker {
        public static void main(String[] args) throws Exception {
            BillingLedgerJourneyRegressionTest fixture=new BillingLedgerJourneyRegressionTest();
            SqliteClient db=fixture.new SqliteClient(java.nio.file.Paths.get(args[0]));
            BillingSettings settings=new BillingSettings();settings.enabled=true;settings.secretKey="sk_test_fixture";
            settings.webhookSecret="whsec_fixture";settings.starterPrice="price_starter";settings.creatorPrice="price_creator";
            settings.portalConfiguration="bpc_fixture";settings.siteUrl="https://www.example.test";
            com.stripe.Stripe.overrideApiBase(args[2]);
            MusicBillingService service=new MusicBillingService(settings,new BillingRepository(db),new StripeGateway(settings,fixture.json),fixture.json);
            System.out.println("RECOVERY_RESULT:"+fixture.json.writeValueAsString(service.checkout("u",args[1],"zh-CN")));
        }
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
                "d.executescript(pathlib.Path('src/main/resources/db/music-mv-d1-schema.sql').read_text())",
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
