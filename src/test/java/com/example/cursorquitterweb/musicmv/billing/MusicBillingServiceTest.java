package com.example.cursorquitterweb.musicmv.billing;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import static com.example.cursorquitterweb.musicmv.billing.StripeGateway.map;

class MusicBillingServiceTest {
    BillingSettings settings; BillingRepository repo; StripeGateway stripe; MusicBillingService service; ObjectMapper json=new ObjectMapper();
    @BeforeEach void setup() {
        settings=new BillingSettings(); settings.enabled=true;settings.secretKey="sk_test_fixture";settings.webhookSecret="whsec_fixture";
        settings.starterPrice="price_starter";settings.creatorPrice="price_creator";settings.portalConfiguration="bpc_fixture";settings.siteUrl="http://localhost:3000";
        repo=mock(BillingRepository.class); stripe=mock(StripeGateway.class);service=new MusicBillingService(settings,repo,stripe,json);
    }
    @Test void reminderUsesBalanceAndLegacyAcknowledgementDoesNotAccessDatabase() {
        when(repo.refreshBalance(eq("u"),anyLong())).thenReturn(map("unused_welcome_id","gift","welcome_remaining",10));
        assertThat(service.credits("u")).containsEntry("welcomeNotice",null)
            .containsEntry("unusedWelcomeCredits",map("id","gift","credits",10));
        clearInvocations(repo);
        service.acknowledgeWelcomeNotice("u","gift");
        verifyNoInteractions(repo,stripe);
    }
    @Test void productionKeysAndIncompleteConfigurationRemainDisabled() {
        assertThat(settings.ready()).isTrue();settings.secretKey="sk_live_fixture";assertThat(settings.ready()).isFalse();
        assertThatThrownBy(()->service.checkout("u","starter","en")).isInstanceOf(ApiException.class);verifyNoInteractions(repo,stripe);
    }
    @Test void welcomeCreditsDoNotDependOnPaymentConfiguration() {
        settings.secretKey="";
        when(repo.refreshBalance(eq("u"),anyLong())).thenReturn(map("remaining",10,"welcome_remaining",10,"allowance",0,"reserved",0));
        assertThat(service.credits("u")).containsEntry("remaining",10).containsEntry("welcomeRemaining",10).containsEntry("available",true).containsEntry("checkoutAvailable",false);
        verify(repo).refreshBalance(eq("u"),anyLong());verifyNoInteractions(stripe);
    }
    private void paidCheckoutFixture(String price) throws Exception {
        checkoutCustomer();
        when(repo.owner("stripe","cus_1")).thenReturn("u");
        JsonNode paid=session("cs_test_1","complete");
        ((com.fasterxml.jackson.databind.node.ObjectNode)paid).put("payment_status","paid").put("invoice","in_1").put("subscription","sub_1");
        when(stripe.checkoutSession("cs_test_1")).thenReturn(paid);
        when(stripe.invoice("in_1")).thenReturn(json.readTree("{\"id\":\"in_1\",\"livemode\":false,\"status\":\"paid\",\"billing_reason\":\"subscription_create\",\"customer\":\"cus_1\",\"parent\":{\"subscription_details\":{\"subscription\":\"sub_1\"}},\"lines\":{\"data\":[{\"quantity\":1,\"pricing\":{\"price_details\":{\"price\":\""+price+"\"}},\"parent\":{\"subscription_item_details\":{\"subscription\":\"sub_1\"}},\"period\":{\"start\":100,\"end\":200}}]}}"));
    }
    @Test void paidCheckoutRepairsMissingGrantAndReturnsFreshBalance() throws Exception {
        paidCheckoutFixture("price_starter");
        when(repo.paymentGranted("u","in_1")).thenReturn(false,true);
        when(repo.refreshBalance(eq("u"),anyLong())).thenReturn(map("remaining",10),map("remaining",310));
        assertThat(service.status("u","cs_test_1")).containsEntry("paymentConfirmed",true).containsEntry("remaining",310);
        verify(repo).grant("stripe","in_1","u","sub_1","starter",300,100,200);
        service.status("u","cs_test_1");
        verify(stripe,times(1)).invoice("in_1");
        verify(repo,times(1)).grant(anyString(),anyString(),anyString(),anyString(),anyString(),anyInt(),anyLong(),anyLong());
    }
    @Test void ordinaryStatusRecoversSavedOrderWithoutReturnQuery() throws Exception {
        paidCheckoutFixture("price_creator");
        when(repo.subscription("u")).thenReturn(pending("creator",200,"cs_test_1"));
        when(repo.paymentGranted("u","in_1")).thenReturn(false,true);
        assertThat(service.status("u")).containsEntry("paymentConfirmed",true);
        verify(repo).grant("stripe","in_1","u","sub_1","creator",1000,100,200);
    }
    @Test void unpaidCheckoutNeverRetrievesInvoiceOrGrants() throws Exception {
        paidCheckoutFixture("price_starter");
        ((com.fasterxml.jackson.databind.node.ObjectNode)stripe.checkoutSession("cs_test_1")).put("payment_status","unpaid");
        assertThat(service.status("u","cs_test_1")).containsEntry("paymentConfirmed",false);
        verify(stripe,never()).invoice(anyString());
        verify(repo,never()).grant(anyString(),anyString(),anyString(),anyString(),anyString(),anyInt(),anyLong(),anyLong());
    }
    @Test void foreignCheckoutOrInvoiceCannotGrant() throws Exception {
        for(String field:new String[]{"client_reference_id","customer","subscription"}) {
            paidCheckoutFixture("price_starter");
            ((com.fasterxml.jackson.databind.node.ObjectNode)stripe.checkoutSession("cs_test_1")).put(field,"other");
            assertThatThrownBy(()->service.status("u","cs_test_1")).isInstanceOf(ApiException.class);
        }
        paidCheckoutFixture("price_starter");
        ((com.fasterxml.jackson.databind.node.ObjectNode)stripe.invoice("in_1")).put("customer","cus_other");
        assertThatThrownBy(()->service.status("u","cs_test_1")).isInstanceOf(ApiException.class);
        verify(repo,never()).grant(anyString(),anyString(),anyString(),anyString(),anyString(),anyInt(),anyLong(),anyLong());
    }
    @Test void invalidInvoiceStateNeverGrantsDuringReconciliation() throws Exception {
        for(String field:new String[]{"status","livemode"}) {
            paidCheckoutFixture("price_starter");
            com.fasterxml.jackson.databind.node.ObjectNode invoice=(com.fasterxml.jackson.databind.node.ObjectNode)stripe.invoice("in_1");
            if(field.equals("status")) invoice.put(field,"open");else invoice.put(field,true);
            assertThatThrownBy(()->service.status("u","cs_test_1")).isInstanceOf(IllegalStateException.class);
        }
        verify(repo,never()).grant(anyString(),anyString(),anyString(),anyString(),anyString(),anyInt(),anyLong(),anyLong());
    }
    private void renewalFixture(String status,String invoiceStatus,String price) throws Exception {
        paidCheckoutFixture(price);
        when(stripe.subscriptions("cus_1")).thenReturn(json.readTree("{\"data\":[{\"id\":\"sub_1\",\"status\":\""+status+"\",\"latest_invoice\":\"in_renewal\",\"items\":{\"data\":[{\"price\":{\"id\":\""+price+"\"},\"current_period_end\":200}]}}]}"));
        com.fasterxml.jackson.databind.node.ObjectNode invoice=(com.fasterxml.jackson.databind.node.ObjectNode)stripe.invoice("in_1").deepCopy();
        invoice.put("id","in_renewal").put("billing_reason","subscription_cycle").put("status",invoiceStatus);
        when(stripe.invoice("in_renewal")).thenReturn(invoice);
    }
    @Test void latestPaidRenewalRepairsCreditsWithoutOriginalCheckoutAndDoesNotExposeInvoiceId() throws Exception {
        renewalFixture("active","paid","price_creator");
        when(repo.refreshBalance(eq("u"),anyLong())).thenReturn(map("remaining",10),map("remaining",1010));
        when(repo.paymentGranted("u","in_renewal")).thenReturn(false,true);
        java.util.Map<String,Object> result=service.status("u");
        assertThat(result).containsEntry("remaining",1010);
        assertThat(((java.util.Map<?,?>)result.get("subscription")).containsKey("_latestInvoice")).isFalse();
        service.status("u");
        verify(repo,times(1)).grant("stripe","in_renewal","u","sub_1","creator",1000,100,200);
        verify(stripe,never()).checkoutSession(anyString());
        verify(stripe,times(1)).invoice("in_renewal");
        verify(stripe,times(2)).subscriptions("cus_1");
    }
    @Test void failedOrPendingRenewalPreservesStatusAndNeverGrants() throws Exception {
        for(String status:new String[]{"past_due","unpaid","incomplete","paused"}) {
            renewalFixture(status,"open","price_starter");
            when(repo.refreshBalance(eq("u"),anyLong())).thenReturn(map("remaining",10));
            java.util.Map<String,Object> result=service.status("u");
            assertThat(result).containsEntry("remaining",10);
            assertThat(((java.util.Map<?,?>)result.get("subscription")).get("status")).isEqualTo(status);
        }
        verify(repo,never()).grant(anyString(),anyString(),anyString(),anyString(),anyString(),anyInt(),anyLong(),anyLong());
    }
    @Test void foreignOrWrongEnvironmentRenewalCannotGrant() throws Exception {
        for(String field:new String[]{"customer","subscription","livemode","id"}) {
            renewalFixture("active","paid","price_starter");
            com.fasterxml.jackson.databind.node.ObjectNode invoice=(com.fasterxml.jackson.databind.node.ObjectNode)stripe.invoice("in_renewal");
            if(field.equals("livemode")) invoice.put(field,true);
            else if(field.equals("subscription")) ((com.fasterxml.jackson.databind.node.ObjectNode)invoice.path("parent").path("subscription_details")).put(field,"sub_other");
            else invoice.put(field,"other");
            assertThatThrownBy(()->service.status("u")).isInstanceOf(ApiException.class);
        }
        verify(repo,never()).grant(anyString(),anyString(),anyString(),anyString(),anyString(),anyInt(),anyLong(),anyLong());
    }
    @Test void paidProrationDoesNotReceiveMonthlyAllowance() throws Exception {
        renewalFixture("active","paid","price_creator");
        ((com.fasterxml.jackson.databind.node.ObjectNode)stripe.invoice("in_renewal")).put("billing_reason","subscription_update");
        service.status("u");
        verify(repo,never()).grant(anyString(),anyString(),anyString(),anyString(),anyString(),anyInt(),anyLong(),anyLong());
    }
    @Test void renewalCompensationFollowedByWebhookSharesSameInvoiceIdentity() throws Exception {
        renewalFixture("active","paid","price_starter");
        service.status("u");
        String payload=event("invoice.paid","{\"id\":\"in_renewal\"}",false);
        service.webhook(payload,sign(payload,Instant.now().getEpochSecond()));
        verify(repo,times(2)).grant("stripe","in_renewal","u","sub_1","starter",300,100,200);
        verify(repo).processed("stripe","evt_1","invoice.paid");
    }
    @Test void endedSubscriptionDoesNotReconcileInvoicesAndAllowsPlanSelection() throws Exception {
        checkoutCustomer();
        when(stripe.subscriptions("cus_1")).thenReturn(json.readTree("{\"data\":[{\"id\":\"sub_1\",\"status\":\"canceled\",\"latest_invoice\":\"in_old\"}]}"));
        assertThat(service.status("u")).doesNotContainKey("subscription");
        verify(stripe,never()).invoice(anyString());
        verify(repo).syncSubscription("stripe","u",null,null,false);
    }
    @Test void unknownPriceDoesNotAcknowledgeWebhookAndCanRetryAfterConfigurationFix() throws Exception {
        paidCheckoutFixture("price_changed");
        String payload=event("invoice.paid","{\"id\":\"in_1\"}",false);
        assertThatThrownBy(()->service.webhook(payload,sign(payload,Instant.now().getEpochSecond()))).isInstanceOf(ApiException.class);
        verify(repo,never()).processed(anyString(),anyString(),anyString());
        settings.starterPrice="price_changed";
        service.webhook(payload,sign(payload,Instant.now().getEpochSecond()));
        verify(repo).grant("stripe","in_1","u","sub_1","starter",300,100,200);
        verify(repo).processed("stripe","evt_1","invoice.paid");
    }
    @Test void malformedOrAmbiguousAllowanceDoesNotGrantOrAcknowledge() throws Exception {
        for(String issue:new String[]{"empty","quantity","period","duplicate","subscription"}) {
            paidCheckoutFixture("price_starter");
            com.fasterxml.jackson.databind.node.ArrayNode lines=(com.fasterxml.jackson.databind.node.ArrayNode)stripe.invoice("in_1").path("lines").path("data");
            com.fasterxml.jackson.databind.node.ObjectNode line=(com.fasterxml.jackson.databind.node.ObjectNode)lines.get(0);
            if(issue.equals("empty")) lines.removeAll();
            if(issue.equals("quantity")) line.put("quantity",2);
            if(issue.equals("period")) ((com.fasterxml.jackson.databind.node.ObjectNode)line.path("period")).put("end",99);
            if(issue.equals("duplicate")) lines.add(line.deepCopy());
            if(issue.equals("subscription")) ((com.fasterxml.jackson.databind.node.ObjectNode)line.path("parent").path("subscription_item_details")).put("subscription","sub_other");
            String payload=event("invoice.paid","{\"id\":\"in_1\"}",false);
            assertThatThrownBy(()->service.webhook(payload,sign(payload,Instant.now().getEpochSecond()))).isInstanceOf(ApiException.class);
        }
        verify(repo,never()).processed(anyString(),anyString(),anyString());
        verify(repo,never()).grant(anyString(),anyString(),anyString(),anyString(),anyString(),anyInt(),anyLong(),anyLong());
    }
    @Test void liveModeMustBeExplicitAndMatchTheKey() {
        settings.secretKey="sk_live_fixture";assertThat(settings.ready()).isFalse();
        settings.mode="live";assertThat(settings.ready()).isTrue();
        settings.secretKey="sk_test_fixture";assertThat(settings.ready()).isFalse();
    }
    @Test void liveWebhookOnlyGrantsMatchingLiveInvoice() throws Exception {
        settings.mode="live";settings.secretKey="sk_live_fixture";
        when(repo.owner("stripe","cus_1")).thenReturn("u");
        when(stripe.subscriptions("cus_1")).thenReturn(json.readTree("{\"data\":[]}"));
        when(stripe.invoice("in_live")).thenReturn(json.readTree("{\"id\":\"in_live\",\"livemode\":true,\"status\":\"paid\",\"billing_reason\":\"subscription_create\",\"customer\":\"cus_1\",\"lines\":{\"data\":[{\"quantity\":1,\"pricing\":{\"price_details\":{\"price\":\"price_starter\"}},\"parent\":{\"subscription_item_details\":{\"subscription\":\"sub_1\"}},\"period\":{\"start\":100,\"end\":200}}]}}"));
        String wrong=event("invoice.paid","{\"id\":\"in_live\"}",false);
        assertThatThrownBy(()->service.webhook(wrong,sign(wrong,Instant.now().getEpochSecond()))).isInstanceOf(ApiException.class);
        String right=event("invoice.paid","{\"id\":\"in_live\"}",true);
        service.webhook(right,sign(right,Instant.now().getEpochSecond()));
        verify(repo).grant("stripe","in_live","u","sub_1","starter",300,100,200);
    }
    @Test void reserveFailsClosedAndUnknownRequestsAreNotReleased() {
        when(repo.reserve(eq("u"),eq("r"),eq("j"),anyLong(),eq(10))).thenReturn(false);
        assertThatThrownBy(()->service.reserve("u","r","j")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getStatus().value()).isEqualTo(402));
        service.settle("j","submission_unknown");verify(repo,never()).settle(anyString(),anyString());
        service.settle("j","failed");verify(repo).settle("j","released");
    }
    @Test void disabledBillingDoesNotTouchUninitializedTables() {
        settings.enabled=false;service.reserve("u","r","j");service.settle("j","completed");service.status("u");verifyNoInteractions(repo,stripe);
    }
    @Test void paidInvoiceGrantsFromServerPriceAndDeduplicatesEvents() throws Exception {
        JsonNode invoice=json.readTree("{\"id\":\"in_1\",\"livemode\":false,\"status\":\"paid\",\"billing_reason\":\"subscription_cycle\",\"customer\":\"cus_1\",\"lines\":{\"data\":[{\"quantity\":1,\"pricing\":{\"price_details\":{\"price\":\"price_starter\"}},\"parent\":{\"subscription_item_details\":{\"subscription\":\"sub_1\",\"proration\":false}},\"period\":{\"start\":100,\"end\":200}}]}}");
        when(stripe.subscriptions("cus_1")).thenReturn(json.readTree("{\"data\":[{\"id\":\"sub_1\",\"status\":\"active\"}]}"));
        when(stripe.invoice("in_1")).thenReturn(invoice);when(repo.owner("stripe","cus_1")).thenReturn("u");
        String event=event("invoice.paid","{\"id\":\"in_1\"}",false);service.webhook(event,sign(event,Instant.now().getEpochSecond()));
        verify(repo).grant("stripe","in_1","u","sub_1","starter",300,100,200);
        when(repo.processed("stripe","evt_1")).thenReturn(true);service.webhook(event,sign(event,Instant.now().getEpochSecond()));verify(stripe,times(1)).invoice("in_1");
    }
    @Test void invalidStaleAndLiveEventsNeverGrant() throws Exception {
        String event=event("invoice.paid","{\"id\":\"in_1\"}",false);
        assertThatThrownBy(()->service.webhook(event,"bad")).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.webhook(event,sign(event,1))).isInstanceOf(ApiException.class);
        String live=event("invoice.paid","{\"id\":\"in_1\"}",true);
        assertThatThrownBy(()->service.webhook(live,sign(live,Instant.now().getEpochSecond()))).isInstanceOf(ApiException.class);
        verifyNoInteractions(repo,stripe);
    }
    @Test void providerFailureDoesNotAcknowledgeEvent() throws Exception {
        when(stripe.invoice("in_1")).thenThrow(new IllegalStateException("temporary"));
        String event=event("invoice.paid","{\"id\":\"in_1\"}",false);
        assertThatThrownBy(()->service.webhook(event,sign(event,Instant.now().getEpochSecond()))).isInstanceOf(IllegalStateException.class);
        verify(repo,never()).processed(anyString(),anyString(),anyString());
    }
    @Test void existingSubscriptionCannotPurchaseAnother() throws Exception {
        when(repo.customer("stripe","u")).thenReturn(map("customer_id","cus_1"));
        when(stripe.subscriptions("cus_1")).thenReturn(json.readTree("{\"data\":[{\"status\":\"past_due\"}]}"));
        assertThatThrownBy(()->service.checkout("u","starter","en")).isInstanceOf(ApiException.class);
        verify(repo,never()).claimCheckout(anyString(),anyString(),anyString(),anyString(),anyString(),anyLong());
    }
    @Test void creditNotesRecordRevocationBeforeEventAcknowledgement() throws Exception {
        String event=event("credit_note.created","{\"invoice\":\"in_1\"}",false);
        service.webhook(event,sign(event,Instant.now().getEpochSecond()));
        org.mockito.InOrder order=inOrder(repo);order.verify(repo).processed("stripe","evt_1");order.verify(repo).revoke("stripe","in_1");order.verify(repo).processed("stripe","evt_1","credit_note.created");
    }
    @Test void creditBalanceNeverCallsStripeAndIncludesPendingHolds() {
        when(repo.refreshBalance(eq("u"),anyLong())).thenReturn(map("remaining",280,"allowance",300,"reserved",20,"period_end",200L));
        java.util.Map<String,Object> result=service.credits("u");
        assertThat(result).containsEntry("generationCredits",10).containsEntry("remaining",280)
                .containsEntry("reserved",20).containsEntry("periodEnd",200L);
        verifyNoInteractions(stripe);
    }
    @Test void subscriptionDateUsesStripeItemPeriodInsteadOfCreditGrantExpiry() throws Exception {
        checkoutCustomer();
        when(repo.refreshBalance(eq("u"),anyLong())).thenReturn(map("period_end",200L));
        when(stripe.subscriptions("cus_1")).thenReturn(json.readTree("{\"data\":[{\"id\":\"sub_1\",\"status\":\"active\",\"items\":{\"data\":[{\"price\":{\"id\":\"price_starter\"},\"current_period_end\":300}]}}]}"));
        assertThat(((java.util.Map<?,?>)service.status("u").get("subscription")).get("periodEnd")).isEqualTo(300L);
    }
    @Test void cancellationKeepsPaidPeriodAndDoesNotRevokeCredits() throws Exception {
        when(repo.refreshBalance(eq("u"),anyLong())).thenReturn(map("remaining",20,"reserved",0,"allowance",300,"period_end",200L));
        when(repo.customer("stripe","u")).thenReturn(map("customer_id","cus_1"));
        when(stripe.subscriptions("cus_1")).thenReturn(json.readTree("{\"data\":[{\"status\":\"active\",\"cancel_at_period_end\":true}]}"));
        assertThat(service.status("u")).containsEntry("remaining",20);
        verify(repo,never()).revoke(anyString(),anyString());
    }
    @Test void anotherProviderSubscriptionBlocksStripeBeforeCreatingCheckout() {
        when(repo.subscription("u")).thenReturn(map("provider","paypal","subscription_id","sub_other"));
        assertThatThrownBy(()->service.checkout("u","starter","en")).isInstanceOf(ApiException.class);
        verifyNoInteractions(stripe);
    }
    private java.util.Map<String,Object> pending(String plan,long expires,String id) {
        return map("provider","stripe","checkout_token","token","checkout_plan",plan,"checkout_expires",expires,
                "checkout_id",id,"checkout_return_url","http://localhost:3000/zh-cn/pricing");
    }
    private JsonNode session(String id,String state) throws Exception {
        return json.readTree("{\"id\":\""+id+"\",\"status\":\""+state+"\",\"customer\":\"cus_1\",\"client_reference_id\":\"u\",\"mode\":\"subscription\",\"livemode\":false,\"url\":\"https://checkout.stripe.com/session\"}");
    }
    private void checkoutCustomer() throws Exception {
        when(repo.customer("stripe","u")).thenReturn(map("customer_id","cus_1"));
        when(stripe.subscriptions("cus_1")).thenReturn(json.readTree("{\"data\":[]}"));
    }
    @Test void checkoutReadsSharedSlotAndVerifiesTheSameSession() throws Exception {
        checkoutCustomer();
        when(repo.subscription("u")).thenReturn(pending("starter",Instant.now().getEpochSecond()+600,"cs_existing"));
        when(stripe.checkoutSession("cs_existing")).thenReturn(session("cs_existing","open"));
        assertThat(service.checkout("u","starter","en")).containsEntry("url","https://checkout.stripe.com/session");
        verify(stripe,never()).checkout(anyString(),anyString(),anyString(),anyString(),anyLong(),anyString());
        verify(repo,never()).clearCheckout(anyString(),anyString(),anyString());
    }
    @Test void lostCheckoutResponseCanBeRecoveredWithinLastThirtyMinutes() throws Exception {
        checkoutCustomer();long expires=Instant.now().getEpochSecond()+600;
        when(repo.subscription("u")).thenReturn(pending("starter",expires,null));
        when(stripe.checkout("cus_1","u","price_starter","token",expires,"http://localhost:3000/zh-cn/pricing")).thenReturn(session("cs_restored","open"));
        assertThat(service.checkout("u","starter","fr")).containsEntry("url","https://checkout.stripe.com/session");
        verify(repo).saveCheckout("stripe","u","token","cs_restored","https://checkout.stripe.com/session");
        verify(repo,never()).clearCheckout(anyString(),anyString(),anyString());
    }
    @Test void providerTimeoutPreservesOriginalTokenInsteadOfCreatingAnotherOrder() throws Exception {
        checkoutCustomer();long expires=Instant.now().getEpochSecond()+600;
        when(repo.subscription("u")).thenReturn(pending("starter",expires,null));
        when(stripe.checkout(anyString(),anyString(),anyString(),anyString(),anyLong(),anyString())).thenThrow(new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,"BILLING_PROVIDER_UNAVAILABLE","retry"));
        assertThatThrownBy(()->service.checkout("u","starter","en")).isInstanceOf(ApiException.class);
        verify(repo,never()).clearCheckout(anyString(),anyString(),anyString());
        verify(repo,never()).claimCheckout(anyString(),anyString(),anyString(),anyString(),anyString(),anyLong());
    }
    @Test void changingPlanExpiresOldCheckoutBeforeOpeningNewOne() throws Exception {
        checkoutCustomer();long expires=Instant.now().getEpochSecond()+600;
        java.util.Map<String,Object> old=pending("starter",expires,"cs_old"),fresh=pending("creator",expires+3600,null);
        fresh.put("checkout_token","new-token");
        when(repo.subscription("u")).thenReturn(old,old,map("provider","stripe"),fresh);
        when(stripe.checkoutSession("cs_old")).thenReturn(session("cs_old","open"));
        when(stripe.expireCheckout("cs_old")).thenReturn(session("cs_old","expired"));
        when(stripe.checkout(anyString(),anyString(),eq("price_creator"),eq("new-token"),anyLong(),anyString())).thenReturn(session("cs_new","open"));
        assertThat(service.checkout("u","creator","ja")).containsEntry("url","https://checkout.stripe.com/session");
        org.mockito.InOrder order=inOrder(stripe,repo);
        order.verify(stripe).expireCheckout("cs_old");order.verify(repo).clearCheckout("stripe","u","token");
        order.verify(repo).claimCheckout(eq("stripe"),eq("u"),anyString(),eq("creator"),eq("http://localhost:3000/ja/pricing"),anyLong());
        order.verify(stripe).checkout(anyString(),anyString(),eq("price_creator"),eq("new-token"),anyLong(),anyString());
    }
    @Test void legacyAccountReturnIsExpiredBeforeNewPricingCheckout() throws Exception {
        checkoutCustomer();long expires=Instant.now().getEpochSecond()+600;
        java.util.Map<String,Object> old=pending("starter",expires,"cs_old"),fresh=pending("starter",expires+3600,null);
        old.put("checkout_return_url","https://suno.film/zh-cn/account");fresh.put("checkout_token","new-token");
        when(repo.subscription("u")).thenReturn(old,old,map("provider","stripe"),fresh);
        when(stripe.checkoutSession("cs_old")).thenReturn(session("cs_old","open"));
        when(stripe.expireCheckout("cs_old")).thenReturn(session("cs_old","expired"));
        when(stripe.checkout(anyString(),anyString(),eq("price_starter"),eq("new-token"),anyLong(),anyString())).thenReturn(session("cs_new","open"));
        assertThat(service.checkout("u","starter","zh-CN")).containsKey("url");
        org.mockito.InOrder order=inOrder(stripe,repo);
        order.verify(stripe).expireCheckout("cs_old");order.verify(repo).clearCheckout("stripe","u","token");
        order.verify(repo).claimCheckout(eq("stripe"),eq("u"),anyString(),eq("starter"),eq("http://localhost:3000/zh-cn/pricing"),anyLong());
    }
    @Test void failedExpiryDoesNotReleaseOrReplaceCheckout() throws Exception {
        checkoutCustomer();when(repo.subscription("u")).thenReturn(pending("starter",Instant.now().getEpochSecond()+600,"cs_old"));
        when(stripe.checkoutSession("cs_old")).thenReturn(session("cs_old","open"));
        when(stripe.expireCheckout("cs_old")).thenThrow(new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,"BILLING_PROVIDER_UNAVAILABLE","retry"));
        assertThatThrownBy(()->service.checkout("u","creator","en")).isInstanceOf(ApiException.class);
        verify(repo,never()).clearCheckout(anyString(),anyString(),anyString());
    }
    @Test void expiredValidationWithoutChannelOrderAllowsFreshCheckout() throws Exception {
        checkoutCustomer();long expires=Instant.now().getEpochSecond()+600;
        java.util.Map<String,Object> old=pending("starter",expires,null),fresh=pending("starter",expires+3600,null);fresh.put("checkout_token","new-token");
        when(repo.subscription("u")).thenReturn(old,old,map("provider","stripe"),fresh);
        when(stripe.checkout(anyString(),anyString(),anyString(),eq("token"),anyLong(),anyString())).thenThrow(new StripeGateway.CheckoutExpiryRejectedException());
        when(stripe.checkoutSessions("cus_1",expires-3600)).thenReturn(json.readTree("{\"data\":[],\"has_more\":false}"));
        when(stripe.checkout(anyString(),anyString(),anyString(),eq("new-token"),anyLong(),anyString())).thenReturn(session("cs_new","open"));
        assertThat(service.checkout("u","starter","de")).containsKey("url");
        verify(repo).clearCheckout("stripe","u","token");
    }
    @Test void expiredValidationRecoversLegacyOrderAndDoesNotClearIt() throws Exception {
        checkoutCustomer();long expires=Instant.now().getEpochSecond()+600;
        when(repo.subscription("u")).thenReturn(pending("starter",expires,null));
        when(stripe.checkout(anyString(),anyString(),anyString(),anyString(),anyLong(),anyString())).thenThrow(new StripeGateway.CheckoutExpiryRejectedException());
        JsonNode restored=session("cs_legacy","open");((com.fasterxml.jackson.databind.node.ObjectNode)restored).put("expires_at",expires);
        when(stripe.checkoutSessions("cus_1",expires-3600)).thenReturn(json.createObjectNode().put("has_more",false).set("data",json.createArrayNode().add(restored)));
        assertThat(service.checkout("u","starter","en")).containsKey("url");
        verify(repo,never()).clearCheckout(anyString(),anyString(),anyString());
    }
    @Test void incompleteChannelSearchCannotReleaseUnknownCheckout() throws Exception {
        checkoutCustomer();long expires=Instant.now().getEpochSecond()+600;
        when(repo.subscription("u")).thenReturn(pending("starter",expires,null));
        when(stripe.checkout(anyString(),anyString(),anyString(),anyString(),anyLong(),anyString())).thenThrow(new StripeGateway.CheckoutExpiryRejectedException());
        when(stripe.checkoutSessions(anyString(),anyLong())).thenReturn(json.readTree("{\"data\":[],\"has_more\":true}"));
        assertThatThrownBy(()->service.checkout("u","starter","en")).isInstanceOf(ApiException.class);
        verify(repo,never()).clearCheckout(anyString(),anyString(),anyString());
    }
    @Test void knownExpiredCheckoutIsReplacedAndCompletedRaceRemainsBlocked() throws Exception {
        checkoutCustomer();long expires=Instant.now().getEpochSecond()-60;
        java.util.Map<String,Object> old=pending("starter",expires,"cs_expired"),fresh=pending("starter",expires+3600,null);fresh.put("checkout_token","new-token");
        when(repo.subscription("u")).thenReturn(old,old,map("provider","stripe"),fresh);
        when(stripe.checkoutSession("cs_expired")).thenReturn(session("cs_expired","expired"));
        when(stripe.checkout(anyString(),anyString(),anyString(),eq("new-token"),anyLong(),anyString())).thenReturn(session("cs_new","open"));
        assertThat(service.checkout("u","starter","en")).containsKey("url");
        verify(repo).clearCheckout("stripe","u","token");
        clearInvocations(repo,stripe);when(repo.subscription("u")).thenReturn(old);
        when(stripe.subscriptions("cus_1")).thenReturn(json.readTree("{\"data\":[]}"),json.readTree("{\"data\":[{\"id\":\"sub_new\",\"status\":\"active\"}]}"));
        assertThatThrownBy(()->service.checkout("u","starter","en")).isInstanceOf(ApiException.class);
        verify(repo,never()).clearCheckout(anyString(),anyString(),anyString());
    }
    @Test void completedOrForeignCheckoutCannotBeReplaced() throws Exception {
        checkoutCustomer();when(repo.subscription("u")).thenReturn(pending("starter",Instant.now().getEpochSecond()+600,"cs_old"));
        when(stripe.checkoutSession("cs_old")).thenReturn(session("cs_old","complete"));
        assertThatThrownBy(()->service.checkout("u","starter","en")).isInstanceOf(ApiException.class);
        ((com.fasterxml.jackson.databind.node.ObjectNode)stripe.checkoutSession("cs_old")).put("client_reference_id","other");
        assertThatThrownBy(()->service.checkout("u","creator","en")).isInstanceOf(ApiException.class);
        verify(repo,never()).clearCheckout(anyString(),anyString(),anyString());verify(stripe,never()).expireCheckout(anyString());
    }
    @Test void subscriptionNotificationUsesVerifiedCurrentStateNotStalePayload() throws Exception {
        when(repo.owner("stripe","cus_1")).thenReturn("u");
        when(stripe.subscriptions("cus_1")).thenReturn(json.readTree("{\"data\":[]}"));
        String payload=event("customer.subscription.updated","{\"customer\":\"cus_1\",\"status\":\"active\"}",false);
        service.webhook(payload,sign(payload,Instant.now().getEpochSecond()));
        verify(repo).syncSubscription("stripe","u",null,null,false);
        verify(repo).processed("stripe","evt_1","customer.subscription.updated");
    }
    @Test void creditsUsesOneBalanceRefreshAndDisabledBillingDoesNotQuery() {
        when(repo.refreshBalance(eq("u"),anyLong())).thenReturn(map("remaining",10,"welcome_remaining",10,"reserved",0));
        assertThat(service.credits("u")).containsEntry("remaining",10);
        verify(repo).refreshBalance(eq("u"),anyLong());
        verify(repo,never()).ensureWelcomeCredits(anyString());
        verify(repo,never()).reconcile(anyString());
        verify(repo,never()).balance(anyString(),anyLong());
        clearInvocations(repo);settings.enabled=false;
        assertThat(service.credits("u")).containsEntry("available",false);
        verifyNoInteractions(repo);
    }
    private String event(String type,String object,boolean live) {return "{\"id\":\"evt_1\",\"object\":\"event\",\"livemode\":"+live+",\"type\":\""+type+"\",\"data\":{\"object\":"+object+"}}";}
    private String sign(String body,long timestamp) throws Exception {
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(settings.webhookSecret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        StringBuilder hex=new StringBuilder();for(byte b:mac.doFinal((timestamp+"."+body).getBytes(StandardCharsets.UTF_8)))hex.append(String.format("%02x",b));
        return "t="+timestamp+",v1="+hex;
    }
}
