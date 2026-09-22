package com.example.cursorquitterweb.musicmv.billing;

import java.util.*;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.*;
import com.stripe.model.*;
import com.stripe.net.*;
import com.stripe.exception.StripeException;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import org.springframework.http.HttpStatus;

@Service
public class StripeGateway {
    private final BillingSettings settings;
    private final ObjectMapper mapper;
    public StripeGateway(BillingSettings settings,ObjectMapper mapper) { this.settings=settings; this.mapper=mapper; }
    private RequestOptions options(String key) {
        settings.requireReady();
        return RequestOptions.builder().setApiKey(settings.secretKey).setIdempotencyKey(key)
            .setConnectTimeout(10000).setReadTimeout(20000).setMaxNetworkRetries(1).build();
    }
    public JsonNode customer(String user) {
        return call(() -> Customer.create(map("metadata",map("sunofilm_user_id",user)),options("sunofilm-customer-"+user)));
    }
    public JsonNode subscriptions(String customer) {
        return call(() -> Subscription.list(map("customer",customer,"status","all","limit",100),options(null)));
    }
    public JsonNode checkout(String customer,String user,String price,String token,long expires,String url) {
        return call(() -> com.stripe.model.checkout.Session.create(map("mode","subscription","customer",customer,
            "client_reference_id",user,"line_items",Collections.singletonList(map("price",price,"quantity",1)),
            "subscription_data",map("metadata",map("sunofilm_user_id",user),"billing_mode",map("type","flexible")),
            "success_url",url+"?billing=return","cancel_url",url,"expires_at",expires),options("sunofilm-checkout-"+token)));
    }
    public JsonNode portal(String customer,String url) {
        return call(() -> com.stripe.model.billingportal.Session.create(map("customer",customer,"return_url",url,
            "configuration",settings.portalConfiguration),options(null)));
    }
    public JsonNode invoice(String id) { return call(() -> Invoice.retrieve(id,options(null))); }
    public JsonNode charge(String id) { return call(() -> Charge.retrieve(id,options(null))); }
    public JsonNode invoicePayments(String paymentIntent) {
        return call(() -> InvoicePayment.list(map("payment",map("type","payment_intent","payment_intent",paymentIntent),"limit",100),options(null)));
    }
    public JsonNode subscription(String id) { return call(() -> Subscription.retrieve(id,options(null))); }
    private interface Request { StripeObject run() throws StripeException; }
    private JsonNode call(Request request) {
        try { return mapper.readTree(request.run().toJson()); }
        catch(Exception e) {
            // 不向浏览器暴露供应商响应、请求参数或凭据。
            throw new ApiException(HttpStatus.BAD_GATEWAY,"BILLING_PROVIDER_UNAVAILABLE","Billing could not confirm this request. Please retry.",true,null);
        }
    }
    public static Map<String,Object> map(Object... pairs) {
        Map<String,Object> result=new LinkedHashMap<>();
        for(int i=0;i<pairs.length;i+=2) result.put((String)pairs[i],pairs[i+1]);
        return result;
    }
}
