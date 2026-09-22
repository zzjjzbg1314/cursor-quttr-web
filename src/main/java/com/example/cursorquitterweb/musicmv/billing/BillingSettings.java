package com.example.cursorquitterweb.musicmv.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import org.springframework.http.HttpStatus;

@Component
public class BillingSettings {
    @Value("${music-mv.billing.enabled:false}") public boolean enabled;
    @Value("${STRIPE_SECRET_KEY:}") public String secretKey;
    @Value("${STRIPE_WEBHOOK_SECRET:}") public String webhookSecret;
    @Value("${STRIPE_STARTER_PRICE_ID:}") public String starterPrice;
    @Value("${STRIPE_CREATOR_PRICE_ID:}") public String creatorPrice;
    @Value("${STRIPE_PORTAL_CONFIGURATION_ID:}") public String portalConfiguration;
    @Value("${MUSIC_MV_BILLING_SITE_URL:http://localhost:3000}") public String siteUrl;

    public boolean ready() {
        return enabled && secretKey != null && (secretKey.startsWith("sk_test_") || secretKey.startsWith("rk_test_"))
                && present(webhookSecret,"whsec_") && present(starterPrice,"price_")
                && present(creatorPrice,"price_") && present(portalConfiguration,"bpc_");
    }
    public void requireReady() {
        if (!ready()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"BILLING_UNAVAILABLE","Billing is not available.");
    }
    public String price(String plan) {
        if ("starter".equals(plan)) return starterPrice;
        if ("creator".equals(plan)) return creatorPrice;
        throw new ApiException(HttpStatus.BAD_REQUEST,"BILLING_PLAN_INVALID","Unknown plan.");
    }
    public int allowance(String price) {
        if (present(starterPrice,"price_") && starterPrice.equals(price)) return 30;
        if (present(creatorPrice,"price_") && creatorPrice.equals(price)) return 100;
        return 0;
    }
    public String returnUrl(String locale) {
        java.net.URI uri=java.net.URI.create(siteUrl);
        boolean local="http".equals(uri.getScheme()) && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
        if ((!local && !"https".equals(uri.getScheme())) || uri.getHost()==null || uri.getUserInfo()!=null
                || uri.getQuery()!=null || uri.getFragment()!=null) throw new IllegalStateException("Invalid billing site URL");
        return siteUrl.replaceAll("/+$", "")+("zh-cn".equals(locale)?"/zh-cn":"")+"/pricing";
    }
    private boolean present(String value,String prefix) { return value!=null && value.startsWith(prefix) && value.length()>prefix.length(); }
}
