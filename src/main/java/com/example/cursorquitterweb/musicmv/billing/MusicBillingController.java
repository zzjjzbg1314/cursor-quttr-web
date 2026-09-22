package com.example.cursorquitterweb.musicmv.billing;

import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.example.cursorquitterweb.musicmv.service.MusicMvAuthService;
import com.example.cursorquitterweb.musicmv.service.MusicMvRenderClientAuthenticationService;
import static com.example.cursorquitterweb.musicmv.billing.StripeGateway.map;

@RestController
@ConditionalOnProperty(prefix="music-mv",name="enabled",havingValue="true")
@RequestMapping("/api/music-mv/v1/billing")
public class MusicBillingController {
    private final MusicBillingService billing;
    private final MusicMvAuthService auth;
    private final MusicMvRenderClientAuthenticationService clientAuth;
    public MusicBillingController(MusicBillingService billing,MusicMvAuthService auth,MusicMvRenderClientAuthenticationService clientAuth) {
        this.billing=billing;this.auth=auth;this.clientAuth=clientAuth;
    }
    @GetMapping("/capabilities") public Map<String,Object> capabilities() { return billing.capabilities(); }
    @GetMapping("/status") public Map<String,Object> status(HttpServletRequest request) { return billing.status(owner(request)); }
    @PostMapping("/checkout") public Map<String,Object> checkout(@RequestBody Map<String,String> body,HttpServletRequest request) {
        return billing.checkout(owner(request),body.get("plan"),body.get("locale"));
    }
    @PostMapping("/portal") public Map<String,Object> portal(@RequestBody Map<String,String> body,HttpServletRequest request) {
        return billing.portal(owner(request),body.get("locale"));
    }
    @PostMapping("/webhook") public Map<String,Object> webhook(@RequestBody String payload,@RequestHeader(value="Stripe-Signature",required=false) String signature) {
        billing.webhook(payload,signature); return map("received",true);
    }
    private String owner(HttpServletRequest request) {
        clientAuth.requireAuthorized(request.getHeader("X-Music-Mv-Client-Token"));
        return auth.requireUserId(request);
    }
}
