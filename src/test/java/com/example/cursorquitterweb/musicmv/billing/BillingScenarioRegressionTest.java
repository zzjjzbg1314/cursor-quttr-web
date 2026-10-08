package com.example.cursorquitterweb.musicmv.billing;

import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.example.cursorquitterweb.musicmv.billing.StripeGateway.map;

// 全场景回归新增证据；风险复现断言只描述现状，不能视为安全验收通过。
class BillingScenarioRegressionTest {
    BillingSettings settings;
    BillingRepository repo;
    StripeGateway stripe;
    MusicBillingService service;
    ObjectMapper json = new ObjectMapper();

    @BeforeEach void setup() {
        settings = new BillingSettings(); settings.enabled = true;
        settings.secretKey = "sk_test_fixture"; settings.webhookSecret = "whsec_fixture";
        settings.starterPrice = "price_starter"; settings.creatorPrice = "price_creator";
        settings.portalConfiguration = "bpc_fixture"; settings.siteUrl = "https://www.example.test";
        repo = mock(BillingRepository.class); stripe = mock(StripeGateway.class);
        service = new MusicBillingService(settings, repo, stripe, json);
        when(repo.customer(anyString(),anyString())).thenReturn(null);
        when(repo.subscription(anyString())).thenReturn(null);
    }

    @ParameterizedTest(name="A04 mode={0} key={1}")
    @CsvSource({"test,sk_test_fixture,true", "test,rk_test_fixture,true", "live,sk_live_fixture,true",
        "live,rk_live_fixture,true", "test,sk_live_fixture,false", "live,rk_test_fixture,false",
        "unknown,sk_test_fixture,false", "TEST,sk_test_fixture,false", "test,other,false", "test,'',false"})
    void configurationModeMatrix(String mode, String key, boolean expected) {
        settings.mode=mode; settings.secretKey=key; assertThat(settings.ready()).isEqualTo(expected);
    }

    @ParameterizedTest(name="A05 missing={0}")
    @ValueSource(strings={"secret","webhook","starter","creator","portal"})
    void missingConfigurationNeverCreatesCheckout(String missing) {
        if(missing.equals("secret")) settings.secretKey=null;
        if(missing.equals("webhook")) settings.webhookSecret="";
        if(missing.equals("starter")) settings.starterPrice="prod_fixture";
        if(missing.equals("creator")) settings.creatorPrice="";
        if(missing.equals("portal")) settings.portalConfiguration="";
        assertThatThrownBy(()->service.checkout("u","starter","en")).isInstanceOfSatisfying(ApiException.class,
            e->assertThat(e.getCode()).isEqualTo("BILLING_UNAVAILABLE"));
        verifyNoInteractions(repo,stripe);
    }

    @ParameterizedTest(name="A09 url={0}")
    @ValueSource(strings={"http://evil.test", "https://user:pass@example.test", "https://example.test/?x=1",
        "https://example.test/#x", "file:///tmp/x", "//example.test", "https://", "bad url"})
    void unsafeSiteUrlCannotCreateChannelObjects(String url) {
        settings.siteUrl=url;
        assertThatThrownBy(()->service.checkout("u","starter","en")).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(repo,stripe);
    }

    @ParameterizedTest(name="B07 invalidPlan={0}")
    @ValueSource(strings={"", "unknown", "annual", "topup", "STARTER", "price_starter"})
    void invalidPlanNeverCreatesCustomer(String plan) {
        assertThatThrownBy(()->service.checkout("u",plan,"en")).isInstanceOfSatisfying(ApiException.class,
            e->{assertThat(e.getCode()).isEqualTo("BILLING_PLAN_INVALID");assertThat(e.getStatus().value()).isEqualTo(400);});
        verifyNoInteractions(repo,stripe);
    }

    @Test void B07_nullPlanNeverCreatesCustomer() {
        assertThatThrownBy(()->service.checkout("u",null,"en")).isInstanceOf(ApiException.class);
        verifyNoInteractions(repo,stripe);
    }

    @ParameterizedTest(name="B06 invalidCheckout={0}")
    @ValueSource(strings={"in_1","cs_","cs_../bad","cs_test_1?x=y","cs_test_1/","https://evil.test"})
    void invalidCheckoutIdIsRejectedBeforeRetrieval(String id) {
        assertThatThrownBy(()->service.status("u",id)).isInstanceOfSatisfying(ApiException.class,
            e->assertThat(e.getCode()).isEqualTo("BILLING_CHECKOUT_INVALID"));
        verify(stripe,never()).checkoutSession(anyString());
    }

