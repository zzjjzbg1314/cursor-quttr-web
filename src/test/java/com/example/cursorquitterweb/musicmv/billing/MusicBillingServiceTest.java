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
    @Test void productionKeysAndIncompleteConfigurationRemainDisabled() {
        assertThat(settings.ready()).isTrue();settings.secretKey="sk_live_fixture";assertThat(settings.ready()).isFalse();
        assertThatThrownBy(()->service.reserve("u","r","j")).isInstanceOf(ApiException.class);verifyNoInteractions(repo,stripe);
    }
    @Test void reserveFailsClosedAndUnknownRequestsAreNotReleased() {
        when(repo.reserve(eq("u"),eq("r"),eq("j"),anyLong())).thenReturn(false);
        assertThatThrownBy(()->service.reserve("u","r","j")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getStatus().value()).isEqualTo(402));
        service.settle("j","submission_unknown");verify(repo,never()).settle(anyString(),anyString());
        service.settle("j","failed");verify(repo).settle("j","released");
    }
    @Test void disabledBillingDoesNotTouchUnmigratedTables() {
        settings.enabled=false;service.reserve("u","r","j");service.settle("j","completed");service.status("u");verifyNoInteractions(repo,stripe);
    }
    @Test void paidInvoiceGrantsFromServerPriceAndDeduplicatesEvents() throws Exception {
        JsonNode invoice=json.readTree("{\"id\":\"in_1\",\"livemode\":false,\"status\":\"paid\",\"billing_reason\":\"subscription_cycle\",\"customer\":\"cus_1\",\"lines\":{\"data\":[{\"quantity\":1,\"pricing\":{\"price_details\":{\"price\":\"price_starter\"}},\"parent\":{\"subscription_item_details\":{\"subscription\":\"sub_1\",\"proration\":false}},\"period\":{\"start\":100,\"end\":200}}]}}");
        when(stripe.invoice("in_1")).thenReturn(invoice);when(repo.owner("cus_1")).thenReturn("u");
        String event=event("invoice.paid","{\"id\":\"in_1\"}",false);service.webhook(event,sign(event,Instant.now().getEpochSecond()));
        verify(repo).grant("in_1","u","sub_1","starter",30,100,200);
        when(repo.processed("evt_1")).thenReturn(true);service.webhook(event,sign(event,Instant.now().getEpochSecond()));verify(stripe,times(1)).invoice("in_1");
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
        verify(repo,never()).processed(anyString(),anyString());
    }
    @Test void existingSubscriptionCannotPurchaseAnother() throws Exception {
        when(repo.customer("u")).thenReturn(map("customer_id","cus_1"));
        when(stripe.subscriptions("cus_1")).thenReturn(json.readTree("{\"data\":[{\"status\":\"past_due\"}]}"));
        assertThatThrownBy(()->service.checkout("u","starter","en")).isInstanceOf(ApiException.class);
        verify(repo,never()).claimCheckout(anyString(),anyString(),anyString(),anyString(),anyLong());
    }
    @Test void creditNotesRecordRevocationBeforeEventAcknowledgement() throws Exception {
        String event=event("credit_note.created","{\"invoice\":\"in_1\"}",false);
        service.webhook(event,sign(event,Instant.now().getEpochSecond()));
        org.mockito.InOrder order=inOrder(repo);order.verify(repo).processed("evt_1");order.verify(repo).revoke("in_1");order.verify(repo).processed("evt_1","credit_note.created");
    }
    private String event(String type,String object,boolean live) {return "{\"id\":\"evt_1\",\"object\":\"event\",\"livemode\":"+live+",\"type\":\""+type+"\",\"data\":{\"object\":"+object+"}}";}
    private String sign(String body,long timestamp) throws Exception {
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(settings.webhookSecret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        StringBuilder hex=new StringBuilder();for(byte b:mac.doFinal((timestamp+"."+body).getBytes(StandardCharsets.UTF_8)))hex.append(String.format("%02x",b));
        return "t="+timestamp+",v1="+hex;
    }
}
