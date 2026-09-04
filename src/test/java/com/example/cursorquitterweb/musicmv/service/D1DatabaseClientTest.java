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
    void doesNotRetryMutatingQueries() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("read timed out"));

        assertThrows(ResourceAccessException.class,
                () -> client.query("UPDATE sample SET value=1"));
        verify(restTemplate, times(1)).exchange(any(String.class), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class));
    }
}