    @ParameterizedTest(name="K02 invalidOffset={0}")
    @ValueSource(ints={-1,100001,Integer.MIN_VALUE,Integer.MAX_VALUE})
    void invalidHistoryOffsetHasNoDatabaseSideEffect(int offset) {
        assertThatThrownBy(()->service.history("u",offset,"all")).isInstanceOfSatisfying(ApiException.class,
            e->assertThat(e.getCode()).isEqualTo("BILLING_HISTORY_INVALID"));
        verifyNoInteractions(repo,stripe);
    }

    @ParameterizedTest(name="K01 filter={0}")
    @ValueSource(strings={"all","welcome","subscription","reserved","consumed","released","revoked"})
    void allHistoryFiltersReachOwnerScopedRepository(String kind) {
        when(repo.history("u",21,100000,kind)).thenReturn(java.util.Collections.emptyList());
        assertThat(service.history("u",100000,kind)).containsEntry("hasMore",false);
        verify(repo).history("u",21,100000,kind);
    }

    @ParameterizedTest(name="H01 locale={0}")
    @ValueSource(strings={"en","zh-CN","zh-TW","ja","ko","es","pt-BR","de","fr"})
    void portalUsesOwnedCustomerAndLocalizedReturn(String locale) throws Exception {
        when(repo.customer("stripe","u")).thenReturn(map("customer_id","cus_u"));
        when(stripe.portal("cus_u",settings.returnUrl(locale))).thenReturn(json.readTree("{\"url\":\"https://billing.stripe.com/fixture\"}"));
        assertThat(service.portal("u",locale)).containsEntry("url","https://billing.stripe.com/fixture");
        verify(stripe).portal("cus_u",settings.returnUrl(locale));
        verify(repo,never()).grant(anyString(),anyString(),anyString(),anyString(),anyString(),anyInt(),anyLong(),anyLong());
    }

    @Test void H02_missingCustomerNeverCreatesPortal() {
        assertThatThrownBy(()->service.portal("u","en")).isInstanceOfSatisfying(ApiException.class,
            e->assertThat(e.getCode()).isEqualTo("BILLING_CUSTOMER_MISSING"));
        verifyNoInteractions(stripe);
    }

    @ParameterizedTest(name="C18/G status={0}")
    @ValueSource(strings={"active","trialing","past_due","unpaid","paused","incomplete"})
    void everyUnfinishedStatusBlocksSecondPurchase(String status) throws Exception {
        customer();
        when(stripe.subscriptions("cus_u")).thenReturn(subscriptions(status));
        assertThatThrownBy(()->service.checkout("u","creator","en")).isInstanceOfSatisfying(ApiException.class,
            e->assertThat(e.getCode()).isEqualTo("BILLING_ALREADY_SUBSCRIBED"));
        verify(stripe,never()).checkout(anyString(),anyString(),anyString(),anyString(),anyLong(),anyString());
        verify(repo,never()).claimCheckout(anyString(),anyString(),anyString(),anyString(),anyString(),anyLong());
    }

    @ParameterizedTest(name="G01/G07/G10 status={0}")
    @ValueSource(strings={"active","trialing","past_due","unpaid","paused","incomplete"})
    void unfinishedSubscriptionSnapshotWithoutPaidInvoiceDoesNotGrant(String status) throws Exception {
        customer(); when(stripe.subscriptions("cus_u")).thenReturn(subscriptions(status));
        Map<?,?> snapshot=(Map<?,?>)service.status("u").get("subscription");
        assertThat(snapshot.get("status")).isEqualTo(status); assertThat(snapshot.get("plan")).isEqualTo("starter");
        verify(stripe,never()).invoice(anyString());
        verify(repo,never()).grant(anyString(),anyString(),anyString(),anyString(),anyString(),anyInt(),anyLong(),anyLong());
    }

