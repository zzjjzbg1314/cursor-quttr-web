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
 @Test void identityAlwaysComesFromAuthenticatedSession(){
  MusicBillingService billing=mock(MusicBillingService.class);MusicMvAuthService auth=mock(MusicMvAuthService.class);
  MusicMvRenderClientAuthenticationService client=mock(MusicMvRenderClientAuthenticationService.class);
  MusicBillingController controller=new MusicBillingController(billing,auth,client);MockHttpServletRequest req=new MockHttpServletRequest();
  when(auth.requireUserId(req)).thenReturn("owner");Map<String,String> body=new HashMap<>();body.put("plan","starter");body.put("locale","en");body.put("userId","attacker");
  controller.checkout(body,req);verify(billing).checkout("owner","starter","en");
  when(auth.requireUserId(req)).thenThrow(new ApiException(HttpStatus.UNAUTHORIZED,"AUTH","Sign in"));
  assertThatThrownBy(()->controller.portal(body,req)).isInstanceOf(ApiException.class);verify(billing,never()).portal(anyString(),anyString());
 }
}
