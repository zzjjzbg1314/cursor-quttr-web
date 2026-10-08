package com.example.cursorquitterweb.musicmv.billing;

import com.example.cursorquitterweb.musicmv.service.MusicMvAuthService;
import com.example.cursorquitterweb.musicmv.service.MusicMvRenderClientAuthenticationService;
import com.example.cursorquitterweb.musicmv.controller.MusicMvExceptionHandler;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static com.example.cursorquitterweb.musicmv.billing.StripeGateway.map;

class BillingScenarioHttpRegressionTest {
    MusicBillingService billing;
    MusicMvAuthService auth;
    MusicMvRenderClientAuthenticationService client;
    MockMvc mvc;

    @BeforeEach void setup() {
        billing=mock(MusicBillingService.class);auth=mock(MusicMvAuthService.class);client=mock(MusicMvRenderClientAuthenticationService.class);
        mvc=MockMvcBuilders.standaloneSetup(new MusicBillingController(billing,auth,client)).setControllerAdvice(new MusicMvExceptionHandler()).build();
    }

    @ParameterizedTest(name="B01 protectedEndpoint={0}")
    @ValueSource(strings={"credits","history","usage","status","checkout","portal","welcome-notice/ack"})
    void everyProtectedEndpointRequiresUser(String endpoint) throws Exception {
        when(auth.requireUserId(any())).thenThrow(new ApiException(HttpStatus.UNAUTHORIZED,"AUTH_REQUIRED","Sign in"));
        mvc.perform(request(endpoint)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        verifyNoInteractions(billing);
    }

    @ParameterizedTest(name="B03 clientCredentialEndpoint={0}")
    @ValueSource(strings={"credits","history","usage","status","checkout","portal","welcome-notice/ack"})
    void everyProtectedEndpointRequiresClientBeforeUser(String endpoint) throws Exception {
        doThrow(new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_MV_RENDER_CLIENT_TOKEN","Invalid credential")).when(client).requireAuthorized(any());
        mvc.perform(request(endpoint)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_MV_RENDER_CLIENT_TOKEN"));
        verifyNoInteractions(billing,auth);
    }

    @ParameterizedTest(name="B08 invalidBodyEndpoint={0}")
    @ValueSource(strings={"checkout","portal","welcome-notice/ack"})
    void malformedOrAbsentBodyCannotReachBilling(String endpoint) throws Exception {
        mvc.perform(post(base(endpoint)).contentType("application/json").content("{")).andExpect(status().isBadRequest());
        mvc.perform(post(base(endpoint)).contentType("application/json")).andExpect(status().isBadRequest());
        verifyNoInteractions(billing);
    }

    @Test void B08_nonNumericOffsetAndWrongMethodAreRejected() throws Exception {
        mvc.perform(get(base("history")).param("offset","abc")).andExpect(status().isBadRequest());
        mvc.perform(get(base("checkout"))).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(billing);
    }

    @Test void B04_allReadAndManagementOperationsUseAuthenticatedOwner() throws Exception {
        when(auth.requireUserId(any())).thenReturn("owner");
        mvc.perform(get(base("credits")).param("userId","attacker")).andExpect(status().isOk());
        mvc.perform(get(base("history")).param("userId","attacker")).andExpect(status().isOk());
        mvc.perform(get(base("usage")).param("userId","attacker")).andExpect(status().isOk());
        mvc.perform(get(base("status")).param("checkout_session_id","cs_test_1").param("userId","attacker")).andExpect(status().isOk());
        mvc.perform(post(base("checkout")).contentType("application/json").content("{\"plan\":\"starter\",\"locale\":\"ja\",\"userId\":\"attacker\",\"priceId\":\"price_fake\",\"credits\":\"10000\"}")).andExpect(status().isOk());
        mvc.perform(post(base("portal")).contentType("application/json").content("{\"locale\":\"fr\",\"userId\":\"attacker\",\"customerId\":\"cus_attacker\"}")).andExpect(status().isOk());
        verify(billing).credits("owner");verify(billing).history("owner",0,"all");verify(billing).usage("owner");verify(billing).status("owner","cs_test_1");
        verify(billing).checkout("owner","starter","ja");verify(billing).portal("owner","fr");
    }

    @Test void B10_publicCapabilitiesAndWebhookDoNotRequireWebsiteSession() throws Exception {
        when(billing.capabilities()).thenReturn(map("available",true));
        mvc.perform(get(base("capabilities"))).andExpect(status().isOk());
        mvc.perform(post(base("webhook")).contentType("application/json").header("Stripe-Signature","fixture-signature").content(" {\"fixture\":true} ")).andExpect(status().isOk()).andExpect(jsonPath("$.received").value(true));
        verify(billing).webhook(" {\"fixture\":true} ","fixture-signature");verifyNoInteractions(client,auth);
    }

    @ParameterizedTest
    @ValueSource(ints={400,401,404,409,402,502,503})
    void B11_apiErrorPreservesStatusCodeRetryabilityAndDetails(int code) throws Exception {
        when(auth.requireUserId(any())).thenReturn("u");
        when(billing.status("u",null)).thenThrow(new ApiException(HttpStatus.valueOf(code),
            "BILLING_FIXTURE","Safe message",true,map("reference","fixture")));
        mvc.perform(get(base("status"))).andExpect(status().is(code))
            .andExpect(jsonPath("$.code").value("BILLING_FIXTURE"))
            .andExpect(jsonPath("$.retryable").value(true))
            .andExpect(jsonPath("$.details.reference").value("fixture"));
    }

    @Test void B11_RISK_unexpectedDatabaseErrorHasNoScopedSafeResponse() {
        when(auth.requireUserId(any())).thenReturn("u");
        when(billing.status("u",null)).thenThrow(new IllegalStateException("fixture-private-database-info"));
        org.assertj.core.api.Assertions.assertThatThrownBy(()->mvc.perform(get(base("status"))))
            .hasRootCauseInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage("fixture-private-database-info");
    }

    private String base(String endpoint) { return "/api/music-mv/v1/billing/"+endpoint; }
    private MockHttpServletRequestBuilder request(String endpoint) {
        if(endpoint.equals("checkout") || endpoint.equals("portal") || endpoint.equals("welcome-notice/ack"))
            return post(base(endpoint)).contentType("application/json").content("{\"plan\":\"starter\",\"locale\":\"en\"}");
        return get(base(endpoint));
    }
}