    @ParameterizedTest(name="F01 signature={0}")
    @ValueSource(strings={"", "bad", "t=1,v1=bad", "t=9999999999,v1=bad"})
    void badSignatureDoesNotTouchLedger(String signature) throws Exception {
        String body=event("invoice.paid",json.createObjectNode().put("id","in_1"));
        assertThatThrownBy(()->service.webhook(body,signature)).isInstanceOfSatisfying(ApiException.class,
            e->assertThat(e.getCode()).isEqualTo("BILLING_SIGNATURE_INVALID"));
        verifyNoInteractions(repo,stripe);
    }

    @Test void F01_bodyTamperingFailsSignature() throws Exception {
        String body=event("invoice.paid",json.createObjectNode().put("id","in_1"));
        String signature=sign(body);
        assertThatThrownBy(()->service.webhook(body+" ",signature)).isInstanceOf(ApiException.class);
        verifyNoInteractions(repo,stripe);
    }

    @ParameterizedTest(name="E01 plan={0}")
    @CsvSource({"starter,300", "creator,1000"})
    void paidInvoiceHasExactOwnerAllowanceAndPeriod(String plan,int allowance) throws Exception {
        paidInvoice(plan);
        send("invoice.paid",json.createObjectNode().put("id","in_1"));
        verify(repo).grant("stripe","in_1","u","sub_u",plan,allowance,100,200);
        verify(repo).processed("stripe","evt_fixture","invoice.paid");
    }

    @ParameterizedTest(name="E02/E03/E05/E08/E09/E11 malformed={0}")
    @ValueSource(strings={"price","quantity0","quantity2","quantityMissing","periodMissing","start0","endEqual","subMismatch","empty","pagination"})
    void malformedInvoiceDoesNotGrantOrAcknowledge(String issue) throws Exception {
        ObjectNode invoice=paidInvoice("starter"); ObjectNode line=(ObjectNode)invoice.path("lines").path("data").get(0);
        if(issue.equals("price")) ((ObjectNode)line.path("pricing").path("price_details")).put("price","price_unknown");
        if(issue.equals("quantity0")) line.put("quantity",0);
        if(issue.equals("quantity2")) line.put("quantity",2);
        if(issue.equals("quantityMissing")) line.remove("quantity");
        if(issue.equals("periodMissing")) line.remove("period");
        if(issue.equals("start0")) ((ObjectNode)line.path("period")).put("start",0);
        if(issue.equals("endEqual")) ((ObjectNode)line.path("period")).put("end",100);
        if(issue.equals("subMismatch")) ((ObjectNode)line.path("parent").path("subscription_item_details")).put("subscription","sub_b");
        if(issue.equals("empty")) ((ArrayNode)invoice.path("lines").path("data")).removeAll();
        if(issue.equals("pagination")) ((ObjectNode)invoice.path("lines")).put("has_more",true);
        assertThatThrownBy(()->send("invoice.paid",json.createObjectNode().put("id","in_1"))).isInstanceOf(RuntimeException.class);
        verify(repo,never()).grant(anyString(),anyString(),anyString(),anyString(),anyString(),anyInt(),anyLong(),anyLong());
        verify(repo,never()).processed(anyString(),anyString(),anyString());
    }

    @Test void E07_prorationAndOneRegularLineGrantOnlyOneAllowance() throws Exception {
        ObjectNode invoice=paidInvoice("starter"); ArrayNode lines=(ArrayNode)invoice.path("lines").path("data");
        ObjectNode adjustment=lines.get(0).deepCopy();
        ((ObjectNode)adjustment.path("parent").path("subscription_item_details")).put("proration",true);
        adjustment.put("quantity",2);lines.insert(0,adjustment);
        send("invoice.paid",json.createObjectNode().put("id","in_1"));
        verify(repo,times(1)).grant("stripe","in_1","u","sub_u","starter",300,100,200);
    }

    @Test void E10_missingLineSubscriptionUsesVerifiedInvoiceParent() throws Exception {
        ObjectNode invoice=paidInvoice("creator");
        ((ObjectNode)invoice.path("lines").path("data").get(0).path("parent").path("subscription_item_details")).remove("subscription");
        send("invoice.paid",json.createObjectNode().put("id","in_1"));
        verify(repo).grant("stripe","in_1","u","sub_u","creator",1000,100,200);
    }

