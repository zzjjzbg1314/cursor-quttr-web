package com.example.cursorquitterweb.musicmv.billing;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import org.springframework.mock.web.MockHttpServletRequest;
import com.example.cursorquitterweb.musicmv.service.*;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import org.springframework.http.HttpStatus;
import static com.example.cursorquitterweb.musicmv.billing.StripeGateway.map;
import java.util.*;
class MusicBillingControllerTest {
 @Test void missingSessionReturnsUnauthorizedThroughScopedHandler() throws Exception {
  MusicBillingService billing=mock(MusicBillingService.class);MusicMvAuthService auth=mock(MusicMvAuthService.class);
  when(auth.requireFreshUserId(any())).thenThrow(new ApiException(HttpStatus.UNAUTHORIZED,"AUTH_REQUIRED","Sign in"));
  org.springframework.test.web.servlet.MockMvc mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new MusicBillingController(billing,auth,mock(MusicMvRenderClientAuthenticationService.class)))
   .setControllerAdvice(new com.example.cursorquitterweb.musicmv.controller.MusicMvExceptionHandler()).build();
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/music-mv/v1/billing/history"))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized())
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("AUTH_REQUIRED"));
  verifyNoInteractions(billing);
 }

 @Test void identityAlwaysComesFromAuthenticatedSession(){
  MusicBillingService billing=mock(MusicBillingService.class);MusicMvAuthService auth=mock(MusicMvAuthService.class);
  MusicMvRenderClientAuthenticationService client=mock(MusicMvRenderClientAuthenticationService.class);
  MusicBillingController controller=new MusicBillingController(billing,auth,client);MockHttpServletRequest req=new MockHttpServletRequest();
  when(auth.requireFreshUserId(req)).thenReturn("owner");Map<String,String> body=new HashMap<>();body.put("plan","starter");body.put("locale","en");body.put("userId","attacker");
  controller.history(0,req);verify(billing).historyPage("owner","all",null);
  controller.usage(req);verify(billing).usage("owner");
  body.put("id","notice");controller.acknowledgeWelcomeNotice(body,req);verify(billing).acknowledgeWelcomeNotice("owner","notice");
  controller.credits(req);verify(billing).credits("owner");
  controller.checkout(body,req);verify(billing).checkout("owner","starter","en");
  when(auth.requireFreshUserId(req)).thenThrow(new ApiException(HttpStatus.UNAUTHORIZED,"AUTH","Sign in"));
  assertThatThrownBy(()->controller.credits(req)).isInstanceOf(ApiException.class);verify(billing,times(1)).credits(anyString());
  assertThatThrownBy(()->controller.portal(body,req)).isInstanceOf(ApiException.class);verify(billing,never()).portal(anyString(),anyString());
 }
}
