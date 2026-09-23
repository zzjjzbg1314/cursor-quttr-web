package com.example.cursorquitterweb.musicmv.billing;

import java.util.*;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import com.fasterxml.jackson.databind.*;
import com.stripe.net.Webhook;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import static com.example.cursorquitterweb.musicmv.billing.StripeGateway.map;

@Service
@ConditionalOnProperty(prefix="music-mv",name="enabled",havingValue="true")
public class MusicBillingService {
    private final BillingSettings settings;
    private final BillingRepository repo;
    private final StripeGateway stripe;
    private final ObjectMapper mapper;
    public MusicBillingService(BillingSettings settings,BillingRepository repo,StripeGateway stripe,ObjectMapper mapper) {
        this.settings=settings;this.repo=repo;this.stripe=stripe;this.mapper=mapper;
    }
    public Map<String,Object> capabilities() { return map("available",settings.ready(),"sandbox",true); }
    public Map<String,Object> status(String user) {
        if(!settings.ready()) return map("available",false,"sandbox",true);
        repo.reconcile(user);
        Map<String,Object> balance=repo.balance(user,Instant.now().getEpochSecond());
        Map<String,Object> result=map("available",true,"sandbox",true,"remaining",balance==null?0:balance.get("remaining"),"periodEnd",balance==null?null:balance.get("period_end"));
        Map<String,Object> customer=repo.customer(user);
        result.put("canManage",customer!=null);
        if(customer!=null) result.put("subscription",activeSubscription(stripe.subscriptions(text(customer,"customer_id"))));
        return result;
    }
    public Map<String,Object> checkout(String user,String plan,String locale) {
        settings.requireReady();
        String price=settings.price(plan),url=settings.returnUrl(locale);
        Map<String,Object> row=repo.customer(user);
        if(row==null) { repo.saveCustomer(user,stripe.customer(user).path("id").asText()); row=repo.customer(user); }
        String customer=text(row,"customer_id");
        if(activeSubscription(stripe.subscriptions(customer))!=null) throw error(HttpStatus.CONFLICT,"BILLING_ALREADY_SUBSCRIBED","Manage your existing subscription first.");
        long now=Instant.now().getEpochSecond();
        repo.claimCheckout(user,UUID.randomUUID().toString(),plan,url,now);
        row=repo.customer(user);
        if(!plan.equals(text(row,"checkout_plan"))) throw error(HttpStatus.CONFLICT,"BILLING_CHECKOUT_PENDING","A checkout for another plan is still open. Please finish it or wait for it to expire.");
        if(row.get("checkout_url")!=null) return map("url",row.get("checkout_url"));
        long expires=((Number)row.get("checkout_expires")).longValue();
        if(expires-now<1800) throw error(HttpStatus.CONFLICT,"BILLING_CHECKOUT_PENDING","Please wait for the pending checkout to expire.");
        // 重试使用已持久化的同一幂等键与参数，不重复创建订阅。
        JsonNode checkout=stripe.checkout(customer,user,price,text(row,"checkout_token"),expires,text(row,"checkout_return_url"));
        repo.saveCheckout(user,text(row,"checkout_token"),checkout.path("id").asText(),checkout.path("url").asText());
        return map("url",checkout.path("url").asText());
    }
    public Map<String,Object> portal(String user,String locale) {
        settings.requireReady(); Map<String,Object> row=repo.customer(user);
        if(row==null) throw error(HttpStatus.NOT_FOUND,"BILLING_CUSTOMER_MISSING","No billing account yet.");
        return map("url",stripe.portal(text(row,"customer_id"),settings.returnUrl(locale)).path("url").asText());
    }
    public void reserve(String user,String request,String job) {
        if(!settings.enabled) return;
        settings.requireReady(); repo.reconcile(user);
        if(repo.hasReservation(user,request)) throw error(HttpStatus.CONFLICT,"BILLING_GENERATION_PENDING","This generation is already being processed.");
        if(!repo.reserve(user,request,job,Instant.now().getEpochSecond())) throw error(HttpStatus.PAYMENT_REQUIRED,"BILLING_QUOTA_EXHAUSTED","No music generations remaining. Choose a plan or wait for renewal.");
    }
    public void settle(String job,String status) {
        if(!settings.enabled) return;
        if("completed".equals(status)) repo.settle(job,"consumed");
        if("failed".equals(status)) repo.settle(job,"released");
    }
    public void webhook(String payload,String signature) {
        settings.requireReady();
        JsonNode event;
        try {
            Webhook.constructEvent(payload,signature,settings.webhookSecret);
            event=mapper.readTree(payload);
        } catch(Exception e) { throw error(HttpStatus.BAD_REQUEST,"BILLING_SIGNATURE_INVALID","Invalid payment notification."); }
        if(!event.has("livemode") || event.path("livemode").asBoolean(true)) throw error(HttpStatus.BAD_REQUEST,"BILLING_MODE_INVALID","Live events are not accepted.");
        String id=event.path("id").asText(),type=event.path("type").asText();
        if(id.isEmpty()) throw error(HttpStatus.BAD_REQUEST,"BILLING_EVENT_INVALID","Missing event identifier.");
        if(repo.processed(id)) return;
        JsonNode object=event.path("data").path("object");
        if("invoice.paid".equals(type)) grantInvoice(stripe.invoice(object.path("id").asText()));
        else if("credit_note.created".equals(type)) repo.revoke(object.path("invoice").asText());
        else if("charge.refunded".equals(type) || "charge.dispute.created".equals(type)) {
            JsonNode charge="charge.refunded".equals(type)?object:stripe.charge(object.path("charge").asText());
            String payment=charge.path("payment_intent").asText();
            if(!payment.isEmpty()) {
                JsonNode payments=stripe.invoicePayments(payment);
                if(payments.path("has_more").asBoolean()) throw new IllegalStateException("Invoice payment pagination requires reconciliation");
                for(JsonNode p:payments.path("data")) repo.revoke(p.path("invoice").asText());
            }
        }
        // 发放与撤回本身幂等；中途失败返回非成功，由 Stripe 重试。
        repo.processed(id,type);
    }
    private void grantInvoice(JsonNode invoice) {
        if(invoice.path("livemode").asBoolean(true) || !"paid".equals(invoice.path("status").asText())) throw new IllegalStateException("Unexpected invoice state");
        String reason=invoice.path("billing_reason").asText();
        if(!"subscription_create".equals(reason) && !"subscription_cycle".equals(reason)) return;
        String user=repo.owner(invoice.path("customer").asText());
        if(user==null) throw new IllegalStateException("Unmapped billing customer");
        if(invoice.path("lines").path("has_more").asBoolean()) throw new IllegalStateException("Unsupported invoice lines");
        for(JsonNode line:invoice.path("lines").path("data")) {
            String price=line.path("pricing").path("price_details").path("price").asText();
            int allowance=settings.allowance(price); if(allowance==0) continue;
            JsonNode details=line.path("parent").path("subscription_item_details");
            if(details.path("proration").asBoolean() || line.path("quantity").asInt()!=1) continue;
            String sub=details.path("subscription").asText();
            if(sub.isEmpty()) sub=invoice.path("parent").path("subscription_details").path("subscription").asText();
            long start=line.path("period").path("start").asLong(),end=line.path("period").path("end").asLong();
            if(sub.isEmpty() || start<=0 || end<=start) throw new IllegalStateException("Invalid invoice allowance period");
            repo.grant(invoice.path("id").asText(),user,sub,price.equals(settings.starterPrice)?"starter":"creator",allowance,start,end);
            return;
        }
    }
    private Map<String,Object> activeSubscription(JsonNode list) {
        if(list.path("has_more").asBoolean()) throw error(HttpStatus.CONFLICT,"BILLING_REVIEW_REQUIRED","Please contact support to review your subscriptions.");
        for(JsonNode sub:list.path("data")) {
            String status=sub.path("status").asText();
            if(!"canceled".equals(status) && !"incomplete_expired".equals(status))
                return map("status",status,"cancelAtPeriodEnd",sub.path("cancel_at_period_end").asBoolean());
        }
        return null;
    }
    private String text(Map<String,Object> row,String key) { Object v=row.get(key);return v==null?"":v.toString(); }
    private ApiException error(HttpStatus status,String code,String message) {return new ApiException(status,code,message);}
}