    @Test void F08_processedFailureReplaysGrantWithSameInvoiceIdentity() throws Exception {
        paidInvoice("starter");
        doThrow(new IllegalStateException("fixture database failure")).doNothing().when(repo).processed("stripe","evt_fixture","invoice.paid");
        assertThatThrownBy(()->send("invoice.paid",json.createObjectNode().put("id","in_1"))).isInstanceOf(IllegalStateException.class);
        send("invoice.paid",json.createObjectNode().put("id","in_1"));
        verify(repo,times(2)).grant("stripe","in_1","u","sub_u","starter",300,100,200);
        // 此测试证明重试键相同；实际入账去重由仓储SQL测试验证。
    }

    @Test void F08_grantFailureDoesNotMarkProcessedAndRetryCanSucceed() throws Exception {
        paidInvoice("starter");
        doThrow(new IllegalStateException("fixture database failure")).doNothing().when(repo).grant("stripe","in_1","u","sub_u","starter",300,100,200);
        assertThatThrownBy(()->send("invoice.paid",json.createObjectNode().put("id","in_1"))).isInstanceOf(IllegalStateException.class);
        verify(repo,never()).processed(anyString(),anyString(),anyString());
        send("invoice.paid",json.createObjectNode().put("id","in_1"));
        verify(repo).processed("stripe","evt_fixture","invoice.paid");
    }

    @Test void F11_unmappedCustomerDoesNotAcknowledgeSubscription() throws Exception {
        assertThatThrownBy(()->send("customer.subscription.updated",json.createObjectNode().put("customer","cus_missing"))).isInstanceOf(IllegalStateException.class);
        verify(repo,never()).processed(anyString(),anyString(),anyString()); verifyNoInteractions(stripe);
    }

    @Test void J05_disputeRetrievesChargeAndRevokesInvoiceBeforeAcknowledgement() throws Exception {
        when(stripe.charge("ch_1")).thenReturn(json.readTree("{\"payment_intent\":\"pi_1\",\"livemode\":false}"));
        when(stripe.invoicePayments("pi_1")).thenReturn(json.readTree("{\"has_more\":false,\"data\":[{\"invoice\":\"in_1\"}]}"));
        send("charge.dispute.created",json.createObjectNode().put("charge","ch_1"));
        org.mockito.InOrder order=inOrder(repo,stripe);
        order.verify(stripe).charge("ch_1");order.verify(stripe).invoicePayments("pi_1");
        order.verify(repo).revoke("stripe","in_1");order.verify(repo).processed("stripe","evt_fixture","charge.dispute.created");
    }

    @Test void J06_paginatedRefundCannotBeAcknowledged() throws Exception {
        when(stripe.invoicePayments("pi_1")).thenReturn(json.readTree("{\"has_more\":true,\"data\":[]}"));
        assertThatThrownBy(()->send("charge.refunded",json.createObjectNode().put("payment_intent","pi_1"))).isInstanceOf(IllegalStateException.class);
        verify(repo,never()).processed(anyString(),anyString(),anyString());verify(repo,never()).revoke(anyString(),anyString());
    }

    @Test void A07_RISK_identicalPricesRemainReadyAndCreatorGetsStarterAllowance() {
        settings.creatorPrice=settings.starterPrice;
        assertThat(settings.ready()).isTrue();assertThat(settings.allowance(settings.creatorPrice)).isEqualTo(300);
    }

    @Test void E03_RISK_fractionalQuantityIsTruncatedAndGranted() throws Exception {
        ObjectNode invoice=paidInvoice("starter");((ObjectNode)invoice.path("lines").path("data").get(0)).put("quantity",1.5);
        send("invoice.paid",json.createObjectNode().put("id","in_1"));
        verify(repo).grant("stripe","in_1","u","sub_u","starter",300,100,200);
    }

    @Test void E12_RISK_retrievedInvoiceIdentityIsNotComparedWithEvent() throws Exception {
        ObjectNode invoice=paidInvoice("starter");invoice.put("id","in_other");
        send("invoice.paid",json.createObjectNode().put("id","in_1"));
        verify(repo).grant("stripe","in_other","u","sub_u","starter",300,100,200);
    }

    @Test void G14_RISK_multipleActiveSubscriptionsSelectFirstWithoutReview() throws Exception {
        customer();ObjectNode list=(ObjectNode)subscriptions("active");
        ObjectNode another=list.path("data").get(0).deepCopy();another.put("id","sub_other");((ArrayNode)list.path("data")).add(another);
        when(stripe.subscriptions("cus_u")).thenReturn(list);
        assertThat(((Map<?,?>)service.status("u").get("subscription")).get("id")).isEqualTo("sub_u");
    }

