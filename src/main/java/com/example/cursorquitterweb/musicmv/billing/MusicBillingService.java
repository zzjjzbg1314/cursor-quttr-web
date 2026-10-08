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
    private static final String PROVIDER = "stripe";
    private final BillingSettings settings;
    private final BillingRepository repo;
    private final StripeGateway stripe;
    private final ObjectMapper mapper;
    public MusicBillingService(BillingSettings settings,BillingRepository repo,StripeGateway stripe,ObjectMapper mapper) {
        this.settings=settings;this.repo=repo;this.stripe=stripe;this.mapper=mapper;
    }
    public Map<String,Object> capabilities() { return map("available",settings.enabled,"checkoutAvailable",settings.ready(),"sandbox",!settings.live(),"enabled",settings.enabled,"generationCredits",BillingSettings.SONG_CREDITS); }
    public Map<String,Object> credits(String user) {
        if(!settings.enabled) return capabilities();
        Map<String,Object> balance=repo.refreshBalance(user,Instant.now().getEpochSecond());
        Map<String,Object> result=new LinkedHashMap<>(capabilities());
        result.put("welcomeNotice",null);
        result.put("unusedWelcomeCredits",balance!=null && balance.get("unused_welcome_id")!=null ? map("id",balance.get("unused_welcome_id"),"credits",balance.get("welcome_remaining")) : null);
        result.put("welcomeRemaining",balance==null?0:balance.get("welcome_remaining"));
        result.put("remaining",balance==null?0:balance.get("remaining"));
        result.put("allowance",balance==null?0:balance.get("allowance"));
        result.put("reserved",balance==null?0:balance.get("reserved"));
        result.put("periodEnd",balance==null?null:balance.get("period_end"));
        return result;
    }
    public Map<String,Object> acknowledgeWelcomeNotice(String user,String id) {
        // 兼容旧前端的确认请求；提醒按登录会话去重，不读写数据库。
        return map("acknowledged",true);
    }
    public Map<String,Object> history(String user,int offset) { return history(user,offset,"all"); }
    public Map<String,Object> history(String user,int offset,String kind) {
        if(!Arrays.asList("all","welcome","subscription","reserved","consumed","released","revoked").contains(kind)) throw error(HttpStatus.BAD_REQUEST,"BILLING_HISTORY_INVALID","Invalid filter.");
        if(offset<0 || offset>100000) throw error(HttpStatus.BAD_REQUEST,"BILLING_HISTORY_INVALID","Invalid page.");
        if(!settings.enabled) return map("items",Collections.emptyList(),"hasMore",false);
        List<Map<String,Object>> rows=repo.history(user,21,offset,kind);
        return map("items",rows.subList(0,Math.min(20,rows.size())),"hasMore",rows.size()>20);
    }
    public Map<String,Object> usage(String user) {
        Map<String,Object> result=new LinkedHashMap<>(credits(user));
        if(settings.enabled) { result.put("totals",repo.usage(user));result.put("generations",repo.generationSpend(user)); }
        return result;
    }
    public Map<String,Object> status(String user) { return status(user,null); }
    public Map<String,Object> status(String user,String checkoutId) {
        Map<String,Object> result=credits(user);
        if(!settings.ready()) {
            if(checkoutId!=null && !checkoutId.isEmpty()) settings.requireReady();
            return result;
        }
        Map<String,Object> customer=repo.customer(PROVIDER,user);
        result.put("canManage",customer!=null);
        Map<String,Object> currentSubscription=customer==null?null:refreshSubscription(user,text(customer,"customer_id"));
        String latestInvoice=currentSubscription==null?"":text(currentSubscription,"_latestInvoice");
        if(currentSubscription!=null) {
            currentSubscription.remove("_latestInvoice");
            result.put("subscription",currentSubscription);
            if(!latestInvoice.isEmpty()) {
                if(reconcileSubscriptionInvoice(user,text(customer,"customer_id"),text(currentSubscription,"id"),latestInvoice))
                    result.putAll(credits(user));
            }
        }
        // 日常刷新直接核对订阅最新发票，不再依赖首购收银台仍可读取。
        if((checkoutId==null || checkoutId.isEmpty()) && !latestInvoice.isEmpty()) return result;
        if(checkoutId==null || checkoutId.isEmpty()) {
            Map<String,Object> saved=repo.subscription(user);
            if(saved!=null && PROVIDER.equals(text(saved,"provider"))) checkoutId=text(saved,"checkout_id");
        }
        if(checkoutId==null || checkoutId.isEmpty()) return result;
        if(!checkoutId.matches("cs_[A-Za-z0-9_]{1,240}")) throw error(HttpStatus.BAD_REQUEST,"BILLING_CHECKOUT_INVALID","Invalid checkout.");
        JsonNode session=stripe.checkoutSession(checkoutId);
        if(customer==null || !user.equals(session.path("client_reference_id").asText()) || !text(customer,"customer_id").equals(session.path("customer").asText()) || !session.has("livemode") || session.path("livemode").asBoolean()!=settings.live())
            throw error(HttpStatus.NOT_FOUND,"BILLING_CHECKOUT_INVALID","Checkout not found.");
        String invoiceId=session.path("invoice").asText("");
        boolean paid="complete".equals(session.path("status").asText()) && "subscription".equals(session.path("mode").asText())
                && "paid".equals(session.path("payment_status").asText()) && !invoiceId.isEmpty();
        if(paid && !repo.paymentGranted(user,invoiceId)) {
            JsonNode invoice=stripe.invoice(invoiceId);
            String subscription=session.path("subscription").asText("");
            if(!invoiceId.equals(invoice.path("id").asText()) || !text(customer,"customer_id").equals(invoice.path("customer").asText())
                    || !user.equals(repo.owner(PROVIDER,invoice.path("customer").asText())) || subscription.isEmpty()
                    || !subscription.equals(invoice.path("parent").path("subscription_details").path("subscription").asText()))
                throw error(HttpStatus.CONFLICT,"BILLING_REVIEW_REQUIRED","Payment ownership could not be verified.");
            // 仅使用渠道核实的已支付发票补偿，复用发票唯一键防止与回调重复发放。
            grantInvoice(invoice);
            result.putAll(credits(user));
        }
        result.put("paymentConfirmed",paid && repo.paymentGranted(user,invoiceId));
        result.put("paymentRevoked",paid && repo.paymentRevoked(invoiceId));
        return result;
    }
    public Map<String,Object> checkout(String user,String plan,String locale) {
        settings.requireReady();
        settings.price(plan);
        String url=settings.returnUrl(locale);
        requireStripeOwner(user);
        Map<String,Object> row=repo.customer(PROVIDER,user);
        if(row==null) { repo.saveCustomer(PROVIDER,user,stripe.customer(user).path("id").asText()); row=repo.customer(PROVIDER,user); }
        String customer=text(row,"customer_id");
        if(refreshSubscription(user,customer)!=null) throw error(HttpStatus.CONFLICT,"BILLING_ALREADY_SUBSCRIBED","Manage your existing subscription first.");
        for(int attempt=0;attempt<3;attempt++) {
            row=repo.subscription(user);
            if(row==null || text(row,"checkout_token").isEmpty() || (!PROVIDER.equals(text(row,"provider"))
                    && row.get("checkout_expires") instanceof Number && ((Number)row.get("checkout_expires")).longValue()<Instant.now().getEpochSecond())) {
                repo.claimCheckout(PROVIDER,user,UUID.randomUUID().toString(),plan,url,Instant.now().getEpochSecond());
                row=repo.subscription(user);
            }
            if(row==null || !PROVIDER.equals(text(row,"provider")) || row.get("subscription_id")!=null)
                throw error(HttpStatus.CONFLICT,"BILLING_ALREADY_SUBSCRIBED","Manage your existing subscription first.");
            String token=text(row,"checkout_token");
            JsonNode checkout=restoreCheckout(user,customer,row);
            if(checkout!=null) {
                verifyCheckoutOwner(user,customer,checkout);
                String state=checkout.path("status").asText();
                if("complete".equals(state)) {
                    if(!checkoutSubscriptionEnded(customer,checkout) || refreshSubscription(user,customer)!=null)
                        throw error(HttpStatus.CONFLICT,"BILLING_ALREADY_SUBSCRIBED","Manage your existing subscription first.");
                    // 已支付订单不可重用；仅在原订阅确已结束且无其他订阅时释放旧占用。
                    repo.clearCheckout(PROVIDER,user,token);
                    continue;
                }
                if("open".equals(state) && plan.equals(text(row,"checkout_plan")) && !legacyCheckoutReturn(row)) {
                    String checkoutUrl=checkout.path("url").asText("");
                    if(checkoutUrl.isEmpty()) throw error(HttpStatus.BAD_GATEWAY,"BILLING_PROVIDER_UNAVAILABLE","Checkout URL is unavailable.");
                    repo.saveCheckout(PROVIDER,user,token,checkout.path("id").asText(),checkoutUrl);
                    return map("url",checkoutUrl);
                }
                if("open".equals(state)) {
                    // 切换套餐或迁移旧返回路径前先关闭原收银台，避免同时存在两笔可支付订单。
                    checkout=stripe.expireCheckout(checkout.path("id").asText());
                    verifyCheckoutOwner(user,customer,checkout);
                    state=checkout.path("status").asText();
                }
                if(!"expired".equals(state)) throw error(HttpStatus.CONFLICT,"BILLING_CHECKOUT_RETRY","Checkout is still being confirmed. Please retry.");
            }
            if(refreshSubscription(user,customer)!=null)
                throw error(HttpStatus.CONFLICT,"BILLING_ALREADY_SUBSCRIBED","Manage your existing subscription first.");
            repo.clearCheckout(PROVIDER,user,token);
        }
        throw error(HttpStatus.CONFLICT,"BILLING_CHECKOUT_RETRY","Checkout changed during this request. Please retry.");
    }
    private boolean checkoutSubscriptionEnded(String customer,JsonNode checkout) {
        String id=checkout.path("subscription").asText("");
        if(id.isEmpty()) return false;
        JsonNode subscription=stripe.subscription(id);
        if(!id.equals(subscription.path("id").asText()) || !customer.equals(subscription.path("customer").asText())
                || !subscription.has("livemode") || subscription.path("livemode").asBoolean()!=settings.live())
            throw error(HttpStatus.CONFLICT,"BILLING_REVIEW_REQUIRED","Previous subscription ownership could not be verified.");
        String status=subscription.path("status").asText();
        return "canceled".equals(status) || "incomplete_expired".equals(status);
    }
    private JsonNode restoreCheckout(String user,String customer,Map<String,Object> row) {
        if(!text(row,"checkout_id").isEmpty()) return stripe.checkoutSession(text(row,"checkout_id"));
        long expires=((Number)row.get("checkout_expires")).longValue();
        try {
            // 即使剩余不足半小时，也重放原参数与幂等键，恢复已创建但尚未保存的收银台。
            return stripe.checkout(customer,user,settings.price(text(row,"checkout_plan")),text(row,"checkout_token"),expires,text(row,"checkout_return_url"));
        } catch(StripeGateway.CheckoutExpiryRejectedException e) {
            // Stripe 明确拒绝旧到期参数后，仍查找渠道订单，兼容旧数据并避免误清除成功订单。
            JsonNode sessions=stripe.checkoutSessions(customer,Math.max(0,expires-3600));
            if(!sessions.path("data").isArray() || !sessions.has("has_more") || sessions.path("has_more").asBoolean()) throw error(HttpStatus.CONFLICT,"BILLING_REVIEW_REQUIRED","Please contact support to review your checkouts.");
            JsonNode found=null;
            for(JsonNode session:sessions.path("data")) {
                if(user.equals(session.path("client_reference_id").asText()) && session.path("expires_at").asLong()==expires) {
                    if(found!=null) throw error(HttpStatus.CONFLICT,"BILLING_REVIEW_REQUIRED","Please contact support to review your checkouts.");
                    found=session;
                }
            }
            return found;
        }
    }
    private boolean legacyCheckoutReturn(Map<String,Object> row) {
        String path=java.net.URI.create(text(row,"checkout_return_url")).getPath();
        return path!=null && path.endsWith("/account");
    }
    private void verifyCheckoutOwner(String user,String customer,JsonNode session) {
        if(!user.equals(session.path("client_reference_id").asText()) || !customer.equals(session.path("customer").asText())
                || !session.has("livemode") || session.path("livemode").asBoolean()!=settings.live() || !"subscription".equals(session.path("mode").asText()))
            throw error(HttpStatus.CONFLICT,"BILLING_REVIEW_REQUIRED","Please contact support to review your checkout.");
    }
    public Map<String,Object> portal(String user,String locale) {
        settings.requireReady(); requireStripeOwner(user); Map<String,Object> row=repo.customer(PROVIDER,user);
        if(row==null) throw error(HttpStatus.NOT_FOUND,"BILLING_CUSTOMER_MISSING","No billing account yet.");
        String url=stripe.portal(text(row,"customer_id"),settings.returnUrl(locale)).path("url").asText();
        try {
            java.net.URI uri=java.net.URI.create(url);
            if(!"https".equals(uri.getScheme()) || !"billing.stripe.com".equals(uri.getHost()) || uri.getUserInfo()!=null)
                throw new IllegalArgumentException();
        } catch(IllegalArgumentException e) {
            throw error(HttpStatus.BAD_GATEWAY,"BILLING_PROVIDER_UNAVAILABLE","Subscription management is unavailable.");
        }
        return map("url",url);
    }
    public void reserve(String user,String request,String job) {
        if(!settings.enabled) return;
        repo.ensureWelcomeCredits(user); repo.reconcile(user);
        if(repo.hasReservation(user,request)) throw error(HttpStatus.CONFLICT,"BILLING_GENERATION_PENDING","This generation is already being processed.");
        if(!repo.reserve(user,request,job,Instant.now().getEpochSecond(),BillingSettings.SONG_CREDITS)) throw error(HttpStatus.PAYMENT_REQUIRED,"BILLING_QUOTA_EXHAUSTED","Not enough credits. Choose a plan or wait for renewal.");
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
        if(!event.has("livemode") || event.path("livemode").asBoolean()!=settings.live()) throw error(HttpStatus.BAD_REQUEST,"BILLING_MODE_INVALID","Payment environment mismatch.");
        String id=event.path("id").asText(),type=event.path("type").asText();
        if(id.isEmpty()) throw error(HttpStatus.BAD_REQUEST,"BILLING_EVENT_INVALID","Missing event identifier.");
        if(repo.processed(PROVIDER,id)) return;
        JsonNode object=event.path("data").path("object");
        if("invoice.paid".equals(type)) {
            String invoiceId=requiredIdentity(object,"id");
            JsonNode invoice=stripe.invoice(invoiceId);
            if(!invoiceId.equals(invoice.path("id").asText())) throw new IllegalStateException("Invoice identity mismatch");
            grantInvoice(invoice);
        }
        else if("customer.subscription.created".equals(type) || "customer.subscription.updated".equals(type) || "customer.subscription.deleted".equals(type)) {
            String customer=object.path("customer").asText();
            String user=repo.owner(PROVIDER,customer);
            if(user==null) throw new IllegalStateException("Unmapped billing customer");
            // 回调可能乱序，重新查询渠道当前状态，不直接应用旧通知中的状态。
            refreshSubscription(user,customer);
        }
        else if("credit_note.created".equals(type)) repo.revoke(PROVIDER,requiredIdentity(object,"invoice"));
        else if("charge.refunded".equals(type) || "charge.dispute.created".equals(type)) {
            boolean dispute="charge.dispute.created".equals(type);
            String chargeId=dispute?requiredIdentity(object,"charge"):null;
            JsonNode charge=dispute?stripe.charge(chargeId):object;
            if(dispute && (!chargeId.equals(charge.path("id").asText()) || !charge.path("livemode").isBoolean()
                    || charge.path("livemode").asBoolean()!=settings.live())) throw new IllegalStateException("Charge identity or environment mismatch");
            String payment=requiredIdentity(charge,"payment_intent");
            {
                JsonNode payments=stripe.invoicePayments(payment);
                if(!payments.path("data").isArray() || !payments.path("has_more").isBoolean() || payments.path("has_more").asBoolean()) throw new IllegalStateException("Incomplete invoice payment list");
                // 先核对整批关联，再写撤销，避免坏记录导致部分执行却被误标完成。
                for(JsonNode p:payments.path("data")) requiredIdentity(p,"invoice");
                for(JsonNode p:payments.path("data")) repo.revoke(PROVIDER,p.path("invoice").asText());
            }
        }
        // 发放与撤回本身幂等；中途失败返回非成功，由 Stripe 重试。
        repo.processed(PROVIDER,id,type);
    }
    private boolean reconcileSubscriptionInvoice(String user,String customer,String subscription,String invoiceId) {
        if(repo.paymentGranted(user,invoiceId)) return false;
        JsonNode invoice=stripe.invoice(invoiceId);
        if(!invoiceId.equals(invoice.path("id").asText()) || !customer.equals(invoice.path("customer").asText())
                || !user.equals(repo.owner(PROVIDER,customer)) || !invoice.has("livemode") || invoice.path("livemode").asBoolean()!=settings.live()
                || !subscription.equals(invoice.path("parent").path("subscription_details").path("subscription").asText()))
            throw error(HttpStatus.CONFLICT,"BILLING_REVIEW_REQUIRED","Subscription invoice ownership could not be verified.");
        // 未付款或差价发票不授予月度额度；迟到回调与刷新共用发票唯一键。
        String reason=invoice.path("billing_reason").asText();
        if(!"paid".equals(invoice.path("status").asText()) || (!"subscription_create".equals(reason) && !"subscription_cycle".equals(reason))) return false;
        grantInvoice(invoice,false);
        return true;
    }
    private void grantInvoice(JsonNode invoice) { grantInvoice(invoice,true); }
    private void grantInvoice(JsonNode invoice,boolean refresh) {
        if(!invoice.has("livemode") || invoice.path("livemode").asBoolean()!=settings.live() || !"paid".equals(invoice.path("status").asText())) throw new IllegalStateException("Unexpected invoice state");
        String reason=invoice.path("billing_reason").asText();
        if(!"subscription_create".equals(reason) && !"subscription_cycle".equals(reason)) return;
        String user=repo.owner(PROVIDER,invoice.path("customer").asText());
        if(user==null) throw new IllegalStateException("Unmapped billing customer");
        if(refresh) refreshSubscription(user,invoice.path("customer").asText());
        if(invoice.path("lines").path("has_more").asBoolean()) throw new IllegalStateException("Unsupported invoice lines");
        JsonNode allowanceLine=null;
        for(JsonNode line:invoice.path("lines").path("data")) {
            JsonNode details=line.path("parent").path("subscription_item_details");
            if(details.path("proration").asBoolean()) continue;
            String price=line.path("pricing").path("price_details").path("price").asText();
            if(settings.allowance(price)==0 || !line.path("quantity").isIntegralNumber() || !line.path("quantity").canConvertToLong() || line.path("quantity").asLong()!=1 || allowanceLine!=null)
                throw error(HttpStatus.BAD_GATEWAY,"BILLING_REVIEW_REQUIRED","Invoice allowance requires review.");
            allowanceLine=line;
        }
        if(allowanceLine==null) throw error(HttpStatus.BAD_GATEWAY,"BILLING_REVIEW_REQUIRED","Invoice allowance is missing.");
        String price=allowanceLine.path("pricing").path("price_details").path("price").asText();
        String sub=allowanceLine.path("parent").path("subscription_item_details").path("subscription").asText();
        String invoiceSub=invoice.path("parent").path("subscription_details").path("subscription").asText();
        if(sub.isEmpty()) sub=invoiceSub;
        long start=positiveInteger(allowanceLine.path("period").path("start")),end=positiveInteger(allowanceLine.path("period").path("end"));
        if(sub.isEmpty() || (!invoiceSub.isEmpty() && !sub.equals(invoiceSub)) || start<=0 || end<=start)
            throw error(HttpStatus.BAD_GATEWAY,"BILLING_REVIEW_REQUIRED","Invalid invoice allowance period.");
        repo.grant(PROVIDER,invoice.path("id").asText(),user,sub,price.equals(settings.starterPrice)?"starter":"creator",settings.allowance(price),start,end);
    }
    private void requireStripeOwner(String user) {
        Map<String,Object> current=repo.subscription(user);
        if(current!=null && !PROVIDER.equals(text(current,"provider"))) {
            boolean pending=current.get("checkout_expires") instanceof Number && ((Number)current.get("checkout_expires")).longValue()>Instant.now().getEpochSecond();
            if(current.get("subscription_id")!=null || pending) throw error(HttpStatus.CONFLICT,"BILLING_ALREADY_SUBSCRIBED","Manage your existing subscription first.");
        }
    }
    private Map<String,Object> refreshSubscription(String user,String customer) {
        String token=UUID.randomUUID().toString();
        repo.beginSubscriptionRead(user,token);
        Map<String,Object> active=activeSubscription(stripe.subscriptions(customer));
        if(!repo.syncSubscriptionRead(PROVIDER,user,active==null?null:String.valueOf(active.get("id")),active==null?null:String.valueOf(active.get("status")),active!=null && Boolean.TRUE.equals(active.get("cancelAtPeriodEnd")),token))
            throw error(HttpStatus.CONFLICT,"BILLING_CHECKOUT_RETRY","Subscription changed during this request. Please refresh.");
        return active;
    }
    private Map<String,Object> activeSubscription(JsonNode list) {
        if(!list.path("data").isArray() || list.path("has_more").asBoolean()) throw error(HttpStatus.CONFLICT,"BILLING_REVIEW_REQUIRED","Subscription status could not be verified.");
        Map<String,Object> active=null;
        for(JsonNode sub:list.path("data")) {
            String status=sub.path("status").asText();
            if(!"canceled".equals(status) && !"incomplete_expired".equals(status)) {
                JsonNode cancelNode=sub.path("cancel_at");
                Long cancelAt=cancelNode.isIntegralNumber() && cancelNode.canConvertToLong() && cancelNode.asLong()>0 ? cancelNode.asLong() : null;
                // 门户也可能只设置明确取消时间，不能仅凭账期末取消标记判断续费状态。
                boolean scheduledCancellation=sub.path("cancel_at_period_end").asBoolean() || cancelAt!=null;
                if(active!=null || sub.path("id").asText().isEmpty() || !Arrays.asList("active","trialing","past_due","unpaid","incomplete","paused").contains(status))
                    throw error(HttpStatus.CONFLICT,"BILLING_REVIEW_REQUIRED","Subscription status is ambiguous.");
                JsonNode items=sub.path("items");
                if(!items.path("data").isArray() || items.path("has_more").asBoolean() || items.path("data").size()!=1 || planKey(sub)==null)
                    throw error(HttpStatus.CONFLICT,"BILLING_REVIEW_REQUIRED","Subscription items are ambiguous.");
                active=map("id",sub.path("id").asText(),"status",status,"cancelAtPeriodEnd",scheduledCancellation,"cancelAt",cancelAt,"plan",planKey(sub),"periodEnd",subscriptionPeriodEnd(sub),"_latestInvoice",sub.path("latest_invoice").asText(""));
            }
        }
        return active;
    }
    private Long subscriptionPeriodEnd(JsonNode sub) {
        for(JsonNode item:sub.path("items").path("data")) {
            if(settings.allowance(item.path("price").path("id").asText())>0) {
                JsonNode end=item.path("current_period_end");
                return end.isIntegralNumber() && end.canConvertToLong() && end.asLong()>0?end.asLong():null;
            }
        }
        return null;
    }
    private String planKey(JsonNode sub) {
        for(JsonNode item:sub.path("items").path("data")) {
            String price=item.path("price").path("id").asText();
            if(price.equals(settings.starterPrice)) return "starter";
            if(price.equals(settings.creatorPrice)) return "creator";
        }
        return null;
    }
    private String text(Map<String,Object> row,String key) { Object v=row.get(key);return v==null?"":v.toString(); }
    private String requiredIdentity(JsonNode object,String key) {
        JsonNode value=object.path(key);
        if(!value.isTextual() || value.asText().trim().isEmpty()) throw new IllegalStateException("Missing billing association");
        return value.asText();
    }
    private long positiveInteger(JsonNode value) {
        if(!value.isIntegralNumber() || !value.canConvertToLong() || value.asLong()<=0) throw new IllegalStateException("Invalid billing period");
        return value.asLong();
    }
    private ApiException error(HttpStatus status,String code,String message) {return new ApiException(status,code,message);}
}
