package com.example.cursorquitterweb.musicmv.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

class D1DatabaseClientTest {
    private RestTemplate restTemplate;
    private D1DatabaseClient client;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        client = new D1DatabaseClient(new ObjectMapper(), restTemplate);
        ReflectionTestUtils.setField(client, "enabled", true);
        ReflectionTestUtils.setField(client, "baseUrl", "https://api.cloudflare.test/client/v4");
        ReflectionTestUtils.setField(client, "accountId", "account");
        ReflectionTestUtils.setField(client, "databaseId", "database");
        ReflectionTestUtils.setField(client, "apiToken", "token");
    }

    @Test
    void retriesOneTransientReadFailure() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("read timed out"))
                .thenReturn(new ResponseEntity<String>(
                        "{\"success\":true,\"result\":[{\"success\":true,\"results\":[{\"value\":1}],\"meta\":{}}]}",
                        HttpStatus.OK));

        assertEquals(1, ((Number) client.query("SELECT value FROM sample").firstRow()
                .get("value")).intValue());
        verify(restTemplate, times(2)).exchange(any(String.class), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class));
    }

    @Test
    void preservesExecutionStatisticsWithoutExposingQueryValuesAsMetricTags() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<String>("{\"success\":true,\"result\":[{\"success\":true,\"results\":[],\"meta\":{\"duration\":1.25,\"rows_read\":42,\"rows_written\":2}}]}",HttpStatus.OK));
        D1QueryResult result=client.query("SELECT ?", "private-value");
        assertEquals(1.25,result.getDurationMs()); assertEquals(42,result.getRowsRead()); assertEquals(2,result.getRowsWritten());
        io.micrometer.core.instrument.Metrics.globalRegistry.getMeters().stream()
                .filter(m -> m.getId().getName().startsWith("music.mv.d1"))
                .forEach(m -> org.junit.jupiter.api.Assertions.assertFalse(m.getId().toString().contains("private-value")));
    }

    @Test
    void doesNotRetryMutatingQueries() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("read timed out"));

        assertThrows(ResourceAccessException.class,
                () -> client.query("UPDATE sample SET value=1"));
        verify(restTemplate, times(1)).exchange(any(String.class), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class));
    }
    @Test
    void billingBatchUsesOneHttpRequestAndPreservesOrderedResultsWithoutRetryingWrites() {
        when(restTemplate.exchange(any(String.class),eq(HttpMethod.POST),any(HttpEntity.class),eq(String.class)))
            .thenReturn(new ResponseEntity<String>("{\"success\":true,\"result\":[{\"success\":true,\"results\":[]},{\"success\":true,\"results\":[]},{\"success\":true,\"results\":[{\"remaining\":10}]}]}",HttpStatus.OK));
        java.util.List<D1Statement> statements=java.util.Arrays.asList(
            D1Statement.of("INSERT INTO grants VALUES(?)","u"),
            D1Statement.of("UPDATE reservations SET state=?","consumed"),
            D1Statement.of("SELECT remaining FROM grants WHERE user_id=?","u"));
        java.util.List<D1QueryResult> results=client.batch(statements);
        assertEquals(3,results.size());assertEquals(10,((Number)results.get(2).firstRow().get("remaining")).intValue());
        org.mockito.ArgumentCaptor<HttpEntity> request=org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate,times(1)).exchange(any(String.class),eq(HttpMethod.POST),request.capture(),eq(String.class));
        java.util.Map<?,?> body=(java.util.Map<?,?>)request.getValue().getBody();
        java.util.List<?> batch=(java.util.List<?>)body.get("batch");
        assertEquals(3,batch.size());assertEquals(statements.get(0).getSql(),((java.util.Map<?,?>)batch.get(0)).get("sql"));
        org.mockito.Mockito.reset(restTemplate);
        when(restTemplate.exchange(any(String.class),eq(HttpMethod.POST),any(HttpEntity.class),eq(String.class)))
            .thenThrow(new ResourceAccessException("timeout"));
        assertThrows(ResourceAccessException.class,()->client.batch(statements));
        verify(restTemplate,times(1)).exchange(any(String.class),eq(HttpMethod.POST),any(HttpEntity.class),eq(String.class));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"missing-result","statement-failed","timeout"})
    void billingCreditsPropagatesIncompleteFailedOrTimedOutBatch(String scenario) {
        com.example.cursorquitterweb.musicmv.billing.BillingSettings settings=new com.example.cursorquitterweb.musicmv.billing.BillingSettings();settings.enabled=true;
        com.example.cursorquitterweb.musicmv.billing.StripeGateway stripe=mock(com.example.cursorquitterweb.musicmv.billing.StripeGateway.class);
        com.example.cursorquitterweb.musicmv.billing.MusicBillingService billing=new com.example.cursorquitterweb.musicmv.billing.MusicBillingService(settings,
            new com.example.cursorquitterweb.musicmv.billing.BillingRepository(client),stripe,new ObjectMapper());
        if(scenario.equals("timeout"))when(restTemplate.exchange(any(String.class),eq(HttpMethod.POST),any(HttpEntity.class),eq(String.class)))
            .thenThrow(new ResourceAccessException("测试批次读取超时"));
        else {
            String results=scenario.equals("missing-result")?"[{\"success\":true,\"results\":[]},{\"success\":true,\"results\":[]}]":
                "[{\"success\":true,\"results\":[]},{\"success\":false,\"error\":\"fixture statement failure\"},{\"success\":true,\"results\":[{\"remaining\":10}]}]";
            when(restTemplate.exchange(any(String.class),eq(HttpMethod.POST),any(HttpEntity.class),eq(String.class)))
                .thenReturn(new ResponseEntity<String>("{\"success\":true,\"result\":"+results+"}",HttpStatus.OK));
        }
        org.assertj.core.api.Assertions.assertThatThrownBy(()->billing.credits("u")).isInstanceOf(RuntimeException.class);
        verify(restTemplate,times(1)).exchange(any(String.class),eq(HttpMethod.POST),any(HttpEntity.class),eq(String.class));
        org.mockito.Mockito.verifyNoInteractions(stripe);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"missing-rows","empty-rows","object-rows"})
    void I19_RISK_emptyOrMalformedBalanceResultReturnsSuccessfulZero(String shape) {
        String last=shape.equals("missing-rows")?"{\"success\":true}":shape.equals("empty-rows")?"{\"success\":true,\"results\":[]}":"{\"success\":true,\"results\":{\"remaining\":999}}";
        when(restTemplate.exchange(any(String.class),eq(HttpMethod.POST),any(HttpEntity.class),eq(String.class)))
            .thenReturn(new ResponseEntity<String>("{\"success\":true,\"result\":[{\"success\":true,\"results\":[]},{\"success\":true,\"results\":[]},"+last+"]}",HttpStatus.OK));
        com.example.cursorquitterweb.musicmv.billing.BillingSettings settings=new com.example.cursorquitterweb.musicmv.billing.BillingSettings();settings.enabled=true;
        com.example.cursorquitterweb.musicmv.billing.StripeGateway stripe=mock(com.example.cursorquitterweb.musicmv.billing.StripeGateway.class);
        com.example.cursorquitterweb.musicmv.billing.MusicBillingService billing=new com.example.cursorquitterweb.musicmv.billing.MusicBillingService(settings,
            new com.example.cursorquitterweb.musicmv.billing.BillingRepository(client),stripe,new ObjectMapper());
        org.assertj.core.api.Assertions.assertThat(billing.credits("u")).containsEntry("remaining",0).containsEntry("allowance",0).containsEntry("enabled",true);
        verify(restTemplate,times(1)).exchange(any(String.class),eq(HttpMethod.POST),any(HttpEntity.class),eq(String.class));
        org.mockito.Mockito.verifyNoInteractions(stripe);
    }

}