    @Test void G15_RISK_malformedSubscriptionListClearsCurrentSnapshot() throws Exception {
        customer();when(stripe.subscriptions("cus_u")).thenReturn(json.createObjectNode());
        assertThat(service.status("u")).doesNotContainKey("subscription");
        verify(repo).syncSubscription("stripe","u",null,null,false);
    }

    @Test void H04_RISK_emptyPortalUrlReturnsSuccess() throws Exception {
        customer();when(stripe.portal(anyString(),anyString())).thenReturn(json.createObjectNode());
        assertThat(service.portal("u","en")).containsEntry("url","");
    }

    @Test void F14_RISK_creditNoteWithoutInvoiceRevokesEmptyIdentityAndAcknowledges() throws Exception {
        send("credit_note.created",json.createObjectNode());
        verify(repo).revoke("stripe","");verify(repo).processed("stripe","evt_fixture","credit_note.created");
    }

    @Test void J07_RISK_missingRefundPaymentIsAcknowledgedWithoutRevocation() throws Exception {
        send("charge.refunded",json.createObjectNode());
        verify(repo,never()).revoke(anyString(),anyString());verify(repo).processed("stripe","evt_fixture","charge.refunded");
    }

    @Test void J09_RISK_disputeClosedIsAcknowledgedWithoutRestoringAnything() throws Exception {
        send("charge.dispute.closed",json.createObjectNode().put("status","won"));
        verify(repo).processed("stripe","evt_fixture","charge.dispute.closed");
        verify(repo,never()).revoke(anyString(),anyString());verifyNoInteractions(stripe);
    }

    @Test void J07_RISK_wrongEnvironmentDisputeChargeStillRevokes() throws Exception {
        when(stripe.charge("ch_1")).thenReturn(json.readTree("{\"payment_intent\":\"pi_1\",\"livemode\":true}"));
        when(stripe.invoicePayments("pi_1")).thenReturn(json.readTree("{\"has_more\":false,\"data\":[{\"invoice\":\"in_1\"}]}"));
        send("charge.dispute.created",json.createObjectNode().put("charge","ch_1"));
        verify(repo).revoke("stripe","in_1");verify(repo).processed("stripe","evt_fixture","charge.dispute.created");
    }

    @Test void A12_databaseFailureCannotBecomeSuccessfulZeroBalance() {
        when(repo.refreshBalance(eq("u"),anyLong())).thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(()->service.credits("u")).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(stripe);
    }

    @Test void C11_duplicateRecoveredSessionsRequireReviewAndKeepSlot() throws Exception {
        customer(); long expires=Instant.now().getEpochSecond()+600;
        when(repo.subscription("u")).thenReturn(map("provider","stripe","checkout_token","token",
            "checkout_plan","starter","checkout_expires",expires,"checkout_return_url",settings.returnUrl("en")));
        when(stripe.checkout(anyString(),anyString(),anyString(),anyString(),anyLong(),anyString()))
            .thenThrow(new StripeGateway.CheckoutExpiryRejectedException());
        ObjectNode session=json.createObjectNode().put("client_reference_id","u").put("expires_at",expires);
        ObjectNode list=json.createObjectNode().put("has_more",false);
        list.set("data",json.createArrayNode().add(session.deepCopy().put("id","cs_one"))
            .add(session.deepCopy().put("id","cs_two")));
        when(stripe.checkoutSessions(eq("cus_u"),anyLong())).thenReturn(list);
        assertThatThrownBy(()->service.checkout("u","starter","en")).isInstanceOfSatisfying(ApiException.class,
            e->assertThat(e.getCode()).isEqualTo("BILLING_REVIEW_REQUIRED"));
        verify(repo,never()).clearCheckout(anyString(),anyString(),anyString());
        verify(repo,never()).saveCheckout(anyString(),anyString(),anyString(),anyString(),anyString());
    }

