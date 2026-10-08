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