    @Test void C24_customerSaveFailureRetriesSameOwnerBeforeCreatingOrder() throws Exception {
        when(stripe.customer("u")).thenReturn(json.createObjectNode().put("id","cus_u"));
        doThrow(new IllegalStateException("database unavailable")).doNothing().when(repo).saveCustomer("stripe","u","cus_u");
        assertThatThrownBy(()->service.checkout("u","starter","en")).isInstanceOf(IllegalStateException.class);
        verify(stripe,never()).checkout(anyString(),anyString(),anyString(),anyString(),anyLong(),anyString());
        when(repo.customer("stripe","u")).thenReturn(null,map("customer_id","cus_u"));
        when(stripe.subscriptions("cus_u")).thenReturn(subscriptions("active"));
        assertThatThrownBy(()->service.checkout("u","starter","en")).isInstanceOf(ApiException.class);
        verify(stripe,times(2)).customer("u"); verify(repo,times(2)).saveCustomer("stripe","u","cus_u");
        verify(stripe,never()).customer("other");
    }

    @ParameterizedTest
    @ValueSource(strings={"revoke","processed"})
    void F09_refundFailureDoesNotCompleteAndRetryRepeatsSameInvoice(String failure) throws Exception {
        ObjectNode credit=json.createObjectNode().put("invoice","in_1");
        if(failure.equals("revoke")) doThrow(new IllegalStateException("database unavailable")).doNothing()
            .when(repo).revoke("stripe","in_1");
        else doThrow(new IllegalStateException("database unavailable")).doNothing()
            .when(repo).processed("stripe","evt_fixture","credit_note.created");
        assertThatThrownBy(()->send("credit_note.created",credit)).isInstanceOf(IllegalStateException.class);
        if(failure.equals("revoke")) verify(repo,never()).processed("stripe","evt_fixture","credit_note.created");
        send("credit_note.created",credit);
        verify(repo,times(2)).revoke("stripe","in_1");
        verify(repo,times(failure.equals("revoke")?1:2)).processed("stripe","evt_fixture","credit_note.created");
    }

    @ParameterizedTest
    @ValueSource(strings={"invoice.payment_failed","invoice.payment_action_required","unknown.fixture"})
    void F12_ignoredEventsAcknowledgeWithoutGrantOrRevoke(String type) throws Exception {
        send(type,json.createObjectNode().put("id","in_1"));
        verify(repo).processed("stripe","evt_fixture",type);
        verify(repo,never()).grant(anyString(),anyString(),anyString(),anyString(),anyString(),anyInt(),anyLong(),anyLong());
        verify(repo,never()).revoke(anyString(),anyString()); verifyNoInteractions(stripe);
    }

    @Test void G03_staleCancellationEventsUseLatestResumedState() throws Exception {
        customer(); when(stripe.subscriptions("cus_u")).thenReturn(subscriptions("active"));
        ObjectNode old=json.createObjectNode().put("customer","cus_u").put("cancel_at_period_end",true);
        send("customer.subscription.updated",old); send("customer.subscription.updated",old);
        verify(repo,times(2)).syncSubscription("stripe","u","sub_u","active",false);
        verify(repo,never()).revoke(anyString(),anyString());
    }

    @Test void G18_providerOutagePreservesSnapshotAndLedgerReadsRemainIndependent() throws Exception {
        customer(); when(stripe.subscriptions("cus_u")).thenThrow(new ApiException(
            org.springframework.http.HttpStatus.BAD_GATEWAY,"BILLING_PROVIDER_UNAVAILABLE","retry"));
        assertThatThrownBy(()->service.status("u")).isInstanceOfSatisfying(ApiException.class,
            e->assertThat(e.getCode()).isEqualTo("BILLING_PROVIDER_UNAVAILABLE"));
        verify(repo,never()).syncSubscription(anyString(),anyString(),any(),any(),anyBoolean());
        clearInvocations(stripe); service.credits("u"); service.history("u",0);
        verifyNoInteractions(stripe);
    }

    @Test void I20_disabledBillingSkipsReservationAndSettlementButCanResumeSettlement() {
        settings.enabled=false; service.reserve("u","request","job");service.settle("job","completed");
        verifyNoInteractions(repo,stripe);
        settings.enabled=true;service.settle("job","completed");verify(repo).settle("job","consumed");
    }

    @Test void G17_RISK_multipleItemsSelectFirstAndIgnoreItemPagination() throws Exception {
        customer(); ObjectNode list=(ObjectNode)subscriptions("active");
        ObjectNode items=(ObjectNode)list.path("data").get(0).path("items");items.put("has_more",true);
        ((ArrayNode)items.path("data")).add(json.createObjectNode().put("current_period_end",300)
            .set("price",json.createObjectNode().put("id","price_creator")));
        when(stripe.subscriptions("cus_u")).thenReturn(list);
        Map<?,?> sub=(Map<?,?>)service.status("u").get("subscription");
        assertThat(sub.get("plan")).isEqualTo("starter");assertThat(sub.get("periodEnd")).isEqualTo(200L);
        verify(repo).syncSubscription("stripe","u","sub_u","active",false);
    }

    @Test void C21_checkoutContentionStopsAfterThreeRoundsWithoutCreatingAnotherSession() throws Exception {
        customer();
        when(repo.subscription("u")).thenReturn(map("provider","stripe","checkout_token","old-token","checkout_plan","starter",
            "checkout_expires",Instant.now().getEpochSecond()+600,"checkout_id","cs_old","checkout_return_url",settings.returnUrl("en")));
        when(stripe.checkoutSession("cs_old")).thenReturn(json.createObjectNode().put("id","cs_old").put("status","expired")
            .put("client_reference_id","u").put("customer","cus_u").put("livemode",false).put("mode","subscription"));
        when(repo.clearCheckout("stripe","u","old-token")).thenReturn(false);
        assertThatThrownBy(()->service.checkout("u","starter","en")).isInstanceOfSatisfying(ApiException.class,
            e->{assertThat(e.getStatus().value()).isEqualTo(409);assertThat(e.getCode()).isEqualTo("BILLING_CHECKOUT_RETRY");});
        verify(stripe,times(3)).checkoutSession("cs_old");verify(repo,times(3)).clearCheckout("stripe","u","old-token");
        verify(stripe,never()).checkout(anyString(),anyString(),anyString(),anyString(),anyLong(),anyString());
        verify(repo,never()).claimCheckout(anyString(),anyString(),anyString(),anyString(),anyString(),anyLong());
        verify(repo,never()).saveCheckout(anyString(),anyString(),anyString(),anyString(),anyString());
    }

    private void customer() throws Exception {
        when(repo.customer("stripe","u")).thenReturn(map("customer_id","cus_u"));
        when(repo.owner("stripe","cus_u")).thenReturn("u");
        when(stripe.subscriptions("cus_u")).thenReturn(json.readTree("{\"data\":[],\"has_more\":false}"));
    }
    private JsonNode subscriptions(String status) throws Exception {
        return json.readTree("{\"data\":[{\"id\":\"sub_u\",\"status\":\""+status+"\",\"items\":{\"data\":[{\"price\":{\"id\":\"price_starter\"},\"current_period_end\":200}]}}],\"has_more\":false}");
    }
    private ObjectNode paidInvoice(String plan) throws Exception {
        customer();
        ObjectNode invoice=(ObjectNode)json.readTree("{\"id\":\"in_1\",\"customer\":\"cus_u\",\"livemode\":false,\"status\":\"paid\",\"billing_reason\":\"subscription_create\",\"parent\":{\"subscription_details\":{\"subscription\":\"sub_u\"}},\"lines\":{\"has_more\":false,\"data\":[{\"quantity\":1,\"pricing\":{\"price_details\":{\"price\":\"price_"+plan+"\"}},\"parent\":{\"subscription_item_details\":{\"subscription\":\"sub_u\",\"proration\":false}},\"period\":{\"start\":100,\"end\":200}}]}}");
        when(stripe.invoice("in_1")).thenReturn(invoice);return invoice;
    }
    private String event(String type,ObjectNode object) throws Exception {
        return json.writeValueAsString(json.createObjectNode().put("id","evt_fixture").put("object","event").put("livemode",false).put("type",type).set("data",json.createObjectNode().set("object",object)));
    }
    private void send(String type,ObjectNode object) throws Exception { String body=event(type,object);service.webhook(body,sign(body)); }
    private String sign(String body) throws Exception {
        long timestamp=Instant.now().getEpochSecond();Mac mac=Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(settings.webhookSecret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        StringBuilder hex=new StringBuilder();for(byte b:mac.doFinal((timestamp+"."+body).getBytes(StandardCharsets.UTF_8)))hex.append(String.format("%02x",b));
        return "t="+timestamp+",v1="+hex;
    }
}
